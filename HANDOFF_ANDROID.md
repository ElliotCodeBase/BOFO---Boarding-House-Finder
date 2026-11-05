# Handoff (Android) - v2.9.0: Admin next steps, step 5 of 5 PARTLY done (auto-lock only)
- Done: auto-lock. `AutoLockRules` (+ test), `AppPrefs.adminAutoLock / adminStoppedAt`, `AdminLock.locked` (static, survives rotation), `ShellFragment.onStop/onStart/lock/unlocked`, `AdminMfaSheet.showLock` (lock mode: not cancelable, "Sign out instead" button, Try again when offline, closes at once when the account has no authenticator), Settings row `admin_lock_row`, strings in `strings_admin_lock.xml`.
- NOT done: moderator role split. Plan: migration `035_admin_level.sql` (`users.admin_level` 'admin'|'moderator', default 'admin'; extend `protect_users` so nobody changes their own level; check the level inside admin_review_report ban/suspend/takedown actions, admin_unban, boost refund, admin_unsuspend / admin_relist if step 1 exists), app hides those buttons, banner shows "Moderator". Needs the user's OK for the migration first.
- Still open from earlier steps: step 1 code missing in the user's repo; step 3: own saved templates, Approve clean, flag on reports + chip; step 4: item 11 (AI screens on AdminListChrome).
- Open risks: a process kill while locked re-locks on the next start (stoppedAt is kept in prefs) but a restored lock sheet may open in normal mode first; lock/rotation/offline paths not tested on a device.
- Nothing compiled or run: `gradlew testDebugUnitTest assembleDebug`.
- Version 2.9.0 / 20900.

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
