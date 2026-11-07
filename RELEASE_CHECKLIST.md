# BOFO release checklist (v2.11.0: two apps, BOFO Finder + BOFO Admin)

Do the sections in order. Tick each box yourself. Nothing here was run by Claude; the apps have never been compiled by Claude.

## 0. Before anything: does it build?
- [ ] Android Studio: open `bofo-android`, Gradle sync. From `bofo-android` run: `.\gradlew :shared:testDebugUnitTest :app-admin:assembleDebug :app:assembleDebug` (both apps + the shared tests). Paste any compile error to Claude first; fix before releasing.
- [ ] Run the app unit tests too (`.\gradlew :app:testDebugUnitTest`). Expected: the admin rule tests now run only in `shared/`; `app` keeps PushRules, BanRules, Models and the rest.
- [ ] Nobody is signed in with a debug APK that has dev passwords baked in. Release builds have empty `DEV_*` fields.

## 1. Rotate keys (details: `BOFO_STAGE_1/ROTATION_AND_HISTORY_PURGE.md`)
- [ ] Firebase service-account JSON: delete the old key, create a new one, store it only in Supabase secrets.
- [ ] `PUSH_WEBHOOK_SECRET`: new value in Supabase secrets AND `push_settings` table.
- [ ] Supabase: roll `service_role` key (and the JWT secret only if it left your PC; then update the anon key in `config.properties`).
- [ ] Provider keys you set as secrets: `OPENROUTER_API_KEY`, `PAYMONGO_SECRET_KEY`, `PAYMONGO_WEBHOOK_SECRET`.
- [ ] Check git history for old secrets (commands in the rotation file). If found: rotate first, then purge.

## 2. Database (SQL editor, in this order, each one only after you read its header)
- [ ] `038_stage1_security_fixes.sql` (owner cannot undo a take-down). **Now in `supabase/migrations/`.**
- [ ] `039` to `044` (roles, gates, client-version gate, push, refund hardening, stage 4 safety). Skip any you already ran; they are safe to run twice.
- [ ] `supabase functions deploy send-push`
- [ ] Bootstrap the Owner: `select public.bootstrap_owner('owner@email');` (the person must have signed in once). Add other staff through the Admin app (Team > invite).
- [ ] Turn on admin two-step: each staff member enrolls in the Admin app, then `select public.set_admin_mfa_required(true);`
- [ ] Delete dev / test accounts (SQL in the rotation file, item 6).
- [ ] Set the minimum versions AFTER both new apps are live in Play: `select public.set_min_app_version('2.11.0');` and `select public.set_min_admin_client_version('2.11.0');`. Old user apps (with admin screens) are blocked from then on.
- [ ] **Later, only when no 2.10.x user app is in use:** read the header of `045_drop_legacy_admin.sql`, then run it. It stops by itself if an admin account is not in the team list.
- [ ] Optional hardening from `SECURITY_AUDIT_FINAL.md` (N-1 appeals check, N-3 helper grants): read, test on a copy, then apply.

## 3. GitHub
- [ ] Settings > Code security: **secret scanning** and **push protection** ON; Dependabot alerts + security updates ON.
- [ ] Settings > Branches: protect `main` (pull request required, status check required, no force-push, no deletion).
- [ ] Actions: `permissions: contents: read` is set in the workflow; pin actions by commit SHA (use Dependabot for actions).
- [ ] Actions secrets used for signing exist: `BOFO_KEYSTORE_BASE64`, `BOFO_KEYSTORE_PASSWORD`, `BOFO_KEY_ALIAS`, `BOFO_KEY_PASSWORD` (one set per app if the keys differ; the workflow currently builds `bofo-android` only: add the admin module before relying on CI for it).

## 4. Signing keys (two apps = two keys)
- [ ] BOFO Finder (`com.bofo.finder`): existing upload key. Keep a backup of the `.jks` and passwords in two safe places, never in the repo.
- [ ] BOFO Admin (`com.bofo.admin`): **new, separate** upload key. Never reuse the user-app key.
- [ ] Enable Play App Signing for both.
- [ ] Google sign-in: add the SHA-1 of each release key (and the Play App Signing key) to the Google Cloud OAuth client for the matching package. Easy to forget (`bofo-android/docs/RELEASE.md` section 4).
- [ ] Firebase: both package names are registered; each has its own `google-services.json` (git-ignored).

## 5. Play Console, two apps
- [ ] Two separate app entries. Admin app: **Internal testing or closed testing only** (invite your staff by email); do not publish it to the public store listing.
- [ ] Data safety form for both: the user app collects account, location, photos, ID images (owners), chats. The admin app reads personal data of others: describe it honestly.
- [ ] Privacy policy URL on both. Content rating, target audience (not for children) on both.
- [ ] Upload the `.aab` of each; versionName 2.11.0 / versionCode 21100 on both.
- [ ] Pre-launch report: read crashes before promoting to production.

## 6. Roll out order
1. Database migrations (section 2, up to the functions deploy).
2. Admin app to your staff (internal testing). Check sign-in and Team.
3. User app 2.11.0 to production (staged rollout 10%, then 50%, then 100%).
4. When most users updated: set the minimum versions (section 2), later run 045.

## 7. Rollback plan
- **User app bad:** halt the rollout in Play Console; promote the previous release. The server still accepts it until you raise `min_app_version`: so do not raise it until 2.11.0 is stable.
- **Admin app bad:** staff use the previous internal build. Admin actions still work from the old build as long as `min_admin_client_version` is not above it.
- **A migration misbehaves:** every file has a ROLLBACK note in its header (044 and 045 list the exact steps). Take a database backup / point-in-time restore point before 042-045.
- **Locked out of admin:** SQL editor (it never needs a code): `select public.set_admin_mfa_required(false);` then `select public.reset_admin_mfa('email');`. If 045 locked everyone out, run rollback step (a) to (c) of its header.
- **Leaked key:** rotate (section 1) first, ask questions later.

## 8. Smoke test, user app (BOFO Finder 2.11.0)
- [ ] Student: sign in, browse, open a listing, chat, report a listing, sign out. No admin entry anywhere (Profile, Settings).
- [ ] Owner: list a place, verification screen (screenshot is blocked, recents thumbnail is blank), group chat, announcement.
- [ ] Admin account signs in to BOFO Finder: looks like a student app, no admin tab, no admin push.
- [ ] Banned user: sees "You are banned", the Appeal button works once, a second try is refused, status shows pending.
- [ ] Suspended user: same screen with the end time and an Appeal button.
- [ ] Push: a chat message shows a notification; the notification switch in Settings turns it off.

## 9. Smoke test per role (BOFO Admin)
- [ ] **Owner:** sees everything; Team: invite, suspend, lend a permission for 1 hour, end it; Unusual activity; payments and CSV export; Appeals (accept, uphold).
- [ ] **Admin:** reports and IDs; short and long suspension; strike step; cannot open Team or payments; cannot lift a ban (needs Owner or lent `unban`); cannot decide an appeal about a decision they made.
- [ ] **Moderator:** IDs and reports; short suspension; ban and take-down ask for approval; "Being handled by ..." appears when a colleague has the item open; escalate with a note; gets "approved/denied" notification.
- [ ] **Support:** read-only: dashboard, own audit; every write button is hidden or refused.
- [ ] **No-role user** with the Admin app: sign-in refused, no data shown.
- [ ] **Expired / suspended team member:** refused at the next call.
- [ ] After running 045 (later): repeat the four staff roles once.
