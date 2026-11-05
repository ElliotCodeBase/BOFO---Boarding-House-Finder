# BOFO Android: implement the Admin "next" suggestions in 5 steps (ONE step per turn)

You are continuing work on **BOFO Finder** (native Android, Java 17, Gradle Groovy, Supabase backend). The repo zip is attached to this chat. The previous AI finished the "Admin UI plan" (v2.4.10 to v2.4.14) and left a ranked list of suggestions. Your job is to implement ALL of them in 5 steps.

## 0. Hard stop rule (most important)
- Do **only the step I ask for**. When it is done, send the closing message (section 2) and **STOP**.
- Do not start, plan, read files for, or offer to continue with the next step until my message is exactly `next` (or `next step`).
- If I write anything else (a bug, a question, a build log), handle it, keep the step counter where it is, and do not advance.
- If I switch AI and say "resume at Step N": read the top entry of `HANDOFF_ANDROID.md` (it says which steps are done), do NOT redo earlier steps, and run Step N.
- Start with **Step 1** unless I say otherwise.

## 1. Rules for every step (read first, every session)
1. Read `CLAUDE.md`, then only the top entry of `HANDOFF_ANDROID.md`. Use `grep -n` and read only the line ranges you need. Small targeted edits, no rewrites.
2. Deliver a **changes-only zip**: `BOFO-android-vX.Y.Z-stepN-changes.zip` with only added/changed files in their real folders, plus `CHANGED_FILES.txt` (`changed` / `new` / `DELETE`). Never include `build/`, `.git/`, unchanged files, `local.properties`, keystores, or any service_role key.
3. **Version bump on every step** (my standing rule): bump `versionName` and `versionCode` in `bofo-android/app/build.gradle` (versionCode = MAJOR*10000 + MINOR*100 + PATCH), update the version everywhere the old one is shown or written (Settings shows `BuildConfig.VERSION_NAME`), put it in the PR title and at the top of `CHANGES_ANDROID.md` and `HANDOFF_ANDROID.md` (keep only the newest 3 entries in each). Each step is a feature: bump the **minor**. Planned: Step 1 = v2.5.0, Step 2 = v2.6.0, Step 3 = v2.7.0, Step 4 = v2.8.0, Step 5 = v2.9.0 (if `build.gradle` already says higher, use the next minor; trust `build.gradle`, not old docs).
4. **Supabase safety:** never touch the live project. Look at the existing migrations first (`ls supabase/migrations`, use the next free number), write the migration file, and give me the SQL summary. **Ask my OK before adding any migration**; if I say no, build the part that needs no server change and say what it leaves out. Never read or print user data tables (see `CLAUDE.md` section 5).
5. **No Android SDK here, so nothing is compiled.** My last build failed on a one-line mistake (an `isEmpty()` override that was not `public`). Before zipping, do a manual compile review of EVERY changed Java file: every override has `@Override` and the same or wider access; every `R.id` / `R.string` / `R.layout` / `R.drawable` you use exists (grep it); imports are added; ViewBinding class names match the layout file names; no duplicate string names across `strings_*.xml`; checked exceptions are handled; lambdas capture only effectively-final variables. Add/extend plain-JVM unit tests for new pure rules (put rules in `util/*Rules.java` with no Android classes). Never claim it builds or runs.
6. Keep the Admin UI conventions from v2.4.10 to v2.4.14: `AdminListChrome` / `AdminRowAdapter` / `AdminListRules` for lists, `AdminErrorRules` for error sentences, plain-language strings, 48 dp targets, content descriptions, no animation when system animations are off.
7. Notifications since v2.4.15 are sent with a `notification` block + `push_*` data keys + `click_action = com.bofo.finder.OPEN_PUSH` (see `supabase/functions/send-push/index.ts` `buildMessage`). Any new push kind in Step 2 must keep working when the app is closed.
8. Spend few tokens: no long recaps, do not paste code that is already in the zip.

