# v2.2.1 - Lost / deleted authenticator code: reset + safeguards

## What happened
The admin's entry in Google Authenticator was deleted. Admin two-step verification (migration 017) asks for a 6-digit code, and the server only lets a session that already passed the code remove the factor, so the app cannot turn it off by itself. The fix has to be done from the Supabase SQL editor (it never needs a code).

## What changed
- `supabase/migrations/026_admin_mfa_reset.sql` (**run it once in the SQL editor**): new function `reset_admin_mfa(email)`. It deletes the account's authenticator factors, switches `require_admin_mfa` OFF if no admin has a working authenticator left, and logs `admin_mfa_reset` in `audit_logs`. Not callable from the app (no grant to anon / authenticated).
- `res/values/strings_admin_mfa.xml`: the setup screen now says to SAVE THE KEY first (password manager or paper); the "enter code" screen says what to do when the code is lost.
- `docs/SECURITY.md`: new step 4d in section 5.
- `app/build.gradle`: 2.2.1 (versionCode 20201).

## How to disable two-step verification now (do this)
Supabase dashboard > project `zfxldzcoirqolflkkhrh` > SQL Editor, run in this order:
1. `select public.set_admin_mfa_required(false);`   (works with the existing 017, nothing new needed)
2. Run the whole file `026_admin_mfa_reset.sql` once.
3. `select public.reset_admin_mfa('YOUR-ADMIN-EMAIL');`
4. Sign out and in again. The code sheet no longer appears and admin tools work with the normal sign-in.

If you do not want to run 026 yet, step 1 alone already unlocks the admin tools (the app may still show the code sheet at sign-in; close it).

## Set it up again later, without getting stuck
1. Settings > Two-step verification > Show my setup key.
2. **Copy the key into a password manager or write it on paper** before typing the code. With the key you can re-add the entry to any authenticator at any time.
3. Add it to the authenticator, type the code.
4. Turn the requirement on only after that: `select public.set_admin_mfa_required(true);`

## Ways to avoid accidental deletion
- Keep the setup key (above). It is the real backup; the app entry is only a copy.
- Add the key to TWO authenticators (e.g. Google Authenticator and Aegis / Microsoft Authenticator) on the same or another phone.
- Google Authenticator: turn on "Sync with Google Account" so entries survive a deleted entry on a new phone and can be restored there. Use "Transfer accounts > Export" once as an offline backup (keep the QR private).
- Have a second admin account with its own authenticator, so one lost code never locks everyone out.
- Do not run `set_admin_mfa_required(true)` before a second way back exists (key saved).

## If a problem occurs
| Problem | Fix |
|---|---|
| `No account with the email ...` | Check the email in `select email from auth.users;` (use the Google email you sign in with). |
| Admin tools still empty after reset | Sign out and in again; then `select public.set_admin_mfa_required(false);` and check `select value from public.app_flags where key='require_admin_mfa';` is `false`. |
| `function reset_admin_mfa does not exist` | 026 was not run. Run the whole file, then retry. |
| Code sheet still shows at sign-in | The factor is still there: run step 3 again, or `select id,status from auth.mfa_factors;`. |
| "That code did not work" after re-setup | Phone clock must be automatic (Settings > Date & time); codes last 30 s. |
| Locked out of the SQL editor too | Supabase dashboard login is separate from the app; recover that account through Supabase. |

## Not run
Not built or run by Claude (no Android SDK, no live Supabase access). The SQL was not executed; it only uses tables and columns that migration 017 already uses (`auth.mfa_factors`, `public.app_flags`, `public.audit_logs`). If `audit_logs.actor_id` is NOT NULL on your project, change `null` to `(select id from auth.users where lower(email)=lower(trim(email_)))` in the insert.

Note: if you meant the 2-step verification of your own Google account (not the BOFO admin login), that is reset at myaccount.google.com/security with backup codes or a recovery option; this changelog does not cover it.
