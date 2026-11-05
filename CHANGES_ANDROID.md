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

# v2.5.0 - Recently decided + Undo, swipe to dismiss a report (Admin next steps, step 1 of 5)
- **Recently decided.** New card on Admin Home: the team's last 10 decisions (what, about whom or what, when, by whom). An **Undo** button shows only where it can work: suspend, take down, dismiss, ban. Approvals, rejections and refunds show no button. A confirmation says what will happen; the server refuses (plain sentence) if the state already changed.
- **Swipe to dismiss.** In Reports, swipe a waiting row sideways to dismiss it (all waiting reports in that row). A snackbar **Undo** reopens them. The long-press menu stays as the no-gesture way.
- **Server.** `supabase/migrations/032_admin_undo.sql`: `admin_unsuspend`, `admin_relist`, `admin_reopen_report` (admin only, audit row each). **Run it before the Undo buttons work.**
- "What I did" now has labels for the three new actions.
- Fix: `AdminRowAdapter.isEmpty()` is now `public` with `@Override` (a weaker access does not compile).
- Version 2.5.0 (versionCode 20500). Not compiled or run in the sandbox that made this change.
