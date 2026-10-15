import { Pool } from 'pg';
import fs from 'fs';
import path from 'path';
import { config } from './config';
import { log } from './logger';

// PGMEM=1 — ҳолати санҷишӣ (in-memory PostgreSQL); production: ҳамеша PostgreSQL-и воқеӣ
export let pool: any;

export async function query<T = any>(sql: string, params: any[] = []): Promise<T[]> {
  const res = await pool.query(sql, params);
  return res.rows as T[];
}

export async function initDb() {
  if (process.env.PGMEM === '1') {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const { newDb } = require('pg-mem');
    const mem = newDb();
    const PgPool = mem.adapters.createPg().Pool;
    pool = new PgPool();
    const dir = path.join(__dirname, '..', 'migrations');
    for (const f of fs.readdirSync(dir).filter((f: string) => f.endsWith('.sql')).sort()) {
      const sql = fs.readFileSync(path.join(dir, f), 'utf8');
      for (const stmt of sql.split(/;\s*\n/)) {
        let s = stmt.trim();
        if (!s) continue;
        // pg-mem: normalize барои мутобиқшавӣ
        s = s.replace(/ADD COLUMN IF NOT EXISTS /g, 'ADD COLUMN ')
             .replace(/CREATE INDEX IF NOT EXISTS /g, 'CREATE INDEX ')
             .replace(/ADD COLUMN seq BIGSERIAL/g, 'ADD COLUMN seq BIGINT');
        try { await pool.query(s); } catch (e: any) { log.warn('pgmem stmt skip:', (e?.message || '').slice(0, 80)); }
      }
    }
    // Пойваи доимӣ: снапшот ба файл (дигар бо рестарт тоза намешавад)
    const snapFile = path.join(__dirname, '..', 'pgmem_snapshot.json');
    const SERIAL_SKIP: Record<string, boolean> = { messages: true, reports: true, statuses: true };
    const TABLES = ['users', 'pub_keys', 'blocks', 'group_members', 'groups', 'statuses', 'reports', 'messages'];
    async function snapshot() {
      try {
        const data: any = {};
        for (const t of TABLES) data[t] = await query(`SELECT * FROM ${t}`);
        fs.writeFileSync(snapFile, JSON.stringify(data));
      } catch (e) {}
    }
    async function restore() {
      if (!fs.existsSync(snapFile)) return;
      try {
        const data = JSON.parse(fs.readFileSync(snapFile, 'utf8'));
        for (const t of TABLES) {
          for (const row of (data[t] || [])) {
            const cols = Object.keys(row).filter(c => !(SERIAL_SKIP[t] && c.toLowerCase() === 'id'));
            if (cols.length === 0) continue;
            const vals = cols.map((c: string) => (row as any)[c]);
            const ph = cols.map((_c: string, i: number) => `$${i + 1}`).join(',');
            try { await pool.query(`INSERT INTO ${t} (${cols.join(',')}) VALUES (${ph})`, vals); } catch (e) {}
          }
        }
        log.info('pg-mem snapshot restored:', snapFile);
      } catch (e) {
        log.warn('snapshot restore failed');
      }
    }
    await restore();
    setInterval(snapshot, 10_000).unref();
    log.info('DB: pg-mem (TEST MODE) + JSON persistence');
    return;
  }

  pool = new Pool({ ...config.pg, max: 10 });
  await pool.query('CREATE TABLE IF NOT EXISTS schema_migrations (name TEXT PRIMARY KEY, applied_at BIGINT NOT NULL)');
  const applied = new Set((await query('SELECT name FROM schema_migrations')).map((r: any) => r.name));
  const dir = path.join(__dirname, '..', 'migrations');
  const files = fs.readdirSync(dir).filter((f: string) => f.endsWith('.sql')).sort();
  for (const f of files) {
    if (applied.has(f)) continue;
    const sql = fs.readFileSync(path.join(dir, f), 'utf8');
    log.info('migration', f);
    await pool.query(sql);
    await pool.query('INSERT INTO schema_migrations(name, applied_at) VALUES ($1, $2)', [f, Date.now()]);
  }
}

