# STATE after Stage 5 (v2.11.0 / 21100)

## Done (in code, never compiled)
- BOFO Finder (`bofo-android/app`): admin removed. Admin tabs, fragments, services, rules, layouts, strings, nav entries, admin push kinds/channels, the admin rows in Settings and Profile, the MFA code in `AuthService`, and the admin auto-lock prefs are gone (91 files deleted, list in `DELETED_FILES.txt`).
- An admin account that signs in to BOFO Finder gets `AuthRouter.role = STUDENT` (`realRole` stays ADMIN, so the chat rules still match the server). No admin entry point is reachable.
- Kept: banned/suspended screens, `AppealHelper`, `my_suspension`, user push kinds (dm, group, test), the `x-bofo-app-version` header, `BanRules` (validation only; `canSee` removed).
- `PushNotifier` deletes the old `bofo_reports` / `bofo_admin` notification channels from phones.
- S-08 fixed: `VerificationFragment` sets FLAG_SECURE (no screenshots / recents thumbnail of the ID screen).
- Repo hygiene from Stage 1 that was missing from the tree: `038_stage1_security_fixes.sql` copied into `supabase/migrations/`, `.gitignore` UTF-16 tail fixed + secret patterns, workflow `permissions: contents: read`.
- New files: `045_drop_legacy_admin.sql` (NOT for now), `SECURITY_AUDIT_FINAL.md`, `RELEASE_CHECKLIST.md`; docs `ADMIN_GUIDE.md`, `ADMIN_UI_AUDIT.md` finished.
- App-module tests: deleted the 10 admin rule tests (the same rules are tested in `shared/`); `PushRulesTest` rewritten for the user kinds; `BanRulesTest` lost `onlyAdminsSee`.

## Checked (what that means)
- A script checked every `R.*` / `@...` reference in the remaining code and XML against existing resources: no real gap. All edited XML parses. `javac` syntax pass on edited Java: no syntax error (symbol errors are expected without the Android SDK). `PushRules` logic run on plain Java: OK.
- NOT checked: Gradle compile, ViewBinding generation, lint, any test run, any device, any SQL.

## NOT applied (user must run, in this order, only if not yet done)
`038`, `039`..`044` (see RELEASE_CHECKLIST section 2), deploy `send-push`. `045` only later (see its header).

## Known limits / decisions
- Orange-theme toggle (admin-only) removed; orange is the default for everybody.
- `AppConfig` still has the debug-only `DEV_ADMIN_*` values; release builds leave them empty.
- `AuthService` still has password sign-in code for dev accounts (S-04).
- The Admin app has almost no TalkBack labels (docs/ADMIN_UI_AUDIT.md).
- Open security items: `SECURITY_AUDIT_FINAL.md` section 7 (N-1 appeals check, N-3 helper grants, S-07 nonce, history check).
