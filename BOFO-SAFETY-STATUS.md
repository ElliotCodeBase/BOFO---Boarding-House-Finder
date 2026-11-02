# Safety series status (owner name, reports, suspend, ban)
Last updated: v2.1.9. Repo: `C:\Users\user\bofo-finder`. Supabase project: BOFO (`zfxldzcoirqolflkkhrh`).

| Step | Version | What | Code | Database |
|---|---|---|---|---|
| 1 | 2.1.7 | Owner's name on the place page (`bh_owner_v`) | done | `021` applied live |
| 2 | 2.1.8 | Report button, admin Reports review, suspend with duration, suspended screen | done | `022` applied live 2026-11-02 (user's OK), verified |
| 3 | 2.1.9 | Ban by hashed ID, Banned users list, Unban (owners only) | done (zip `BOFO-v2.1.9-changes.zip`) | `023` written, **NOT applied live** (needs the user's OK) |

## Next action
1. User says "apply 023" -> apply `supabase/migrations/023_ban.sql` with the Supabase connector, verify (schema-only), update this file. Do this BEFORE installing 2.1.9.
2. Test with `BOFO-SAFETY-TEST-CHECKLIST.md` on the phone (test accounts `@example.com` only).

## Key facts for any AI
- Active app = `bofo-android/`. Version line 2.1.N, bump +1 per change, changes-only zip (`CLAUDE_ANDROID.md`).
- The app stores NO ID number. Step 3 sends it only to the server, which stores a keyed hash (`owner_verifications.id_hash`, `banned_identities.id_hash`); the pepper is in `app_secrets` (no policies, no grants). The app never hashes.
- `blocks_reports` = chat blocks between two users; `reports` = reports to admins. Do not merge them.
- Suspension and ban are enforced by `is_suspended()` in RESTRICTIVE write policies (022 + `susp_block_ov_insert` in 023) and by the app at start (`my_suspension()`).
- Approving a verification needs `admin_approve_verification(vid, id_number)`; `review_verification` only rejects now.
- Never read user rows / ID images; test data only `@example.com`.
- Not built or run by any AI (no Android SDK). First real test is on the user's phone.
