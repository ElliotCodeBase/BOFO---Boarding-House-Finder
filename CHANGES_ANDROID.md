# v2.12.0 - BOFO Admin: Stage 4i rebuilt (safety features)
- Admin app: queue claiming ("Being handled by ..."), waiting-time colours (green / amber / red), Escalate (+ Escalated chip, Clear), strikes ladder (warning, 3 d, 7 d, ban), Appeals screen, break-glass (Owner lends one permission for 1 hour), recent sign-ins per person, Unusual activity screen, pushes for escalated / decided / unusual.
- User app: banned and suspended screens get "Appeal this decision" (one appeal per decision, decided by a different team member).
- Migration `044_admin_stage4_safety.sql` (NOT applied) and a `send-push` redeploy are needed. New `shared/QueueRules` (+ tests), `PushRules` has 3 new kinds. Not compiled or run on a device.

# v2.11.1 - Fix: one unit test could never pass
- `AnnounceAiServiceTest.theDefaultServiceUsesSupabaseClientNotAnythingElse` waited for the Android main thread, which does not exist in a plain JVM test, so it timed out after 5 s. Replaced by a test that only checks the default service can be built. No app code changed. Version 2.11.1 (versionCode 21101).
- First real Gradle run of 2.11.0: both apps compiled, 306 of 307 tests passed (this was the 1).

# v2.11.0 - Stage 5: admin removed from the user app, final security audit, release checklist
- BOFO Finder no longer has any admin screen, tab, switch or admin notification (admin work is only in the BOFO Admin app). An admin account that opens it looks like a student. Banned / suspended screens and the Appeal button stay.
- The owner ID/selfie screen can no longer be screenshotted or shown in the recents list (S-08). Old admin notification channels are removed from phones.
- New: `SECURITY_AUDIT_FINAL.md`, `RELEASE_CHECKLIST.md`, migration `045_drop_legacy_admin.sql` (run only after old apps are retired; it stops by itself if unsafe), `038` copied into `supabase/migrations/`, `.gitignore` and workflow permissions fixed.
- 91 files are deleted: see `DELETED_FILES.txt` (a zip cannot delete). Version 2.11.0 (versionCode 21100). Not compiled or run in the sandbox.
