#!/usr/bin/env node
/*
 * Oson Aloqa Server — Stage 2
 * Zero-dependency Node.js server: OTP auth, users, search, blocks, reports.
 * Run:  node server.js        (PORT env optional, default 8080)
 *
 * SMS providers (env):
 *   SMS_PROVIDER=none|smsru|irsms|twilio
 *   SMS_API_KEY=...   SMS_FROM=...
 * Without a provider the code is returned as `dev` in the response (test mode).
 */
const http = require('http');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const https = require('https');

const PORT = Number(process.env.PORT || 8080);
const DB_FILE = path.join(__dirname, 'data.json');
const SMS_PROVIDER = (process.env.SMS_PROVIDER || 'none').toLowerCase();
const SMS_API_KEY = process.env.SMS_API_KEY || '';
const SMS_FROM = process.env.SMS_FROM || '';

let db = { users: {}, otps: {}, blocks: {}, reports: {}, keys: {} };
const MEDIA_DIR = path.join(__dirname, 'media');
try { fs.mkdirSync(MEDIA_DIR); } catch (e) {}
try { db = JSON.parse(fs.readFileSync(DB_FILE, 'utf8')); } catch (e) {}
db.users = db.users || {}; db.otps = db.otps || {}; db.blocks = db.blocks || {}; db.reports = db.reports || []; db.keys = db.keys || {}; db.msgs = db.msgs || {};
function save() {
  try { fs.writeFileSync(DB_FILE, JSON.stringify(db)); } catch (e) {}
}

const OPS_TJ = [['55','77','91','92','93','94','Tcell'],['88','Megafon TJ'],['40','44','50','89','ZET-Mobile'],['70','80','98','Tamom'],['17','Beeline TJ']];
function detectOperator(e164) {
  const d = String(e164).replace(/\D/g, '');
  if (d.startsWith('992')) {
    const n = d.slice(3);
    for (const row of OPS_TJ) {
      if (row.slice(0, -1).some(p => n.startsWith(p))) return row[row.length - 1];
    }
    return 'Other TJ';
  }
  if (d.startsWith('7')) {
    const n = d.slice(1);
    if (/^9(1[0-9]|8[0-9])/.test(n)) return 'MTS';
    if (/^9(0[0156]|9[0-4]|6[0-8])/.test(n)) return 'Beeline';
    if (/^9(2[0-9]|3[0-9]|8)|^999/.test(n)) return 'Megafon RU';
    if (/^95[0-9]|^969|^97[89]/.test(n)) return 'Tele2';
    return 'Other RU';
  }
  return 'International';
}

function normalizePhone(p) {
  const d = String(p || '').replace(/\D/g, '');
  if (d.startsWith('992') || d.startsWith('7')) return '+' + d;
  return null;
}

function json(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS'
  });
  res.end(body);
}

function readBody(req) {
  return new Promise(resolve => {
    let data = '';
    req.on('data', c => { data += c; if (data.length > 1e6) req.destroy(); });
    req.on('end', () => {
      try { resolve(data ? JSON.parse(data) : {}); } catch (e) { resolve({}); }
    });
  });
}

