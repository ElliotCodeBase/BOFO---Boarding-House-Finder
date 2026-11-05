# v2.9.1 - Build fix
- `AdminRowAdapter.isEmpty()` is now `public` with `@Override` (a weaker access does not compile: the build failed on it). This was the v2.5.0 fix that never reached the repo.
- Version 2.9.1 (versionCode 20901).

# v2.9.0 - Auto-lock for admins (Admin next steps, step 5 of 5, part 1)
- **Auto-lock.** After 5 minutes or more in the background, the admin tools are covered and the 6-digit code is asked again. What you were typing is kept (the screens are only hidden). Skipped when the account has no two-step sign-in. If you lost the authenticator, "Sign out instead" is on the lock sheet. A rotation or an app restart does not unlock.
- **Settings (admin only):** switch "Lock after 5 minutes away", default on, with a one-line hint.
- **Not done: moderator role split (item 13).** It needs migration `035_admin_level.sql` (column `admin_level`, server checks in the ban / unban / suspend / take down / refund functions, self-assign guard). Waiting for the owner's OK.
- Docs: `ADMIN_GUIDE.md` and `ADMIN_UI_AUDIT.md` updated. No server change.
- Version 2.9.0 (versionCode 20900). Not compiled or run in the sandbox that made this change.

# v2.8.0 - Weekly summary, reports about a person, CSV export (Admin next steps, step 4 of 5, parts 8-10)
- **Last 7 days card** on Admin Home: new users, new listings, reports resolved, each with the 7 days before in brackets. Read-only, no migration.
- **Reports about a person.** In a report's details: "N other waiting reports about this person" and a button "All reports about them" that filters the Reports list to that person (picking a filter from the title clears it).
- **Export CSV** button on Reports and Payments (the list as shown, search included). Reports: id, date, reason key, status. Payments: id, date, amount, status. No names, ID data or details text. Opens the share sheet.
- **Not done:** AI unanswered / AI chat log with the shared chips + row look (item 11): their reason filters do not fit the 3 Waiting / Done / All chips; needs a decision.
- Also in this zip: `docs/ADMIN_NEXT_STEPS.md` (the plan, step 1 asked for it). No server change.
- Version 2.8.0 (versionCode 20800). Not compiled or run in the sandbox that made this change.
