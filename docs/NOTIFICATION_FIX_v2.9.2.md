# Notification fix v2.9.2: a message to me pops up while the app is closed

## 1. The problem
A chat message was sent to you while the BOFO app was closed and no notification appeared.

## 2. What I found (ranked, most likely first)
I could not run a phone or look at your live Supabase from here, so these are the causes the code and setup allow. Step 5 tells which one you have in one test.

| # | Cause | Where |
|---|---|---|
| 1 | The server sent a **data-only** push. Nothing is drawn until the app's own code wakes up and builds the notification. Battery savers (Xiaomi, Oppo, Vivo, Realme, Infinix, Tecno, Samsung "sleeping apps") and Android's priority demotion often stop that when the app is closed. | `send-push/index.ts` |
| 2 | **The app dropped the push silently** when its saved account role was empty (the role is only saved after the first successful sync). No log, no banner. | `PushNotifier.showNow` |
| 3 | **Setup gaps** in the one-time setup: no `google-services.json` in the build, `push_settings` empty, `PUSH_WEBHOOK_SECRET` different from `push_settings.secret`, function not deployed with `--no-verify-jwt`, wrong service-account JSON. | `docs/PUSH_SETUP.md` |
| 4 | The app was **force-stopped** (Settings > Apps > BOFO > Force stop) or the phone blocks auto-start. Android delivers nothing to a force-stopped app. | phone settings |

## 3. Solutions I considered
| Option | Result |
|---|---|
| A. **Hybrid message**: server sends data **and** a ready-made notification; the phone's system draws it | **Chosen.** Works when the app code never runs. Needs only a function redeploy to start working. |
| B. Keep data-only, harden the app (remove the silent drop, log every drop) | **Also done**, it is cheap. Does not help when the phone never starts the app. |
| C. Ask the user for battery "No restrictions" / Autostart (in-app prompt) | Documented in section 7. No in-app prompt yet (Play policy and layout work; ask if you want it). |
| D. Poll with WorkManager or run a foreground service | Rejected: drains battery, Android kills it, and Play dislikes it. |
| E. Other push provider (OneSignal etc.) | Rejected: same Android limits, new account and keys, no gain. |

## 4. What I changed (v2.9.2, versionCode 20902)
| File | Change |
|---|---|
| `supabase/functions/send-push/index.ts` | Hybrid FCM message (title, body, channel, tap action, private lock screen for admin pushes). Answers with a reason. New test `as:"me"`. Accepts a key pasted with line breaks. |
| `.../util/PushNotifier.java` | A signed-in phone with no saved role still shows chats. Every dropped push is logged. |
| `.../ui/PushRouter.java` | A tap on a system-drawn notification opens the right chat / group / admin tab. |
| `.../util/PushRules.java` + `PushRulesTest.java` | New kind `test` (any role may see it) + a unit test. |
| `AndroidManifest.xml` | `OPEN_PUSH` intent-filter on `MainActivity`; notification colour. |
| `app/build.gradle`, `CHANGES_ANDROID.md`, `HANDOFF_ANDROID.md`, `docs/PUSH_SETUP.md` | Version bump and docs. |

No database migration. Nothing was changed on your live Supabase.

**Behaviour change to know about:** while the app is closed, each message is its own notification (the grouped chat stack and the "this chat is already open" check only apply while the app is open). The server still skips blocked senders, muted group members and non-admins for admin pushes.

## 5. How to apply and test
1. **Redeploy the function first** (this alone fixes the closed-app case, even for the app version you have installed):
   ```
   supabase functions deploy send-push --no-verify-jwt --project-ref zfxldzcoirqolflkkhrh
   ```
2. Apply the app change (PowerShell):
   ```
   git checkout main; git pull; Expand-Archive "$HOME\Downloads\BOFO-android-v2.9.2-changes.zip" "C:\Users\user\bofo-finder" -Force
   cd C:\Users\user\bofo-finder; git checkout -b fix-push-v2.9.2; git add -A; git commit -m "v2.9.2: notifications while the app is closed"; git push -u origin fix-push-v2.9.2; gh pr merge
   ```
   Then build and install (Android Studio, or `gradlew installDebug`). The Settings screen shows **2.9.2**.
