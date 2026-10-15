import { FastifyRequest, FastifyReply } from 'fastify';
import jwt from 'jsonwebtoken';
import { config } from '../config';
import { query } from '../db';

export interface Authed {
  phone: string;
}

export function signAccess(phone: string): string {
  return jwt.sign({ phone, t: 'a' }, config.accessSecret || 'dev-access', { expiresIn: config.accessTtlSec });
}

export function signRefresh(phone: string, deviceId: string): string {
  return jwt.sign({ phone, d: deviceId, t: 'r' }, config.refreshSecret || 'dev-refresh', { expiresIn: config.refreshTtlSec });
}

export function verifyAccess(token: string): string | null {
  try {
    const p = jwt.verify(token, config.accessSecret || 'dev-access') as { phone?: string; t?: string };
    return p.t === 'a' && p.phone ? p.phone : null;
  } catch (e) {
    return null;
  }
}

export function verifyRefresh(token: string): { phone: string; deviceId: string } | null {
  try {
    const p = jwt.verify(token, config.refreshSecret || 'dev-refresh') as { phone?: string; d?: string; t?: string };
    return p.t === 'r' && p.phone && p.d ? { phone: p.phone, deviceId: p.d } : null;
  } catch (e) {
    return null;
  }
}

/** Authorization: Bearer <access> ё <refresh-legacy-token> (мутобиқи APK-и кӯҳна: users.token) */
export async function authGuard(req: FastifyRequest, reply: FastifyReply) {
  const raw = (req.headers.authorization || '').replace('Bearer ', '');
  if (!raw) {
    return await reply.code(401).send({ ok: false, error: 'unauthorized' });
  }
  // 1) access-JWT
  const phone = verifyAccess(raw);
  if (phone) {
    (req as any).phone = phone;
    return true;
  }
  // 2) мутобиқи APK-и кӯҳна: users.token (long-lived)
  const rows = await query('SELECT phone FROM users WHERE token = $1', [raw]);
  if (rows.length > 0) {
    (req as any).phone = rows[0].phone;
    return true;
  }
  await reply.code(401).send({ ok: false, error: 'unauthorized' });
  return false;
}

export function normalizePhone(p: any): string | null {
  const d = String(p || '').replace(/\D/g, '');
  if (d.startsWith('992') || d.startsWith('7')) return '+' + d;
  return null;
}

const OPS_TJ: [string[], string][] = [
  [['55', '77', '91', '92', '93', '94'], 'Tcell'],
  [['88'], 'Megafon TJ'],
  [['40', '44', '50', '89'], 'ZET-Mobile'],
  [['70', '80', '98'], 'Tamom'],
  [['17'], 'Beeline TJ']
];

export function detectOperator(e164: string): string {
  const d = String(e164).replace(/\D/g, '');
  if (d.startsWith('992')) {
    const n = d.slice(3);
    for (const [prefixes, name] of OPS_TJ) if (prefixes.some(p => n.startsWith(p))) return name;
    return 'Other TJ';
  }
  if (d.startsWith('7')) {
    const n = d.slice(1);
    if (/^9(1[0-9]|8[0-9])/.test(n)) return 'MTS';
    if (/^9(0[0156]|9[0-4]|6[0-8])/.test(n)) return 'Beeline';
    if (/^9(2[0-9]|3[0-9]|8)/.test(n) || /^999/.test(n)) return 'Megafon RU';
    if (/^95[0-9]/.test(n) || /^969/.test(n) || /^97[89]/.test(n)) return 'Tele2';
    return 'Other RU';
  }
  return 'International';
}

