# Phone notifications: one-time setup (v2.3.0; admin pushes added in v2.6.0)

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

## Admin pushes: ID reviews and the daily reminder (v2.6.0, migration 033)
Both go only to accounts whose `users.role = 'admin'` (the function checks this when it sends; the phone drops them too). Lock screen shows the type or a count only: no names, no ID data.
| Push | When | Tap opens |
|---|---|---|
| "New ID to review" (`idreview`) | an owner submits an ID | Queue, Waiting filter |
| "N items have been waiting more than 3 days" (`stale`) | once a day, only if N > 0 (IDs + reports + payments, same meaning of "waiting" as Admin Home) | the tab with the most waiting items (Queue, Reports or Payments), Waiting filter |

**Set up (once):**
1. Redeploy the function (it has two new kinds): `supabase functions deploy send-push --no-verify-jwt --project-ref zfxldzcoirqolflkkhrh`
2. SQL editor: run `supabase/migrations/033_admin_push.sql` (needs 029 already done and `push_settings` filled in, see step 5 above).
3. Daily reminder: Supabase > Database > Extensions > turn on **pg_cron**, then run once (1:00 UTC = 9:00 in the Philippines; change the hour if you like):
```sql
select cron.schedule('bofo-stale-push', '0 1 * * *', $$select public.push_stale_check()$$);
```
Stop it with `select cron.unschedule('bofo-stale-push');`. The cron job calls the same `push_notify` as the triggers, so it uses the URL and secret already in `push_settings` (no second secret).

**Test it (SQL editor, signed-in admin phone with the app open once so its token is registered):**
| Run | Expected on every admin phone |
|---|---|
| `select public.push_notify('test', '{"as":"idreview"}'::jsonb);` | "Test: New ID to review". Tap: Queue with Waiting |
| `select public.push_notify('test', '{"as":"stale"}'::jsonb);` | "Test: Items are waiting". Tap: Queue |
| `select public.push_notify('test', '{"as":"report"}'::jsonb);` | "Test: New report". Tap: Reports |
| `select public.push_stale_check();` | the real reminder, but only when something has waited more than 3 days |
| An owner account submits an ID | "New ID to review" on admin phones only (not the owner's phone) |
Student and owner phones must show nothing for any of these. Chats, groups and reports keep working as before. Android: Settings > Notifications > BOFO shows the admin channels "Reports (admin)" and "ID reviews and reminders" once an admin has signed in.

## Admin app push (v2.9.11, migration 042)
The BOFO Admin app (`com.bofo.admin`) has its own Firebase app and its own device tokens (`device_tokens.app = 'admin'`). Admin pushes now go to staff **by permission**, not to everyone with `users.role = 'admin'`:
| Push | Who gets it | Tap opens |
|---|---|---|
| `report` | `reports_act` (not Support), never the reporter | Reports |
| `idreview` | `id_review`, never the owner who sent it | IDs |
| `stale` (daily) | anyone with `id_review`, `reports_act` or `payments_view`; each person is told only the counts they could act on | the tab with most waiting items |
| `approval` (new) | `approvals_decide` (Owner, Admin), never the requester, **admin app phones only** | Approvals |
Chats (`dm`, `group`) only go to user-app tokens. A person with the admin app installed gets admin pushes there only; without it the user-app token is used. A phone registered before "log out everywhere" gets nothing.

**Set up (once, in this order):**
1. Firebase: add an Android app `com.bofo.admin` (and `com.bofo.admin.debug`), put its `google-services.json` in `app-admin/` (git-ignored). Same Firebase project / service account as the user app.
2. SQL editor: run `supabase/migrations/042_admin_push.sql` (needs 029, 033, 039).
3. Redeploy: `supabase functions deploy send-push --no-verify-jwt` (if you deploy first, it falls back to the old admin rule until 042 is applied).
4. Open the admin app once, signed in: Android 13+ asks for notification permission; the token registers after the role check.

**Test it (admin app signed in, then closed):** `select public.push_notify('test', '{"as":"approval"}'::jsonb);` (also `idreview`, `report`, `stale`). Expect "Test: ..." on staff phones that hold the permission. Real approval: a Moderator account taps "Request approval" on a ban; Owner/Admin phones get "A ban is waiting for your decision".
Undo: top of `042_admin_push.sql`.

## Test one phone (v2.9.2, any account type)
Open the app once signed in, close it completely, then in the SQL editor:
```sql
select public.push_notify('test', jsonb_build_object('as','me','user_id',(select user_id from public.device_tokens order by updated_at desc limit 1)));
select status_code, left(content,200) as answer, error_msg, created from net._http_response order by created desc limit 3;
```
The answer tells what is wrong (`no_device_token`, `unauthorized`, `FCM_SERVICE_ACCOUNT is not set`, ...). The full table of answers and fixes: `docs/NOTIFICATION_FIX_v2.9.2.md`.

## If nothing arrives
1. App: Settings > Notifications is on and Android's notification permission for BOFO is allowed.
2. SQL: `select user_id, left(token, 12), updated_at from public.device_tokens;` has a row for your account (it appears a few seconds after the app opens).
3. SQL: `select * from net._http_response order by created desc limit 5;` shows what the function answered (200 = ok, 401 = secrets do not match).
4. Supabase > Edge Functions > send-push > Logs: "FCM refused ..." lines say why Google refused (wrong service account / project).
5. `google-services.json` must belong to the same Firebase project as the service account.

## Undo
The undo SQL is at the top of `029_push_notifications.sql`. Delete `google-services.json` to build the app without push.
