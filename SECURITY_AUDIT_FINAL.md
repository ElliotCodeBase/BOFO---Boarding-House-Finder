# BOFO security audit, FINAL (Stage 5, v2.11.0)

**Method:** static review of the repo zip (Stage 1 findings re-checked one by one, scripts over all 43 tables and 134 functions in `supabase/migrations`, `044` read line by line, both manifests, both Gradle files). **Nothing was run:** no SQL was executed, nothing was compiled, no live project or git history was seen. Statements about the live database are "unknown" unless said otherwise.

## 1. Summary
- RLS is enabled on all 43 tables (including the 4 new tables of 044: `admin_item_marks`, `user_strikes`, `appeals`, `admin_break_glass`, which also have all grants revoked).
- All 124 SECURITY DEFINER functions set `search_path` (`public`, `public, auth` or `public, extensions`).
- Of those 124, 34 have no `auth.uid()` / permission check written in the body. Sorted by hand: **15** trigger functions (cannot be called as RPC), **8** revoked from signed-in users (`activate_boost`, `ai_chat_log_purge`, `bootstrap_owner`, `id_hash`, `push_notify`, `push_stale_check`, `reset_admin_mfa`, `set_min_admin_client_version`), **7** owner-group RPCs that check `is_bh_owner()` through a helper (I read the start of each; `edit_announcement` only partly), and **4** helpers that any signed-in user can call (see N-3).
- Found and **fixed in this release:** S-08 (screenshot of the owner ID screen), S-11 (broken `.gitignore`), S-12 part (workflow token permissions), `038` was missing from `supabase/migrations/` (copied).
- **New findings from 039-044:** 1 Medium (N-1), 3 Low (N-2 to N-4). No Critical or High found. Fixes for N-1 and N-3 are given below as SQL **to review first**; they are not applied.
- Still needs YOU: key rotation and history check (S-01), live dev accounts (S-04), `038`/`039`-`044` applied live (unknown), MFA flag (S-03).

## 2. Stage 1 findings, one by one
| ID | Status now | Note |
|---|---|---|
| S-01 firebase key in history | **OPEN, cannot verify** | The zip has no `.git`. Do the checks in `BOFO_STAGE_1/ROTATION_AND_HISTORY_PURGE.md`. Rotate anyway. |
| S-02 owner can undo a take-down | **OPEN until `038` is applied** | The fix existed only in `BOFO_STAGE_1/supabase/migrations/`, not in `supabase/migrations/`. Copied in this release. Apply `038` (safe to run twice). |
| S-03 admin MFA flag off | OPEN (live setting) | `admin_session_ok()` enforces it only when `require_admin_mfa` is on. Turn it on (RELEASE_CHECKLIST 2). |
| S-04 dev accounts / password login | Mitigated, live unknown | Release builds compile empty `DEV_*` fields (`app/build.gradle`). Delete dev accounts live (015 bottom). `grant_type=password` code still exists in the main app. |
| S-05 ID-ban oracle | **FIXED** (040: 10 per hour) | |
| S-06 no per-user caps | **FIXED** (039: reports 10/h, chat 300/h, storage 60 ID + 400 photo files) | Per-user rate keys use `auth.uid()`. |
| S-07 Google token without nonce | **OPEN (main app)** | The Admin app uses a nonce. BOFO Finder does not. Medium. |
| S-08 ID screen can be screenshotted | **FIXED in 2.11.0** | `VerificationFragment` sets `FLAG_SECURE`. Admin ID/2-step screens already had it. |
| S-09 setup key on clipboard | **FIXED** | The main-app MFA sheet is gone; the Admin app does not copy the key. (See N-4 for the invite code.) |
| S-10 `.temp/linked-project.json` | Fixed in tree | The file is not in the zip; `supabase/.temp/` is now ignored. If it was ever tracked: `git rm -r --cached supabase/.temp`. |
| S-11 `.gitignore` UTF-16 junk | **FIXED in 2.11.0** | Rewritten tail (UTF-8), added `*.pem`, `.env*`, service-account patterns, `bofo-android/.gitignore` too. |
| S-12 workflow | Part fixed | `permissions: contents: read` added now (the Stage 1 edit was not in the tree). Actions are pinned by tag, not SHA: OPEN (look up SHAs on your PC / use Dependabot). |
| S-13 CORS `*` | **FIXED** | Functions now only send `Access-Control-Allow-Headers`. |
| S-14 delete-account without re-auth | OPEN (Low) | |
| S-15 webhook timestamp | **FIXED** (`Number.isFinite`) | |
| S-16 `send-push` can target any user | Accepted | Needs the webhook secret. |
| S-17 ai_chat_log | **FIXED server-side** (040: owner-only `ai_log_view`) | The retention cron (014, pg_cron) is live-unknown. |
| S-18 CI config path | OPEN (Info) | `app/build.gradle` reads `../src/main/resources/.../config.properties` and `local.properties`, not `bofo-android/config.properties`. Check that a CI APK has a real `SUPABASE_URL`. |
| S-19 internal docs public | OPEN (Info) | Move to a private repo if you want. |
| S-20 dependencies | OPEN | `okhttp 4.12.0`, `glide 4.16.0`, `firebase-bom 33.7.0`, `maplibre 13.0.0`. No CVE scan was possible offline: enable Dependabot. Stage 5 removed no dependency and added none. |

