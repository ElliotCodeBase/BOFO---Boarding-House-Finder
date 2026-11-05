# STATE after Stage 2 (version 2.9.6 / versionCode 20906)

## Done (all files are in this zip, same paths as the repo, plus `stage2.patch`)
- `supabase/migrations/039_admin_roles.sql`: tables `admin_roles`, `admin_members`, `admin_sessions`, `admin_invites`, `admin_audit_log` (append-only), `admin_approvals`, `rate_limits`; functions `has_permission`, `require_permission`, `is_staff`, `is_admin` (redefined = any active staff), `my_admin_access`, `bootstrap_owner`, invites (create/revoke/accept), member management (update/suspend/remove/logout-all), activity reads, `admin_log_export`, approvals (request/list/decide); rate limits on reports, chat, ID check (S-05, S-06); storage count caps (S-06).
- `supabase/migrations/040_admin_permission_gates.sql`: 13 existing admin functions re-created with a permission check instead of `is_admin()` (bodies copied by script from the latest migration that defines them, see the `-- from` comments); sensitive policies moved to permissions; owner-only wrappers `admin_set_min_app_version`, `admin_set_min_admin_app_version`, `admin_reset_mfa`; AI chat log owner-only + retention job (S-17); `check_id_number` rate limit (S-05).
- `supabase/tests/stage2_role_tests.sql`: runs in a rolled-back transaction, one PASS/FAIL row per check (permission matrix for Owner/Admin/Moderator/Support/no-role, approvals, invites, expiry, log-out-all, owner rules, audit-log immutability).
- `bofo-android/.../util/PermissionRules.java` + `PermissionRulesTest.java`: client mirror of the permission model (UI only; server decides).
- Edge functions: CORS wildcard removed from 4 functions (S-13); `paymongo-webhook` refuses a non-numeric timestamp (S-15).
- `bofo-android/app/build.gradle`: version bump only (2.9.5 -> 2.9.6). If your local file is newer, do not copy mine; bump yours by +1.
- Security findings closed in Stage 2: S-05, S-06, S-13, S-15, S-17. (S-14 re-auth for delete-account moves to Stage 5.)

## What WAS and was NOT run
- RUN: `PermissionRulesTest` logic (10 tests, compiled with javac 21 and a stub JUnit, all passed); a script check that the Java permission lists, owner-only list and presets are identical to the SQL ones.
- NOT RUN: the SQL (no Postgres in this sandbox), the Gradle build, the real JUnit run, the Deno edge functions. Run `stage2_role_tests.sql` once after the migrations and send me any FAIL row.

## How to apply (order matters)
1. Back up: Supabase dashboard > Database > Backups (or `pg_dump`).
2. SQL editor: run `039_admin_roles.sql`, then `select public.bootstrap_owner('YOUR-OWNER-GOOGLE-EMAIL');` (the account must have signed in once), then `040_admin_permission_gates.sql`, then `supabase/tests/stage2_role_tests.sql`.
3. Extract this zip over the repo root, then `supabase functions deploy bofo-chat delete-account bofo-announce create-boost-checkout` and `supabase functions deploy paymongo-webhook --no-verify-jwt`.
4. Stage 1 items still pending from you: rotate Firebase key (S-01), migration 038, delete dev accounts (S-04), admin MFA on (S-03).

## Behaviour to know
- Nothing changes for the current admin today: a user with `users.role='admin'` and no `admin_members` row keeps full access ("legacy", removed in Stage 5). Once you run `bootstrap_owner`, that account is an Owner (still everything).
- Any OTHER old admin account (for example a leftover dev admin) is still a full legacy admin: delete it (S-04).
- Owner-only items (the 12) are now REALLY owner-only on the server: Admin loses `set_ai_enabled`, refunds, unban, take-down, AI chat log, payments view. If you want an Admin to keep one, remove nothing: just do it as Owner, or tell me and Stage 3 adds a per-person toggle (owner-only keys can never be granted to a sub-admin, by design).
- An owner cannot delete their own account through the app (the owner row is protected). Move ownership with the break-glass steps in STAGE2_CHANGES.md first.

## Known limits / decisions for later stages
1. Admin pushes (`send-push`, triggers `push_report`, `push_idreview`) still target users with `users.role='admin'`: sub-admins get no push yet. Stage 3/4: target `admin_members` by permission (reports_view / id_review).
2. App-version gate: `app_version_ok()` compares ONE minimum to the header `x-bofo-app-version`; the new admin app has its own version numbers. Stage 3 must send a header the gate can tell apart (plan: `x-bofo-client: admin` + a separate minimum) before anyone sets `min_admin_app_version` above the admin app's version.
3. "Moderators can hide a listing pending review" is not possible: listings only have `active/unlisted/taken_down`, and `unlisted` is the owner's own switch. Moderators use the approval flow (`report_takedown`). A `hidden` status needs an app change (Stage 4).
4. CSV export, watermark and "no download" for ID images are client behaviour: the server can only refuse the data (`admin_log_export` and `id_view_raw`). Stage 3/4 must call `admin_log_export` before exporting and add the watermark.
5. Users and listings are readable by every active staff member (needed for names in queues). Payments, ID photos outside a pending review, AI chat log, audit trail of others, banned list are permission-gated.
6. `reports_admin_v`, `bh_cards` keep `is_admin()` in their WHERE: this now means "any staff" (read-only views).
7. Session list: `admin_touch_session` records devices; revoking works through `revoked_before` (JWT issue time) in `admin_member_logout_all`, enforced inside every permission check.

## Stage 3 first step
Read `PROMPT.md` Stage 3. Create the Gradle modules `shared/` and `app-admin/` next to `bofo-android/` (applicationId `com.bofo.admin`), move `PermissionRules` into `shared`, Google sign-in with nonce, call `my_admin_access()`, show "No access" + sign out when `active=false`, then port Home, ID queue/review and Reports using the RPCs above. Bump to 2.9.7.
