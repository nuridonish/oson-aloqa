# 🔔 FCM Setup (Marhila 4 — push notification)

## 1. Firebase консол (10 дақиқа, ройгон)
1. https://console.firebase.google.com → **Add project** → ном: `OsonAloqa`
2. Дар лоиҳа: **Add app → Android** → package: `com.osonaloqa.app`
3. Боргирӣ кунед **google-services.json**
4. Файлро гузоред дар: `OsonAloqa-Pro/app/google-services.json`

## 2. Дар Android Studio
`app/build.gradle.kts`-и ПОЧТАИ ХУДATANҲО илова кунед:
```kotlin
plugins {
    id("com.google.gms.google-services")   // дар верх: id("com.google.gms.google-services") version "4.4.2" apply false (root build.gradle.kts)
}
```
Root build.gradle.kts:
```kotlin
id("com.google.gms.google-services") version "4.4.2" apply false
```
Баъд Sync → Build.

## 3. Дар сервер (backend)
1. Firebase Console → Project Settings → **Service Accounts** → Generate new private key → файл `serviceAccount.json`
2. Дар `.env` сервер:
```
FCM_CREDENTIALS_FILE=/path/to/serviceAccount.json
```
Сервер ба таври худкор FCM-ро инициализатсия мекунад (лог: `FCM initialized`).

## 4. Рафтор
- Барнома → login → сервер `pushToken`-ро мегирад (PUT /users/me)
- Паёми нав (барнома пӯшида) → FCM push → notification
- Бо **inline reply** (ҷавоби зуд) — нақшаи Marhila 9

## ⚠️ Бе google-services.json push кор намекунад (танҳо код омода аст)
