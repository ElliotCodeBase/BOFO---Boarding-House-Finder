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

# Handoff (Android) - v2.9.0: Admin next steps, step 5 of 5 PARTLY done (auto-lock only)
- Done: auto-lock. `AutoLockRules` (+ test), `AppPrefs.adminAutoLock / adminStoppedAt`, `AdminLock.locked` (static, survives rotation), `ShellFragment.onStop/onStart/lock/unlocked`, `AdminMfaSheet.showLock` (lock mode: not cancelable, "Sign out instead" button, Try again when offline, closes at once when the account has no authenticator), Settings row `admin_lock_row`, strings in `strings_admin_lock.xml`.
- NOT done: moderator role split. Plan: migration `035_admin_level.sql` (`users.admin_level` 'admin'|'moderator', default 'admin'; extend `protect_users` so nobody changes their own level; check the level inside admin_review_report ban/suspend/takedown actions, admin_unban, boost refund, admin_unsuspend / admin_relist if step 1 exists), app hides those buttons, banner shows "Moderator". Needs the user's OK for the migration first.
- Still open from earlier steps: step 1 code missing in the user's repo; step 3: own saved templates, Approve clean, flag on reports + chip; step 4: item 11 (AI screens on AdminListChrome).
- Open risks: a process kill while locked re-locks on the next start (stoppedAt is kept in prefs) but a restored lock sheet may open in normal mode first; lock/rotation/offline paths not tested on a device.
- Nothing compiled or run: `gradlew testDebugUnitTest assembleDebug`.
- Version 2.9.0 / 20900.

