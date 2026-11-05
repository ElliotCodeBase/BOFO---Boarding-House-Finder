# BOFO Security Audit (Stage 1, static analysis only)

Scope: the repo zip (v2.9.4 in `build.gradle`), working tree only. **The zip has no `.git` folder, so git history was NOT scanned.** Nothing live was probed. Secret values are never printed here (masked to 4 characters or omitted).
Not run: Gradle build, tests, Supabase queries, a dependency vulnerability scan (no network).

## Summary
- No private key, service_role key, keystore, `google-services.json` or API key is present in the working tree. The Supabase anon key in `bofo-android/config.properties` decodes to `role: anon` (public by design, OK).
- The backend is in good shape: RLS is on for every table, definer functions all set `search_path`, ID photos are in a private bucket, role/ban/suspension columns are protected by triggers, admin RPCs check `is_admin()`.
- 1 real hole fixed now (S-02). 3 items need action from you today (S-01, S-03, S-04) because I cannot see your live project or git history.

## Findings
| ID | Sev | Where | What an attacker could do | Fix / status |
|---|---|---|---|---|
| S-01 | **Critical until proven otherwise** | git history (not visible), `secrets/fcm-service-account.json` | You told me a Firebase service-account JSON existed in that path. `.gitignore` covers `secrets/`, but if the file was ever committed it is in the PUBLIC history. Anyone could send push notifications as your app and use the Google project. | **You:** rotate the key now (ROTATION_AND_HISTORY_PURGE.md). History purge is secondary. Not verifiable by me. |
| S-02 | **High** | `001_schema.sql:365-366` policy `bh_update` | A verified owner could `UPDATE boarding_houses SET status='active'` on a listing an admin had taken down, undoing every take-down and ban-driven take-down. The check only looks at `owner_id`. | **FIXED** in `038_stage1_security_fixes.sql` (trigger `protect_bh`). Needs you to run the migration. |
| S-03 | **High** | `017_admin_mfa.sql`; flag `require_admin_mfa` defaults to `false` | While the flag is off, `is_admin()` is true for any Google session of an admin account. A stolen or phished admin Google account gives ID photos, bans, payments, refunds, AI chat logs. | **You, now:** enroll TOTP on each admin, then `select public.set_admin_mfa_required(true);` (it refuses if nobody is enrolled). Stage 2 makes MFA part of the role system, Stage 3 adds it to the new app. |
| S-04 | **High (unverified)** | `015_security_hardening.sql:61-65`, `AppConfig.devEmail/devPassword`, `AuthService.java:117` (`grant_type=password`) | Docs say a "Dev Admin" account (`bofo.dev.admin@example.com`) is a full admin on the live project. If it still exists with a weak password, or a debug APK with its password leaked, that is full admin. Password sign-in is also compiled into the release app. | **You:** run the read-only check and the delete in ROTATION_AND_HISTORY_PURGE.md step 6; disable Email/Password provider in Supabase Auth if you do not use it. Debug builds embed dev passwords (never share debug APKs). Stage 3: new admin app has no password login. |
| S-05 | Medium | `023_ban.sql` `check_id_number()` | Any signed-in user can ask "is this ID number banned?" unlimited times: an oracle to enumerate banned ID numbers and test stolen IDs. | Stage 2 (rate limit table + cap). |
| S-06 | Medium | `reports`, `messages`, `bh_group_messages`, `storage.objects` (`id-verification`, `bh-photos`, `avatars`) | No per-user caps. A bot account can: spam reports (each insert pushes to **every admin phone**, `029` trigger), flood chats, fill storage with 5 MB images without limit. | Stage 2 (per-user hourly caps in triggers, storage count limit, push dedupe). |
| S-07 | Medium | `AuthService.java:141` Google `grant_type=id_token` | The Google ID token is sent without a nonce, so a captured token can be replayed until it expires. | Stage 3 (new admin app uses nonce; Stage 5 for main app). |
| S-08 | Medium | `VerificationFragment` (owner uploads ID/selfie) | `FLAG_SECURE` only exists on the admin review screen (`VerificationReviewFragment:102`). The owner's own ID capture/preview screen can be screenshotted or screen-recorded, and shows in the recents thumbnail. | Stage 5 (main app). Admin screens: Stage 3. |
| S-09 | Low | `AdminMfaSheet.java:194-195` | The authenticator setup key is copied to the clipboard (readable by other apps on older Android, kept after use). | Stage 3: mark clip sensitive and clear after 60 s. |
| S-10 | Low | `supabase/.temp/linked-project.json` (tracked) | Leaks project ref and organisation id of a public repo. | **FIXED**: removed from tracking, `supabase/.temp/` ignored. Run `git rm --cached` (STATE.md). |
| S-11 | Low | `.gitignore` | Last line is UTF-16 junk (the file is treated as binary by git), `bofo-android/.gitignore` lacks `secrets/`, `.env`, `*.pem`, service-account patterns. | **FIXED** (clean UTF-8, patterns added). |
| S-12 | Low | `.github/workflows/android-gradle.yml` | No `permissions:` block (default token may be read/write); actions pinned by tag, not commit SHA. Secrets are only used on manual `workflow_dispatch`, and `pull_request` runs get no secrets (OK). | `permissions: contents: read` **FIXED**. SHA pinning: Stage 5 (I cannot look up SHAs offline). |
| S-13 | Low | `delete-account`, `create-boost-checkout`, `bofo-chat`, `bofo-announce` | `Access-Control-Allow-Origin: *`. These use bearer tokens (no cookies), so risk is low, but unneeded for a mobile app. | Stage 2: restrict or drop CORS. |
| S-14 | Low | `delete-account/index.ts` | A stolen session can delete the account and all files with no recent re-auth. | Stage 2/5. |
| S-15 | Low | `paymongo-webhook/index.ts:21` | `Math.abs(now - Number(parts.t)) > 600` is false when `t` is not a number, so the replay check is skipped (the HMAC still has to match, so not exploitable today). | Stage 2: `!Number.isFinite(ts)` check. |
| S-16 | Low | `send-push`: `planTest(as:"me")` | Pushes to any user_id, but only with the webhook secret. | Accepted; protect the secret (rotation list). |
| S-17 | Low | `ai_chat_log` | Stores user name, email and every question; admins can read all. Purge function exists but needs a cron. | Stage 2: owner-only view, retention cron. |
| S-18 | Info | `bofo-android/app/build.gradle:14-20` | Config is read from `../src/main/resources/com/bofo/finder/config*.properties` (old desktop path, not in this repo), not from `bofo-android/config.properties`. CI builds may get an empty Supabase URL/key. Not a security issue, but check it. | Reported only. |
| S-19 | Info | `docs/` (3,500 lines), `FINAL-HANDOFF.md`, `*-STATUS.md` | Public repo documents your internal architecture, AI-dev DB role, deploy commands and project ref. No credentials or personal data found (searched for emails, UUIDs, phone numbers; only test fixtures). | Stage 5: move internal docs to a private repo. |
| S-20 | Info | dependencies | `okhttp 4.12.0`, `glide 4.16.0`, `firebase-bom 33.7.0` (older), others current-ish. No CVE scan was possible offline. | Stage 5: run `dependency-check` / Dependabot. |

