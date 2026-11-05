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

# v2.4.14 - Admin clarity, trust and accessibility; admin guide (Admin UI step 5 of 5)
- **What I did.** New last card on Admin Home: your own last 50 actions (what you did, to whom or what, and when).
- **Plain messages.** Admin error messages are now short sentences ("Someone already handled this.") instead of HTTP numbers and codes.
- **Hints.** A one-line explanation under Two-step verification, View the app as..., and the BOFO AI switch (Settings, admins only).
- **Admin banner.** A thin amber strip at the top always says "Admin mode - signed in as ...".
- **Accessibility.** Screen-reader descriptions on cards and rows, 48 dp targets on the new controls, no skeleton pulse and no tab fade when system animations are off.
- **Docs.** `docs/ADMIN_GUIDE.md` (one page + test checklist).
- How to get there: Admin > Home > What I did; Settings (admin) for the hints.
- If something goes wrong: if What I did says it could not load, the admin account may not be allowed to read `audit_logs`; tell the developer (no data is lost).
- Version 2.4.14 (versionCode 20414). No server change. Not compiled or run on a device in the sandbox that made this change.
