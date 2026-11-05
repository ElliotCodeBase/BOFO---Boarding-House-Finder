# BOFO admin guide (v2.4.14, one page)

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
Two-step sign-in is kept (Settings). Screenshots are blocked on the ID review screen. Reasons you type may be shown to the person, so keep them short and polite.

## Test checklist (tick on a device)
- [ ] Admin Home counts match the real lists; each card opens the right filtered list.
- [ ] Approve, reject with reason, ban, take down, ban Undo: each works, shows a clear result, and survives going offline mid-action (clear message, nothing half done).
- [ ] Owner and student accounts never see any admin screen, banner or count.
- [ ] Light and dark theme; small phone (360 dp wide); large font size (200%).
- [ ] Long-press menus on Queue, Reports, Payments; plain tap still works.
- [ ] Skeleton, offline banner and Try again on each list (turn on airplane mode).
- [ ] "What I did" shows the actions just taken (needs the admin to be allowed to read `audit_logs`).
- [ ] With system animations off: no skeleton pulse, no tab fade.