function readRaw(req, limit) {
  return new Promise(resolve => {
    const chunks = [];
    let size = 0;
    req.on('data', c => {
      size += c.length;
      if (size > limit) { req.destroy(); resolve(null); return; }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', () => resolve(null));
  });
}

const MIME = { jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', gif: 'image/gif', webp: 'image/webp', mp4: 'video/mp4', mkv: 'video/mp4', webm: 'video/webm', mp3: 'audio/mpeg', ogg: 'audio/ogg', pdf: 'application/pdf', zip: 'application/zip', txt: 'text/plain' };
function mimeOf(name) {
  const ext = String(name).split('.').pop().toLowerCase();
  return MIME[ext] || 'application/octet-stream';
}

function httpsGet(url) {
  return new Promise(resolve => {
    https.get(url, r => {
      let b = '';
      r.on('data', c => b += c);
      r.on('end', () => resolve(b));
    }).on('error', () => resolve(''));
  });
}

async function sendSms(phone, text) {
  if (SMS_PROVIDER === 'none') return false;
  try {
    if (SMS_PROVIDER === 'smsru') {
      await httpsGet('https://sms.ru/sms/send?api_id=' + SMS_API_KEY + '&to=' + encodeURIComponent(phone) + '&msg=' + encodeURIComponent(text) + '&json=1');
      return true;
    }
    if (SMS_PROVIDER === 'irsms') {
      await httpsGet(process.env.SMS_URL_TEMPLATE
        .replace('{KEY}', SMS_API_KEY).replace('{TO}', encodeURIComponent(phone)).replace('{TEXT}', encodeURIComponent(text)));
      return true;
    }
  } catch (e) {}
  return false;
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') return json(res, 200, { ok: true });
  const url = new URL(req.url, 'http://x');
  const p = url.pathname;

  if (req.method === 'GET' && p === '/') {
    return json(res, 200, {
      name: 'Oson Aloqa Server', version: '2.0-stage2',
      endpoints: ['/auth/request-otp', '/auth/verify', '/users/me', '/users/search?q=', '/blocks', '/reports'],
      sms_provider: SMS_PROVIDER
    });
  }

  /* ---------- MEDIA (raw upload, before JSON body) ---------- */
  if (req.method === 'POST' && p === '/media/upload') {
    const auth = (req.headers.authorization || '').replace('Bearer ', '');
    if (!Object.values(db.users).find(u => u.token === auth)) return json(res, 401, { ok: false, error: 'unauthorized' });
    const buf = await readRaw(req, 25 * 1024 * 1024);
    if (!buf || buf.length === 0) return json(res, 400, { ok: false, error: 'empty_or_too_big' });
    const id = crypto.randomBytes(8).toString('hex');
    fs.writeFileSync(path.join(MEDIA_DIR, id), buf);
    return json(res, 200, { ok: true, id, url: '/m/' + id });
  }
  if (req.method === 'GET' && p.startsWith('/m/')) {
    const id = p.slice(3).replace(/[^a-f0-9]/g, '');
    const file = path.join(MEDIA_DIR, id);
    if (!fs.existsSync(file)) return json(res, 404, { ok: false, error: 'not_found' });
    const name = url.searchParams.get('name') || 'file';
    res.writeHead(200, { 'Content-Type': mimeOf(name), 'Access-Control-Allow-Origin': '*', 'Content-Disposition': 'inline; filename="' + name.replace(/["\\r\\n]/g, '') + '"' });
    fs.createReadStream(file).pipe(res);
    return;
  }

  const body = (req.method === 'POST' || req.method === 'PUT' || req.method === 'DELETE') ? await readBody(req) : {};

  /* ---------- AUTH ---------- */
  if (req.method === 'POST' && p === '/auth/request-otp') {
    const phone = normalizePhone(body.phone);
    if (!phone) return json(res, 400, { ok: false, error: 'bad_phone' });
    const code = String(Math.floor(100000 + Math.random() * 900000));
    db.otps[phone] = {
      hash: crypto.createHash('sha256').update(code + 'oson').digest('hex'),
      exp: Date.now() + 5 * 60 * 1000,
      attempts: 0
    };
    save();
    const sent = await sendSms(phone, 'Oson Aloqa — code: ' + code);
    return json(res, 200, { ok: true, sent_sms: sent, dev: sent ? undefined : code });
  }

  if (req.method === 'POST' && p === '/auth/verify') {
    const phone = normalizePhone(body.phone);
    const code = String(body.code || '').trim();
    const name = String(body.name || ('User' + (phone || '').slice(-4))).slice(0, 30);
    const rec = phone ? db.otps[phone] : null;
    if (!rec || rec.exp < Date.now()) return json(res, 400, { ok: false, error: 'expired' });
    if (rec.attempts >= 5) return json(res, 429, { ok: false, error: 'too_many' });
    rec.attempts++;
    if (rec.hash !== crypto.createHash('sha256').update(code + 'oson').digest('hex')) { save(); return json(res, 400, { ok: false, error: 'wrong_code' }); }
    delete db.otps[phone];
    let user = db.users[phone];
    if (!user) {
      user = db.users[phone] = {
        phone, name, created: Date.now(),
        token: crypto.randomBytes(24).toString('hex'),
        pushToken: '', about: ''
      };
    } else if (body.name) user.name = name;
    save();
    return json(res, 200, { ok: true, token: user.token, profile: { phone, name: user.name, operator: detectOperator(phone) } });
  }

  /* ---------- AUTH GUARD ---------- */
  const auth = (req.headers.authorization || '').replace('Bearer ', '');
  const me = Object.values(db.users).find(u => u.token === auth);
  if (!me) return json(res, 401, { ok: false, error: 'unauthorized' });

  if (req.method === 'GET' && p === '/users/me') {
    return json(res, 200, { ok: true, profile: { phone: me.phone, name: me.name, operator: detectOperator(me.phone), about: me.about } });
  }

  if (req.method === 'PUT' && p === '/users/me') {
    if (body.name) me.name = String(body.name).slice(0, 30);
    if (typeof body.about === 'string') me.about = body.about.slice(0, 200);
    if (body.pushToken) me.pushToken = String(body.pushToken).slice(0, 200);
    save();
    return json(res, 200, { ok: true, profile: { phone: me.phone, name: me.name, operator: detectOperator(me.phone), about: me.about } });
  }

  if (req.method === 'GET' && p === '/users/search') {
    const q = (url.searchParams.get('q') || '').toLowerCase();
    if (q.length < 3) return json(res, 200, { ok: true, users: [] });
    const qd = q.replace(/\D/g, '');
    const users = Object.values(db.users)
      .filter(u => u.phone !== me.phone)
      .filter(u => u.name.toLowerCase().includes(q) || (qd && u.phone.replace(/\D/g, '').includes(qd)))
      .slice(0, 20)
      .map(u => ({ phone: u.phone, name: u.name, operator: detectOperator(u.phone) }));
    return json(res, 200, { ok: true, users });
  }

  if (req.method === 'GET' && p === '/blocks') {
    return json(res, 200, { ok: true, blocks: db.blocks[me.phone] || [] });
  }
  if (req.method === 'POST' && p === '/blocks') {
    const addr = normalizePhone(body.address);
    if (!addr) return json(res, 400, { ok: false, error: 'bad_phone' });
    const list = db.blocks[me.phone] || (db.blocks[me.phone] = []);
    if (!list.includes(addr)) list.push(addr);
    save();
    return json(res, 200, { ok: true });
  }
  if (req.method === 'DELETE' && p === '/blocks') {
    const addr = normalizePhone(body.address);
    db.blocks[me.phone] = (db.blocks[me.phone] || []).filter(x => x !== addr);
    save();
    return json(res, 200, { ok: true });
  }

  if (req.method === 'POST' && p === '/reports') {
    const addr = normalizePhone(body.address);
    if (!addr) return json(res, 400, { ok: false, error: 'bad_phone' });
    db.reports.push({ from: me.phone, address: addr, reason: String(body.reason || '').slice(0, 300), ts: Date.now() });
    save();
    return json(res, 200, { ok: true });
  }

  if (req.method === 'POST' && p === '/messages/send') {
    const to = normalizePhone(body.to);
    if (!to) return json(res, 400, { ok: false, error: 'bad_phone' });
    if (!db.users[to]) return json(res, 200, { ok: false, error: 'no_user' });
    const q = db.msgs[to] || (db.msgs[to] = []);
    q.push({
      from: me.phone,
      body: String(body.body || '').slice(0, 8000),
      enc: !!body.enc,
      kind: String(body.kind || 'text').slice(0, 10),
      url: String(body.url || '').slice(0, 600),
      ts: Date.now()
    });
    if (q.length > 300) q.splice(0, q.length - 300);
    save();
    return json(res, 200, { ok: true });
  }
  if (req.method === 'GET' && p === '/messages/pull') {
    const q = db.msgs[me.phone] || [];
    db.msgs[me.phone] = [];
    save();
    return json(res, 200, { ok: true, messages: q });
  }

  if (req.method === 'POST' && p === '/keys') {
    db.keys[me.phone] = String(body.pub || '').slice(0, 400);
    save();
    return json(res, 200, { ok: true });
  }
  if (req.method === 'GET' && p === '/keys') {
    const phone = normalizePhone(url.searchParams.get('phone'));
    return json(res, 200, { ok: true, pub: db.keys[phone] || null });
  }

  json(res, 404, { ok: false, error: 'not_found' });
});

server.listen(PORT, () => {
  console.log('Oson Aloqa server: http://0.0.0.0:' + PORT + '  (SMS provider: ' + SMS_PROVIDER + ')');
});



