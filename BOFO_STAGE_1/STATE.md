# STATE after Stage 1 (version bumped to 2.9.5 / versionCode 20905)

## Done
- Static audit of the repo zip: SECURITY_AUDIT.md (20 findings, 1 fixed High, 3 need your action).
- ADMIN_INVENTORY.md, MODULE_PLAN.md, ROTATION_AND_HISTORY_PURGE.md.
- Fixes applied (files in this zip, same paths as the repo, plus `stage1.patch`):
  - `supabase/migrations/038_stage1_security_fixes.sql`: owners can no longer undo a take-down (S-02). **Not run on your database**: run it in the SQL editor, then the TEST block at the bottom of the file. Rollback is in the file header.
  - `.gitignore` (clean UTF-8, more secret patterns), `bofo-android/.gitignore`
  - `.github/workflows/android-gradle.yml`: `permissions: contents: read`
  - `bofo-android/app/build.gradle`: version bump only
  - `supabase/.temp/linked-project.json`: removed (a zip cannot delete: run `git rm --cached supabase/.temp/linked-project.json`; the patch does it too)

## NOT done / could not be done
- **Git history was not scanned**: the zip has no `.git`. Run the 3 commands in ROTATION_AND_HISTORY_PURGE.md on your PC.
- I could not see your live Supabase project, dashboard settings, GitHub settings or Google Cloud, so S-01, S-03, S-04 are yours to do (steps are written).
- Nothing was built or run: no Gradle build, no unit tests, SQL not executed (no Postgres here). The migration is small and mirrors the existing `protect_users` pattern.
- No dependency CVE scan (no network).
- `CHANGES_ANDROID.md` / `HANDOFF_ANDROID.md` top entries (repo rule) not updated, to keep this stage small.
- `build.gradle` reads config from a desktop path that is not in the repo (S-18): please check that CI builds get a Supabase URL.
- The zip's `build.gradle` says 2.9.4 but strings mention v2.9.8: your local repo may be newer than the zip. If your build.gradle is newer, **do not copy my build.gradle**; just bump your own by +1.

## Do these first (in order)
1. Rotate the Firebase service-account key (S-01). 2. Run migration 038. 3. Delete dev accounts + enroll admin MFA and turn it on (S-03, S-04). 4. Turn on GitHub secret scanning, push protection, branch protection.

## How to apply
Extract this zip over the repo root, or `git apply stage1.patch` (it contains a binary hunk for `.gitignore` because the old file had NUL bytes; `git apply` handles it).

## Stage 2 first step
Read `supabase/migrations/` (001-038) and write `039_admin_roles.sql`: tables `admin_roles`, `admin_members`, `admin_invites`, `admin_audit_log`, `admin_sessions`, `admin_approvals`, plus `has_permission(text)` (definer, fixed search_path) and `my_admin_access()`; keep `is_admin()` working for the old app by making it also true for active members with the `legacy_admin` preset. Then fix S-05, S-06, S-13, S-15, S-17 and write SQL role tests and `PermissionRulesTest`.
