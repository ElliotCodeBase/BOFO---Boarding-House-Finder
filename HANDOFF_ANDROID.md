# Handoff (Android) - v2.8.0: Admin next steps, step 4 of 5 PARTLY done (items 8, 9, 10)
- Plan: `docs/ADMIN_NEXT_STEPS.md`. Done: Step 1 (v2.5.0, code missing in the user's repo zip: ask them to apply it), Step 2 (v2.6.0), Step 3 PARTLY (v2.7.0: 8 reject templates + second-opinion flag on IDs only, migration 034 not run; NOT done: saved own templates, "Approve clean" bulk approve (server needs the retyped ID number), flag on reports, filter chip), Step 4 PARTLY (v2.8.0).
- New: `AdminWeeklyService` + "Last 7 days" card (`AdminHomeFragment.showWeek`), `InsightRules`, `CsvRules` (+ tests), `CsvShare` (cache `exports/`, FileProvider path added to `file_paths.xml`), `exportCsv` button in `fragment_admin_list.xml` (hidden except Reports and Payments), `ReportsFragment` person filter (`person`, `showPerson`, `personCount`), strings in `strings_admin_insights.xml`.
- NOT done in step 4: item 11 (AI unanswered / chat log on `AdminListChrome` + `AdminRowAdapter`): the 6 reason filters do not fit the 3 chips; options: extend `AdminListChrome` with custom chips, or keep their own chips and add only search + row look.
- Open risks: weekly numbers cap at 1000 ("1000+"); `reports_admin_v` is assumed readable for the weekly count; export and person filter not tested on a device.
- Next: Step 5 (auto-lock, moderator role split, final QA).
- Nothing compiled or run: `gradlew testDebugUnitTest assembleDebug`.
- Version 2.8.0 / 20800.

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
