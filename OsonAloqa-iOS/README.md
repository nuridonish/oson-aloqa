# Осон Алоқа — iOS (SwiftUI)

**Муҳим:** Apple барои тамоми барномаҳо ин маҳдудиятҳоро гузоштааст (ин бояд қабул карда шавад):

1. **Қабули SMS дар пасзамина имконнопазир аст.** iOS ба ҳеҷ барнома иҷоза намедиҳад мазмуни SMS-ро бинад. Паёмҳои воридшуда дар барномаи аслии «Messages» нишон дода мешаванд.
2. **Фиристодани SMS бо тасдиқи корбар.** Барнома тирезаи аслии SMS-ро мекушояд ва шумо тугмаи «Send»-ро мепахшед — SMS аз ҳисоби симкарта меравад (бe интернет).
3. **Занг** — тавассути симкарта (телефони аслӣ).
4. Видеозанг бе интернет дар iOS имконнопазир аст.

## Чӣ тавр ба iPhone насб кунем?

**Талаб:** компютери Mac + Xcode 15+ (аз App Store). Дар Windows ба iOS тартиб додан имконнопазир аст — ин маҳдудияти Apple аст.

1. Дар Mac Xcode-ро кушоед → **File → New → Project → iOS → App**.
2. Product Name: `OsonAloqa`, Interface: **SwiftUI**, Language: **Swift**.
3. Файли `ContentView.swift`-и эҷодшударо нест кунед.
4. Ҳамаи файлҳои ин папка (`OsonAloqaApp.swift`, `Store.swift`, `RegisterView.swift`, `Countries.swift`, `ChatsView.swift`, `ChatView.swift`) ба лоиҳа илова кунед (Drag & Drop ба навигатори Xcode, «Copy items if needed» ✔).
5. **Signing & Capabilities**: Apple ID-и худро ворид кунед (Team: Personal Team).
6. iPhone-ро бо кабел пайваст кунед → дар Xcode ҳамонро интихоб кунед → **Run (▶)**.
   - Бо Apple ID-и ройгон: барнома 7 рӯз кор мекунад, баъд аз нав тартиб диҳед.
   - Бо Apple Developer ($99/сол): 1 сол + нашр ба TestFlight/App Store.

## Забон
Интерфейси iOS-и ин версия ба забони тоҷикӣ навишта шудааст. Барои зиёд кардани забонҳо `String Catalog` дар Xcode истифода баред.
