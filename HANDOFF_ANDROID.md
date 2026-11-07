# Handoff (Android) - v2.11.1: test fix after the first real build
- 2.11.0 COMPILED (both apps). The only failing unit test was a Looper-dependent one in `AnnounceAiServiceTest`; fixed in the test only. Still not run on a device. SQL 038-045 still unapplied unless the user says otherwise.
- Next: run the same Gradle command, then device test and the RELEASE_CHECKLIST. Version 2.11.1 / 21101.

# Handoff (Android) - v2.11.0: Stage 5, admin removed from the user app
- See `STATE_STAGE5.md`, `SECURITY_AUDIT_FINAL.md`, `RELEASE_CHECKLIST.md`. Delete the files in `DELETED_FILES.txt` after unzipping. Migrations 038-044 may be unapplied (order in RELEASE_CHECKLIST 2); 045 is for later.
- Nothing compiled: from `bofo-android`: `.\gradlew :shared:testDebugUnitTest :app-admin:assembleDebug :app:assembleDebug :app:testDebugUnitTest`. If it fails, fix that first (small edit, patch bump).
- Next: compile + device test both apps, apply SQL, the open items in SECURITY_AUDIT_FINAL section 7, release per the checklist. Version 2.11.0 / 21100.

# Handoff (Android) - v2.10.0: admin app Stage 4 finished
- See `STATE_STAGE4i.md`. Migration 044 written, NOT applied; send-push needs redeploy. 042 and 043 may also be unapplied. Apply order: 042, 043, 044.
- Nothing compiled: from `bofo-android`: `.\gradlew :shared:testDebugUnitTest :app-admin:assembleDebug :app:assembleDebug`.
- Next: apply migrations + deploy + device test, then Stage 5 (remove admin from the user app, final security audit, release checklist). Version 2.10.0 / 21000.

