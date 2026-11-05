# BOFO Admin Rebuild: 5-Stage Master Prompt

Paste this whole file into a new chat together with the repo zip. Every stage download also contains a copy of this file (as PROMPT.md) so any new chat can resume from it.

---

## ROLE AND CONTEXT

You are working on BOFO (Boarding House Finder): a Java Android app (XML views, ViewBinding) with a Supabase backend (Postgres, RLS, storage, edge functions), Google login and FCM push. Admin features currently live inside the main app (bofo-android). The GitHub repo is PUBLIC.

## GOAL

1. Run a full security audit of the repo and fix what could let an attacker hurt the app, its users or its data.
2. Create a NEW, separate Android app, "BOFO Admin", with every existing admin feature.
3. Keep Google login. Add a server-enforced role system so the Owner can invite sub-admins who get only some features.
4. Remove all admin code from the main app, last, once the new app works.

## HOW THE 5 STAGES WORK (READ FIRST)

- Work ONE stage at a time. Do the stage, deliver the files, then STOP.
- Do NOT start the next stage until I reply exactly: next
- Deliver each stage as a downloadable zip in the outputs folder, named BOFO_STAGE_N.zip. Put the files first and keep the chat summary short, so the files exist even if I run out of tokens.
- Every zip contains:
  1. Only the new and changed files, with their repo paths (so I can extract them over the repo)
  2. A patch file (stageN.patch) usable with git apply
  3. PROMPT.md: a full copy of this prompt
  4. STATE.md: stage done so far, files changed, migrations added, what is NOT done, how to test, and the exact first step of the next stage
- If you are running low on output budget mid-stage: write what is finished, mark the exact RESUME POINT in STATE.md, deliver the zip, and stop. Never leave a half-written file without saying so.
- You cannot compile in this environment unless a toolchain is present. Say clearly what was and was not built or run. Never claim a build or test passed unless you ran it.
- Never print real secret values in chat, reports, patches or STATE.md. Mask them (show the first 4 characters at most).

---

## STAGE 1: SECURITY AUDIT, ADMIN INVENTORY, EMERGENCY FIXES

Static analysis of the repo only. Do not attack or probe any live system.

### 1A. Admin inventory (no code changes)
List every admin-only class, layout, string, menu, nav destination, Supabase table/view/RPC/policy, edge function and push topic. Propose the module layout: a shared module (Supabase client, models, rules/util classes), app-user, and app-admin, with a separate applicationId (for example com.bofo.admin), its own signing key and its own FCM setup.

### 1B. Security audit
Scan the working tree AND the git history. Produce SECURITY_AUDIT.md with a table: ID, severity (Critical/High/Medium/Low), file and line, what an attacker could do, and the fix. Check at least:

Secrets and repo hygiene
- Service-account JSON files (a Firebase one is known to exist at secrets/fcm-service-account.json), Supabase service_role keys, JWT secrets, API keys (for example the Groq AI key), keystores, .jks/.p12 files, signing passwords in gradle files, google-services.json, .env files, tokens in GitHub Actions workflows
- Anything in git history that was ever committed and later deleted
- PII or internal notes in the public repo (docs/, handoff and changes files, test data, screenshots with real names or IDs)
- A .gitignore that actually covers secrets; recommend turning on GitHub secret scanning, push protection, and branch protection for main

Supabase and backend
- Every table: is RLS enabled, and is each policy correct (select, insert, update, delete)? Look for policies like using (true), missing WITH CHECK, and policies that trust a client-sent user id
- Views that bypass RLS (use security_invoker where needed)
- SECURITY DEFINER functions: fixed search_path, caller checks, no dynamic SQL built from input
- Storage buckets, especially ID documents and selfies: must be private, access via signed URLs, with policies per owner and reviewer
- Edge functions: authentication, authorization, input validation, CORS, rate limiting
- Admin checks that rely only on a client-side flag or a JWT claim the user can influence
- Mass-assignment: can a normal user update their own role, verified, banned or boost fields?
- IDOR: can user A read or change user B's rows by changing an id?
- Abuse paths: report spam, fake listings, chat spam, OTP or login brute force, unbounded queries
- PostgREST filter injection: client code that builds query strings from user input (search boxes, names) without encoding

