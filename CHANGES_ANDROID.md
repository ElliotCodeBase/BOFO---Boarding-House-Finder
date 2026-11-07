# v2.11.0 - Stage 5: admin removed from the user app, final security audit, release checklist
- BOFO Finder no longer has any admin screen, tab, switch or admin notification (admin work is only in the BOFO Admin app). An admin account that opens it looks like a student. Banned / suspended screens and the Appeal button stay.
- The owner ID/selfie screen can no longer be screenshotted or shown in the recents list (S-08). Old admin notification channels are removed from phones.
- New: `SECURITY_AUDIT_FINAL.md`, `RELEASE_CHECKLIST.md`, migration `045_drop_legacy_admin.sql` (run only after old apps are retired; it stops by itself if unsafe), `038` copied into `supabase/migrations/`, `.gitignore` and workflow permissions fixed.
- 91 files are deleted: see `DELETED_FILES.txt` (a zip cannot delete). Version 2.11.0 (versionCode 21100). Not compiled or run in the sandbox.

# v2.10.0 - BOFO Admin app: Stage 4 finished (safety features)
- Admin app: queue claiming ("Being handled by ..."), waiting-time colours, Escalate, strikes ladder (warning, 3 d, 7 d, ban), Appeals screen, break-glass (Owner lends one permission for 1 hour), recent sign-ins per person, Unusual activity screen, push when your approval request is decided / a report is escalated / activity is unusual.
- User app: banned and suspended screens get "Appeal this decision" (one appeal per decision, decided by a different admin).
- Migration `044_admin_stage4_safety.sql` (needs your OK, NOT applied) and `send-push` must be redeployed. Version 2.10.0 (versionCode 21000). Not compiled or run in the sandbox; the new rules tests (QueueRules, PushRules, SavedReplyRules) pass on plain JUnit.

# v2.9.15 - BOFO Admin app: saved replies for ID rejections
- Rejecting an ID request now has 8 friendly built-in replies (blurry photo, name differs, expired, selfie mismatch, unreadable number, missing back, wrong ID type, cropped). Tap one to fill the box, edit it, or write your own and "Save this as my reply" (up to 10, kept on your phone only; long-press to delete). Any reason of 5+ characters can be sent.
- No migration. Version 2.9.15 (versionCode 20915). Not compiled or run in the sandbox that made this change (the new rules test was run on plain Java: 7 of 7 pass).

