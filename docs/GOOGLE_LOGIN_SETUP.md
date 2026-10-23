# Google login setup for BOFO Finder (done 2026-10-23)

No secrets in this file. Keys live in `KEYS.local.md` (git-ignored).

## How login works (so you can debug it)
App -> opens browser to Supabase `/auth/v1/authorize?provider=google` -> Google -> Supabase callback -> redirects back to the app
(`bofo://auth-callback` on Android, `http://localhost:54321/callback` on desktop) with `?code=...` -> app swaps the code for a session (PKCE).
New user -> RoleView (student/owner) -> `AuthService.createProfile` inserts `users` + `user_agreements`. Returning user -> session restored on splash.
Code: `AuthService.signInWithGoogle/handleDeepLink`, `LoginView`, `Main.showLogin/route`, `AndroidManifest.xml` (deep link).
It is the BROWSER flow, so Google needs a **Web application** OAuth client (not Android; no SHA-1 needed).

## What was done
1. Google Cloud (console.cloud.google.com): created project **BOFO Finder** (Organization: No organization - fine for a personal Gmail, even for a paid app). Ignore the $300 free-trial banner; no billing needed.
2. Google Auth Platform (`/auth/overview`) -> Get started: app name BOFO Finder, support email = own Gmail, Audience = External, contact = own Gmail, agreed to policy, Create.
3. Audience -> Test users: added own Gmail (in Testing mode only listed test users can sign in; cap 100).
4. Clients -> Create client: type **Web application**, name `BOFO Supabase`, Authorized redirect URI exactly
   `https://zfxldzcoirqolflkkhrh.supabase.co/auth/v1/callback` (no trailing slash/spaces), JavaScript origins empty. Copy Client ID + secret into `KEYS.local.md`.
5. Supabase dashboard (project BOFO) -> Authentication -> Sign In / Providers -> Google: enable, paste Client ID + secret, Save.
6. Supabase -> Authentication -> URL Configuration -> Redirect URLs: add `bofo://auth-callback` and `http://localhost:54321/callback`.
7. Test: `mvn javafx:run` (desktop) or `mvn -Pandroid gluonfx:build gluonfx:package` then `gluonfx:install gluonfx:nativerun`.

## Common errors
| Message | Cause / fix |
|---|---|
| `redirect_uri_mismatch` (Google page) | The URI in the Google client differs from step 4. Fix typo/trailing slash. |
| `requested path is invalid` (Supabase) | A redirect URL from step 6 is missing in Supabase URL Configuration. |
| `Access blocked: app has not completed verification` / `access_denied` | Gmail not in Test users (step 3), or publish the app (below). |
| `Unsupported provider: provider is not enabled` | Google not enabled/saved in Supabase (step 5). |
| Browser says Signed in but app stays on login | Port 54321 busy on the PC, or the `bofo://` link not registered (rebuild/reinstall the APK). |
| `invalid_client` | Wrong Client ID/secret pasted in Supabase (no spaces; re-copy). |

## Before real users
- Google Auth Platform -> Audience -> **Publish app** (basic name/email/photo scopes need no Google review). Needs a public Privacy Policy + Terms URL on the Branding page.
- Selling Boost plans = business: DTI/BIR registration, PayMongo go-live (not a Google matter).
- Deploy edge functions `bofo-chat`, `create-boost-checkout`, `paymongo-webhook` with their secrets (see KEYS.local.md).

## Log (2026-10-23)
- Supabase auth logs confirmed Google PKCE login works end to end (user_signedup + /token 200). Dev email/password test accounts also log in (200).
- Bug fixed: clicking "Continue with Google" twice left the old localhost:54321 listener running -> "Sign-in failed" even though login succeeded. `AuthService.startLoopback` now stops the old listener first and returns a clear error; `LoginView` now shows the real error text.
- To read auth logs yourself: Supabase dashboard > Logs > Auth, or ask Claude to query `auth_logs`.

## Delete account (2026-10-23)
Fixed with migration 004 + Edge Function `delete-account` (see HANDOFF.md). Deleting your Google test account also removes its Supabase user; sign in with Google again to recreate it (new role choice).
