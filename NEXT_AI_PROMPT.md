# Instructions for the next AI (BOFO Finder, after Stage 5 / v2.11.0)

Read `CLAUDE.md` first, then only the top entry of `HANDOFF_ANDROID.md`, then `STATE_STAGE5.md`. Follow CLAUDE.md rules (changes-only zip, few tokens, never claim it builds, version bump, no secrets, no live DB changes without the user's OK in this chat).

## State
- Both apps are at **2.11.0 / 21100**. All 5 stages are done IN CODE. BOFO Finder has no admin code; the Admin app has everything.
- NEVER compiled: Gradle has not run on 2.9.15, 2.10.0 or 2.11.0. SQL 038-045 never executed. If the user pastes a compile or SQL error, fix that FIRST (one small targeted edit, bump patch, new changes-only zip).
- After unzipping 2.11.0 the user must delete the files in `DELETED_FILES.txt`.
- Open work: `SECURITY_AUDIT_FINAL.md` section 7 (N-1 appeals stale check, N-3 helper grants, S-07 nonce in the user app, history check, SHA pinning, account-deletion re-auth), `RELEASE_CHECKLIST.md`, accessibility labels in the Admin app (`docs/ADMIN_UI_AUDIT.md`).
- `045_drop_legacy_admin.sql` is NOT to be run until the old user-app versions are blocked (its header says how).

## Closing message shape
What was done (max 8 lines), the zip name, PR title with version, the PowerShell commands from CLAUDE.md section 4, what the user must run in Supabase, what was NOT tested. Then stop.
