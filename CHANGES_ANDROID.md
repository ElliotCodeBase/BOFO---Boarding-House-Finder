# v2.9.15 - BOFO Admin app: saved replies for ID rejections
- Rejecting an ID request now has 8 friendly built-in replies (blurry photo, name differs, expired, selfie mismatch, unreadable number, missing back, wrong ID type, cropped). Tap one to fill the box, edit it, or write your own and "Save this as my reply" (up to 10, kept on your phone only; long-press to delete). Any reason of 5+ characters can be sent.
- No migration. Version 2.9.15 (versionCode 20915). Not compiled or run in the sandbox that made this change (the new rules test was run on plain Java: 7 of 7 pass).

# v2.9.14 - BOFO Admin app: two-step sign-in setup
- Anyone on the admin team can turn on two-step sign-in inside the admin app (More > Two-step sign-in, or from the "No access" screen when the server requires it): add the key to an authenticator app, type the 6-digit code. Needed because Stage 5 removes the old enrolment screen from the user app.
- No migration. Version 2.9.14 (versionCode 20914). Not compiled or run in the sandbox that made this change.

# v2.9.13 - BOFO Admin app: AI chats and AI settings
- Owner: More > AI chats (read what people asked BOFO, search, 24 h / 7 days / all, 30 at a time) and More > AI settings (switch BOFO AI on/off for everyone, see if the chat monitor is on, delete all stored chats after typing DELETE). Changes ask for fingerprint / screen lock.
- No new migration. Version 2.9.13 (versionCode 20913). Not compiled or run in the sandbox that made this change.