## 2. Closing message of every step (exactly this shape, then STOP)
1. What was done: at most 8 short lines, plain language.
2. The zip name, the PR title (with the version), and the PowerShell commands:
   `git checkout main; git pull; Expand-Archive "$HOME\Downloads\<zip>" "C:\Users\user\bofo-finder" -Force`
   `cd C:\Users\user\bofo-finder; git checkout -b admin-next-stepN; git add -A; git commit -m "<PR title>"; git push -u origin admin-next-stepN; gh pr create --title "<PR title>" --body "<one line>"; gh pr merge --squash --delete-branch`
3. Anything I must decide or run in Supabase (SQL, deploy command), and what is not tested.
4. Last line: `Step N of 5 done. Type next for Step N+1.`

Before the closing message: update `HANDOFF_ANDROID.md` (top entry: step N of 5 done, what changed, files, open risks, and "Next: Step N+1 = <one line>") and `CHANGES_ANDROID.md`, so another AI can continue.

## 3. The suggestions (13) and where they go
| # | Suggestion | Effort / benefit | Step |
|---|---|---|---|
| 1 | "Recently decided" list on Admin Home (last 10 decisions) with Undo where possible, plus migration `admin_unsuspend`, `admin_relist`, `admin_reopen_report` | medium / high | 1 |
| 2 | Swipe a report row to dismiss it (once Undo exists) | small / medium | 1 |
| 3 | Push notification to admins for new ID reviews (reports already have one) | medium / high | 2 |
| 4 | Push notification to admins when a waiting item passes 3 days | small / medium | 2 |
| 5 | Saved reply templates for rejection reasons | small / high | 3 |
| 6 | Bulk approve for clean cases (ID number matches and photo passed the sharpness check) | medium / high | 3 |
| 7 | "Needs a second opinion" flag on a report or ID | medium / medium | 3 |
| 8 | Weekly summary card on Admin Home (new users, listings, reports resolved) | small / medium | 4 |
| 9 | Count of "new reports about this person" on a user's profile in the admin view | medium / medium | 4 |
| 10 | CSV export of reports and payments | small / medium | 4 |
| 11 | Same chips, search and row look on the AI unanswered and AI chat log screens | small / medium | 4 |
| 12 | Auto-lock after 5 minutes in the background (ask for the 6-digit code again) | medium / medium | 5 |
| 13 | Light role split: moderator can review IDs and reports, only a full admin can ban or refund | large / high | 5 |

## STEP 1 of 5: Undo and "Recently decided" (suggestions 1, 2)
- Ask my OK for migration `032_admin_undo.sql` (check the number is free): `admin_unsuspend(user)`, `admin_relist(listing)`, `admin_reopen_report(report)`. Admin only (use the existing admin check), `security definer`, each writes an `audit_logs` row, each refuses with a clear error when the current state is no longer the one the decision produced (nothing to undo). Ban/unban Undo already exists since v2.4.12: reuse it.
- Admin Home: new card **Recently decided** opening a list of the last 10 decisions (what, about whom/what, when, by whom) with an **Undo** button only where it is possible (suspend, take down, dismiss, ban). Refunds are not undoable: show no button.
- Reports list: swipe a waiting row to dismiss, with a snackbar **Undo** (calls `admin_reopen_report`). Keep the long-press menu as the non-gesture way.
- Handoff noted a risk: `audit_logs` may lack a SELECT grant for `authenticated`. Give me the one-line SQL to check it (`select has_table_privilege('authenticated','public.audit_logs','select');`).
- Also copy THIS prompt into `docs/ADMIN_NEXT_STEPS.md` so it lives in the repo.
- If I refuse the migration: build the list read-only, hide the Undo buttons that need it, and say so.

