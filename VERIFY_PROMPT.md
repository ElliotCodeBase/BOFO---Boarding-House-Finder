# BOFO Verification and Release: 6-Stage Master Prompt

Paste this whole file into a new chat together with the repo zip (BOFO Finder + BOFO Admin, v2.11.0 / 21100 or newer).
Every stage download also contains a copy of this file, so any new chat can resume from it.

## CONTEXT (do not re-audit what is already done)

- Repo: `bofo-android/` (user app `com.bofo.finder`), `app-admin/` (`com.bofo.admin`), `shared/` (`com.bofo.shared`), `supabase/` (migrations 038..045, functions), `docs/`.
- The 5 admin-rebuild stages are DONE IN CODE (see `STATE_STAGE5.md`, `NEXT_AI_PROMPT.md`, `RELEASE_CHECKLIST.md`, `SECURITY_AUDIT_FINAL.md` section 7, `docs/ADMIN_UI_AUDIT.md`).
- NOTHING has ever been compiled, run on a device, or executed on the live database. All claims so far are "written", not "verified". This prompt closes that gap as far as a sandbox without Android SDK / live Supabase can, and turns the rest into exact steps for me.
- My machine: Windows PowerShell, repo at `C:\Users\user\bofo-finder`, GitHub `ElliotCodeBase/BOFO---Boarding-House-Finder`, branch `main`.

## HOW THE 6 STAGES WORK (READ FIRST)

- Work ONE stage at a time. Do the stage, deliver the files, then STOP.
- Do NOT start the next stage until I reply exactly: `next`
- FILES FIRST: write the deliverables to the outputs folder and present them BEFORE any long explanation, so I get them even if I run out of tokens. Chat summary max 10 lines.
- Every stage delivers:
  1. A changes-only zip named `BOFO_VERIFY_N.zip` (only changed/new files, repo-relative paths, ready to `Expand-Archive -Force` into the repo root). If files must be deleted, include `DELETED_FILES.txt` and tell me the PowerShell line to delete them.
  2. `STATE_VERIFY_N.md`: what was checked and HOW, what was fixed, what was NOT checked, files changed, and the exact first step of the next stage.
  3. A copy of this file as `VERIFY_PROMPT.md`.
- If you are running low on output budget mid-stage: finish the file you are writing, mark the exact RESUME POINT at the top of `STATE_VERIFY_N.md`, deliver the zip, and stop. Never leave a half-written file without saying so.
- If I paste a compile error, test failure or SQL error at any time, FIX THAT FIRST with one small targeted edit, bump the patch version, deliver a changes-only zip, and only then continue the stage.

## LOW-CREDIT MODE (ON BY DEFAULT, APPLY IN EVERY STAGE)

Goal: spend as little of my credit as possible without lowering the quality of the checks.

1. **Read less.** Never open a whole file when `grep -n` / `rg` plus a `view_range` answers the question. Only read files the stage actually needs. Do not re-read `CLAUDE.md`, `STATE_*.md` or `HANDOFF_ANDROID.md` beyond the parts named in the RULES. Never print file contents into the chat.
2. **Scripts, not eyeballs.** Write each check once as a small script in `tools/verify/` (resource refs, symbol/import check, deleted-class grep, ViewBinding ids, SQL object cross-check, secrets grep) and ship them in the zip. Later stages re-run those scripts instead of rewriting them. Scripts print only failures plus a one-line summary.
3. **Must / Should / Nice.** Inside each stage, do the MUST items first, then SHOULD, then NICE. If the budget is tight, stop after the last finished tier and list the rest in `STATE_VERIFY_N.md` as a backlog. Never start a NICE item before every MUST is done.
4. **Skip what is already proven.** If a check was RAN in an earlier stage and its inputs did not change (same files, same hash via `git diff --stat` or `sha256sum`), do not repeat it; write "unchanged since stage N" and move on.
5. **Few tool calls.** Batch commands in one shell call; write several files in one call; no "let me check" calls whose answer you will not use.
6. **Short outputs.** Chat summary max 6 lines (the closing message shape stays). `STATE_VERIFY_N.md` max 40 lines. Reports are tables, not prose. No recap of earlier stages, no explaining what I already know, no repeated warnings.
7. **Ship deltas only.** One changes-only zip per stage. No second copy of files as a patch, no re-delivery of unchanged docs, no regenerating a doc when only 2 lines changed (edit those lines).
8. **Think short.** Decide, act, report. No long option lists, no asking me questions unless a stage is truly blocked (then ask ONE question and keep going on the unblocked items).
9. **Skip and merge commands.** If I reply `next skip` the current stage's NICE items are dropped; if I reply `next merge` combine the next stage with the one after it only when both are small and the budget allows; if I reply `next lite` do MUST items only.
10. **Budget check at the start of each stage.** One line: which tiers you expect to finish. If you cannot finish MUST items, say so before starting and split the stage into a and b (b waits for my `next`).