## 3. Secrets
- Tree scan (JWTs, service keys, private keys, `sk-ant-`, `ghp_`, `AKIA`, `AIza`): **one hit**, `bofo-android/config.properties`. Decoded: the JWT's role is `anon` (the public key, safe to ship by design). No `service_role` key, keystore, `google-services.json`, `.env` or service-account file is in the tree.
- **History: not checked** (no `.git` in the zip). Run the commands in `BOFO_STAGE_1/ROTATION_AND_HISTORY_PURGE.md`.

## 4. Storage and components
- Buckets: `id-verification` **private** (read policy `idv_read`, 040: pending items only, `id_view_raw` / `id_review`). `bh-photos` and `avatars` are **public on purpose** (listing photos and profile pictures). 5 MB / 2 MB limits and PNG/JPEG only.
- Manifests: BOFO Finder: 1 exported activity (launcher / deep link), service, provider not exported; `allowBackup=false`, cleartext off. Admin app: same shape; FileProvider not exported.
- Admin push kinds no longer exist in BOFO Finder (`PushRules` drops them), so a phone signed in to both apps cannot show an admin notification in the wrong app.

## 5. Line-by-line review of 044
Read in full. The structure is sound: every RPC calls `require_permission` (or checks `auth.uid()` for the user's own appeal), all use a fixed search_path, rate limits exist, tables have no direct grants, trigger functions are revoked from users.

**N-1 (Medium): an old appeal can lift a newer decision.** `admin_appeal_decide` never checks that the appeal still matches the user's CURRENT ban or suspension. Example: user appeals a 3-day suspension (T1); before anyone decides, a 7-day suspension is issued (T2); accepting the old appeal runs `suspended_until = null` and removes the new suspension. Same for a ban lifted and re-issued. Proposed fix (inside `admin_appeal_decide`, after loading `a`, before the `if lift` block):
```sql
  if lift then
    if (a.kind = 'ban' and not exists (select 1 from public.users where id = a.user_id and banned_at is not distinct from a.ref_at))
    or (a.kind = 'suspension' and not exists (select 1 from public.users where id = a.user_id and suspended_until is not distinct from a.ref_at)) then
      raise exception 'This appeal is about an older decision. Reject it; the person can appeal the current one.';
    end if;
  end if;
```
**N-2 (Low, design): ban appeals can deadlock.** Lifting a ban needs `unban` (Owner only). If the Owner made the ban, nobody else can decide ("different admin") unless the Owner lends `unban` by break-glass. This fails safe (no ban lifted wrongly) but the appeal waits. Document it (done in ADMIN_GUIDE). Also `original_decider` can be NULL (deleted account / unknown): then the "different admin" rule is skipped.

**N-3 (Low): helper functions callable with any user id.** `is_verified_owner(uuid)`, `is_owner_or_admin(uuid)` and `can_list_bh(uuid)` are executable by every signed-in user (015 only removed `anon`). They answer "is this uuid an owner/admin?". `recompute_bh(uuid)` still has the default grant (callable even by `anon`); it only recomputes numbers. Proposed fix, to run only after checking that policies using them still evaluate (RLS calls run as the caller, so keep `authenticated` for the ones used inside policies; the cleanest fix is to make them ignore the argument: compare to `auth.uid()` only):
```sql
revoke all on function public.recompute_bh(uuid) from public, anon, authenticated;
-- policies call the three role helpers with auth.uid(); keep authenticated but make the helpers answer only for the caller:
-- create or replace function public.is_owner_or_admin(uid uuid) ... where id = uid and uid = auth.uid() ...   (same for is_verified_owner, can_list_bh)
```
Not applied: it touches policies that the app depends on.

**N-4 (Low): minor.**
- `admin_issue_strike` on a report that has no `reported_user_id` (a place-only report) fails on a NOT NULL error instead of a clear message. Fails safe (whole call rolls back).
- A break-glass grant still works for a key listed in the member's `extra_deny` (a lend beats a deny). Intended, but worth knowing.
- The Team screen's "Copy" puts an invite token on the clipboard without the sensitive flag (tokens are single use and expire).
- `search_path` is `public` without `pg_temp` (common hardening, not a hole here because all calls are schema-qualified or in `public`).
- Break-glass can only be lent by someone with `members_manage`, which is Owner-only: so an Admin cannot lend Owner powers to a friend. Checked.

Checked and fine in 044: claim/escalate/strike/appeal/break-glass all lock rows (`for update`) where races matter; a user can submit an appeal only for their own current ban/suspension and only 5 per day; `has_permission` replacement only adds the break-glass branch and only for 6 keys (`ban_permanent, unban, listing_takedown, suspend_long, payments_view, csv_export`), never for members, MFA, config, deletion or raw IDs; the unusual-activity trigger cannot block an admin action (exception swallowed) and its action names match those written by `admin_log` in 040.

## 6. Risk from Stage 5 itself
- BOFO Finder now treats an admin account as a student; DB rules still apply (an admin cannot chat as a student). No admin RPC is reachable from the user app UI. Old user-app versions still have admin screens until `min_app_version` blocks them: **set it before the release goes out**.
- `045_drop_legacy_admin.sql` removes the "role=admin means full access" fallback. It refuses to run unless every `role='admin'` account is in the team list, an owner exists and `min_app_version` is set. If the owner's own row is missing it would lock everyone out of admin: that is why the stop checks exist.
- Dead code check: no reference to a deleted class or removed method is left (grepped), but the project was not compiled.

## 7. Still open (short list), updated in verification Stage 3 (2026-11-07)
| # | Item | Status | Detail |
|---|------|--------|--------|
| 1 | S-01 history check, key rotation | OPEN (you) | Commands and per-hit actions: `docs/HISTORY_CHECK.md` |
| 2 | Apply 038-044 live | OPEN (you) | Order: `supabase/RUN_ORDER.md`; proof: `VERIFY_AFTER.sql` |
| 3 | S-03 MFA flag | OPEN (live setting) | Order below; check with `SECURITY_CHECKS.sql` M1, M2 |
| 4 | S-04 live dev accounts | OPEN (live) | Order below; check D1, D2 |
| 5 | S-07 nonce in main app | OPEN, Stage 4 | |
| 6 | N-1 appeal vs current decision | CLOSED in code (044 body has the check; READ) | Prove live: `SECURITY_CHECKS.sql` N-1 |
| 7 | N-3 helper grants | CLOSED in code by `046_stage3_helper_grants.sql` (not applied live) | `is_verified_owner` left as is on purpose (used for another user's id) |
| 8 | S-12 SHA pinning, 10 CVE scan, 11 S-18 CI | OPEN, Stage 6 | |
| 9 | S-14 re-auth for account deletion | OPEN, Stage 4 | |

**Order for S-03 / S-04 (do not skip steps, or you lock yourself out):**
1. Back up. Run `SECURITY_CHECKS.sql` D1, D2, M2 (read-only).
2. Real owner: enrol the authenticator in the admin app, sign out and in with the code. Re-run M2: owner `has_mfa = true`.
3. If a dev account is staff (D2 has rows) remove it from the team in the admin app first.
4. S-04: delete the dev accounts in Supabase > Authentication > Users (their data goes with them), or run the line commented at the bottom of `015_security_hardening.sql`. Re-run D1: 0 rows.
5. S-03: `select public.set_admin_mfa_required(true);` (it refuses unless someone has a verified authenticator). Test the admin app. Break-glass off: `select public.set_admin_mfa_required(false);`.