## STEP 2 of 5: Admin push for ID reviews and stale items (suggestions 3, 4)
- New push kinds, **admin accounts only** (role checked on the server at send time, same as `report`; the phone also drops them unless the signed-in account is an admin): `idreview` (an owner/student submits an ID for verification; find the table and add an AFTER INSERT trigger like migration 029 does) and `stale` (once a day, one notification: "N items have been waiting more than 3 days", sent by a scheduled call to `send-push`, e.g. `pg_cron` + `pg_net`, with the same secret header).
- Lock-screen safe: no names, no ID data, only counts and the type. Admin channel created when an admin signs in (like `createReportsChannel`). Tap opens the right tab with the Waiting filter (extend `PushRouter` / `ShellFragment.openFromPush`).
- Update `send-push/index.ts`, `PushRules` (+ tests), `PushNotifier`, `docs/PUSH_SETUP.md` (new migration number, how to test with the `test` kind, the cron setup). Needs my OK for the migration. Do not break `dm`, `group`, `report`.

## STEP 3 of 5: Faster reviews (suggestions 5, 6, 7)
- **Rejection templates:** 6 to 8 friendly built-in reasons (wrong/blurry photo, ID expired, name mismatch, ID number unreadable, ...) in the reject dialog of the ID review screen; picking one fills the text box, still editable; let the admin save up to 10 own templates on the phone (no server).
- **Bulk approve:** on the Queue, an "Approve clean (N)" action. Clean = ID number matches AND the photo passed the sharpness check (find those fields in the verification code; if one of them is not stored, say so and use only what exists). Confirmation dialog with the count, max 20 per batch, one approve call per item with a progress line and a result summary ("18 approved, 2 skipped"). Anything not clean is never touched. No Undo exists for approvals, so the dialog says so.
- **Second opinion flag:** any admin can mark a report or ID "needs a second opinion" (and clear it); shows as a pill and as a filter chip. Needs a column + RPC (new migration, my OK first; a user must not be able to set it).

## STEP 4 of 5: Insights, export, consistency (suggestions 8, 9, 10, 11)
- **Weekly summary card** on Admin Home: last 7 days vs the 7 before for new users, new listings, reports resolved. Use existing read access if possible; otherwise one read-only RPC (migration, my OK first).
- **"New reports about this person"** count in the admin view of a user, tappable to the Reports list filtered to that person.
- **CSV export** of the current filtered Reports and Payments list (overflow menu "Export CSV"): write to the app cache, share through the existing FileProvider. UTF-8 with BOM so Excel opens it, neutralise cells that start with `=`, `+`, `-`, `@`. Reports: id, date, reason key, status. Payments: id, date, amount, status. **No names, ID data or report details text.**
- **AI unanswered / AI chat log** screens use `AdminListChrome` + `AdminRowAdapter` (chips, search, offline banner, skeleton, retry). UI only: do not change what data they show or who can read it.

## STEP 5 of 5: Safety, roles and final check (suggestions 12, 13)
- **Auto-lock:** admin only, after 5 minutes in the background ask for the 6-digit code again with the existing `AdminMfaSheet`. Skip if the admin has no two-step sign-in. Do not lose half-typed input. A Settings switch (admin only, default on) with a one-line hint.
- **Light role split:** keep `users.role = 'admin'` everywhere; add a column `admin_level` (`'admin'` default, `'moderator'`) in a migration (my OK first). Moderators can review IDs and reports; only `'admin'` can ban, unban, suspend, take down, refund. **Enforce on the server** (the RPCs check the level), and hide the buttons in the app. A user must never be able to change their own level (extend the self-assign guard trigger from `001_schema.sql`). Setting a level is done by SQL only: put the snippet in `docs/ADMIN_GUIDE.md`. Show "Moderator" in the admin banner. If it cannot be finished safely in one turn, do the server part and the app gating for the actions that exist, and list what is left.
- **Final QA:** update `docs/ADMIN_GUIDE.md` and `docs/ADMIN_UI_AUDIT.md`; do the manual compile review again over every file changed in Steps 1 to 5; list in the closing message what still needs a device test and which migrations I have / have not run.

## Now
Run **Step 1** and stop.
