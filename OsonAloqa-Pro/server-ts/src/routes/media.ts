import { FastifyInstance } from 'fastify';
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { query } from '../db';
import { authGuard } from '../middleware/auth';
import { config } from '../config';

const MIME: Record<string, string> = {
  jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', gif: 'image/gif', webp: 'image/webp',
  mp4: 'video/mp4', mkv: 'video/mp4', webm: 'video/webm', mp3: 'audio/mpeg', ogg: 'audio/ogg',
  pdf: 'application/pdf', zip: 'application/zip', txt: 'text/plain'
};

function mimeOf(name: string): string {
  const ext = String(name).split('.').pop()!.toLowerCase();
  return MIME[ext] || 'application/octet-stream';
}

export default async function (app: FastifyInstance) {

  // octet-stream parser дар index.ts ба қайд шудааст

  app.post('/media/upload', { preHandler: authGuard }, async (req: any, reply) => {
    const limit = config.mediaMaxMb * 1024 * 1024;
    const buf = (req as any).body as Buffer | undefined;
    if (!buf || buf.length === 0) return reply.code(400).send({ ok: false, error: 'empty' });
    if (buf.length > limit) return reply.code(400).send({ ok: false, error: 'too_big' });
    const id = crypto.randomBytes(8).toString('hex');
    if (!fs.existsSync(config.mediaDir)) fs.mkdirSync(config.mediaDir, { recursive: true });
    fs.writeFileSync(path.join(config.mediaDir, id), buf);
    const name = String((req.headers as any)['x-file-name'] || 'file').slice(0, 100);
    await query('INSERT INTO media(id, owner, name, mime, size, created_at) VALUES ($1,$2,$3,$4,$5,$6)', [
      id, req.phone, name, mimeOf(name), buf.length, Date.now()
    ]);
    return { ok: true, id, url: '/m/' + id };
  });

  app.get('/m/:id', async (req, reply) => {
    const id = String((req.params as any).id).replace(/[^a-f0-9]/g, '');
    const rows = await query('SELECT name, mime FROM media WHERE id = $1', [id]);
    const file = path.join(config.mediaDir, id);
    if (rows.length === 0 || !fs.existsSync(file)) return reply.code(404).send({ ok: false, error: 'not_found' });
    const requestedName = String((req.query as any).name || rows[0].name || 'file');
    reply
      .header('Content-Type', rows[0].mime || mimeOf(requestedName))
      .header('Access-Control-Allow-Origin', '*')
      .header('Content-Disposition', `inline; filename="${requestedName.replace(/["\r\n]/g, '')}"`);
    return reply.send(fs.createReadStream(file));
  });
}


