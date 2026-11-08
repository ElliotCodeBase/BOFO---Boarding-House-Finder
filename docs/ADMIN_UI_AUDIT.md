# Admin UI audit (v2.4.10, step 1 of 5)
Read from the code, not run on a device. One line each: problem -> file.

## Confusing
- Admin landed straight on Queue: no overview of what else needs action -> `ShellFragment.tabsFor`. (Fixed in step 1: Admin Home.)
- Reports filter is the screen title ("tap the title"): nothing says it is tappable -> `ReportsFragment`.
- Tab order Queue / Payments / Reports puts payments between two moderation tabs -> `ShellFragment.tabsFor`.
- Nothing on screen says "you are in Admin mode" except the tab names -> `ShellFragment`. (Step 1: role tag.)
- Two-line plain rows show owner name + ID type + date, never how long it has waited -> `QueueFragment`.
- Admin switches (AI for users) have no one-line explanation -> `ProfileFragment`. (Step 5.)

## Slow
- Reviewing an ID needs ID, selfie and a typed ID number across dialogs, buttons at the top of the screen -> `VerificationReviewFragment`. (Step 2.)
- A report opens as a text dialog; the reported listing or message must be opened in other screens -> `ReportsFragment`. (Step 4.)
- Report actions are 2 dialogs deep (Actions > duration > reason > confirm) -> `ReportsFragment.actions`. (Step 3.)
- Lists have no search or Waiting/Done filter; Payments shows the latest 100 unfiltered -> `fragment_admin_list.xml`, `AdminPaymentsFragment`. (Step 4.)
- No badge anywhere: the admin must open each tab to see if there is work -> `ShellFragment.refreshBadge` returned early for admins. (Step 1: Queue + Reports badges.)

## Risky
- Reject of an ID has no quick reason chips, so reasons are free text or empty -> `VerificationReviewFragment.decide`. (Step 2.)
- Ban has a clear confirmation, but dismiss / suspend / take down confirmations are not consistent; none has Undo -> `ReportsFragment`. (Step 3.)
- Errors for lists are a short toast that disappears; no retry button -> `QueueFragment.reload`, `ListArea.toast`. (Step 4.)
- Offline looks the same as "empty list" after a failed load (list keeps old rows, only a toast) -> `QueueFragment`, `AdminPaymentsFragment`. (Step 4.)
- No record visible to the admin of what they did (audit_logs exists server-side for take-down only) -> step 5.

## Status after step 5 (v2.4.14)
Fixed: Admin Home + badges, review screen, confirmations + ban Undo, long-press, chips/search/offline/retry, grouped reports with inline place, activity log, plain error sentences, hints under admin switches, admin banner.
Not done: Undo for suspend / take down / dismiss (needs new server functions), auto-lock after inactivity, AI screens on the new list look, Banned users search unchanged.

## Status after the next-steps series (v2.9.0)
Added: Undo + Recently decided (v2.5.0), admin push (v2.6.0), 8 reject reasons + second-opinion flag on IDs (v2.7.0), Last 7 days card, reports about a person, CSV export (v2.8.0), auto-lock (v2.9.0).
Not done: moderator role split (needs migration 035 and the owner's OK), Approve clean (bulk approve), saved own reject templates, second-opinion flag on reports, AI screens on the shared list look.

## Final status (v2.11.0, Stage 5)
**Done:** the in-app admin was removed from BOFO Finder; its screens exist only in the Admin app (separate icon, own push channels, fingerprint / two-step on sensitive actions). The old screens and their problems listed above are gone with them.

**Checked in the Admin app code (not on a device):** Undo exists for a ban (`ReportsScreen`, needs the `unban` permission). I did NOT re-verify Undo for suspend / take down / dismiss, "Recently decided", or a retry row after a failed load in the Admin app; treat them as unconfirmed until tested.

**Still open (never run on a device):**
- Small phone (320 dp) and 200% font were not tested: long names in Team / Appeals rows may be cut.
- The Admin app screens are built in code (no layout XML) and almost none sets a content description (only `IdScreen` does): TalkBack will read unlabeled icon buttons badly. Real gap for accessibility.
- Banned users search is still a plain contains-match.
- Bulk approve and saved own reject templates were never built.
- An admin account that opens BOFO Finder sees a plain student app with no hint to use the Admin app.

## Stage 5 result (v2.12.2) - fixed in code, NOT run with TalkBack on a device
| Item | Status | Where |
|------|--------|-------|
| Icon buttons without label | Nothing to fix: the admin app has no icon-only buttons. Bottom tabs use text titles; the only image (`IdScreen`) has a description | READ |
| Headings for TalkBack navigation | Fixed: Title and Section texts are headings | `Ui.text` |
| Tappable cards read as plain text | Fixed: announced as buttons | `Ui.asButton` (Appeals, IDs, More, Payments, Reports, Team, Export) |
| Filter chips: selected state only by colour | Fixed: "Selected / Not selected" spoken | `Ui.chip` (Reports, Appeals, AI chats, AI needs attention) |
| Touch targets under 48 dp | Fixed: every button is at least 48 dp tall. Cards are larger | `Ui.button` |
| Long-press only action (delete saved reply) | Fixed: also a TalkBack custom action | `IdScreen.fillChips` |
| Screen change not announced (login, no access, locked, two-step) | Fixed: pane title from the first heading | `MainActivity.show` |
| State changes (approved, rejected, banned, locked) | Already spoken: every one shows a Toast or Snackbar, which TalkBack reads. Not heard on a device | READ |
| Text contrast | Fixed: success on tint 3.60 to 5.63, error on tint 3.97 to 5.67, error on bg 4.27 to 6.11 (admin `colors.xml`); all 12 used pairs now 4.5+ | `tools/verify/check_a11y.py` RAN |
| Large font clips button rows | Fixed: ID review and Approvals buttons stack at font scale 1.3+; Reports chips scroll sideways. Other rows already wrap or scroll | `Ui.actions` |
| Focus order | Not changed: views are added top to bottom, so reading order = visual order. Not heard on a device | READ |
| Sub-screens in More: focus stays on the card after opening one | NOT fixed (backlog): needs a focus move to the new screen's first heading | `MoreScreen.sub` |
| 320 dp wide phone, long names in Team / Appeals | NOT checked (needs a device) | sheet row A6 |
| Banned users search is a plain contains-match; bulk approve; saved reject templates | NOT done (features, not accessibility) | |
