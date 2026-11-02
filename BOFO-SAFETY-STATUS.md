# Safety series status (owner name, reports, suspend/ban)
- [x] Step 1 (v2.1.7): owner name on place page. Migration 021 applied live.
- [x] Step 2 (v2.1.8): code done (Report button, admin Reports review, suspend with duration, suspended screen). **Migration 022 written, NOT applied live: needs the user's OK.**
- [ ] Step 3 (v2.1.9): ban + hashed ID block at registration/verification, Banned Users list, Unban, audit log.
Rules: CLAUDE_ANDROID.md (version +1 each step, changes-only zip, no live DB change without OK, never read public.users / id_verifications rows).
