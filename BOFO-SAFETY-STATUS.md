# Safety series status (owner name, reports, suspend/ban)
- [x] Step 1 (v2.1.7): owner name on place page. Needs migration 021 live.
- [ ] Step 2 (v2.1.8): Report button, `reports` table, admin Reports review (dismiss / suspend with duration), suspension enforced at sign-in. Existing ReportsFragment in admin: check what it already does first.
- [ ] Step 3 (v2.1.9): ban + hashed ID block at registration/verification, Banned Users list, Unban, audit log.
Rules: CLAUDE_ANDROID.md (version +1 each step, changes-only zip, no live DB change without OK, never read public.users / id_verifications rows).
