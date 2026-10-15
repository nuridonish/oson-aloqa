import { FastifyInstance } from 'fastify';
import { query } from '../db';

export default async function (app: FastifyInstance) {

  app.get('/api/stats', async () => {
    try {
      const users = await query('SELECT COUNT(*)::int AS c FROM users');
      const msgs = await query('SELECT COUNT(*)::int AS c FROM messages');
      const groups = await query('SELECT COUNT(*)::int AS c FROM groups');
      const statuses = await query('SELECT COUNT(*)::int AS c FROM statuses');
      const last = await query('SELECT from_phone, to_phone, body, ts FROM messages ORDER BY ts DESC LIMIT 8');
      return { ok: true, users: users[0].c, messages: msgs[0].c, groups: groups[0].c, statuses: statuses[0].c, last };
    } catch (e) {
      return { ok: false };
    }
  });

  app.get('/dashboard', async (_req, reply) => {
    reply.type('text/html').header('charset', 'utf-8');
    return `<!DOCTYPE html><html><head><meta charset="utf-8"><meta http-equiv="refresh" content="5"><title>Oson Aloqa — Dashboard</title>
<style>body{font-family:'Segoe UI',Arial;background:#0e1621;color:#e9edf0;margin:0;padding:24px}
h1{color:#0A84FF;margin:0 0 4px}.sub{color:#8696A0;margin-bottom:20px}
.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:22px}
.card{background:#17212b;border-radius:14px;padding:18px}.num{font-size:34px;font-weight:700;color:#0A84FF}
.lbl{color:#8696A0;font-size:13px}table{width:100%;border-collapse:collapse;background:#17212b;border-radius:12px;overflow:hidden}
td,th{padding:10px 14px;border-bottom:1px solid #22303c;text-align:left;font-size:14px}th{color:#8696A0;font-weight:600}
.wrap{max-width:900px;margin:0 auto}</style></head><body><div class="wrap">
<h1>⚡ Oson Aloqa — сервер (v3 Fastify)</h1><div class="sub">auto-refresh 5s • api: <b id="b"></b></div>
<div class="grid">
<div class="card"><div class="num" id="u">…</div><div class="lbl">Корбарон</div></div>
<div class="card"><div class="num" id="m">…</div><div class="lbl">Паёмҳо</div></div>
<div class="card"><div class="num" id="g">…</div><div class="lbl">Гурӯҳҳо</div></div>
<div class="card"><div class="num" id="s">…</div><div class="lbl">Статусҳо</div></div>
</div>
<h3>Охирин паёмҳо</h3><table><tr><th>Аз</th><th>Ба</th><th>Паём</th><th>Вақт</th></tr><tbody id="rows"></tbody></table>
<p class="sub">/auth/request-otp • /auth/verify • /auth/refresh • /users/search • /messages/send • /messages/pull • /messages/sync • /media/upload • /groups/* • /status</p>
</div><script>
document.getElementById('b').textContent = location.origin;
fetch('/api/stats').then(r=>r.json()).then(d=>{if(!d.ok)return;
u.textContent=d.users;m.textContent=d.messages;g.textContent=d.groups;s.textContent=d.statuses;
rows.innerHTML=d.last.map(x=>'<tr><td>'+x.from_phone+'</td><td>'+x.to_phone+'</td><td>'+(x.body||'').slice(0,60)+'</td><td>'+new Date(x.ts).toLocaleTimeString()+'</td></tr>').join('');
}).catch(()=>{});
</script></body></html>`;
  });
}
