# v2.12.1 - Verification Stage 4: Google nonce, re-auth before delete, release gate for test logins
- S-07: user-app Google sign-in sends a nonce (Google gets the SHA-256, Supabase the raw value), same pattern as the Admin app. New `util/Nonce` (+ test).
- S-14: "Delete my account" now asks you to prove it is you first: Google accounts sign in with Google again, others get an e-mail code. A sign-in for a different account deletes nothing. New `util/ReauthRules` (+ test).
- S-04: `AppConfig.devEmail/devPassword` return empty and `AuthService.devSignIn` refuses unless `BuildConfig.DEBUG`; `tools/verify/check_release_dev.py` checks `build.gradle` too.
- Both apps 2.12.1 / 21201. Not compiled or run on a device. Needs Supabase Google provider "Skip nonce checks" OFF.

# v2.12.0 - BOFO Admin: Stage 4i rebuilt (safety features)
- Admin app: queue claiming ("Being handled by ..."), waiting-time colours (green / amber / red), Escalate (+ Escalated chip, Clear), strikes ladder (warning, 3 d, 7 d, ban), Appeals screen, break-glass (Owner lends one permission for 1 hour), recent sign-ins per person, Unusual activity screen, pushes for escalated / decided / unusual.
- User app: banned and suspended screens get "Appeal this decision" (one appeal per decision, decided by a different team member).
- Migration `044_admin_stage4_safety.sql` (NOT applied) and a `send-push` redeploy are needed. New `shared/QueueRules` (+ tests), `PushRules` has 3 new kinds. Not compiled or run on a device.

# v2.11.1 - Fix: one unit test could never pass
- `AnnounceAiServiceTest.theDefaultServiceUsesSupabaseClientNotAnythingElse` waited for the Android main thread, which does not exist in a plain JVM test, so it timed out after 5 s. Replaced by a test that only checks the default service can be built. No app code changed. Version 2.11.1 (versionCode 21101).
- First real Gradle run of 2.11.0: both apps compiled, 306 of 307 tests passed (this was the 1).
