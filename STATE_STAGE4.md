# STATE after Stage 4, part 1 (admin app 2.9.8 / 20908)

Per the prompt's split rule, this part ships the management screen, audit log, reasons and approvals first.

## Done (app-admin only, no new SQL: all RPCs already exist from Stage 2)
- **Invite code entry**: the "No access" screen now lets an invited person paste the code (`admin_invite_accept`) or sign out. Without this an invite could never be accepted in the new app.
- **Team** (More > Team, Owner / `members_manage`): invite (Google e-mail, role, optional access-ends-after-N-days; code shown once with Copy), member list (role, status, last active, actions count), change role, per-person permission toggles (owner-only keys are not offered), suspend / reactivate, log out all devices, remove. Owners cannot be changed. Fingerprint / screen lock before each change.
- **Activity log**: More > What I did (`audit_view_own`), More > All admin activity (`audit_view_all`, Owner).
- **Approvals** tab: list of requests; Approve / Deny only when `PermissionRules.canDecide` is true (needs `approvals_decide`, the action's own permission, and not your own request). Waiting requests show "Waiting for an approver".
- **Reasons**: quick-pick reason chips on Reports actions (reason still required for suspend, ban, take down).
- Tabs: Home, IDs, Reports, Approvals, More (each only if the role can use it). Version 2.9.8 for the admin app and the main app.

## NOT run
Gradle build of this part was not run (no SDK here). Please run `.\gradlew :app-admin:assembleDebug` and send me the first error.

## NOT done yet (backlog, in the order I suggest)
1. Migration 041: admin-client version gate (`x-bofo-client: admin`) + `device_tokens` app column + push targeting by permission; FCM service in the admin app; "approval needs you" push. Do not set `min_admin_app_version` before this.
2. Strikes ladder, Escalate button, queue claiming + waiting-time colours, saved reply templates, unusual-activity alert, per-admin session list + revoke screen, break-glass, appeals, undo window beyond ban-undo.
3. Features still only in the old app: Payments check, Unanswered AI questions, Banned/suspended lists, CSV export (formula-injection safe, must call `admin_log_export` first).
4. Two-step enrolment inside the admin app.

## First step of the next part
Write `041_admin_client_gate_and_push.sql` (read `app_version_ok()` and `send-push` first), then the banned/suspended list + unban, then Payments and Unanswered AI.
