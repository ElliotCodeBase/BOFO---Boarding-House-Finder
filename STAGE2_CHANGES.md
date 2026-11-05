# BOFO Stage 2 - changelog, access guide, fix guide (v2.9.6)

## 1. What I did
| # | Change | Where |
|---|---|---|
| 1 | New role system on the server: Owner, Admin, Moderator, Support (+ "legacy admin" for the old app) | `039_admin_roles.sql` |
| 2 | 27 permission flags; 15 are owner-only (the 12 items of your list) and can never be granted to a sub-admin | `039` |
| 3 | Per-person toggles (extra grant / extra deny) on top of the role | `admin_members.extra_grant/extra_deny`, `admin_member_update` |
| 4 | Invites: one-time token (stored as hash), 48 h, bound to the invited Google email, optional expiry of the membership | `admin_invite_create/accept/revoke` |
| 5 | Owner rules: owners cannot be changed, demoted, suspended, removed; max 2 owners; owner created only by `bootstrap_owner()` | trigger `guard_admin_members` |
| 6 | Append-only audit log (no update/delete/truncate; also blocks the SQL editor); old `audit_logs` rows are copied in automatically | `admin_audit_log`, trigger `copy_audit_log` |
| 7 | Approvals: moderator requests (ban / long suspend / take-down), admin or owner decides; approving runs the action as the approver, so an Admin cannot approve a ban | `request_admin_approval`, `decide_admin_approval` |
| 8 | Every existing admin function now needs its own permission (13 functions) and conflict check (nobody decides a report about themselves or someone they invited); staff cannot be suspended or banned | `040_admin_permission_gates.sql` |
| 9 | Sensitive reads/writes moved to permissions: payments, AI chat log, banned list, ID photos (reviewers see only PENDING items), colleges/amenities/listing/review deletes | `040` policies |
| 10 | New owner-only wrappers: min app version, admin min version, reset another admin's MFA | `040` |
| 11 | "Log out all devices", suspend/expire a member: enforced inside every permission check (JWT issue time) | `revoked_before` |
| 12 | Security fixes: S-05 (ID-check rate limit), S-06 (report/chat caps, storage count caps), S-13 (CORS), S-15 (webhook timestamp), S-17 (AI chat log owner-only + 90-day job) | 039, 040, edge functions |
| 13 | SQL role tests and Java `PermissionRules` + 10 unit tests | `supabase/tests`, `bofo-android/.../util` |

## 2. How to access / use
Run in the Supabase SQL editor, in this order: `039`, then your owner, then `040`, then the tests.
```sql
select public.bootstrap_owner('you@gmail.com');      -- once; sign in with Google in the app first
select public.my_admin_access();                     -- as a signed-in user (app): role + permissions
```
From the (Stage 3) admin app, as Owner: `admin_invite_create(email, role, extra_grant, days)` returns the token ONCE; send it to the person; they sign in with that Google email and call `admin_invite_accept(token)`.
Owner screens in later stages: members list `admin_members_list()`, `admin_member_update/suspend/remove/logout_all`, `admin_activity_all()`, `admin_approvals_list()`.
Look at the log in the SQL editor: `select * from public.admin_audit_log order by id desc limit 50;`

## 3. Fix guide (simple)
| Problem | Do this |
|---|---|
| 039 or 040 stops with an error | Fix the line named in the error and run the whole file again (both files are safe to run twice). Send me the error text. |
| Error about `storage.objects` policy (restrictive) | Comment out the two `create policy idv_cap` / `bhp_cap` blocks in 039 and run again. Only the upload-count caps are lost. |
| I locked myself out (an admin says "admins only") | SQL editor: `begin; select set_config('bofo.break_glass','on',true); delete from public.admin_members where lower(email)='you@gmail.com'; commit;` then you are a legacy admin again (needs `users.role='admin'`). Run `bootstrap_owner` again after. |
| An Admin lost a feature (set AI, refund, unban, take-down, payments) | By design: owner-only. Do it as Owner. |
| "Too many requests" (code PT429) for a real user | `delete from public.rate_limits where user_id = '<uuid>';` To change a limit edit the numbers in `trg_rate_reports`, `trg_rate_chat`, or the `hit_rate_limit('id_check', 10, 3600)` line. |
| Invite says "not valid for this account" | It is expired (48 h), already used, revoked, or the Google email differs. Create a new invite. |
| A moderator's action says "admins only" | They lack the permission: check `select public.my_admin_access();` as them, or change the role with `admin_member_update`. |
| Test script cannot insert into `auth.users` | Run it with the Supabase CLI `psql` connection (owner role), or skip it and test by hand with a test account per role. |
| Test shows FAIL rows | Send me the FAIL rows (who / what / expected / got). |
| Edge function error after deploy | Re-deploy; `paymongo-webhook` needs `--no-verify-jwt`. Restore the old file from git (`git checkout -- supabase/functions`) to roll back. |
| pg_cron notice | Enable pg_cron in Database > Extensions and run the last block of 040 again. |
| Full rollback | Roll back 040 (restore the function bodies from the `-- from` migrations and the old policies), then 039: steps are in the headers of both files. |

## 4. NOT done in Stage 2
SQL and Gradle were not run here. Pushes to sub-admins, the separate admin-app version gate, the `hidden` listing status, and the moderator-facing UI come in Stages 3-4 (see STATE.md "Known limits").
