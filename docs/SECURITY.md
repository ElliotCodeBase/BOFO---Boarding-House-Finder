# BOFO Finder - security (v2.0.119)

Static review of `supabase/migrations/001-016` and the Android app's use of them. Reviewed from the repo, not from the live project: check the live state in the Supabase dashboard (Advisors > Security) too.

## 1. AI access to user data (the reason for 016)
The Supabase connector, the dashboard SQL editor and the `postgres` password all run as the database OWNER. That role ignores row-level security and can read `auth.users` (emails, Google profile), messages, ID scans and selfies. A rule in a prompt is not a lock.

- `016_ai_dev_role.sql` creates `bofo_ai_dev`: reads the public catalog, edits only DEMO listings owned by an `@example.com` test account, adds reviews only as a test account. It has no access to users, auth, chats, verifications, payments, AI chat log or storage, and cannot change schema, policies or roles. Tested on a local Postgres 16 with stubbed Supabase schemas (see the test list at the bottom).
- To use it (you, once): `alter role bofo_ai_dev with login password '<long random>';` in the SQL editor, then give the AI THAT role's connection string. Never give an AI the postgres password, the service_role key or an owner-level connector token.
- If you keep the owner-level connector: use Supabase's read-only mode and project scoping if your connector offers it, disconnect it when not needed, and revoke the token in Supabase (Account > Access Tokens) after each AI session.
- Demo data must only ever use test accounts (`@example.com`). Never put reviews, chats, favorites or listings under a real person's account.

## 2. Findings and what was done
| # | Finding | Risk | Status |
|---|---|---|---|
| 1 | AI/connector runs as database owner | High | 016 role + rules in `CLAUDE.md` section 7 |
| 2 | `bump_view()` could be called in a loop to inflate "Hot" | Medium | Fixed in 015 (1 view per person per place per hour) |
| 3 | `is_admin()` / `is_verified_owner()` callable by signed-out visitors (leaks "is this id an owner/admin") | Low | Fixed in 015 |
| 4 | App RPCs (`delete_my_account`, `switch_role`, ...) executable by anon and any DB role (they check `auth.uid()`, so harmless today) | Low | Fixed in 015 |
| 5 | Dev Admin / Dev Student accounts exist on the live project; Dev Admin is a full admin | High before release | YOU: delete or set strong passwords (commented SQL at the bottom of 015) |
| 6 | Live project is missing 013 (unverified owners can list) | Info | Run it when you want it; 015 handles both cases |
| 7 | ID / selfie purge (30 days after a decision) is commented out in 001 | Privacy | YOU: decide the period, match it to the Privacy Policy, then enable the cron job |
| 8 | `reviews_read` is `using (true)`: reviews of unlisted places and reviewer ids are readable by any signed-in user | Low | Open. Optional: restrict to active places |
| 9 | Any student can review any place (no "I chatted with this owner" check) | Low | Open. Optional |
| 11 | Admin accounts had password / Google sign-in only | Medium | Fixed in 017 + in-app setup (section 5). Switch is OFF until you turn it on |
| 10 | `ai_chat_log` stores user email + questions (admin-only, off by default, 90-day purge) | Privacy | OK. Keep the switch off until the app version that tells users is out |

Checked and fine: RLS is on for every table; users cannot change their own role / verification (`protect_users`); chats are created only through `get_or_create_conversation`; ID bucket is private with insert/read only for the owner folder and admins; listing and avatar uploads are limited to the user's own folder, 5 MB and 2 MB, PNG/JPEG only; account deletion removes the user's files.

## 5. Admin accounts: the order to do it in
1. **Real admin account.** Sign in to the app with your real Google account once (so it has a profile), then in the SQL editor:
   `update public.users set role='admin', is_verified=true, verification_status='verified' where id=(select id from auth.users where email='YOUR-GOOGLE-EMAIL');`
   Nobody can promote themselves: a trigger (`protect_users`) blocks role changes from the app. Only the SQL editor can do it.
2. **Two-step verification (migration 017, already applied live, switch OFF).** Sign in as that admin: Settings > Two-step verification > Show my setup key > add it to an authenticator app (or tap Open authenticator) > type the 6-digit code. Then sign out and in once: the code sheet should appear, and after you type the code the admin tools work.
3. **Turn the requirement on** (SQL editor): `select public.set_admin_mfa_required(true);` It refuses unless an admin has a verified authenticator. From then on, `is_admin()` is true only in a session that passed the code, so every admin policy and RPC is covered.
4. **Break-glass:** `select public.set_admin_mfa_required(false);` from the SQL editor undoes it at once (the SQL editor never needs a code).
4b. **Lock out old app versions (migration 018, applied live, OFF):** install v2.0.121 or newer on your phone first, then `select public.set_min_admin_app_version('2.0.121');`. Older apps (no version header, or a lower number) then get no admin data. Undo: `select public.set_min_admin_app_version('');`. After switching it on, open an ID scan once to check the Storage side works.
5. **Before release:** delete the dev accounts (SQL at the bottom of 015). The dev buttons were removed from the login in v2.0.121, and the Dev Admin account is deleted.
6. **Later (not built):** move ID-scan review and takedowns to a separate admin web page behind its own login, so the student and owner app has no admin screens at all. Until then the admin tabs only appear for accounts whose role is admin on the server.

## 3. Things only you can do (dashboard)
1. Authentication > Providers: keep only Google (and email if you use it); turn on "Confirm email"; set a minimum password length of 10 or more.
2. Authentication > URL configuration: only your real redirect URLs (`bofo://auth-callback`), no wildcards.
3. Project Settings > API: never put the `service_role` key in the app, the repo or `KEYS.local.md` that gets synced anywhere; rotate it if an AI or a log ever saw it.
4. Database > Backups (paid plans) or a weekly manual export before any migration.
5. Run Advisors > Security and Performance after each migration and fix what it flags.

## 4. Test list for 015 + 016 (local Postgres 16)
Migration 017 (local Postgres 16): switch off = admin works with or without a code; on = admin only with aal2, student with aal2 is still not admin, a token without the aal claim is refused; the switch refuses to turn on with no verified authenticator (also with only an unverified one); the app roles cannot call it; off again from the SQL editor works and is written to `audit_logs`. The Android screens were NOT built or run (no Android SDK here).

Migrations 015 + 016: passed: bump_view twice = 1 view; anon cannot call `is_admin`, `is_verified_owner`, `delete_my_account`, `mark_read`; signed-in users still can; `bofo_ai_dev` gets "permission denied" on `users`, `auth.users`, `messages`, `conversations`, `owner_verifications`, `user_agreements`, `favorites`, `audit_logs`, `ai_chat_log`, `payments`, `storage.objects`, `reviews.student_id`; it can insert a listing for a test owner but not for a real owner; updates and deletes of a real owner's listing change 0 rows; it can add a review only as a test student; `create table`, `alter role ... superuser`, `create role` and `drop policy` are refused; both migrations re-run cleanly. NOT tested against the live Supabase project.
