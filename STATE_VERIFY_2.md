# STATE_VERIFY_2: database verification (038..045). No live DB touched.
**Labels:** RAN = executed here, READ = code read only, NOT CHECKED = needs your DB.

## Checked
| Check | Label | Result |
|-------|-------|--------|
| Parse 038..045 (and all 001..044 files) with the real Postgres parser (pglast 8.7) | RAN | 44/44 parse |
| Idempotency: CREATE FUNCTION w/o OR REPLACE, table/index/column w/o IF NOT EXISTS, trigger/policy w/o prior DROP, INSERT w/o ON CONFLICT | RAN | 0 defects |
| `CREATE OR REPLACE` signature drift (param names, defaults, return type vs earlier migration) | RAN | 0 defects |
| Use-before-create across files (functions, tables) | RAN | 0; order 038..045 is valid |
| SECURITY DEFINER without `set search_path` | RAN | 0 |
| RLS enabled on all 11 new tables; direct grants revoked | RAN | 0 defects |
| `revoke ... from public, anon` on every callable SECURITY DEFINER function | RAN | 1 Low: `admin_session_ok` (below) |
| No `grant ... to anon/public` in 038..045 | RAN | 0 |
| 045 guard (RAISE before first destructive statement; checks members, owner, min_app_version); 044 provides what 045 expects | RAN+READ | OK |
| App to SQL: 67 RPC names (+ argument names, inline and `Ui.json`) and 21 tables used by `select()` exist | RAN | 0 mismatches |
| App no longer calls the 3 functions 045 drops | RAN | OK |
| `send-push`: secret required (empty secret rejected), constant-time compare; logs only kind and counts | READ | OK |
| `bofo-chat`: user JWT checked with `getUser`, service role only inside the function | READ | OK |

## Findings
| # | Sev | Finding | Status |
|---|-----|---------|--------|
| 1 | Low | `admin_session_ok()` (039) can be run by the logged-out role: it only returns true/false (MFA flag + app version). | Not fixed; fold into Stage 3 N-3 (helper grants) |
| 2 | Low | `014_ai_chat_log_REMOVE.sql` sits in `supabase/migrations/`; `supabase db push` would run it and delete all AI chats. SQL editor use is safe. | Not fixed; suggest moving it to `supabase/one_off/` (your call) |
| 3 | Low | `bofo-chat` error logs include the first 120-200 characters of model output, which can echo a user's words. | Backlog for Stage 4 log check |
| 4 | Info | 045 header says v2.11.0, 044 says v2.12.0 (docs only). Decide the real minimum app version before `set_min_app_version`. | Needs you |

No real defect needing a fix, so there is **no 046** and no version bump (SQL/docs only).

## NOT CHECKED
Nothing was executed on Postgres (no server in the sandbox; pglast only parses). `VERIFY_AFTER.sql` and `ROLE_TESTS.sql` are written but never run. Policy logic was listed, not proven. 040's "byte-for-byte from earlier migrations" claim not diffed. Live schema may differ from the migrations.

## Files changed
New: `supabase/RUN_ORDER.md`, `supabase/VERIFY_AFTER.sql`, `supabase/ROLE_TESTS.sql`, `tools/verify/check_sql.py`, this file. Changed: `tools/verify/run_all.sh`, `VERIFY_PROGRESS.md`.

## First step of Stage 3
Read `SECURITY_AUDIT_FINAL.md` section 7, then write migration 046 for N-1 (appeals stale check) and N-3 (helper grants, including `admin_session_ok`).
