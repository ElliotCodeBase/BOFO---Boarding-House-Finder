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

# v2.4.13 - Admin lists and Reports: filters, search, grouping, clearer states (Admin UI step 4 of 5)
- **Queue, Reports and Payments** now share one look: filter chips **Waiting / Done / All**, a search box, and a status in words on a coloured pill (Waiting, Approved, Rejected, Paid, Failed, ...). Waiting items are shown oldest first, with "Waiting 3 days".
- **Loading and errors.** A skeleton shows while a list loads for the first time. If you are offline (or a refresh fails) you keep the last list and see a banner; with nothing to show you get a plain sentence and a **Try again** button.
- **Reports.** Each row shows the reported place's photo, its name and the start of what was written. Waiting reports about the same person and place are grouped into one row ("2 reports"); the details list the others. When you suspend, ban or dismiss a grouped row, the other waiting reports in it are marked handled too. The title still opens the finer filters (Suspended, Dismissed, Taken down, Banned users).
- **Admin Home.** Cards now open the right list already filtered: IDs > Queue Waiting, Reports > Waiting, Payments > Waiting (pending or failed), Suspended > Reports answered with a suspension, Taken down > Reports answered with a take down.
- How to get there: Admin > Home > any card, or the Queue / Payments / Reports tabs.
- If something goes wrong: pull down to refresh, or tap Try again. A grouped row whose other reports could not be marked handled shows them again after the refresh.
- Version 2.4.13 (versionCode 20413). No server change. Not compiled or run on a device in the sandbox that made this change.
