# Migration run order, 038..045 (Supabase SQL editor, one file at a time)

**Before 042: take a backup / restore point** (Dashboard > Database > Backups). 042..045 replace function bodies and drop functions.
Every file 038..044 is safe to run twice (RAN: `tools/verify/check_sql.py` found no non-idempotent statement). 045 stops by itself when its guard fails.

| # | File | One line | Needs first | Skip if already run? |
|---|------|----------|-------------|----------------------|
| 1 | 038_stage1_security_fixes | Owner can no longer undo an admin take-down (`protect_bh` trigger) | 001 | Yes, safe to re-run |
| 2 | 039_admin_roles | Roles, team members, invites, audit log, approvals, rate limits, `has_permission()` | 017, 018, 038 | Yes |
| 3 | 040_admin_permission_gates | Every admin function and sensitive policy needs its own permission | 039 (and old 001..037) | Yes |
| 4 | 041_admin_client_version_gate | Admin app gets its own minimum version (off until you set a value) | 039, 040 | Yes |
| 5 | **backup now** | | | |
| 6 | 042_admin_push | Token `app` column, 3-argument `register_push_token`, admin push helpers | 029, 033, 039 | Yes. Then redeploy `send-push` |
| 7 | 043_refund_hardening | Refund only a PAID payment, reason required | 040 | Yes |
| 8 | 044_admin_stage4_safety | Claiming, escalation, strikes, appeals, break-glass, sessions, unusual-activity push | 039, 040, 042, 043 | Yes |
| 9 | 045_drop_legacy_admin | Removes the "users.role = admin has full access" fallback and 3 old functions | 039..044 | **Run last, separately, only after** old user apps (2.10.x and older) are blocked via `set_min_app_version` |
| 10 | 046_stage3_helper_grants | N-3: helpers answer only for the caller; closes `recompute_bh` / `admin_session_ok` to anon | 039 | Yes. Run after 044; independent of 045 |

After 1..4: run `VERIFY_AFTER.sql` part A (A1..A11), then `ROLE_TESTS.sql`. After 6..8 run part A again. After 9 run part B.
Do NOT run `014_ai_chat_log_REMOVE.sql` (deletes every stored AI chat). Numbers 032 and 035 do not exist: harmless.
Also available: `supabase/tests/stage2_role_tests.sql` (fake accounts, rolled back).
