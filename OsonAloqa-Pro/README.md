# ⚡ Осон Алоқа — Офлайн Мессенҷер + VoIP

**Таҳиякунанда: Мустафо Холбутаев**
Мақсад: алоқаи осон ва арзон байни Русия ва Тоҷикистон

## Имкониятҳо
- 💬 Чат (SMS офлайн + интернет-чат бо E2EE)
- 📞 Занг (SIM + WebRTC интернет-занг)
- 🎥 Видеозанг
- 🖼 Медиа (акс, видео, файл, паёми овозӣ)
- 👥 Гурӯҳҳо + 📢 Каналҳо + 🟢 Статусҳо
- 💰 Баланс USSD (Tcell, Babilon, Beeline TJ, МТС, Билайн, МегаФон, Tele2)
- 📞 Таърихи зангҳо
- 🔐 Рамзгузорӣ E2EE (X25519 + AES-256-GCM)
- 🌐 4 забон: Тоҷикӣ / Русӣ / English / Oʻzbekcha

## Архитектура
```
OsonAloqa-Pro/           ← Android (Kotlin, minSdk 26, targetSdk 36)
  server-ts/             ← Backend (Node.js + Fastify + WS + PostgreSQL)
    docker-compose.yml   ← api + postgres + redis + minio + coturn + caddy
OsonAloqa-Internet/      ← Нусхаи 5.0+ (бе WebRTC)
OsonAloqa/               ← Нусхаи Android 4.4 (офлайн)
OsonAloqa-iOS/           ← SwiftUI (iOS 15+)
```

## Сохтани APK
```bash
# Debug
./gradlew assembleDebug -PSERVER_URL=https://api.osonaloqa.tj

# Release (имзошуда)
./gradlew assembleRelease -PSERVER_URL=https://api.osonaloqa.tj
```

## Сервер
```bash
cd server-ts
docker compose up -d --build
```

## Лицензия
© 2025 Мустафо Холбутаев. Ҳамаи ҳуқуқҳо ҳифз шудаанд.
