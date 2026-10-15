import { FastifyInstance } from 'fastify';
import { authGuard } from '../middleware/auth';
import { deliverRealtime } from '../realtime/ws';

const calls = new Map<string, any>(); // callId → info

export default async function (app: FastifyInstance) {

  app.addHook('preHandler', authGuard);

  // REST-compat барои signaling тавассути HTTP (агар WS дастрас набошад)
  app.post('/calls/signal', async (req: any, reply) => {
    const b = (req.body || {}) as any;
    const to = String(b.to || '');
    const type = String(b.type || '');
    if (!to || !type) return reply.code(400).send({ ok: false, error: 'bad_request' });
    const delivered = deliverRealtime(to, { type, from: (req as any).phone, payload: b.payload ?? b });
    return { ok: true, delivered };
  });

  app.post('/calls/end', async (req: any, reply) => {
    const b = (req.body || {}) as any;
    const to = String(b.to || '');
    if (to) deliverRealtime(to, { type: 'call.end', from: (req as any).phone });
    return { ok: true };
  });
}
