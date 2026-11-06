# v2.9.14 - BOFO Admin app: two-step sign-in setup
- Anyone on the admin team can turn on two-step sign-in inside the admin app (More > Two-step sign-in, or from the "No access" screen when the server requires it): add the key to an authenticator app, type the 6-digit code. Needed because Stage 5 removes the old enrolment screen from the user app.
- No migration. Version 2.9.14 (versionCode 20914). Not compiled or run in the sandbox that made this change.

# v2.9.13 - BOFO Admin app: AI chats and AI settings
- Owner: More > AI chats (read what people asked BOFO, search, 24 h / 7 days / all, 30 at a time) and More > AI settings (switch BOFO AI on/off for everyone, see if the chat monitor is on, delete all stored chats after typing DELETE). Changes ask for fingerprint / screen lock.
- No new migration. Version 2.9.13 (versionCode 20913). Not compiled or run in the sandbox that made this change.


# v2.9.12 - BOFO Admin app: refunds
- Owner can tap a PAID payment in Admin > More > Payments to mark it refunded (reason, two confirmations, fingerprint). It ends the boost; the money is still refunded in PayMongo.
- Migration `043_refund_hardening.sql` (run it): only a paid payment can be refunded, reason required.
- Version 2.9.12 (versionCode 20912). Not compiled or run in the sandbox that made this change.


