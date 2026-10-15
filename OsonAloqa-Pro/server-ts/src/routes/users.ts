import { FastifyInstance } from 'fastify';
import { query } from '../db';
import { authGuard, detectOperator } from '../middleware/auth';

export default async function (app: FastifyInstance) {

  app.addHook('preHandler', authGuard);

  app.get('/users/me', async (req: any, reply) => {
    const rows = await query('SELECT phone, name, about FROM users WHERE phone = $1', [req.phone]);
    if (rows.length === 0) return reply.code(404).send({ ok: false, error: 'not_found' });
    const u = rows[0];
    return { ok: true, profile: { phone: u.phone, name: u.name, operator: detectOperator(u.phone), about: u.about } };
  });

  app.put('/users/me', async (req: any, reply) => {
    const b = (req.body || {}) as any;
    if (b.name) await query('UPDATE users SET name = $2 WHERE phone = $1', [req.phone, String(b.name).slice(0, 30)]);
    if (typeof b.about === 'string') await query('UPDATE users SET about = $2 WHERE phone = $1', [req.phone, b.about.slice(0, 200)]);
    if (b.pushToken) await query('UPDATE users SET push_token = $2 WHERE phone = $1', [req.phone, String(b.pushToken).slice(0, 200)]);
    const rows = await query('SELECT phone, name, about FROM users WHERE phone = $1', [req.phone]);
    const u = rows[0];
    return { ok: true, profile: { phone: u.phone, name: u.name, operator: detectOperator(u.phone), about: u.about } };
  });

  app.get('/users/search', async (req: any, reply) => {
    const q = String((req.query as any).q || '').toLowerCase();
    if (q.length < 3) return { ok: true, users: [] };
    const qd = q.replace(/\D/g, '') || 'ZZZNEVERZZZ';
    const rows = await query(
      `SELECT phone, name FROM users
       WHERE phone <> $1 AND (LOWER(name) LIKE $2 OR phone LIKE $3)
       LIMIT 20`,
      [req.phone, `%${q}%`, `%${qd}%`]
    );
    return { ok: true, users: rows.map(u => ({ phone: u.phone, name: u.name, operator: detectOperator(u.phone) })) };
  });
}
