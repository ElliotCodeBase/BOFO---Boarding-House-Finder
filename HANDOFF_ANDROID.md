# Handoff (Android) - v2.6.0: Admin next steps, step 2 of 5 done (admin push)
- Plan: `docs/ADMIN_NEXT_STEPS.md` (one step per turn, STOP until `next`). Done: Step 1 (v2.5.0), **Step 2 (v2.6.0)**. **Next: Step 3 = faster reviews**: rejection templates in the ID reject dialog, "Approve clean (N)" bulk approve on the Queue, "needs a second opinion" flag (column + RPC = migration 034, needs the user's OK first).
- New: migration `033_admin_push.sql` (user approved; NOT run live by the AI): trigger `push_idreview` on `owner_verifications` + `push_stale_check()` (no grants; called by pg_cron or by hand). Cron is NOT in the migration: see `docs/PUSH_SETUP.md` "Admin pushes".
- `supabase/functions/send-push/index.ts`: new kinds `idreview` (skips the push if the row is no longer pending, e.g. a banned ID is auto-rejected), `stale` (counts only; `place` = queue|reports|payments, the biggest group), `test` (`{"as":"idreview"|"stale"|"report"}`). Admins are read from `users.role='admin'` at send time. Must be REDEPLOYED.
- App: `PushRules` (IDREVIEW, STALE, CH_ADMIN, `adminOnly`, `adminTab`, tests), `PushNotifier.createAdminChannels` (also called by `PushService.syncAsync` for admins), admin pushes use the lock-screen-safe layout, `ShellFragment.openFromPush` opens Queue / Reports / Payments with the Waiting filter, strings in `strings_admin_push.xml`.
- NOTE: the prompt (rule 7) says pushes carry a `notification` block, `push_*` keys and `buildMessage` "since v2.4.15". That code is NOT in the repo the user supplied: `send-push` is data-only and the app draws the notification (`PushNotifier`, `BofoMessagingService`), which already works with the app closed. The new kinds follow the repo as it is. If a newer send-push exists on the user's PC, merge the three new plan functions into it.
- Open risks: tap while the Queue tab is already open does not re-apply the Waiting filter (existing `showTab` behaviour). Nothing was tested on a device or against FCM.
- Nothing was compiled or run: run `gradlew testDebugUnitTest assembleDebug`. `PushRules`/`PushRulesTest` were compiled and run with plain javac/JUnit (11 tests pass).
- Version 2.6.0 / 20600.

# Handoff (Android) - v2.5.0: Admin next steps, step 1 of 5 done (Undo + Recently decided)
- Plan: `docs/ADMIN_NEXT_STEPS.md` (the user pastes it; says one step per turn, then STOP until `next`). Done: **Step 1**. **Next: Step 2 = admin push for new ID reviews and items waiting over 3 days** (`send-push`, `PushRules`, `PushNotifier`, migration 033 needs the user's OK).
- New: migration `032_admin_undo.sql` (user approved; NOT run on live by the AI): `admin_unsuspend(uid)`, `admin_relist(bh)`, `admin_reopen_report(rid)`; audit actions `user_unsuspend`, `listing_relist`, `report_reopen`.
- New app files: `ui/RecentDecidedFragment` (nav `recentDecidedFragment`, card on `AdminHomeFragment`), `service/AdminUndoService`, `util/AdminUndoRules` (+ test), `ui/RowSwipe` (ListView swipe helper), strings in `strings_admin_undo.xml`. `AdminRowAdapter.Row` has an optional Undo button (`row_undo` in `item_admin_row.xml`). `ReportsFragment.swipeDismiss` / `undoSwipe`. `AdminActivityRules.ACTIONS` + `admin_act_labels` got 3 entries at the END (indexes unchanged). `AdminErrorRules` knows "nothing to undo".
- Recently decided reads `audit_logs` (all admins' rows, columns action / target / actor / time / report id; never reasons) and hides Undo when a newer row of the list already undid it.
- Open risk: `audit_logs` SELECT grant for `authenticated` unverified (check: `select has_table_privilege('authenticated','public.audit_logs','select');`). Without it Recently decided and What I did show "could not load".
- Open risk: swipe (`RowSwipe`) and screen-reader use were not tested on a device; swipe has no TalkBack action (long-press menu is the non-gesture way).
- Fixed the non-public `AdminRowAdapter.isEmpty()` compile error (if the user's local copy already fixed it, the zip just overwrites with the same fix).
- Nothing was compiled or run: run `gradlew testDebugUnitTest assembleDebug`.
- Version 2.5.0 / 20500.

# Handoff (Android) - v2.4.14: Admin UI plan COMPLETE (step 5 of 5 done)
- All 5 steps are shipped: 2.4.10 Home + badges, 2.4.11 review screen, 2.4.12 safe decisions + ban Undo + long-press, 2.4.13 lists + reports, 2.4.14 clarity / trust / accessibility / docs. Guide + test checklist: `docs/ADMIN_GUIDE.md`; audit with final status: `docs/ADMIN_UI_AUDIT.md`.
- Step 5: `ui/ActivityLogFragment` (nav `activityLogFragment`, reads `audit_logs` where `actor_id` = me via the existing admin-read policy; no migration; shows action/target/time only, never reasons), `util/AdminActivityRules`, `util/AdminErrorRules` (drop-in for `ErrorText.full` in the 5 admin screens), hints in `SettingsFragment.withHint`, admin banner in `fragment_shell.xml` / `ShellFragment`, tab fade skipped when animations are off, strings in `strings_admin_clarity.xml`.
- Not done (say so to the user): auto-lock after inactivity; Undo for suspend / take down / dismiss (needs a migration with `admin_unsuspend`, `admin_relist`, `admin_reopen_report`; the user has not approved it); AI unanswered / AI chat log screens still use their own list look; dark theme and 200% text were not checked on a device.
- Open risk: if `audit_logs` has no SELECT grant for `authenticated`, What I did shows "could not load" (RLS policy `audit_admin_read` exists, the table grant was not verified).
- Nothing was compiled or run: run `gradlew testDebugUnitTest assembleDebug` and the checklist in `docs/ADMIN_GUIDE.md`.
- Version 2.4.14 / 20414.
