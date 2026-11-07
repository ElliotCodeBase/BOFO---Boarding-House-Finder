# BOFO admin guide (v2.11.0)

> **v2.11.0: admin work happens only in the BOFO Admin app.** The student/owner app (BOFO Finder) has no admin tabs, no admin switches and no admin push any more. If an admin account signs in to BOFO Finder it just looks like a plain student account. The sections below that say "Home / Queue / Payments / Reports / Profile > Settings" describe the same screens as they are now in the Admin app (Home, Queue, Payments, Reports, More). Where the old text says "View the app as...", the two-step switch or the AI switch in Profile > Settings, use More > Two-step sign-in and More > AI settings in the Admin app.

## Who can do what (since v2.8, migrations 039-040)
You are staff only if you have a row in the team list. Roles: **Owner** (everything), **Admin** (day-to-day moderation and approvals, no team changes), **Moderator** (IDs and reports, short suspensions, asks for approval for bans and take-downs), **Support** (read-only help: dashboard, own audit). The Team screen shows each person's exact permissions. Since migration 045 (run only after old apps are retired) a plain `role = admin` flag on an account grants nothing on its own: the person must be in the team list.

## Safe habits
- Sign in only on your own phone with the fingerprint lock on. Use the two-step code. Never share the account.
- Escalate (do not guess) when a report is unclear. Use the Strike step instead of choosing a punishment yourself.
- A decision you take is in the audit log with your name. Lending a permission (break-glass) is for emergencies and lasts one hour.


## Where things are
- **Home** (first tab): cards with counts - IDs waiting, open reports, payments to check (pending or failed), unanswered AI questions (last 7 days), suspended users, banned users, listings taken down. "Oldest waiting 3 days" shows how long the oldest item has waited. Tap a card to open the list already filtered. Pull down to refresh. **What I did** (last card) lists your own last 50 actions.
- **Queue**: owner ID requests. Chips Waiting / Done / All, search by name. Tap = review. Press and hold = Open or Reject.
- **Payments**: boost payments. Waiting = pending or failed. Press and hold = Open or Refund.
- **Reports**: Waiting reports are oldest first; several waiting reports about the same person and place are one row. Tap the title for finer filters (Suspended, Dismissed, Taken down, Banned users).
- **Profile > Settings**: two-step sign-in, "View the app as...", and the BOFO AI switch, each with a one-line hint.
- The amber strip at the top always says "Admin mode" and the account you are signed in with.

## Reviewing an ID
Switch between ID front, ID back and selfie. Pinch to zoom, double-tap to reset. **Approve** asks you to type the ID number printed on the photo (the server compares it with what the owner typed). **Reject** asks for a reason chip (blurry, expired, face does not match, other) and an optional note.

## Decisions and Undo
- Ban, take down and refund show one clear confirmation that names the person, place or amount. Dismiss, reject and approve do not ask twice.
- **Undo (5 seconds) exists only for a ban.** Suspend, take down, dismiss, refund and ID decisions cannot be undone from the app (the server has no function for it). Unban lives in Reports > Banned users.
- A ban does not bring taken-down places back when you unban.

## Safety
Two-step sign-in is kept (Settings). After 5 minutes in the background the admin tools lock and ask for the 6-digit code again (switch in Settings; lost authenticator: "Sign out instead"). Screenshots are blocked on the ID review screen. Reasons you type may be shown to the person, so keep them short and polite.

## Test checklist (tick on a device)
- [ ] Admin Home counts match the real lists; each card opens the right filtered list.
- [ ] Approve, reject with reason, ban, take down, ban Undo: each works, shows a clear result, and survives going offline mid-action (clear message, nothing half done).
- [ ] Owner and student accounts never see any admin screen, banner or count.
- [ ] Light and dark theme; small phone (360 dp wide); large font size (200%).
- [ ] Long-press menus on Queue, Reports, Payments; plain tap still works.
- [ ] Skeleton, offline banner and Try again on each list (turn on airplane mode).
- [ ] "What I did" shows the actions just taken (needs the admin to be allowed to read `audit_logs`).
- [ ] With system animations off: no skeleton pulse, no tab fade.
- [ ] Auto-lock: leave the app 5+ minutes, come back: the code is asked, typed text is still there, a wrong code is refused, Sign out instead works; rotate while locked: still locked.
- [ ] Faster reviews: 8 reject reasons, second-opinion flag (needs migration 034), Last 7 days card, "All reports about them", Export CSV on Reports and Payments (opens in Excel, no names).

## New in v2.10.0 (migration 044)
- **Queue claiming:** opening an ID request or a waiting report takes it for 30 minutes; colleagues see "Being handled by <name>" and a warning when they open it. "Release" lets go sooner. It is a heads-up, not a lock: the server still lets either person decide.
- **Waiting colours:** green under a day, amber 1 to 3 days, red after 3 days (words and colour).
- **Escalate:** on a waiting report, "Escalate to a senior" (note required). Owner and Admins get a notification (no names in it); the report shows "Escalated" and the Escalated chip lists them. "Clear the escalation" needs approvals_decide.
- **Strikes ladder:** "Strike: next step" applies warning, then 3-day suspension, then 7-day suspension, then a permanent ban. The server picks the step and runs it through the normal report rules. A warning is a record for staff only (the user is not messaged). A moderator at the ban step is told to use Ban (request approval). Strikes never expire.
- **Appeals:** a banned or suspended user sees "Appeal this decision" (one per decision). More > Appeals: Owner / Admin with approvals_decide accept or reject; never the person who made the original decision. Lifting a ban still needs `unban` (Owner, or break-glass).
- **Break-glass (Owner):** Team > pick a person > "Lend a permission for 1 hour": ban, lift a ban, take down a listing, long suspension, payments view or CSV export only. Reason required, fingerprint asked, audit-logged; ends by itself or "End it now" in Team > "Lent right now".
- **Recent sign-ins:** Team > person > "Recent sign-ins" (phone model and time of each app start) with "Log out all devices".
- **Unusual activity (Owner):** More > Unusual activity; plus a notification when a non-owner does 10 or more bans, suspensions, take-downs, rejected IDs, unbans, refunds or strikes in one hour.
- **Approval decided:** the moderator who asked gets a notification "approved" / "denied".
