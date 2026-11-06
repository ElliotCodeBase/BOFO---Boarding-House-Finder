# STATE after Stage 3 (version 2.9.7 / versionCode 20907)

## Done
- `shared/` (Android library `com.bofo.shared`, Java 17): `PermissionRules`, `AutoLockRules`, `Async`, `Http`, `SessionStore` (own Keystore key + file, so it never collides with the user app), new `Api` (Supabase client; sends `x-bofo-client: admin` + `x-bofo-app-version`), and the two unit tests moved with their package renamed. The main app keeps its own copies for now (no behaviour change; Stage 5 removes the duplicates).
- `app-admin/` (`com.bofo.admin`, debug suffix `.debug`, v2.9.7): one Activity, screens built in code.
  - Google sign-in only, with a nonce (Google gets the SHA-256, Supabase gets the raw value). No password, no dev accounts.
  - After sign-in: authenticator-app code if the account has a verified factor, then `my_admin_access()`. No active role -> the app signs out and the login screen says "No access".
  - Tabs shown only for the role's permissions (Home = `dashboard_view`, IDs = `id_review`, Reports = `reports_view`). The server still rejects anything not allowed.
  - Auto-lock after 5 min in the background (`AutoLockRules`), unlock with fingerprint / face / screen lock. Same check before approve, reject, suspend, ban, take down (60 s grace). No screen lock on the phone = sensitive actions refused (fails closed).
  - Security: FLAG_SECURE on the whole app (and on the ID dialog window), session encrypted with the Android Keystore, `allowBackup=false` + data-extraction rules, cleartext off, R8 on, no logging of personal data, ID photos watermarked with the reviewer's e-mail, signed links valid 2 min, photos never cached to disk.
  - Home: grouped cards (Needs you / Safety / Last 7 days with change vs the week before). A card is hidden when the role cannot read it.
  - ID queue + review: three photos, approve (ID number typed, server checks it), reject with reasons + note, second-opinion flag.
  - Reports: Waiting / Answered / All, waiting reports grouped per person (your action applies to all of that person's waiting reports), reports about one person, dismiss, suspend (1-30 days), ban, take down, undo ban (needs `unban`). A role without the permission but with `approvals_request` sees "Request approval" instead.
- `bofo-android/settings.gradle`: includes `:shared` and `:app-admin` (folders next to `bofo-android/`). `bofo-android/app/build.gradle`: version bump only (2.9.6 -> 2.9.7; if your local file is newer, do not copy mine, bump yours by +1).

## What was and was NOT run
- NOT run: Gradle build, any Android code, unit tests, the app on a phone. There is no Android SDK or JDK in this sandbox. The Java was written carefully and re-read, but expect a few compile errors on the first build: send me the first error text.
- The two unit tests in `shared` are the same tests that passed in Stage 2, only the package name changed.

## Deviations from the prompt (say if you want them changed)
1. Screens are built in code (no XML / ViewBinding) and English text is in the code, not `strings.xml`, to keep Stage 3 small. Colors and the Text.Bofo style names are reused.
2. Colors are a copy of the main app's `colors.xml` (light only; no night variants yet). The "single source of the look" move into `shared/res` is left for Stage 5.
3. Images use plain `BitmapFactory`, not Glide.

## NOT done (first items of Stage 4 unless noted)
1. Server: `app_version_ok()` still compares one minimum. The admin app already sends `x-bofo-client: admin`; a small migration (041) must use it before anyone sets `min_admin_app_version`. Do not set that value yet.
2. FCM for the admin app: the plugin line is in `app-admin/build.gradle` but no `FirebaseMessagingService`, no `platform/app` column on `device_tokens`, and `send-push` still targets `users.role='admin'`. You need to add the `com.bofo.admin` app in Firebase and put its `google-services.json` in `app-admin/` (git-ignored).
3. Two-step enrollment is not in this app: use the main app's current screen to enrol, then the admin app asks for the code. Stage 4 adds enrol + reset.
4. Report detail shows no photo of the place and no per-place picker (it uses the report's own place); "take down" is hidden when the report has no place.
5. `hidden` listing status for moderators, CSV export and `admin_log_export`, approvals inbox, members screen, audit log, strikes: Stage 4.
6. A separate release key and secrets `BOFO_ADMIN_KEYSTORE_*` (build.gradle reads them from the environment) and a GitHub workflow for `:app-admin`: yours to create (the existing workflow only builds the user app).
7. Second Google OAuth Android client (package `com.bofo.admin` + SHA-1 of the admin keys) in Google Cloud Console; the same Web client ID is used for the id-token flow.

## How to apply
1. Extract this zip over the repo root (or `git apply stage3.patch`). New top-level folders: `shared/`, `app-admin/`.
2. Google Cloud: add the Android OAuth client for `com.bofo.admin` (debug and release SHA-1). Firebase: add the app (optional for now).
3. Android Studio: open `bofo-android/` (the settings file pulls in the new modules). Build `:app-admin:assembleDebug`, run `:shared:testDebugUnitTest`.
4. Test per role (use `admin_invite_create` from the SQL editor or Stage 2's test accounts):
   - Owner: Home, IDs, Reports all visible; ban shows Undo.
   - Admin: no ban / take down buttons that work (server says "Your role cannot do this"); suspend over 7 days works.
   - Moderator: suspend up to 7 days works; longer, ban, take down show "Request approval".
   - Support: Home + Reports list only, no action buttons, no IDs tab.
   - Google account with no role: lands on the login screen with "No access".
   - Leave the app 5+ min: lock screen. Screenshot attempt: blocked.

## Stage 4 first step
Read `PROMPT.md` Stage 4. First migration 041 (admin-client version gate + `device_tokens` app column + push targeting by permission), then the members screen (invite / change role / suspend / log out all), audit log, approvals inbox, then the remaining admin features from ADMIN_INVENTORY.md.
