# STATE after Stage 4, part 4 (admin app 2.9.11 / 20911): admin push

Interpreted "next stage" as the first item of the Stage 4c backlog (admin push). Stage 5 (delete admin from the user app) still waits.

## Done
- **Migration `042_admin_push.sql` (written, NOT applied; needs your OK):** `device_tokens.app` ('user'|'admin'); `register_push_token(token, version, app)` (3rd arg defaults to 'user', so the user app is unchanged; 'admin' needs `is_staff()`); `staff_has(user, perm)`; `push_admin_tokens(perms[], exclude)` (service role only); trigger `push_approval` on `admin_approvals`.
- **`send-push`:** admin kinds target staff by permission (report = reports_act, idreview = id_review, stale = per-person counts, approval = approvals_decide, admin-app tokens only). dm/group read only 'user' tokens. Admin-app tokens get `click_action = com.bofo.admin.OPEN_PUSH`. Falls back to the old `users.role='admin'` rule if 042 is missing, so deploy order cannot break pushes. Test kind `approval` added.
- **Admin app:** `AdminMessagingService` (foreground, lock-screen safe), `PushRegistrar` (register after the role check, unregister at sign-out), channels `bofo_admin` / `bofo_reports`, Android 13 notification permission, tap routes to the right tab (`singleTop`, `onNewIntent`), `AdminApp.access()` for the phone-side check. `shared/PushRules` (+ test): phone drops a push the account may not see.
- Docs: `docs/PUSH_SETUP.md` section "Admin app push". Versions 2.9.11 in both `build.gradle` files.

## What was and was NOT run
- RAN: TypeScript strict type-check of `send-push/index.ts` with stubs for Deno and supabase-js (clean).
- NOT run: Deno, the SQL, Gradle, the Java unit tests (no JDK/JUnit in this sandbox; `PushRulesTest` is unrun), any phone. Expect possible small compile errors: send me the first one.

## Decisions to check
1. `report` pushes now go to `reports_act` holders, so Support (view only) gets none. Change the permission in `planReport` if you want them to.
2. No push yet when an approval is DECIDED (back to the moderator).
3. Pushes still arrive on a locked app (contents stay private). Auto-lock does not stop them.

## How to test
See `docs/PUSH_SETUP.md`, "Admin app push". Also: a Moderator must get NO approval push; a student phone must get nothing admin.

## NOT done (backlog)
Strikes ladder, Escalate, queue claiming, saved replies, unusual-activity alert, session list + revoke, break-glass, appeals (migrations); Refunds, AI chat log + settings in the admin app; two-step enrolment in the admin app; then Stage 5.
