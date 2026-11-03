# Phone notifications: one-time setup (v2.3.0)

The app code is done. Notifications start working after these 6 steps. Until step 1 is done the app builds and runs exactly as before (no Firebase = push is skipped).

## 1. Firebase (gives the app its notification address)
1. https://console.firebase.google.com > create a project (or open yours) > **Add app > Android**, package name `com.bofo.finder`.
2. Download `google-services.json` and put it in `bofo-android/app/` (next to `app/build.gradle`). It is already in `.gitignore`: do not commit it.
3. Android Studio: **File > Sync Project with Gradle Files**.

## 2. Firebase > service account (lets Supabase send the notification)
Firebase console > Project settings > **Service accounts > Generate new private key** > a `.json` file. Keep it private, never commit it.

## 3. Supabase secrets (Dashboard > Edge Functions > Secrets, or `supabase secrets set`)
| Name | Value |
|---|---|
| `FCM_SERVICE_ACCOUNT` | the whole content of the service-account `.json` (paste it as is) |
| `PUSH_WEBHOOK_SECRET` | a long random string you make up (30+ characters). You need the same string in step 5 |

## 4. Deploy the function
```
supabase functions deploy send-push --no-verify-jwt --project-ref zfxldzcoirqolflkkhrh
```
(`--no-verify-jwt` because the database calls it with the secret header, not with a user login.)

## 5. Database
Supabase > SQL editor:
1. Run `supabase/migrations/029_push_notifications.sql` (needs 022 and 024 to be live already).
2. Then run this once, with YOUR secret from step 3:
```sql
insert into public.push_settings (key, value) values
  ('url',    'https://zfxldzcoirqolflkkhrh.supabase.co/functions/v1/send-push'),
  ('secret', 'PASTE-THE-SAME-RANDOM-STRING')
on conflict (key) do update set value = excluded.value;
```
Until step 5.2 is done the triggers do nothing (messages and reports work as always).

## 6. Try it (two phones, or one phone + the Supabase dashboard)
Use a phone or emulator **with Google Play services** (an emulator image "with Google Play").
| Test | Expected |
|---|---|
| Student A and owner B signed in on two phones. A sends a chat message, B's app is closed | B's phone shows a notification "A: message". Tap: opens that chat |
| B's phone has that chat open | no banner (you are already looking at it) |
| Owner posts in the boarding group / posts an announcement | every other member gets "House name" + the message / the pinned announcement |
| A student reports a user | **only phones signed in with an admin account** show "New report" (reason only). Student / owner phones show nothing |
| Admin signs out, then a report is sent | that phone shows nothing (sign-out removes its token) |
| Settings > Notifications off | that phone stops getting notifications; on again: it gets them again |
| Muted member / blocked sender | no notification |

## If nothing arrives
1. App: Settings > Notifications is on and Android's notification permission for BOFO is allowed.
2. SQL: `select user_id, left(token, 12), updated_at from public.device_tokens;` has a row for your account (it appears a few seconds after the app opens).
3. SQL: `select * from net._http_response order by created desc limit 5;` shows what the function answered (200 = ok, 401 = secrets do not match).
4. Supabase > Edge Functions > send-push > Logs: "FCM refused ..." lines say why Google refused (wrong service account / project).
5. `google-services.json` must belong to the same Firebase project as the service account.

## Undo
The undo SQL is at the top of `029_push_notifications.sql`. Delete `google-services.json` to build the app without push.
