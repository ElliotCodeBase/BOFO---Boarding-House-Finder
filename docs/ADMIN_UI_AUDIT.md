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
