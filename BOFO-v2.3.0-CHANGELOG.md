# v2.3.0 - Phone notifications: chats, boarding group chats, admin reports

The app now notifies the phone like other apps do: a banner in the status bar / lock screen **even when BOFO is closed**. Tap = open the right screen.

## Who gets what
| Account | Notification | Tap opens |
|---|---|---|
| Student, Owner | new message in a student <-> owner chat | that chat |
| Student, Owner | new message or announcement in a boarding group chat (muted members and your own messages are skipped) | the group chat (owner: the group chat of that house, student: Boarding tab) |
| **Admin only** | a new user report (reason only, lock-screen safe) | Reports tab |

Admin-only is enforced three times: the server sends report pushes only to tokens of users whose `users.role = 'admin'` (read at send time), the phone drops a report push unless the account signed in on it is an admin, and sign-out removes the phone's token (and deletes the Firebase token on the phone).

## How it works
Database trigger (migration 029) > edge function `send-push` > Firebase Cloud Messaging > `BofoMessagingService` on the phone > notification. Messages are data-only, so the app draws them itself: one notification per chat that grows with the newest messages (up to 6), separate Android channels (Messages, Boarding group chats, Announcements, Reports (admin)), no banner for the chat you are looking at, blocked senders skipped.

## What changed
- **New:** `service/PushService`, `app/BofoMessagingService`, `util/PushNotifier`, `util/PushRules`, `util/PushState`, `ui/PushRouter`, `res/values/strings_push.xml`, `ic_stat_bofo.xml`, `ic_set_bell.xml`, `PushRulesTest` (8 tests), `supabase/functions/send-push`, `supabase/migrations/029_push_notifications.sql`, `docs/PUSH_SETUP.md`.
- **Changed:** `app/build.gradle` (2.3.0 / 20300, Firebase BoM + messaging, google-services plugin only when `google-services.json` exists), root `build.gradle`, `AndroidManifest.xml` (POST_NOTIFICATIONS, service), `MainActivity` (notification tap), `ShellFragment` (permission prompt once, sync, open from push), `AuthService` (sign-out removes the phone), `AuthRouter`, `ChatFragment` / `BoardingChatFragment` (open-chat tracking), `SettingsFragment` + `fragment_settings.xml` (Notifications switch), `AppPrefs`, `BofoApp`.
- Settings > **Notifications** switch (default on). Android 13+ asks the system permission once after sign-in.

## Needs you (one time)
`docs/PUSH_SETUP.md`: Firebase project + `google-services.json`, two Supabase secrets, deploy `send-push`, run migration 029 + one insert. **Nothing was applied to Supabase.** Without these steps the app works exactly as v2.2.6.

## Not run
Not built or run by Claude (no Android SDK, no Firebase project, no phone). Checked: Java syntax of every changed file, `PushRulesTest` compiled and run (8/8 pass), XML parsed. Please build once and test with the table in `docs/PUSH_SETUP.md`.

## Known limits
- Needs Google Play services on the phone (normal phones; emulator needs a "Google Play" image).
- Notifications are not sent for messages from muted/banned senders only where the server rules already block the message; a suspended recipient still gets banners (the app opens the suspended screen).
- No per-category switches inside the app yet: use Android's own notification settings for BOFO (channels).
- The JavaFX desktop app has no push.