## Checked and OK (so you know it was looked at)
- **RLS**: enabled on all 36 tables created in the migrations; `using(true)` only on public catalogue tables (colleges, amenities, reviews, bh_activity, app_flags). Tables with no policy (ai_usage, app_secrets, device_tokens, push_settings, bh_view_seen) are denied by RLS, and the secret-bearing ones also have grants revoked.
- **Mass assignment**: `protect_users` (role, is_verified, verification_status, suspension, ban), `protect_reviews`, `protect_ov` (id_hash) reset protected columns for client roles. users insert policy forces role in student/owner and unverified.
- **IDOR**: conversations/messages/favorites/reviews/reports policies bind to `auth.uid()`; group RPCs check membership.
- **Views**: `reports_admin_v`, `bh_cards`, `profiles_v`, `inbox_v` are owner-run views whose WHERE uses `auth.uid()`/`is_admin()`: safe as written. Do not add columns without keeping that filter. Stage 2 will switch to `security_invoker` where possible.
- **SECURITY DEFINER**: every function found sets `search_path`; no dynamic SQL built from input; admin functions revoke from `public, anon`.
- **Storage**: `id-verification` is private, owner insert into own folder only, no update/delete, read = owner or admin. Admin screen loads via signed URLs and has FLAG_SECURE and no Glide cache.
- **Edge functions**: all user functions call `auth.getUser()`; send-push and paymongo-webhook use a secret / HMAC with replay window and amount check; checkout prices server-side with throttle.
- **FCM**: no topics. Admin pushes go to device tokens of users whose DB role is admin, decided at send time; tokens are only writable through RPCs.
- **Android manifest**: only `MainActivity` exported (launcher, `bofo://auth-callback`, push action); `allowBackup=false`; cleartext off; FileProvider not exported; no WebView. PKCE protects the callback code.
- **Session storage**: AES-256-GCM with an Android Keystore key.
- **CSV export**: `CsvRules.cell` neutralises `= + - @` and tab (unit-tested).
- **PostgREST filter**: `AiChatRules.searchTerm/orFilter` strips `* % \ "`, commas, parentheses and URL-encodes.
- **Logging**: 21 `Log.*` calls; none print tokens, emails or passwords.
- **R8**: release has minify and shrink on.
