import { FastifyInstance } from 'fastify';
import { query } from '../db';
import { authGuard, normalizePhone } from '../middleware/auth';

export default async function (app: FastifyInstance) {

  app.addHook('preHandler', authGuard);

  app.get('/blocks', async (req: any) => {
    const rows = await query('SELECT address FROM blocks WHERE owner = $1 ORDER BY ts DESC', [req.phone]);
    return { ok: true, blocks: rows.map(r => r.address) };
  });

  app.post('/blocks', async (req: any, reply) => {
    const addr = normalizePhone((req.body || {}).address);
    if (!addr) return reply.code(400).send({ ok: false, error: 'bad_phone' });
    await query('INSERT INTO blocks(owner, address, ts) VALUES ($1,$2,$3) ON CONFLICT (owner, address) DO NOTHING', [
      req.phone, addr, Date.now()
    ]);
    return { ok: true };
  });

  app.delete('/blocks', async (req: any, reply) => {
    const addr = normalizePhone((req.body || {}).address);
    if (!addr) return reply.code(400).send({ ok: false, error: 'bad_phone' });
    await query('DELETE FROM blocks WHERE owner = $1 AND address = $2', [req.phone, addr]);
    return { ok: true };
  });

  app.post('/reports', async (req: any, reply) => {
    const addr = normalizePhone((req.body || {}).address);
    if (!addr) return reply.code(400).send({ ok: false, error: 'bad_phone' });
    const reason = String((req.body || {}).reason || '').slice(0, 300);
    await query('INSERT INTO reports(reporter, address, reason, ts) VALUES ($1,$2,$3,$4)', [
      req.phone, addr, reason, Date.now()
    ]);
    return { ok: true };
  });

  app.post('/keys', async (req: any, reply) => {
    const pub = String((req.body || {}).pub || '').slice(0, 400);
    if (!pub) return reply.code(400).send({ ok: false, error: 'bad_request' });
    await query('INSERT INTO pub_keys(phone, pub, ts) VALUES ($1,$2,$3) ON CONFLICT (phone) DO UPDATE SET pub = $2, ts = $3', [
      req.phone, pub, Date.now()
    ]);
    return { ok: true };
  });

  app.get('/keys/public/:phone', async (req: any, reply) => {
    const phone = normalizePhone((req.params as any).phone);
    if (!phone) return reply.code(400).send({ ok: false, error: 'bad_phone' });
    const rows = await query('SELECT pub FROM pub_keys WHERE phone = ', [phone]);
    return { ok: true, pub: rows.length ? rows[0].pub : null };
  });

  app.get('/keys', async (req: any, reply) => {
    const phone = normalizePhone((req.query as any).phone);
    if (!phone) return reply.code(400).send({ ok: false, error: 'bad_phone' });
    const rows = await query('SELECT pub FROM pub_keys WHERE phone = $1', [phone]);
    return { ok: true, pub: rows.length ? rows[0].pub : null };
  });
}