3. Open the app, signed in, allow notifications. Wait 10 seconds. **Swipe the app away** (do not Force stop).
4. Supabase > SQL editor, run:
   ```sql
   select public.push_notify('test', jsonb_build_object('as','me','user_id',(select user_id from public.device_tokens order by updated_at desc limit 1)));
   select status_code, left(content,200) as answer, error_msg, created from net._http_response order by created desc limit 3;
   ```
   Expected: a "BOFO test notification" on the phone and `{"sent":1,...}`.
5. Real test: second account sends you a chat message while your app is closed. A banner with the sender's name and the message appears. Tap it: that chat opens.

## 6. If a problem occurs (read the `answer` column from step 4)
| Answer | Meaning | Fix |
|---|---|---|
| no row at all | The database never called the function | `select key from public.push_settings;` must show `url` and `secret`: run step 5.2 of `PUSH_SETUP.md`. Check the trigger: `select tgname from pg_trigger where tgname in ('push_dm','push_group');` (empty = run `029_push_notifications.sql`). |
| `401 unauthorized` | `push_settings.secret` is not the same as the function secret `PUSH_WEBHOOK_SECRET` | Set both to the same string (Dashboard > Edge Functions > Secrets, and the `insert ... on conflict` in `PUSH_SETUP.md`). |
| `404` | Function not deployed or wrong URL | Redeploy (step 1). The URL must be `https://zfxldzcoirqolflkkhrh.supabase.co/functions/v1/send-push`. |
| `error: FCM_SERVICE_ACCOUNT is not set` | Secret missing or not valid JSON | Paste the whole service-account `.json` as the secret value, then redeploy. |
| `error: FCM auth failed ...` | Wrong or revoked service account | Firebase > Project settings > Service accounts > Generate new private key, replace the secret. |
| `{"sent":0,"reason":"no_device_token"}` | This phone never registered | Settings in the app: Notifications on and allowed in Android. Settings says "not set up in this build" = `google-services.json` is missing: put it in `bofo-android/app/` and rebuild. Check: `select user_id, left(token,12), updated_at from public.device_tokens;` |
| `{"sent":0,"reason":"nobody_to_notify"}` | Blocked sender, unknown conversation, or you messaged yourself | Test with the `as:"me"` query above to rule out the chat itself. |
| `{"sent":0,"removed":1}` | The phone's token was dead and was deleted | Open the app once (signed in): it registers a new token. |
| `"failed":1,"why":"FCM 403 ..."` | Firebase refused. "SENDER_ID_MISMATCH" = `google-services.json` and the service account belong to different Firebase projects. "API not enabled" = enable **Firebase Cloud Messaging API (V1)** in Google Cloud for that project | Fix the project match / enable the API, no redeploy needed. |
| `"failed":1,"why":"FCM 400 ..."` | Bad message field | Send the `why` text to me. |
| `{"sent":1,...}` but the phone shows nothing | Google delivered it, the phone hid it | Section 7. |

## 7. Phone settings (most common reason in practice)
- **Do not Force stop.** Swiping from recent apps is fine; Force stop blocks everything until you open the app again.
- Android: Settings > Apps > BOFO > Notifications: on (all channels). Settings > Apps > BOFO > Battery: **Unrestricted** / "No restrictions".
- Xiaomi / Redmi / POCO: Security > Permissions > Autostart: BOFO on; Battery saver: No restrictions; lock BOFO in recent apps.
- Oppo / Realme / Vivo / Infinix / Tecno: allow Auto-launch / Background activity for BOFO; turn off "Smart power saving" for it.
- Samsung: Battery > Background usage limits > remove BOFO from "Sleeping apps".
- Need to see why the app itself dropped a push? Android Studio Logcat, filter `BOFO`: lines "push not shown: ..." name the reason.

## 8. Undo
Redeploy the previous version of `send-push/index.ts` from git (`git checkout <old commit> -- supabase/functions/send-push/index.ts`, then deploy). The app change is harmless without the server change and the other way round.

## 9. Not verified
Nothing was run on a phone. The app code was not compiled in the sandbox that made it (no Android SDK); the edge function passed a TypeScript type-check only. Run `gradlew testDebugUnitTest assembleDebug` and the test in section 5.