## RULES (APPLY IN EVERY STAGE)

- Read `CLAUDE.md` first, then the top entry of `HANDOFF_ANDROID.md`, then the latest `STATE_*.md`. Follow `CLAUDE.md` rules.
- Never claim something builds, passes, or runs unless you actually ran it. Use exactly these labels in every report: **RAN** (you executed it here and saw the output), **READ** (you only read the code), **NOT CHECKED** (needs my machine, phone, or live DB). Never mix them.
- Run whatever IS possible in the sandbox: `javac` syntax passes, plain-Java logic tests (e.g. `PermissionRules`, `AutoLockRules`, `PushRules`, `BanRules`), XML parsing, resource-reference scripts, grep-based checks, SQL parsing with a Postgres parser or `psql` if one exists. State plainly if a tool is missing.
- No live database changes and no secrets in files. SQL is only written and reviewed; I run it myself, in this chat's OK.
- Keep the version rule: bump `versionCode` and `versionName` in BOTH apps for any stage that changes code (2.11.1 / 21101, then +1 per stage). SQL-only or docs-only stages do not bump.
- Do not rewrite working code for style. Smallest fix that removes the risk.
- Few tokens: no long recaps of earlier stages, no repeating the audit.

---

## STAGE 1: BUILD-RISK SWEEP (finds compile errors without Gradle)

Goal: remove the errors I would otherwise hit on the first `gradlew` run.
Tiers: MUST = items 1, 2, 3 (Gradle, symbols, ViewBinding) and the deleted-class grep. SHOULD = 4, 5 (manifest, resources). NICE = 6 (javac / plain-Java tests beyond what changed).

1. Gradle consistency: `settings.gradle` includes `:shared` and `:app-admin`; module `build.gradle` files (namespace, compileSdk, Java 17, dependencies, ViewBinding flags, R8 / proguard files exist, signing config reads env vars safely when absent, `google-services.json` missing does not break debug builds); version catalogs / repositories; `app` still depends on `:shared` only if needed.
2. Symbol check, all three modules: every import resolves to a class that exists (or a declared dependency); every method called on `Api`, `SessionStore`, `AuthService`, `Async`, `Http`, `PermissionRules` exists with that signature; no references left to the 91 deleted admin files (grep deleted class names, layout ids, string keys, nav ids, menu items, manifest entries, proguard rules).
3. ViewBinding: for every `XxxBinding` used, the layout `xxx.xml` exists and the ids used in Java exist in it. Navigation graphs: every destination and action id used in code exists.
4. Manifest: every Activity / Service / Receiver / Provider named exists; permissions; `allowBackup=false`, data-extraction rules, `usesCleartextTraffic=false`, notification channels referenced in code exist.
5. Resources: run the `R.*` / `@...` reference script again on all three modules (strings, colors, drawables, styles, dimens, menus, ids). Check duplicate resource names across `app`, `app-admin`, `shared`.
6. Run `javac` syntax passes on all Java; run the shared unit tests as plain Java where possible.
7. Fix every real problem found (smallest edit). List risky spots you could not prove.

