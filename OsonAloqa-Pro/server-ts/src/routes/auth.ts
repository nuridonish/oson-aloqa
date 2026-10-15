import { FastifyInstance } from 'fastify';
import crypto from 'crypto';
import { query } from '../db';
import { config } from '../config';
import { normalizePhone, detectOperator, signAccess, signRefresh, verifyRefresh } from '../middleware/auth';
import { sendSms } from '../services/sms';

const sha256 = (s: string) => crypto.createHash('sha256').update(s).digest('hex');

interface OtpRow { code_hash: string; expires_at: number; attempts: number }
interface UserRow { phone: string; name: string; about: string; token: string }

const rl = { config: { rateLimit: { max: 20, timeWindow: '1 minute' } } };

export default async function (app: FastifyInstance) {

  app.post('/auth/request-otp', { ...rl }, async (req, reply) => {
    const phone = normalizePhone((req.body as any)?.phone);
    if (!phone) return reply.code(400).send({ ok: false, error: 'bad_phone' });
    const code = String(Math.floor(100000 + Math.random() * 900000));
    await query(
      `INSERT INTO otp_codes(phone, code_hash, expires_at, attempts) VALUES ($1,$2,$3,0)
       ON CONFLICT (phone) DO UPDATE SET code_hash=$2, expires_at=$3, attempts=0`,
      [phone, sha256(code + 'oson'), Date.now() + config.otp.ttl]
    );
    const sent = await sendSms(phone, 'Oson Aloqa — code: ' + code);
    return reply.send({ ok: true, sent_sms: sent, ...(sent ? {} : { dev: code }) });
  });

  app.post('/auth/verify', { ...rl }, async (req, reply) => {
    const b = (req.body || {}) as any;
    const phone = normalizePhone(b.phone);
    const code = String(b.code || '').trim();
    const rawName = String(b.name || '').slice(0, 30);
    const name = rawName || 'User' + (phone || '').slice(-4);
    if (!phone) return reply.code(400).send({ ok: false, error: 'bad_phone' });
    const rows = await query<OtpRow>('SELECT code_hash, expires_at, attempts FROM otp_codes WHERE phone = $1', [phone]);
    if (rows.length === 0) return reply.code(400).send({ ok: false, error: 'expired' });
    const rec = rows[0];
    if (rec.expires_at < Date.now()) return reply.code(400).send({ ok: false, error: 'expired' });
    if (rec.attempts >= config.otp.maxAttempts) return reply.code(429).send({ ok: false, error: 'too_many' });
    if (rec.code_hash !== sha256(code + 'oson')) {
      await query('UPDATE otp_codes SET attempts = attempts + 1 WHERE phone = $1', [phone]);
      return reply.code(400).send({ ok: false, error: 'wrong_code' });
    }
    await query('DELETE FROM otp_codes WHERE phone = $1', [phone]);

    const existing = await query<UserRow>('SELECT phone, name, about FROM users WHERE phone = $1', [phone]);
    let profile: any;
    if (existing.length > 0) {
      if (rawName) await query('UPDATE users SET name = $2 WHERE phone = $1', [phone, rawName]);
      profile = { phone, name: rawName || existing[0].name, operator: detectOperator(phone), about: existing[0].about };
    } else {
      await query('INSERT INTO users(phone, name, operator, created_at) VALUES ($1,$2,$3,$4)', [
        phone, name, detectOperator(phone), Date.now()
      ]);
      profile = { phone, name, operator: detectOperator(phone), about: '' };
    }

    // ===== v3: access + refresh (device-bound) =====
    const deviceId = crypto.randomUUID();
    const refreshToken = signRefresh(phone, deviceId);
    await query(
      'INSERT INTO devices(id, phone, refresh_hash, ua, created_at, last_seen) VALUES ($1,$2,$3,$4,$5,$6)',
      [deviceId, phone, sha256(refreshToken), String(req.headers['user-agent'] || '').slice(0, 120), Date.now(), Date.now()]
    );
    const accessToken = signAccess(phone);
    // ===== мутобиқи APK-и кӯҳна: users.token = refresh-и дастгоҳи ҳамин =====
    const legacyToken = sha256(refreshToken + phone).slice(0, 48);
    await query('UPDATE users SET token = $2 WHERE phone = $1', [phone, legacyToken]);

    return reply.send({
      ok: true,
      token: legacyToken,
      access_token: accessToken,
      refresh_token: refreshToken,
      device_id: deviceId,
      expires_in: config.accessTtlSec,
      profile
    });
  });

  app.post('/auth/refresh', { ...rl }, async (req, reply) => {
    const b = (req.body || {}) as any;
    const parsed = verifyRefresh(String(b.refresh_token || ''));
    if (!parsed) return reply.code(401).send({ ok: false, error: 'unauthorized' });
    const rows = await query('SELECT refresh_hash FROM devices WHERE id = $1 AND phone = $2', [parsed.deviceId, parsed.phone]);
    if (rows.length === 0 || rows[0].refresh_hash !== sha256(String(b.refresh_token))) {
      return reply.code(401).send({ ok: false, error: 'unauthorized' });
    }
    const access = signAccess(parsed.phone);
    return reply.send({ ok: true, access_token: access, expires_in: config.accessTtlSec });
  });

  app.get('/auth/devices', async (req, reply) => {
    const auth = String(req.headers.authorization || '').replace('Bearer ', '');
    const me = await query<UserRow>('SELECT phone FROM users WHERE token = $1', [auth]);
    if (me.length === 0 && !verifyRefresh(auth)) return reply.code(401).send({ ok: false, error: 'unauthorized' });
    const phone = me.length ? me[0].phone : (verifyRefresh(auth)?.phone || '');
    const rows = await query('SELECT id, ua, created_at, last_seen FROM devices WHERE phone = $1 ORDER BY last_seen DESC', [phone]);
    return reply.send({ ok: true, devices: rows });
  });

  app.post('/auth/logout-all', async (req, reply) => {
    const auth = String(req.headers.authorization || '').replace('Bearer ', '');
    const me = await query<UserRow>('SELECT phone FROM users WHERE token = $1', [auth]);
    if (me.length === 0) return reply.code(401).send({ ok: false, error: 'unauthorized' });
    await query('DELETE FROM devices WHERE phone = $1', [me[0].phone]);
    await query('UPDATE users SET token = $2 WHERE phone = $1', [me[0].phone, '']);
    return reply.send({ ok: true });
  });
}
