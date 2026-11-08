# Handoff (Android) - v2.12.5: migration 047 (applied live) fixes the Approvals list

# Handoff (Android) - v2.12.4: Admin tab switching
- `MainActivity.open` caches one view per tab (`tabs`, `tabBuilt`, 3 min); `HomeScreen.load` runs its counts in parallel on `Async.fanout()`. Database 033-044 + 046 are applied live (045 and 014 are NOT); owner = jorenemencianomendez@gmail.com.

# Handoff (Android) - v2.12.3: Admin Google sign-in error shows the reason
- If the Admin sign-in fails, the toast now names the exception and the installed package (`com.bofo.admin` or `.debug`). Fix is in Google Cloud: an Android OAuth client per package with the signing key SHA-1 (`gradlew :app-admin:signingReport`). Then continue with Stage 6.

# Handoff (Android) - v2.12.2: verification Stage 5 (admin accessibility + smoke sheet)
- See `STATE_VERIFY_5.md`. Next: `cd bofo-android; .\gradlew :shared:testDebugUnitTest :app-admin:assembleDebug :app:assembleDebug`, paste the first error if any. Then TalkBack pass with `docs/SMOKE_TEST_SHEET.md` (A section first).
- Verification progress: `VERIFY_PROGRESS.md` (Stage 6 is next: CI, release setup, final report). SQL 038-046 still to be applied by the user.

# Handoff (Android) - v2.12.1: verification Stage 4 (app-side security)
- See `STATE_VERIFY_4.md`. Next: `cd bofo-android; .\gradlew :shared:testDebugUnitTest :app:testDebugUnitTest :app-admin:assembleDebug :app:assembleDebug :app:assembleRelease`, then paste the first error if any. Then device test: Google sign-in, delete account (Google + e-mail code), release build has no test-login buttons.
- Verification progress: `VERIFY_PROGRESS.md` (Stage 5 is next: admin accessibility + per-role smoke tests). SQL 038-046 still to be applied by the user.

# Handoff (Android) - v2.12.0: Stage 4i rebuilt (admin safety features)
- The 2.10.0 / Stage 4i code was missing from the repo (docs and version said done, `app-admin` was still at 2.9.15: no 044, no QueueRules, no Appeals). This version REBUILDS it from the descriptions in `docs/ADMIN_GUIDE.md`, `SECURITY_AUDIT_FINAL.md` section 5 and `045_drop_legacy_admin.sql` (table / function names match them). If you still have the real 2.10.0 files, diff before merging.
- See `STATE_STAGE4i.md`. Migration `044_admin_stage4_safety.sql` written, NOT applied; `send-push` must be redeployed. Apply order: 042, 043, 044 (045 only later).
- N-1 (old appeal lifts a newer decision) and the N-4 place-only strike message are already fixed inside 044.
- Nothing compiled in the sandbox for Android; `QueueRulesTest` + `PushRulesTest` (15) pass on plain JUnit stand-ins. SQL never run. Next: `.\gradlew :shared:testDebugUnitTest :app-admin:assembleDebug :app:assembleDebug`, apply 044, device test per `RELEASE_CHECKLIST.md`. Version 2.12.0 / 21200.
