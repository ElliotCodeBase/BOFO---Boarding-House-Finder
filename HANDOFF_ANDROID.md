# Handoff (Android) - v2.7.0: Admin next steps, step 3 of 5 PARTLY done (reject templates + second opinion)
- Done: reject templates (`RejectDialog`, strings in `strings_review_admin.xml`), second-opinion flag (`ReviewSpeedRules` + test, `VerificationService.flagSecondOpinion`, `QueueFragment.quick/flag`, `strings_admin_speed.xml`, migration `034_second_opinion.sql` NOT run). Audit actions `verification_flagged` / `verification_unflagged` show as "Other action" in What I did (not added to `AdminActivityRules.ACTIONS` to avoid clashing with step 1).
- NOT done: "Approve clean (N)". `admin_approve_verification` requires the retyped ID number, so a bulk approve needs a new server path that skips that check. Wait for the user's decision before building it.
- Repo check: the zip the user supplied has the v2.5.0 notes but NO step 1 code (no `RecentDecidedFragment`, `AdminUndoRules`, `032_admin_undo.sql`). Ask the user to apply the step 1 zip.
- Nothing compiled or run: `gradlew testDebugUnitTest assembleDebug`.
- Version 2.7.0 / 20700.

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
