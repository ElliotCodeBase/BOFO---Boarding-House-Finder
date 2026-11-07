# STATE_VERIFY_3: open security items, server side. No live DB touched. SQL never run on Postgres.
Labels: RAN = executed here, READ = read only, NOT CHECKED = needs your DB/PC.

| Item | Label | Result |
|------|-------|--------|
| N-1 appeal vs current decision | READ | Already fixed in `044` (`admin_appeal_decide` checks `ref_at` against the current ban/suspension). Live proof: `SECURITY_CHECKS.sql` N-1 |
| N-3 helper grants | RAN (parse+lint) / NOT CHECKED (live) | `046_stage3_helper_grants.sql`: `is_owner_or_admin`, `can_list_bh` answer only for `auth.uid()`; `recompute_bh` closed (only definer triggers call it); `admin_session_ok` closed to anon. Not changed: `is_verified_owner` (called for another user's id in `get_or_create_conversation`) |
| N-3 safety reasoning | READ | The two helpers are used only in policies as `f(auth.uid())`; no app or Edge Function calls them; `recompute_bh` has no caller except definer triggers |
| Re-check RLS across all 001..046 | RAN | Every created table has RLS on (0 without). 17 have RLS and no policy (deny-all by design: admin_*, user_strikes, appeals, device_tokens, push_settings, app_secrets, rate_limits...). Live check: `SECURITY_CHECKS.sql` R1..R5 |
| S-01 history | NOT CHECKED | `docs/HISTORY_CHECK.md`: commands + rotate-then-purge table per kind of hit |
| S-03 MFA, S-04 dev accounts | NOT CHECKED | Read-only SQL (M1, M2, D1, D2) and the safe order in `SECURITY_AUDIT_FINAL.md` section 7 |
| Lint of all migrations incl. 046 | RAN | 0 failures (the Stage 2 `admin_session_ok` finding is now closed by 046) |

## Risks to know
- If a policy somewhere calls `is_owner_or_admin`/`can_list_bh` with a uid other than `auth.uid()`, it would now be false. I found none (grep of all migrations); `SECURITY_CHECKS.sql` cannot prove it, so after 046 test: owner creates and edits a listing, admin publishes a listing.
- Rollback for 046 is in its header.

## Not done / backlog
`014_ai_chat_log_REMOVE.sql` still inside `supabase/migrations/` (Stage 2 finding 2). N-4 minor items unchanged.

## Files
New: `supabase/migrations/046_stage3_helper_grants.sql`, `supabase/SECURITY_CHECKS.sql`, `docs/HISTORY_CHECK.md`, this file. Changed: `SECURITY_AUDIT_FINAL.md` (section 7), `supabase/RUN_ORDER.md`, `VERIFY_PROGRESS.md`. SQL/docs only: no version bump.

## First step of Stage 4
S-07: add the nonce (Google gets SHA-256, Supabase gets the raw value) to the user app's Google sign-in, same pattern as the Admin app.
