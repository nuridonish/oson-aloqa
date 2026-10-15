import { FastifyInstance } from 'fastify';
import crypto from 'crypto';
import { query } from '../db';
import { authGuard, normalizePhone } from '../middleware/auth';
import { deliverRealtime, isOnline } from '../realtime/ws';
import { pushTo } from '../services/push';

export default async function (app: FastifyInstance) {

  app.addHook('preHandler', authGuard);

  app.post('/groups/create', async (req: any, reply) => {
    const name = String((req.body || {}).name || '').slice(0, 60);
    const type = (req.body || {}).type === 'channel' ? 'channel' : 'group';
    const avatar = String((req.body || {}).avatar || '').slice(0, 600);
    if (!name) return reply.code(400).send({ ok: false, error: 'bad_name' });
    const id = crypto.randomBytes(6).toString('hex');
    await query('INSERT INTO groups(id, name, type, owner, avatar, created_at) VALUES ($1,$2,$3,$4,$5,$6)', [
      id, name, type, (req as any).phone, avatar, Date.now()
    ]);
    await query("INSERT INTO group_members(group_id, phone, role, joined_at) VALUES ($1,$2,'admin',$3)", [id, (req as any).phone, Date.now()]);
    return { ok: true, id, name, type };
  });

  app.post('/groups/join', async (req: any, reply) => {
    const id = String((req.body || {}).id || '').slice(0, 20);
    const rows = await query('SELECT id, name, type FROM groups WHERE id = $1', [id]);
    if (rows.length === 0) return reply.send({ ok: false, error: 'not_found' });
    await query(
      "INSERT INTO group_members(group_id, phone, role, joined_at) VALUES ($1,$2,'member',$3) ON CONFLICT (group_id, phone) DO NOTHING",
      [id, (req as any).phone, Date.now()]
    );
    return { ok: true, group: rows[0] };
  });

  app.post('/groups/add', async (req: any, reply) => {
    const id = String((req.body || {}).id || '').slice(0, 20);
    const phone = normalizePhone((req.body || {}).phone);
    if (!phone) return reply.code(400).send({ ok: false, error: 'bad_phone' });
    const g = await query("SELECT owner FROM groups WHERE id = $1", [id]);
    if (g.length === 0) return reply.send({ ok: false, error: 'not_found' });
    const me = await query("SELECT role FROM group_members WHERE group_id = $1 AND phone = $2", [id, (req as any).phone]);
    if (me.length === 0 || !['owner', 'admin'].includes(me[0].role)) return reply.send({ ok: false, error: 'forbidden' });
    await query(
      "INSERT INTO group_members(group_id, phone, role, joined_at) VALUES ($1,$2,'member',$3) ON CONFLICT (group_id, phone) DO NOTHING",
      [id, phone, Date.now()]
    );
    return { ok: true };
  });

  app.post('/groups/remove', async (req: any, reply) => {
    const id = String((req.body || {}).id || '').slice(0, 20);
    const phone = normalizePhone((req.body || {}).phone);
    if (!phone) return reply.code(400).send({ ok: false, error: 'bad_phone' });
    const g = await query('SELECT owner FROM groups WHERE id = $1', [id]);
    if (g.length === 0) return reply.send({ ok: false, error: 'not_found' });
    if (g[0].owner !== (req as any).phone && phone !== (req as any).phone) return reply.send({ ok: false, error: 'forbidden' });
    await query("DELETE FROM group_members WHERE group_id = $1 AND phone = $2", [id, phone]);
    return { ok: true };
  });

  app.post('/groups/promote', async (req: any, reply) => {
    const id = String((req.body || {}).id || '').slice(0, 20);
    const phone = normalizePhone((req.body || {}).phone);
    const g = await query('SELECT owner FROM groups WHERE id = $1', [id]);
    if (g.length === 0 || g[0].owner !== (req as any).phone) return reply.send({ ok: false, error: 'forbidden' });
    await query("UPDATE group_members SET role = 'admin' WHERE group_id = $1 AND phone = $2", [id, phone]);
    return { ok: true };
  });

  app.get('/groups/info', async (req: any, reply) => {
    const id = String((req.query as any).id || '').slice(0, 20);
    const g = await query('SELECT id, name, type, owner, avatar FROM groups WHERE id = $1', [id]);
    if (g.length === 0) return reply.send({ ok: false, error: 'not_found' });
    const members = await query(
      `SELECT m.phone, m.role, u.name FROM group_members m LEFT JOIN users u ON u.phone = m.phone WHERE m.group_id = $1`,
      [id]
    );
    return { ok: true, group: g[0], members };
  });

  app.post('/groups/avatar', async (req: any, reply) => {
    const id = String((req.body || {}).id || '').slice(0, 20);
    await query('UPDATE groups SET avatar = $2 WHERE id = $1', [id, String((req.body || {}).avatar || '').slice(0, 600)]);
    return { ok: true };
  });

  app.get('/groups/my', async (req: any) => {
    const rows = await query(
      `SELECT g.id, g.name, g.type, g.owner, g.avatar, m.role AS my_role
       FROM groups g JOIN group_members m ON m.group_id = g.id AND m.phone = $1
       ORDER BY g.created_at DESC`,
      [(req as any).phone]
    );
    return { ok: true, groups: rows };
  });

  // Канал: танҳо owner/admin менависанд
  app.post('/groups/send', async (req: any, reply) => {
    const id = String((req.body || {}).id || '').slice(0, 20);
    const g = await query('SELECT name, type, owner FROM groups WHERE id = $1', [id]);
    if (g.length === 0) return reply.send({ ok: false, error: 'not_found' });
    const member = await query(
      "SELECT role FROM group_members WHERE group_id = $1 AND phone = $2",
      [id, (req as any).phone]
    );
    if (member.length === 0) return reply.send({ ok: false, error: 'forbidden' });
    if (g[0].type === 'channel' && !['owner', 'admin'].includes(member[0].role)) {
      return reply.send({ ok: false, error: 'channel_readonly' });
    }

    const members = await query('SELECT phone FROM group_members WHERE group_id = $1', [id]);
    const base = {
      body: String((req.body || {}).body || '').slice(0, 8000),
      enc: !!((req.body || {}).enc),
      kind: String((req.body || {}).kind || 'text').slice(0, 10),
      url: String((req.body || {}).url || '').slice(0, 600),
      gid: id,
      gname: g[0].name,
      ts: Date.now()
    };
    for (const m of members) {
      if (m.phone === (req as any).phone) continue;
      const seqRow = await query('SELECT COALESCE(MAX(seq),0)+1 AS s FROM messages');
      const seq = Number(seqRow[0].s);
      await query(
        `INSERT INTO messages(from_phone, to_phone, body, enc, kind, url, gid, gname, seq, ts)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`,
        [(req as any).phone, m.phone, base.body, base.enc, base.kind, base.url, base.gid, base.gname, seq, base.ts]
      );
      const wire = { from: (req as any).phone, ...base, seq };
      if (!deliverRealtime(m.phone, { type: 'message.new', message: wire })) {
        const pr = await query('SELECT push_token FROM users WHERE phone = $1', [m.phone]);
        if (pr.length && pr[0].push_token) {
          await pushTo(m.phone, pr[0].push_token, base.gname, base.kind === 'text' ? base.body : '📎 ' + base.kind);
        }
      }
    }
    return { ok: true };
  });

  app.get('/presence', async (req: any, reply) => {
    const phone = normalizePhone((req.query as any).phone);
    if (!phone) return reply.code(400).send({ ok: false, error: 'bad_phone' });
    return { ok: true, online: isOnline(phone) };
  });

  app.post('/status', async (req: any, reply) => {
    const text = String((req.body || {}).text || '').slice(0, 300);
    const media = String((req.body || {}).media || '').slice(0, 600);
    if (!text && !media) return reply.code(400).send({ ok: false, error: 'empty' });
    await query('INSERT INTO statuses(phone, text, media, ts) VALUES ($1,$2,$3,$4)', [
      (req as any).phone, text, media, Date.now()
    ]);
    return { ok: true };
  });

  app.get('/status/feed', async (req: any) => {
    const since = Date.now() - 24 * 60 * 60 * 1000;
    const rows = await query(
      `SELECT s.id, s.phone, s.text, s.media, s.ts, u.name FROM statuses s
       LEFT JOIN users u ON u.phone = s.phone
       WHERE s.ts > $1 ORDER BY s.ts DESC LIMIT 100`,
      [since]
    );
    return { ok: true, statuses: rows };
  });

  app.post('/status/view', async (req: any, reply) => {
    const id = Number((req.body || {}).id || 0);
    if (!id) return reply.code(400).send({ ok: false, error: 'bad_id' });
    const rows = await query('SELECT views FROM statuses WHERE id = $1', [id]);
    if (rows.length === 0) return reply.send({ ok: false, error: 'not_found' });
    const viewers = (rows[0].views || '').split(',').filter(Boolean);
    if (!viewers.includes((req as any).phone)) viewers.push((req as any).phone);
    await query('UPDATE statuses SET views = $2 WHERE id = $1', [id, viewers.join(',')]);
    return { ok: true, views: viewers.length };
  });

  app.get('/status/views', async (req: any, reply) => {
    const id = Number((req.query as any).id || 0);
    const rows = await query('SELECT views FROM statuses WHERE id = $1', [id]);
    if (rows.length === 0) return reply.send({ ok: false, error: 'not_found' });
    const arr = (rows[0].views || '').split(',').filter(Boolean);
    return reply.send({ ok: true, viewers: arr, count: arr.length });
  });

  app.get('/messages/unread', async (req: any) => {
    const rows = await query(
      `SELECT from_phone, COUNT(*) AS cnt FROM messages
       WHERE to_phone = $1 AND delivered = FALSE AND gid = ''
       GROUP BY from_phone`,
      [(req as any).phone]
    );
    return { ok: true, unread: rows };
  });
}