Deliver `BOFO_VERIFY_1.zip` (fixes), `COMPILE_RISK_REPORT.md` (each finding: file, line, RAN/READ, fixed or not), `STATE_VERIFY_1.md`, and the exact commands for me:
```
cd C:\Users\user\bofo-finder\bofo-android
.\gradlew :shared:testDebugUnitTest :app-admin:assembleDebug :app:assembleDebug 2>&1 | Tee-Object build.log
```
and tell me to paste the first error from `build.log` if any. Then STOP and wait for: `next`

---

## STAGE 2: DATABASE VERIFICATION (migrations 038..045, no live changes)

Goal: make the SQL safe to run once, in order, with proof queries.
Tiers: MUST = items 1 and 3 (order, safety, app-to-SQL cross-check) plus `RUN_ORDER.md`. SHOULD = `VERIFY_AFTER.sql`, `ROLE_TESTS.sql`. NICE = item 4 (Edge Functions review).

1. Read 038, 039..045 in order. Check: run order and dependencies between files (functions/tables/columns used before they are created), idempotency (safe to run twice), `security definer` functions set `search_path`, RLS enabled on every new table with explicit policies, no `grant ... to anon/public` by accident, no policy that lets a role read or write outside its permission, destructive statements guarded (045 must stop by itself when an admin is not in the team list), rollback notes present and correct in every header.
2. Parse every file with a SQL parser or `psql --dry-run` equivalent if available (RAN); otherwise mark READ.
3. Cross-check the app: every RPC / table / column the Admin app and BOFO Finder call exists in a migration, with matching argument names and types.
4. Check the `send-push` and `bofo-chat` Edge Functions for the same (auth, secrets from env only, no logging of personal data).

Deliver `supabase/RUN_ORDER.md` (the exact order, what each file does in one line, which are safe to skip if already run, a "take a backup first" note before 042-045), `supabase/VERIFY_AFTER.sql` (read-only queries that prove each migration landed: functions exist, RLS on, policies listed, `my_admin_access()` per role), `supabase/ROLE_TESTS.sql` (per role: what it can and cannot do, to run in the SQL editor on test accounts), fixes as new migration `046_verify_fixes.sql` only if a real defect is found (with ROLLBACK note), `STATE_VERIFY_2.md`. Then STOP and wait for: `next`

---

## STAGE 3: OPEN SECURITY ITEMS, SERVER SIDE

Goal: close what `SECURITY_AUDIT_FINAL.md` section 7 leaves open on the database side.
Tiers: MUST = N-1, N-3 migration and the table/RLS re-check (item 4). SHOULD = S-03, S-04 SQL. NICE = S-01 history-check doc.

1. N-1 (appeals stale check) and N-3 (helper grants): write the fix as a numbered migration with ROLLBACK note and a test query that fails before and passes after.
2. S-01 history check: give me the exact PowerShell / git commands to search the whole history for keys, tokens, `service_role`, `.jks`, `google-services.json`, JWTs (I run them; you do not have my history). Tell me what to do for each kind of hit (rotate first, then purge).
3. S-03 (admin MFA flag), S-04 (live dev accounts): exact SQL to list dev/test accounts and to disable them, plus the order with `set_admin_mfa_required`. Read-only first.
4. Re-check RLS and grants on tables added after the audit (Stage 4 tables, strikes, approvals, push tokens, appeals): list any table without RLS.

Deliver the migration(s), `supabase/SECURITY_CHECKS.sql` (read-only), `docs/HISTORY_CHECK.md`, updated section 7 of `SECURITY_AUDIT_FINAL.md` (item by item: closed / still open and why), `STATE_VERIFY_3.md`. Then STOP and wait for: `next`

---

## STAGE 4: OPEN SECURITY ITEMS, APP SIDE + TESTS

Goal: close the code-side items and make the tests prove them.
Tiers: MUST = S-04 in code, S-07 nonce, S-14 re-auth. SHOULD = tests for what changed. NICE = log grep (use the script from `tools/verify/`).

