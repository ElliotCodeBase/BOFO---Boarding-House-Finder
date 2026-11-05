# v2.8.0 - Weekly summary, reports about a person, CSV export (Admin next steps, step 4 of 5, parts 8-10)
- **Last 7 days card** on Admin Home: new users, new listings, reports resolved, each with the 7 days before in brackets. Read-only, no migration.
- **Reports about a person.** In a report's details: "N other waiting reports about this person" and a button "All reports about them" that filters the Reports list to that person (picking a filter from the title clears it).
- **Export CSV** button on Reports and Payments (the list as shown, search included). Reports: id, date, reason key, status. Payments: id, date, amount, status. No names, ID data or details text. Opens the share sheet.
- **Not done:** AI unanswered / AI chat log with the shared chips + row look (item 11): their reason filters do not fit the 3 Waiting / Done / All chips; needs a decision.
- Also in this zip: `docs/ADMIN_NEXT_STEPS.md` (the plan, step 1 asked for it). No server change.
- Version 2.8.0 (versionCode 20800). Not compiled or run in the sandbox that made this change.

# v2.7.0 - Faster ID reviews: more reject templates + second-opinion flag (Admin next steps, step 3 of 5, part 1)
- **Reject templates.** The reject dialog now has 8 quick reasons (blurry, cut off/glare, back missing, expired, face mismatch, name mismatch, ID type not accepted, Other). Same dialog on the review screen and the Queue long-press.
- **Second opinion.** Queue, long-press a waiting row: "Ask for a second opinion" (or "Clear flag"). The row pill says "Second opinion". Needs `supabase/migrations/034_second_opinion.sql` (not run by the AI); until then the app says it could not change the flag.
- **Not done: "Approve clean (N)".** Approving needs the ID number retyped from the photo (v2.1.9); a bulk approve would skip that check, so it waits for the owner's decision.
- Version 2.7.0 (versionCode 20700). Not compiled or run in the sandbox that made this change.

# v2.6.0 - Admin push for new ID reviews and for items waiting over 3 days (Admin next steps, step 2 of 5)
- **New ID to review.** When an owner submits an ID, admin phones get "New ID to review" (no name, no ID data). Tap: Queue with the Waiting filter.
- **Waiting over 3 days.** Once a day an admin phone gets "N items have been waiting more than 3 days" (counts of IDs + reports + payments only). Tap: the tab with the most waiting items, Waiting filter.
- **Admins only.** The server sends both only to accounts with role admin; the phone also drops them for any other account. Admin notification switches ("Reports (admin)", "ID reviews and reminders") appear in Android settings as soon as an admin signs in.
- **Server (you run it).** Migration `033_admin_push.sql`, redeploy `send-push`, and schedule the daily call (steps in `docs/PUSH_SETUP.md`). Until then nothing new is sent; chats, groups and reports are unchanged.
- **Test.** `select public.push_notify('test', '{"as":"idreview"}'::jsonb);` (also `stale`, `report`) sends a labelled test to admin phones.
- Version 2.6.0 (versionCode 20600). Not compiled or run in the sandbox that made this change.
