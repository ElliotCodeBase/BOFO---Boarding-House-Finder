# Handoff - v1.20.0 In-app email login + phone keyboard (2026-10-29)
- **PR title:** `v1.20.0 - In-app email sign-in (6-digit code / password) and keyboard-aware screens`. Version in `config.properties`, `pom.xml` (versionCode 80), `AndroidManifest.xml`, CHANGES.md.
- `LoginView` rewritten: email -> code typed in the app (Supabase `/auth/v1/otp` + `/verify`), or email + password (sign in / create account), plus Google. `AuthService` got `requestEmailCode / verifyEmailCode / signInWithPassword / signUpWithPassword / friendlyError / authProvider`; `googleName()` falls back to the email prefix. Dev test buttons show only when `dev.*.email` is in `config.local.properties`.
- `Nav`: Attach `keyboard` service lifts the app above the on-screen keyboard and scrolls the focused field into view (new dependency + `attachList` entry in `pom.xml`).
- **Supabase must be set up by hand:** `docs/EMAIL_LOGIN_SETUP.md` (email templates need `{{ .Token }}`; built-in email is limited to ~2/hour so add custom SMTP). Not compiled/run by Claude (no javac / Maven in sandbox).
---
# Handoff - v1.19.15 Gluon license for Android packaging (2026-10-29)
- **PR title:** `v1.19.15 - Android build: add Gluon license step`. Version in `config.properties`, `pom.xml` (versionCode 79), CHANGES.md.
- Native compile passes since v1.19.14; `gluonfx:package` then needs a Gluon license. Get one at https://gluonhq.com/activate, add it as GitHub secret `GLUON_LICENSE` (repo > Settings > Secrets and variables > Actions), re-run **Build Android APK**. Not run by Claude.
- **Update + push (PowerShell, repo `C:\Users\user\bofo-finder`):** `cd C:\Users\user\bofo-finder; Expand-Archive "$HOME\Downloads\BOFO-vX.Y.Z-changes.zip" -DestinationPath . -Force; git add -A; git commit -m "<PR title>"; git push` (if rejected: `git pull --rebase` then `git push`).
---
# Handoff - v1.19.14 Android build fix (2026-10-29)
- **PR title:** `v1.19.14 - Fix Android native build (resource patterns), real APK artifact`. Version in `config.properties`, `pom.xml` (versionCode 78), CHANGES.md.
- Cause of the "green but no APK" run: `pom.xml` `resourcesList` used `\.` and GluonFX writes it unescaped into JSON -> GraalVM `Invalid escape character`; `| tee build.log` hid the failure. Fixed with `[.]` patterns + `pipefail`. `packageType` -> `apk`. Next unknown: later GluonFX stages (reflection/JNI, signing) may show new errors; send the `BOFO-build-log` artifact. Not compiled/run by Claude.
---
# Handoff - v1.19.13 Calmer Directions card (2026-10-29)
- **PR title:** `v1.19.13 - Calmer Directions card`. Version in `config.properties`, `pom.xml` (versionCode 77), CHANGES.md.
- `DirectionsCard.render()` rewritten: result + one quiet line (`quickLine`) + link row; notes/credit in a collapsible `moreBox`. Not compiled/run by Claude.
