# Remove the AI Chats monitor (added in v2.0.91)

Everything for the feature lives in its own files, plus a few small hook points. Do the steps in this order.

## 1. Turn the switch off (stops storing, hides the admin entry)
Supabase > SQL Editor:
```sql
update public.app_flags set value = false, updated_at = now() where key = 'ai_chat_monitor_enabled';
```
Within about 30 seconds the `bofo-chat` function stops writing rows, and the admin Profile no longer shows the "AI Chats (admin)" button. If you only want to pause the feature, stop here (turn it back on with `value = true`).

## 2. Delete the stored data and the database parts
Open `supabase/migrations/014_ai_chat_log_REMOVE.sql`, read it, and run it once in the SQL Editor.
It permanently deletes every stored AI chat, the cron job `ai-chat-log-retention`, the policy, both indexes, the functions `ai_chat_log_wipe` and `ai_chat_log_purge`, the table `ai_chat_log`, the flag row, and the `app_flags` table too if nothing else is in it.
(Kept on purpose: `audit_logs` rows with action `ai_chat_wipe`.)

## 3. Delete these files (all new in v2.0.91)
```
supabase/migrations/014_ai_chat_log.sql
supabase/migrations/014_ai_chat_log_REMOVE.sql
REMOVE_AI_CHATS.md
bofo-android/app/src/main/java/com/bofo/finder/ui/AiChatLogFragment.java
bofo-android/app/src/main/java/com/bofo/finder/ui/AiChatLogAdapter.java
bofo-android/app/src/main/java/com/bofo/finder/service/AiChatLogService.java
bofo-android/app/src/main/java/com/bofo/finder/util/AiChatRules.java
bofo-android/app/src/test/java/com/bofo/finder/util/AiChatRulesTest.java
bofo-android/app/src/main/res/layout/fragment_ai_chat_log.xml
bofo-android/app/src/main/res/layout/item_ai_chat.xml
bofo-android/app/src/main/res/layout/item_ai_chat_msg.xml
bofo-android/app/src/main/res/layout/item_ai_chat_skeleton.xml
bofo-android/app/src/main/res/values/strings_ai_chats.xml
```
All strings of the feature are in `strings_ai_chats.xml`, so `strings.xml` needs no edit.

## 4. Remove the hook points (small edits in existing files)
Search each file for `v2.0.91` or `AI Chats` to find the spot.

| File | What to delete |
|---|---|
| `bofo-android/app/src/main/res/layout/fragment_profile.xml` | the `<Button android:id="@+id/ai_chats_open" ...>` and the comment above it |
| `bofo-android/app/src/main/java/com/bofo/finder/ui/ProfileFragment.java` | the one line `AiChatLogFragment.attachEntry(this, binding.aiChatsOpen, p.role());` in `load()` |
| `bofo-android/app/src/main/res/navigation/nav_graph.xml` | the `<fragment android:id="@+id/aiChatLogFragment" ...>` destination and the comment above it |
| `bofo-android/app/src/main/java/com/bofo/finder/ui/LegalText.java` | the line starting `+ "BOFO AI chats\n...` in the PRIVACY text (the section before "Your choices") |
| `bofo-android/app/src/main/java/com/bofo/finder/ui/BofoChatOverlay.java` | in `open()`, the call `say(getContext().getString(R.string.ai_chats_notice), false);` (keep the `bofo_hello` call and `addStarters()`) |
| `supabase/functions/bofo-chat/index.ts` | the whole block from the comment line containing `AI Chats monitor (v2.0.91)` down to the comment line containing `end of AI Chats monitor`, and the two `logAfterAnswer(admin, u.user, message, ...)` lines (one just before the private-info refusal `return json(...)`, one just before the final `return json({ reply, intent, ...`) |

Then redeploy the function: `supabase functions deploy bofo-chat`.

## 5. Check
- Build the app (`gradlew assembleDebug`) and run the unit tests; no leftover reference to `AiChatLogFragment`, `AiChatRules`, `AiChatLogService`, `ai_chats_*` or `aiChatsOpen` should remain (search the repo).
- Sign in as an admin: Profile shows no "AI Chats" button. Ask BOFO a question as a student: no row is written (the table no longer exists).
- Release the app update. Users on older versions are unaffected (they never had the screen).

## What the privacy sentences said (removed in step 4)
- Privacy policy: "Conversations with the BOFO AI assistant ... are stored and can be reviewed by BOFO administrators, and they are deleted automatically after 90 days."
- Chat first-open bubble: "Conversations with the BOFO AI are stored and can be reviewed by admins."
If you bumped `policy.privacy.version` when adding the feature, decide whether to bump it again.
