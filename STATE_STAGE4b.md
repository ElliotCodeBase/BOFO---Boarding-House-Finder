# STATE after Stage 4, part 2 (admin app 2.9.9 / 20909)

## Done
- `supabase/migrations/041_admin_client_version_gate.sql`: the admin app has its own minimum version. Requests with header `x-bofo-client: admin` are checked against `min_admin_client_version`; all other requests keep using `min_admin_app_version` exactly as before. New owner wrapper `admin_set_min_admin_client_version(v)` (needs `app_config`), logged in the new audit log. Nothing changes until you set a value. Rollback notes are in the file header. NOT run (no Postgres here).
- App: More > "Banned and suspended" (`banned_view`; Unban with required reason + fingerprint/screen lock, owner only) and More > "Payments" (`payments_view`, read-only list of the latest 100).

## How to apply
1. Supabase SQL editor: run `041_admin_client_version_gate.sql` (after 039 and 040). Check: `select public.app_version_ok();` in the editor returns true.
2. Extract the zip over the repo, `.\gradlew :app-admin:assembleDebug`.
3. Do NOT turn the gate on until your phone runs the admin app 2.9.9 or newer: `select public.set_min_admin_client_version('2.9.9');` Turn off with an empty text.

## NOT done (backlog)
1. Admin push: `FirebaseMessagingService` in the admin app, `device_tokens` app column, `send-push` targeting `admin_members` by permission, "approval needs you" push. (I did not read `send-push` yet; this needs its own careful step.)
2. Unanswered AI questions screen, refunds, CSV export (formula-injection safe, must call `admin_log_export` first).
3. Strikes ladder, Escalate, queue claiming + waiting colours, saved replies, unusual-activity alert, session list + revoke screen, break-glass, appeals.
4. Two-step enrolment inside the admin app.
