import { WebSocketServer, WebSocket } from 'ws';
import type { Server } from 'http';
import jwt from 'jsonwebtoken';
import { config } from '../config';
import { query } from '../db';
import { verifyAccess } from '../middleware/auth';
import { log } from '../logger';

interface Client { ws: WebSocket; phone: string }
const online = new Map<string, Client>();

export function isOnline(phone: string): boolean {
  const c = online.get(phone);
  return !!c && c.ws.readyState === WebSocket.OPEN;
}

/** Фиристодани фаврӣ */
export function deliverRealtime(to: string, payload: object): boolean {
  const c = online.get(to);
  if (c && c.ws.readyState === WebSocket.OPEN) {
    c.ws.send(JSON.stringify(payload));
    return true;
  }
  return false;
}

async function resolvePhoneDb(token: string): Promise<string | null> {
  const viaJwt = verifyAccess(token);
  if (viaJwt) return viaJwt;
  try {
    const rows = await query('SELECT phone FROM users WHERE token = $1', [token]);
    if (rows.length) return rows[0].phone;
  } catch (e) {}
  return null;
}

export function initWs(server: Server) {
  const wss = new WebSocketServer({ server, path: '/ws' });

  wss.on('connection', async (ws, req) => {
    try {
      const url = new URL(req.url || '/', 'http://x');
      const token = url.searchParams.get('token') || '';
      const phone = await resolvePhoneDb(token);
      if (!phone) {
        ws.close();
        return;
      }
      online.set(phone, { ws, phone });
      log.info('WS online:', phone);
      ws.send(JSON.stringify({ type: 'hello', phone }));

      ws.on('message', async raw => {
        let m: any;
        try { m = JSON.parse(raw.toString()); } catch (e) { return; }
        try {
          const type = String(m.type || '');

          if (type === 'ping') return ws.send(JSON.stringify({ type: 'pong' }));

          if (type === 'message.send') {
            const to = String(m.to || '');
            const exists = await query('SELECT 1 FROM users WHERE phone = $1', [to]);
            if (exists.length === 0) return ws.send(JSON.stringify({ type: 'message.error', error: 'no_user', to }));
            const uuid = m.uuid ? String(m.uuid).slice(0, 64) : null;
            if (uuid) {
              const dup = await query('SELECT seq, ts FROM messages WHERE from_phone = $1 AND uuid = $2', [phone, uuid]);
              if (dup.length > 0) {
                ws.send(JSON.stringify({ type: 'message.ack', state: 'sent', seq: dup[0].seq, to, uuid, idempotent: true }));
                return;
              }
            }
            const seqRow = await query('SELECT COALESCE(MAX(seq),0)+1 AS s FROM messages');
            const seq = Number(seqRow[0].s);
            const ts = Date.now();
            await query(
              `INSERT INTO messages(from_phone, to_phone, body, enc, kind, url, gid, gname, uuid, seq, ts)
               VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
              [phone, to, String(m.body || '').slice(0, 8000), !!m.enc, String(m.kind || 'text').slice(0, 10),
               String(m.url || '').slice(0, 600), String(m.gid || '').slice(0, 20), String(m.gname || '').slice(0, 60),
               uuid, seq, ts]
            );
            ws.send(JSON.stringify({ type: 'message.ack', state: 'sent', seq, to, uuid: m.uuid || null }));
            const wire = { from: phone, body: m.body, enc: !!m.enc, kind: m.kind || 'text', url: m.url || '', gid: m.gid || '', gname: m.gname || '', ts, seq };
            if (!deliverRealtime(to, { type: 'message.new', message: wire })) {
              const pr = await query('SELECT push_token FROM users WHERE phone = $1', [to]);
              if (pr.length && pr[0].push_token) {
                try {
                  const push = await import('../services/push');
                  await push.pushTo(to, pr[0].push_token, phone, wire.kind === 'text' ? wire.body : '📎 ' + wire.kind);
                } catch (e) {}
              }
            }
            return;
          }

          if (type === 'message.ack') {
            const seq = Number(m.seq || 0);
            const state = m.state === 'read' ? 'read' : 'delivered';
            if (!seq) return;
            await query(
              'INSERT INTO message_acks(msg_seq, phone, state, ts) VALUES ($1,$2,$3,$4) ON CONFLICT (msg_seq, phone) DO UPDATE SET state=$3, ts=$4',
              [seq, phone, state, Date.now()]
            );
            const owner = await query('SELECT from_phone, to_phone FROM messages WHERE seq = $1', [seq]);
            if (owner.length) {
              const peer = owner[0].from_phone === phone ? owner[0].to_phone : owner[0].from_phone;
              deliverRealtime(peer, { type: 'message.ack', seq, state, by: phone });
            }
            return;
          }

          if (type === 'typing') {
            const to = String(m.to || '');
            deliverRealtime(to, { type: 'typing', from: phone });
            return;
          }

          if (type === 'presence') {
            // нақша: паҳн ба контактҳо
            return;
          }

          if (type === 'reaction') {
            const seq = Number(m.seq || 0);
            if (!seq) return;
            const emoji = String(m.emoji || '').slice(0, 16);
            if (emoji) {
              await query(
                'INSERT INTO message_reactions(msg_seq, phone, emoji, ts) VALUES ($1,$2,$3,$4) ON CONFLICT (msg_seq, phone) DO UPDATE SET emoji=$3, ts=$4',
                [seq, phone, emoji, Date.now()]
              );
            } else {
              await query('DELETE FROM message_reactions WHERE msg_seq = $1 AND phone = $2', [seq, phone]);
            }
            const owner = await query('SELECT from_phone, to_phone FROM messages WHERE seq = $1', [seq]);
            if (owner.length) {
              const peer = owner[0].from_phone === phone ? owner[0].to_phone : owner[0].from_phone;
              deliverRealtime(peer, { type: 'reaction', seq, emoji, by: phone });
            }
            return;
          }

          if (type === 'edit') {
            const seq = Number(m.seq || 0);
            const owner = await query('SELECT from_phone, to_phone FROM messages WHERE seq = $1', [seq]);
            if (owner.length === 0 || owner[0].from_phone !== phone) return;
            await query('UPDATE messages SET edit_body = $2, edited = TRUE WHERE seq = $1', [seq, String(m.body || '').slice(0, 8000)]);
            deliverRealtime(owner[0].to_phone, { type: 'edit', seq, body: m.body });
            return;
          }

          if (type === 'delete') {
            const seq = Number(m.seq || 0);
            const owner = await query('SELECT from_phone, to_phone FROM messages WHERE seq = $1', [seq]);
            if (owner.length === 0 || owner[0].from_phone !== phone) return;
            if (m.for_all) {
              await query('UPDATE messages SET deleted_for_all = TRUE WHERE seq = $1', [seq]);
              deliverRealtime(owner[0].to_phone, { type: 'delete', seq });
            }
            return;
          }

          // ===== WebRTC signaling (call.*) =====
          if (type.startsWith('call.') || type === 'ice' || type === 'sdp') {
            const to = String(m.to || '');
            deliverRealtime(to, { type, from: phone, payload: m.payload ?? null, sdp: m.sdp ?? null, candidate: m.candidate ?? null });
            return;
          }
        } catch (e) {
          log.warn('WS frame error:', (e as Error).message);
        }
      });

      ws.on('close', () => {
        if (online.get(phone)?.ws === ws) online.delete(phone);
        log.info('WS offline:', phone);
      });
    } catch (e) {
      ws.close();
    }
  });
}

