# Handoff (Android) - v2.9.3: dashed connector legs on routes
- User screenshot (Directions, Me -> BH): the line "cuts through buildings". Cause: `Router` prepended the raw GPS point and appended the raw house point to the road geometry, and drew them solid. Fix: dashed connectors (`Router.withEnds`, `MapGeo.routeJson`, `MapCardView.paintRoute`). Test: `RouteConnectorsTest`.
- Open: the road itself can still be wrong where OpenStreetMap lacks a path; a better snap would need a self-hosted router or a place-snap step. Dash style is the same as the estimate dash (layer `route-dash`).
- Nothing compiled or run: `gradlew testDebugUnitTest assembleDebug`, then open Directions > Me -> BH on a phone.
- Version 2.9.3 / 20903.

# Handoff (Android) - v2.9.2: notifications while the app is closed
- Cause was not provable from the repo alone (no phone, no live DB). Ranked: (1) phone / battery saver kills or never starts the app, so data-only pushes never reach `BofoMessagingService`; (2) setup gaps (google-services.json missing in the build, `push_settings` empty, secrets mismatch, function not deployed with `--no-verify-jwt`); (3) code: `PushNotifier` dropped every push when the saved role was empty. All three are addressed or made visible, see `docs/NOTIFICATION_FIX_v2.9.2.md`.
- Changed: `supabase/functions/send-push/index.ts` (hybrid payload, reasons, test-to-me), `PushNotifier`, `PushRouter`, `PushRules` (+ `TEST`, + test), `AndroidManifest.xml` (OPEN_PUSH filter, notification colour). Redeploy the function FIRST: it already fixes the closed-app case for the current app build.
- Trade-off: while the app is closed each message is its own system notification (no MessagingStyle stack, no "chat already open" check; the server still filters blocked senders, muted members and admin-only kinds).
- Open: force-stopped apps and some phones (Xiaomi, Oppo, Vivo, Realme, Infinix, Tecno, Samsung) need Autostart / battery "No restrictions": documented, no in-app prompt yet.
- Nothing compiled or run: `gradlew testDebugUnitTest assembleDebug`, then the test in the guide on a real phone.
- Version 2.9.2 / 20902.

# Handoff (Android) - v2.9.1: build fix
- `AdminRowAdapter.isEmpty()` made `public` + `@Override` after the user's build failed on it. Everything in the v2.9.0 handoff entry below still applies (moderator role split waiting for the user's OK on migration 035; other open items listed there).
- Version 2.9.1 / 20901.