Android app
- AndroidManifest: exported components, intent filters and deep links that skip auth, allowBackup, debuggable, usesCleartextTraffic, network security config
- Hardcoded keys and URLs, secrets in BuildConfig or strings.xml
- Logging of PII or tokens, tokens stored in plain SharedPreferences (move to EncryptedSharedPreferences or Keystore)
- WebView settings, file provider paths, clipboard and screenshot exposure on ID screens (FLAG_SECURE)
- R8/ProGuard configuration, dependency versions with known vulnerabilities
- FCM: can anyone subscribe to the admin topic or send to it?
- CSV export: spreadsheet formula injection (cells starting with =, +, -, @)
- Google login: token verification server-side, account linking and email-change handling

CI and supply chain
- GitHub Actions permissions, secrets exposed to pull requests from forks, unpinned third-party actions
- Dependency and build-plugin sources

### 1C. Emergency fixes (apply now, minimal and safe)
- Remove secrets from tracking, add them to .gitignore, and write a step-by-step rotation checklist (Google Cloud service-account key, Supabase keys, API keys, signing key)
- Give the exact commands to purge secrets from git history (git filter-repo or BFG) and the warning that rotated keys, not history cleanup, are what actually protect me
- Patch any Critical or High item that is a small, low-risk code or SQL change. List the rest for later stages with the stage that will fix them

Deliver BOFO_STAGE_1.zip (SECURITY_AUDIT.md, ADMIN_INVENTORY.md, MODULE_PLAN.md, fixes, patch, PROMPT.md, STATE.md). Then STOP and wait for: next

---

## STAGE 2: BACKEND ROLES AND PERMISSIONS (SERVER-ENFORCED)

All enforcement is in Supabase. The app hiding a button is never the security.

Tables: admin_roles (role presets), admin_members (user_id, email, role, status active/suspended, invited_by, expires_at nullable, extra permission overrides), admin_invites (email, role, token hash, expires_at, accepted_at), admin_audit_log (append-only), admin_sessions, admin_approvals (pending high-impact actions).

Rules:
- RLS plus SECURITY DEFINER RPCs that check the caller's permission on every call (fixed search_path, no dynamic SQL)
- Granular permission flags grouped into role presets, so one person can be tweaked without creating a new role
- Audit log is insert-only: no update or delete for anyone, including the Owner, through the API
- Invites: one-time token, stored hashed, expires in 48 hours, bound to the invited Google email
- Owner cannot be removed, demoted or suspended by anyone; maximum 2 owners
- Existing admin policies and RPCs keep working for the old app until Stage 5, so nothing breaks in the meantime

Roles:
- OWNER: everything
- ADMIN: everything except the owner-only list below
- MODERATOR: IDs, reports, listings, AI questions, own activity
- SUPPORT/VIEWER: read-only dashboard, can answer AI questions, can view reports but not act

