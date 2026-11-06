# STATE after Stage 4, part 5 (admin app 2.9.12 / 20912): refunds

Builds on STATE_STAGE4d.md (push, migration 042 may still be unapplied).

## Done
- **Payments screen:** with `payments_manage` (Owner) a PAID payment shows "Tap to refund": reason (5+ chars) -> confirmation with amount and reason -> fingerprint / screen lock -> `admin_refund_boost`. Only the bookkeeping: status 'refunded' + the boost ends + audit row. The dialogs say the money itself is refunded in PayMongo. Screen reloads after success; the server's error sentence is shown when it refuses.
- **Migration `043_refund_hardening.sql` (written, NOT applied; needs your OK):** same function, now also requires a reason of 5+ characters, locks the row, and refuses anything but status 'paid' (before it marked any payment refunded). Run after 040. Works without it too (the app only offers paid ones), but then the server does not stop a hand-made call.
- `shared/RefundRules` (+ test): canRefund, cleanReason, validReason. `PaymentsScreen` now takes `Access`; `MoreScreen` updated. Version 2.9.12 in both `build.gradle`.

## What was and was NOT run
- NOT run: Gradle, Java tests (no JDK/JUnit here), the SQL, a phone. Expect possible small compile errors: send me the first one.

## How to test (Owner)
1. More > Payments: a paid row says "Tap to refund"; pending / failed / refunded rows do not. Admin / Moderator / Support: no Payments entry at all.
2. Refund with a short reason: refused on the phone. With a good one: lock prompt, then "Marked as refunded", row turns refunded, the boost is over in the user app. More > All admin activity shows `boost_refunded`.
3. After 043: in the SQL editor, calling it on a pending payment answers "Only a paid payment can be refunded".

## NOT done (backlog)
AI chat log + AI settings in the admin app; strikes ladder, Escalate, queue claiming, saved replies, unusual-activity alert, session list + revoke, break-glass, appeals (migrations); push when an approval is decided; two-step enrolment in the admin app; then Stage 5.
