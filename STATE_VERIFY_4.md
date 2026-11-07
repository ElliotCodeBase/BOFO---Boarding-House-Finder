# STATE_VERIFY_4: open security items, app side + tests (v2.12.1 / 21201, both apps)
Labels: RAN = executed here, READ = read only, NOT CHECKED = needs Gradle / phone / live project.

| Item | Label | Result |
|------|-------|--------|
| S-07 nonce in user-app Google sign-in (`LoginFragment`, `AuthService`, new `Nonce`) | READ + RAN (Nonce logic) | SHA-256 known vector passes; raw value is 43 URL-safe chars, differs each time. Compiles: NOT CHECKED |
| S-14 re-auth before delete (`SettingsFragment`, `AuthService`, new `ReauthRules`) | READ + RAN (rules) | Google account: Google sign-in again with nonce; others: e-mail code (`create_user=false`); wrong account: nothing deleted, session untouched. UI: NOT CHECKED |
| S-04 in code: dev logins gated by `BuildConfig.DEBUG` in `AppConfig` and `AuthService.devSignIn`; release `build.gradle` empty | RAN (`tools/verify/check_release_dev.py`) | 0 failures. Admin app has no password/dev login |
| Tests: `NonceTest`, `ReauthRulesTest` (new, JUnit) | RAN as plain Java (13 assertions) | Pass. The JUnit versions run with Gradle: NOT CHECKED |
| Other rule classes changed since Stage 2 (shared rules, PushRules, BanRules) | n/a | Unchanged since Stage 1 Gradle run (you confirmed pass); no new tests needed |
| Stage 1 scripts again (resources, ViewBinding, calls) | RAN | 0 problems |
| Log check, both apps (`Log.*`, `println`, `printStackTrace`) | RAN (grep) | No log line prints a token, e-mail, ID number or photo URL. Two `Log.e` print the exception only (`LoginFragment`) |

## Needs you
- Supabase > Authentication > Providers > Google: "Skip nonce checks" must be OFF (the Admin app already relies on it). If sign-in fails with a nonce error after 2.12.1, this is the first place to look.
- Supabase e-mail OTP must be enabled (it already is for e-mail sign-in) so the delete code can be sent.
- Run Gradle (command in `HANDOFF_ANDROID.md`); paste the first error if any.
- Phone test: Google account deletion (Google sheet appears), e-mail account deletion (code arrives), release build shows no Student/Owner/Admin test buttons.

## Not done
Google-account fallback: if the phone has no native Google setup, deletion falls back to the e-mail code (works if the Google account's e-mail can receive one). The browser Google flow has no nonce (PKCE already protects it).

## Files
New: `util/Nonce.java`, `util/ReauthRules.java`, `NonceTest.java`, `ReauthRulesTest.java`, `tools/verify/check_release_dev.py`, this file. Changed: `AuthService`, `AppConfig`, `LoginFragment`, `SettingsFragment`, `strings.xml`, both `build.gradle`, `CHANGES_ANDROID.md`, `HANDOFF_ANDROID.md`, `tools/verify/run_all.sh`, `VERIFY_PROGRESS.md`.

## First step of Stage 5
Read `docs/ADMIN_UI_AUDIT.md`, then grep `app-admin` for icon buttons / `setImageResource` / `ImageButton` without `contentDescription`.