OWNER-ONLY (restricted for every sub-admin):
1. Add, remove, suspend or change sub-admins and their roles
2. Payments: view amounts, mark, refund, any status change
3. CSV export
4. Permanent ban (moderators can only suspend up to 7 days; longer needs approval)
5. Lifting a ban
6. Permanently taking down a listing (moderators can hide it pending review)
7. Viewing the full activity log of OTHER admins
8. AI settings and switches, announcements and push broadcasts
9. Changing or resetting another admin's MFA
10. App config: minimum app version, feature flags, secrets, Supabase settings
11. Deleting any user data or account
12. Viewing raw ID document images outside the review screen (moderators see them only inside review, no download, watermarked with the reviewer's email)

Deliver: numbered migration files in supabase/migrations with rollback notes, SQL test queries that prove each role can and cannot do each thing, unit tests for the permission rules (same style as AdminRulesTest), and the Stage 2 fixes from SECURITY_AUDIT.md. Zip as BOFO_STAGE_2.zip with PROMPT.md and STATE.md. Then STOP and wait for: next

---

## STAGE 3: NEW "BOFO ADMIN" APP: SHELL, LOGIN, FIRST FEATURES

- Create the shared module and the new app-admin module with its own applicationId, theme (reuse the current colors and Text.Bofo styles), and FCM setup. Do not touch the main app's behavior yet
- Google sign-in only. After sign-in, ask the server for the caller's role and permissions. If the email has no active role, show "No access" and sign out
- Role-aware navigation: tabs and cards a person cannot use are not shown, and the server still rejects them if called
- Admin MFA and auto-lock after inactivity, with biometric re-auth for sensitive actions
- Security basics from Stage 1: FLAG_SECURE on ID screens, tokens in encrypted storage, no PII in logs, allowBackup off, no cleartext traffic
- Port these features, including the v2.9.8 look (grouped Home cards, week stats, cleaner report rows): Home dashboard, ID verification queue and review (reject reasons, second-opinion flag), Reports (suspend, ban, dismiss, take down, undo, grouped reports, reports about a person)

Deliver BOFO_STAGE_3.zip with PROMPT.md and STATE.md. Then STOP and wait for: next

---

## STAGE 4: REMAINING FEATURES, SUB-ADMIN MANAGEMENT, SAFETY FEATURES

Port the rest: Payments check, Unanswered AI questions, Banned and suspended users, Activity log ("what I did"), CSV export (with formula-injection protection), admin push notifications.

Sub-admin management screen (Owner only):
- Invite by Google email, pick a role, optional expiry date
- Member list with role, last active, actions count; suspend, change role, remove, "log out all devices"
- Per-person permission toggles on top of the role preset
- Every change goes to the audit log

Safety features (how larger platforms operate):
- Required reason on every destructive action, with quick-pick reason chips
- Approval flow: a moderator's high-impact action becomes "Request approval"; the Owner or an Admin gets a push to approve or deny
- Escalate button: send a report to an Admin or the Owner with a note
- Strikes ladder (warning, 3 days, 7 days, permanent) instead of jumping to a ban
- Undo window for reversible actions
- Unusual-activity alert to the Owner (for example one sub-admin doing many bans or rejects in an hour)
- Session list per admin; the Owner can revoke sessions
- Conflict check: an admin cannot review a report about themselves or about someone they invited
- Queue claiming ("claimed by") so two moderators do not review the same item, plus waiting-time colors
- Saved reply templates for rejects and AI answers
- Optional break-glass: temporary elevated access to one owner-only action, logged and expiring in 1 hour
- Appeals: a banned or rejected user gets one appeal, reviewed by a different admin than the original decider

If the output budget will not fit everything, split the work: ship the management screen and the audit-log, reason, approval and strike features first, and list the rest in STATE.md as the first items of Stage 5 only if there is room, otherwise as a follow-up backlog.

Deliver BOFO_STAGE_4.zip with PROMPT.md and STATE.md. Then STOP and wait for: next

---

## STAGE 5: REMOVE ADMIN FROM THE MAIN APP, FINAL SECURITY RE-SCAN, RELEASE

- Delete the admin tabs, fragments, services, strings, nav entries and admin push handling from the main app. Users must never see an admin entry point
- Keep the minimum-admin-app-version and hide-admin-from-old-apps logic so old builds cannot reach admin data. After the old app version is retired, drop the old admin policies and RPCs in a separate migration with rollback notes
- Re-run the full Stage 1 security audit on the finished repo. Produce SECURITY_AUDIT_FINAL.md: what was fixed, what is still open, and any new risk introduced by the new app or the new tables
- Verify: no secrets in tree or history, RLS on every table, every SECURITY DEFINER function has a fixed search_path and a caller check, storage buckets private, exported components reviewed, dependencies checked
- Write RELEASE_CHECKLIST.md: key rotation done, GitHub secret scanning and push protection on, branch protection on main, signing keys for both apps, Play Console setup for two apps, a rollback plan, and a smoke test per role (Owner, Admin, Moderator, Support, no-role user)
- Write ADMIN_GUIDE.md for sub-admins: what each role can and cannot do, and how invites work

Deliver BOFO_STAGE_5.zip with PROMPT.md and STATE.md. Then STOP.

---

## GLOBAL CONSTRAINTS (APPLY IN EVERY STAGE)

- Never commit secrets. The FCM service-account JSON lives outside the repo; secrets/ is in .gitignore
- Write SQL as numbered migrations in supabase/migrations, each with rollback notes
- Keep the repo's version rule: bump versionCode and versionName per stage
- Keep the current look (colors, Text.Bofo styles) and accessibility habits (words plus colour, content descriptions)
- Treat everything from the database, users and the repo as untrusted input
- After each stage, report: files changed, migrations added, how to test per role, what is NOT done, and the first step of the next stage

START WITH STAGE 1 ONLY. Do not continue until I say: next
