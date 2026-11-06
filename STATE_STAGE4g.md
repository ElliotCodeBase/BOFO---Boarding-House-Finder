# STATE after Stage 4, part 7 (admin app 2.9.14 / 20914): two-step sign-in setup

## Done (app-admin + shared only; NO new SQL)
- **More > Two-step sign-in** (every role): shows ON/OFF; "Set up" (fingerprint / screen lock) -> Supabase MFA enrol (`POST /auth/v1/factors`, totp) -> key shown grouped for typing + "Open authenticator app" (only an `otpauth://totp/` link is opened) -> 6-digit code -> challenge + verify (existing `verifyTotp`), which upgrades the session to two-step (aal2).
- **"No access" screen:** new "Set up two-step sign-in" button, for the case where the owner switched on `require_admin_mfa` and the account has no factor yet (the server then answers "no access"). After setup the role check runs again.
- `AdminAuth.enrollTotp()`; `shared/TwoStepRules` (+ test). Whole app is FLAG_SECURE, so the key cannot be screenshotted. Version 2.9.14 in both `build.gradle`.
- Deliberately NOT here: removing / replacing a factor. Losing a phone is handled by an owner (`admin_reset_mfa`, `mfa_manage`), so a stolen unlocked session cannot quietly swap the second lock.

## What was and was NOT run
- NOT run: Gradle, Java tests (no JDK/JUnit here), a phone. Send me the first compile error if any.

## How to test
1. An account with no factor: More > Two-step sign-in says OFF; Set up -> lock prompt -> key appears; add it to an authenticator app (link or by hand); wrong code -> "That code did not work"; right code -> ON.
2. Sign out and in: the app asks for the 6-digit code after Google sign-in.
3. With `require_admin_mfa` on and a fresh staff account: "No access" screen -> Set up two-step sign-in -> after the code the role check passes.
4. Abandoned attempts leave an unverified factor in Supabase Auth; harmless (not counted as ON), each attempt has its own name.

## NOT done (backlog)
Strikes ladder, Escalate, queue claiming, saved replies, unusual-activity alert, session list + revoke, break-glass, appeals (migrations); push when an approval is decided; AI announcements; then Stage 5.
