# 🚀 Осон Алоқа — Деплой ба VPS (Ubuntu 22.04)

## Қадами 1 — VPS
VPS аз Hetzner/Aeza/Vultr (~$5/моҳ). Ubuntu 22.04 + домен `api.osonaloqa.tj` (A-record → IP).

## Қадами 2 — Docker
```bash
curl -fsSL https://get.docker.com | sh
```

## Қадами 3 — Лоиҳа
```bash
cd /opt
git clone https://github.com/shumo/osonaloqa.git osonaloqa   # ё scp
cd osonaloqa/server-ts
cp .env.example .env
nano .env   # JWT_SECRET, PGPASSWORD, SMS_PROVIDER
```

## Қадами 4 — Оғоз
```bash
docker compose up -d --build
curl http://localhost:8080/health
```

## Қадами 5 — HTTPS (Caddy автоматӣ)
`Caddyfile`-ро таҳрир кунед:
```
api.osonaloqa.tj {
    reverse_proxy api:8080
}
```
```bash
docker compose restart caddy
```

## Қадами 6 — Android
Дар барнома: Профил → Сервер → `https://api.osonaloqa.tj`
Ё баъди зиёдтар: `gradlew assembleRelease -PSERVER_URL=https://api.osonaloqa.tj`

## SMS
`.env`:
```
SMS_PROVIDER=smsru      # ё irsms / twilio
SMS_API_KEY=калид
SMS_FROM=OsonAloqa
```

## Backup
```bash
docker compose exec postgres pg_dump -U osonaloqa osonaloqa > backup.sql
```

## Restore
```bash
cat backup.sql | docker compose exec -T postgres psql -U osonaloqa osonaloqa
```

## Логҳо
```bash
docker compose logs -f api
```
