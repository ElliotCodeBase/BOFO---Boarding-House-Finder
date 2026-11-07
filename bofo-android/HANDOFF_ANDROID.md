# Handoff (Android) - v2.12.0: Stage 4i rebuilt (admin safety features)
- The 2.10.0 / Stage 4i code was missing from the repo (docs and version said done, `app-admin` was still at 2.9.15: no 044, no QueueRules, no Appeals). This version REBUILDS it from the descriptions in `docs/ADMIN_GUIDE.md`, `SECURITY_AUDIT_FINAL.md` section 5 and `045_drop_legacy_admin.sql` (table / function names match them). If you still have the real 2.10.0 files, diff before merging.
- See `STATE_STAGE4i.md`. Migration `044_admin_stage4_safety.sql` written, NOT applied; `send-push` must be redeployed. Apply order: 042, 043, 044 (045 only later).
- N-1 (old appeal lifts a newer decision) and the N-4 place-only strike message are already fixed inside 044.
- Nothing compiled in the sandbox for Android; `QueueRulesTest` + `PushRulesTest` (15) pass on plain JUnit stand-ins. SQL never run. Next: `.\gradlew :shared:testDebugUnitTest :app-admin:assembleDebug :app:assembleDebug`, apply 044, device test per `RELEASE_CHECKLIST.md`. Version 2.12.0 / 21200.

# Handoff (Android) - v2.11.1: test fix after the first real build
- 2.11.0 COMPILED (both apps). The only failing unit test was a Looper-dependent one in `AnnounceAiServiceTest`; fixed in the test only. Still not run on a device. SQL 038-045 still unapplied unless the user says otherwise.
- Next: run the same Gradle command, then device test and the RELEASE_CHECKLIST. Version 2.11.1 / 21101.

# Handoff (Android) - v2.11.0: Stage 5, admin removed from the user app
- See `STATE_STAGE5.md`, `SECURITY_AUDIT_FINAL.md`, `RELEASE_CHECKLIST.md`. Delete the files in `DELETED_FILES.txt` after unzipping. Migrations 038-044 may be unapplied (order in RELEASE_CHECKLIST 2); 045 is for later.
- Nothing compiled: from `bofo-android`: `.\gradlew :shared:testDebugUnitTest :app-admin:assembleDebug :app:assembleDebug :app:testDebugUnitTest`. If it fails, fix that first (small edit, patch bump).
- Next: compile + device test both apps, apply SQL, the open items in SECURITY_AUDIT_FINAL section 7, release per the checklist. Version 2.11.0 / 21100.
