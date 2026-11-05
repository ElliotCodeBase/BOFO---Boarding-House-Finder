# Proposed layout (Stage 3 builds it)

```
bofo/                        (repo root; same repo, one Gradle build)
  shared/                    com.bofo.shared   (Android library, Java 17)
    SupabaseClient, SessionStore, Http, Async, Cache, ErrorText, Money
    models: Role, Profile, BoardingHouse, ...
    rules (pure, unit-tested): SuspendRules, BanRules, VerificationRules, PushRules, CsvRules
    permission model: Permission (enum of flags), RolePreset, PermissionRules
    res: colors, Text.Bofo styles, base theme (single source of the look)
  app-user/                  applicationId com.bofo.finder   (today's bofo-android minus admin)
  app-admin/                 applicationId com.bofo.admin    (NEW)
    own launcher icon + label "BOFO Admin", own FCM app in the same Firebase project
```
Gradle: `settings.gradle` includes `:shared :app-user :app-admin`. `bofo-android/` stays the folder name until Stage 5 so existing paths and CI keep working: Stage 3 adds `shared/` and `app-admin/` next to it and moves nothing that the main app uses (no behaviour change).

## Separation rules
- **applicationId** `com.bofo.admin` (debug suffix `.debug`), own `namespace com.bofo.admin`.
- **Signing**: separate upload key (`bofo-admin-release.jks`), own secrets `BOFO_ADMIN_KEYSTORE_*`; Play App Signing on. Keys never in the repo.
- **Firebase**: second Android app in the same Firebase project (`com.bofo.admin`) with its own `google-services.json` (ignored by git). `send-push` learns a `platform/app` column on `device_tokens` so admin pushes go only to admin-app tokens.
- **Google login**: a second OAuth Android client (package + SHA-1 of the admin key); same Web client ID can be reused for the Supabase id-token flow. The admin app sends a **nonce**.
- **Auth**: Google only. After sign-in the app calls one RPC (`my_admin_access()`, Stage 2) and shows "No access" + sign-out when there is no active role. No password login, no dev accounts.
- **Server is the gate**: hiding a tab is only UX. Every admin RPC and policy checks the permission server-side (Stage 2).
- **Min version**: `set_min_admin_app_version` keeps working; old main-app builds lose admin access via `app_version_ok()`.
- **Hardening in the admin app from day one**: FLAG_SECURE on all admin screens that show people, EncryptedSharedPreferences/Keystore, `allowBackup=false`, no cleartext, no logging of PII, R8 on, biometric re-auth for sensitive actions, clipboard cleared, auto-lock.

## Stage mapping
1: audit + inventory + this plan + emergency fixes. 2: backend roles. 3: shell + login + Home/ID queue/Reports. 4: everything else + management. 5: remove admin from the main app, final audit, release.