1. S-07: nonce in the user app's Google sign-in (same pattern as the Admin app: Google gets the SHA-256, Supabase gets the raw value).
2. S-14: re-authentication before account deletion.
3. S-04 in code: remove or hard-gate the dev password sign-in and `DEV_ADMIN_*` values so release builds cannot contain them (check `build.gradle` release config, not only `AppConfig`).
4. Tests: add or repair unit tests for every rule class that changed since Stage 2 (shared rules, `PushRules`, `BanRules`, any new nonce/re-auth helper). Run them as plain Java where possible (RAN) and say which need Gradle.
5. Log check: grep both apps for logging of tokens, emails, ID numbers, photo URLs.

Deliver changes-only zip with version bump (both apps), tests, `STATE_VERIFY_4.md`, and the PowerShell commands for me (unzip, delete listed files, `git add -A`, commit, push, then the Gradle test command). Then STOP and wait for: `next`

---

## STAGE 5: ADMIN APP ACCESSIBILITY AND PER-ROLE SMOKE TESTS

Goal: finish `docs/ADMIN_UI_AUDIT.md` and give me a test sheet I can actually follow on a phone.
Tiers: MUST = labels on icon buttons and touch targets, plus the Owner and Admin smoke sheets. SHOULD = other roles, focus order, state announcements. NICE = contrast and large-font checks.

1. TalkBack: `contentDescription` / labels for every icon button, image, and custom control built in code; correct focus order; announce state changes (approved, rejected, banned, locked); minimum 48dp touch targets; text contrast against the colors used; large font scale does not clip key buttons.
2. Update `docs/ADMIN_UI_AUDIT.md` with each item: fixed / not fixed.
3. Smoke-test sheet per role (Owner, Admin, Moderator, ID reviewer, Support if they exist in the code): sign-in, no-access case, auto-lock and unlock, each sensitive action with its permission check, request-approval path, audit log, push, expired session, no network, and what the screen must show. Use `RELEASE_CHECKLIST.md` sections 8 and 9 as the base; add missing cases.

Deliver changes-only zip with version bump, `docs/SMOKE_TEST_SHEET.md` (checkbox table: step, expected result, pass/fail, notes), updated audit doc, `STATE_VERIFY_5.md`. Then STOP and wait for: `next`

---

## STAGE 6: CI, RELEASE SETUP AND FINAL REPORT

Goal: make the release repeatable and give me one final truth document.
Tiers: MUST = `VERIFICATION_REPORT.md` and the workflow for both apps. SHOULD = pinned actions / Dependabot, updated `RELEASE_CHECKLIST.md`. NICE = handoff rewrites (edit only the changed lines).

1. GitHub Actions: build both apps (`:shared:testDebugUnitTest`, `:app:assembleDebug`, `:app-admin:assembleDebug`, then release bundles when signing secrets exist), separate secrets per app, `permissions: contents: read`, pin every action to a full commit SHA (S-12; if you cannot know the SHA, use a Dependabot `github-actions` config and say so), add Dependabot for Gradle and Actions, S-18 CI config check.
2. Release docs: signing keys for two apps, Play App Signing, SHA-1 per app in Google Cloud OAuth, Firebase per package, Play Console two app entries (Admin = internal/closed testing only), data-safety notes, staged rollout, rollback. Update `RELEASE_CHECKLIST.md` so every box states who does it (me / Claude) and links the file or SQL that supports it.
3. `VERIFICATION_REPORT.md`: one table of every item from `SECURITY_AUDIT_FINAL.md` section 7 and the Stage 1 to 5 checks with status **RAN / READ / NOT CHECKED / CLOSED / OPEN**, nothing rounded up. Then a short "run these on your machine" list in the right order, and the "paste results back here" lines for each.
4. `FINAL-HANDOFF.md` and `NEXT_AI_PROMPT.md` updated to the true state.

Deliver `BOFO_VERIFY_6.zip` (workflow, dependabot, docs), `VERIFICATION_REPORT.md`, `STATE_VERIFY_6.md`. Then STOP.

---

## CLOSING MESSAGE SHAPE (EVERY STAGE)

What was done (max 8 lines), zip name, PR title with version, the PowerShell commands, what I must run in Supabase (if any), what was NOT tested. Then stop and wait for `next`.

START WITH STAGE 1 ONLY. Do not continue until I say: next
