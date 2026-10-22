# Handoff – Dev login bypass (2026-10-22)

## What changed
- `config.properties`: added `dev.bypassLogin=true` and `dev.role=student` (student | owner | admin).
- `AppConfig`: `devBypass()` / `devRole()`.
- `AuthService.devLogin(Role)`: fake local profile (verified, fixed UUID `00000000-0000-0000-0000-0000000de000`), no token.
- `Main.start`: when bypass is on, skips splash + Google login and routes straight to the home for `dev.role`.
- `LoginView`: when bypass is on (e.g. after Sign out) shows "DEV: skip login as Student / Owner / Admin" buttons.

## Caveats
- No real Supabase session: requests use the anon key, so anything behind RLS (saving, chat, publishing listings, owner/admin data) may return 401/403 or empty. Public reads (colleges, listings) should work.
- The fake user id has no row in `users`, so inserts with FK to `users` will fail. For full testing, use a real login or create a test user row in Supabase.
- NOT compiled here (no Maven network access) – run `mvn javafx:run` / your usual build to verify.

## Before release
Set `dev.bypassLogin=false` in `src/main/resources/com/bofo/finder/config.properties` (or remove the dev code).
