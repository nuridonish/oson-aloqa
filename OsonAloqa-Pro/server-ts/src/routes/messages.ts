import { FastifyInstance } from 'fastify';
import { query } from '../db';
import { authGuard, normalizePhone } from '../middleware/auth';
import { deliverRealtime, isOnline } from '../realtime/ws';
import { pushTo } from '../services/push';

export default async function (app: FastifyInstance) {

  app.addHook('preHandler', authGuard);

  // ===== ФИРИСТОДАН (idempotency бо UUID, seq) =====
  app.post('/messages/send', async (req: any, reply) => {
    const b = (req.body || {}) as any;
    const to = normalizePhone(b.to);
    if (!to) return reply.code(400).send({ ok: false, error: 'bad_phone' });

    const exists = await query('SELECT 1 FROM users WHERE phone = $1', [to]);
    if (exists.length === 0) return reply.send({ ok: false, error: 'no_user' });

    // Idempotency: uuid-и клиент
    if (b.uuid) {
      const dup = await query('SELECT seq, ts FROM messages WHERE from_phone = $1 AND uuid = $2', [req.phone, String(b.uuid).slice(0, 64)]);
      if (dup.length > 0) return reply.send({ ok: true, idempotent: true, seq: dup[0].seq, ts: dup[0].ts });
    }

    const seqRow = await query('SELECT COALESCE(MAX(seq),0)+1 AS s FROM messages');
    const seq = Number(seqRow[0].s);
    const msg = {
      from: req.phone,
      body: String(b.body || '').slice(0, 8000),
      enc: !!b.enc,
      kind: String(b.kind || 'text').slice(0, 10),
      url: String(b.url || '').slice(0, 600),
      gid: String(b.gid || '').slice(0, 20),
      gname: String(b.gname || '').slice(0, 60),
      uuid: b.uuid ? String(b.uuid).slice(0, 64) : null,
      ts: Date.now()
    };
    await query(
      `INSERT INTO messages(from_phone, to_phone, body, enc, kind, url, gid, gname, uuid, seq, ts)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
      [msg.from, to, msg.body, msg.enc, msg.kind, msg.url, msg.gid, msg.gname, msg.uuid, seq, msg.ts]
    );

    // real-time + push
    const wire = { from: msg.from, body: msg.body, enc: msg.enc, kind: msg.kind, url: msg.url, gid: msg.gid, gname: msg.gname, ts: msg.ts, seq };
    if (!deliverRealtime(to, { type: 'message.new', message: wire })) {
      const pr = await query('SELECT push_token FROM users WHERE phone = $1', [to]);
      if (pr.length && pr[0].push_token) await pushTo(to, pr[0].push_token, msg.from, msg.kind === 'text' ? msg.body : '📎 ' + msg.kind);
    }
    return reply.send({ ok: true, seq, ts: msg.ts });
  });

  // ===== ПУЛ (legacy: delivered-batch) =====
  app.get('/messages/pull', async (req: any) => {
    const rows = await query(
      `SELECT from_phone AS "from", body, enc, kind, url, gid, gname, seq, ts
       FROM messages WHERE to_phone = $1 AND delivered = FALSE ORDER BY seq ASC`,
      [req.phone]
    );
    await query('UPDATE messages SET delivered = TRUE WHERE to_phone = $1 AND delivered = FALSE', [req.phone]);
    return { ok: true, messages: rows };
  });

  // ===== SYNC (v3: cursor last_seq, дигар тоза намешавад) =====
  app.get('/messages/sync', async (req: any, reply) => {
    const after = Number((req.query as any).after_seq || 0);
    const usePgMem = process.env.PGMEM === '1';
    const acksSel = usePgMem ? '' : `,
              (SELECT COALESCE(json_agg(json_build_object('phone', a.phone, 'state', a.state)))
                 FROM message_acks a WHERE a.msg_seq = messages.seq) AS acks`;
    const reactSel = usePgMem ? '' : `,
              (SELECT COALESCE(json_agg(json_build_object('phone', r.phone, 'emoji', r.emoji)))
                 FROM message_reactions r WHERE r.msg_seq = messages.seq) AS reactions`;
    const rows = await query(
      `SELECT from_phone AS "from", body, enc, kind, url, gid, gname, seq, ts,
              deleted_for_all, edit_body, edited${acksSel}${reactSel}
       FROM messages
       WHERE (to_phone = $1 OR from_phone = $1) AND seq > $2
       ORDER BY seq ASC LIMIT 500`,
      [req.phone, after]
    );
    const maxRow = await query('SELECT COALESCE(MAX(seq),0) AS m FROM messages');
    return reply.send({ ok: true, messages: rows, cursor: Number(maxRow[0].m) });
  });

  // ===== ACK (✓✓ / хондашуда) =====
  app.post('/messages/ack', async (req: any, reply) => {
    const b = (req.body || {}) as any;
    const seq = Number(b.seq || 0);
    const state = b.state === 'read' ? 'read' : 'delivered';
    if (!seq) return reply.code(400).send({ ok: false, error: 'bad_seq' });
    await query(
      'INSERT INTO message_acks(msg_seq, phone, state, ts) VALUES ($1,$2,$3,$4) ON CONFLICT (msg_seq, phone) DO UPDATE SET state = $3, ts = $4',
      [seq, req.phone, state, Date.now()]
    );
    const owner = await query('SELECT from_phone, to_phone FROM messages WHERE seq = $1', [seq]);
    if (owner.length) {
      const peer = owner[0].from_phone === req.phone ? owner[0].to_phone : owner[0].from_phone;
      deliverRealtime(peer, { type: 'message.ack', seq, state, by: req.phone });
    }
    return { ok: true };
  });

  // ===== REACTION =====
  app.post('/messages/reaction', async (req: any, reply) => {
    const b = (req.body || {}) as any;
    const seq = Number(b.seq || 0);
    if (!seq) return reply.code(400).send({ ok: false, error: 'bad_seq' });
    const emoji = String(b.emoji || '').slice(0, 16);
    if (emoji) {
      await query(
        'INSERT INTO message_reactions(msg_seq, phone, emoji, ts) VALUES ($1,$2,$3,$4) ON CONFLICT (msg_seq, phone) DO UPDATE SET emoji = $3, ts = $4',
        [seq, req.phone, emoji, Date.now()]
      );
    } else {
      await query('DELETE FROM message_reactions WHERE msg_seq = $1 AND phone = $2', [seq, req.phone]);
    }
    const owner = await query('SELECT from_phone, to_phone FROM messages WHERE seq = $1', [seq]);
    if (owner.length) {
      const peer = owner[0].from_phone === req.phone ? owner[0].to_phone : owner[0].from_phone;
      deliverRealtime(peer, { type: 'reaction', seq, emoji, by: req.phone });
    }
    return { ok: true };
  });

  // ===== EDIT / DELETE =====
  app.post('/messages/edit', async (req: any, reply) => {
    const b = (req.body || {}) as any;
    const seq = Number(b.seq || 0);
    const rows = await query('SELECT from_phone FROM messages WHERE seq = $1', [seq]);
    if (rows.length === 0 || rows[0].from_phone !== req.phone) return reply.code(403).send({ ok: false, error: 'forbidden' });
    await query('UPDATE messages SET edit_body = $2, edited = TRUE WHERE seq = $1', [seq, String(b.body || '').slice(0, 8000)]);
    const to = rows[0].from_phone === req.phone ? (await query('SELECT to_phone FROM messages WHERE seq = $1', [seq]))[0]?.to_phone : null;
    if (to) deliverRealtime(to, { type: 'edit', seq, body: String(b.body || '') });
    return { ok: true };
  });

  app.post('/messages/delete', async (req: any, reply) => {
    const b = (req.body || {}) as any;
    const seq = Number(b.seq || 0);
    const rows = await query('SELECT from_phone, to_phone FROM messages WHERE seq = $1', [seq]);
    if (rows.length === 0) return reply.code(404).send({ ok: false, error: 'not_found' });
    const forAll = !!b.for_all && rows[0].from_phone === req.phone;
    if (forAll) {
      await query('UPDATE messages SET deleted_for_all = TRUE WHERE seq = $1', [seq]);
      deliverRealtime(rows[0].to_phone, { type: 'delete', seq });
    } else {
      // танҳо барои худ: (client-side) — сервер пинҳон мекунад барои ин корбар
      await query(
        'INSERT INTO message_acks(msg_seq, phone, state, ts) VALUES ($1,$2,$3,$4) ON CONFLICT (msg_seq, phone) DO UPDATE SET state = $3, ts = $4',
        [seq, req.phone, 'deleted_self', Date.now()]
      );
    }
    return { ok: true };
  });

  // ===== TYPING (тавассути sync/REST барои захира) =====
  app.post('/messages/typing', async (req: any) => {
    const to = normalizePhone((req.body || {}).to);
    if (to && isOnline(to)) deliverRealtime(to, { type: 'typing', from: req.phone });
    return { ok: true };
  });
}

