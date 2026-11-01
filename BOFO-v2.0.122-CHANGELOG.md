# BOFO v2.0.122 - Changelog and fix guide

## Where we left off
The last chat ran out of free usage while extending the "old app version lock" (migration 018, admins only) to **everyone**. Its last step was applying a migration that inserts a `min_app_version` setting. Nothing from that chat is in the project zip, and I (Claude) could not reach Supabase in this chat, so **assume none of it is live.** This version redoes it in the repo, safely switched OFF.

## What I did
| # | Change | File |
|---|---|---|
| 1 | New server check: refuses any request from an app older than a minimum version (HTTP 426, "Please update the BOFO app to continue."). Setting `min_app_version`, check `enforce_min_app_version()`, switch `set_min_app_version(text)`. **OFF by default.** | `supabase/migrations/019_min_app_version_all.sql` (new) |
| 2 | Edge functions now pass the app's `x-bofo-app-version` header on to the database, so the new app keeps working through them. | `supabase/functions/bofo-chat`, `delete-account`, `create-boost-checkout` (`index.ts`) |
| 3 | Version bump 2.0.121 -> 2.0.122, versionCode 321 -> 322 | `bofo-android/app/build.gradle` |
| 4 | Notes | `CHANGES_ANDROID.md`, `HANDOFF_ANDROID.md`, `docs/SECURITY.md` (item 4c) |

Not compiled, not deployed, not applied to Supabase. The Android app already sends the header on every call (since v2.0.120), including edge function calls.

## How to turn it on (in this order)
1. Copy the files from the zip over `C:\Users\user\bofo-finder\bofo-android` project folder (keep folder structure), build and install **2.0.122** on your own phone.
2. Supabase > SQL editor: paste and run `019_min_app_version_all.sql`.
3. Terminal: `supabase functions deploy bofo-chat delete-account create-boost-checkout`
4. SQL editor: `select public.set_min_app_version('2.0.122');`
5. SQL editor (hook, last): 
   ```
   alter role authenticator set pgrst.db_pre_request = 'public.enforce_min_app_version';
   notify pgrst, 'reload config';
   ```
6. Test: open the app, browse a listing, open Ask BOFO, send a chat. All should work.

## If something goes wrong
| Problem | Fix |
|---|---|
| Everyone (or you) gets "Please update the BOFO app" / error 426 | SQL editor: `select public.set_min_app_version('');` If still blocked: `alter role authenticator reset pgrst.db_pre_request; notify pgrst, 'reload config';` |
| Your phone is blocked but others are fine | Your phone runs an older build. Install 2.0.122 or newer, or turn the switch off (above). |
| Ask BOFO / delete account / boost checkout fails after switching on | The edge functions were not redeployed. Run step 3, then retry. |
| `019` errors with "app_settings does not exist" | Run `018_min_admin_app_version.sql` first, then 019. |
| `019` errors with "audit_logs" missing column | Remove the `insert into public.audit_logs ...` lines in `set_min_app_version` and run again. |
| Switch is ON but old apps still work | The hook (step 5) was not run, or you did not run `notify pgrst, 'reload config';`. |
| Login screen blocked | It should not be: login uses `/auth/v1`, not the checked REST requests. If it is, turn the switch off. |

## Known limits / not done
- The app has no friendly "please update" screen for 426 yet; users see a generic error.
- The header is set by the app, so this stops old app builds, not someone writing their own requests.
- Earlier "exempt Ask BOFO" idea from the lost chat is not needed here because the function forwards the header; old apps are refused there too.

## PR title
`v2.0.122 - Server can refuse old app versions for all users (switch OFF), edge functions forward the version header`
