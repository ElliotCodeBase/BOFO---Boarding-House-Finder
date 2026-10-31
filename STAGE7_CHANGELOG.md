# BOFO Android v2.0.7 - Stage 7 changelog (chat)

Not opened or built in Android Studio / Gradle by Claude. Everything below was written and checked as far as a sandbox without Android allows (see "What was checked").

## What was added or changed

| File | What I did |
|---|---|
| `ui/ChatsFragment.java` + `res/layout/fragment_chats.xml`, `item_conversation.xml` | NEW. The Chats tab: inbox list, unread badge, time, pull to refresh, long-press to delete a chat from my list. |
| `ui/ChatFragment.java` + `res/layout/fragment_chat.xml`, `item_msg_mine.xml`, `item_msg_theirs.xml` | NEW. The chat screen: bubbles, ticks, paging, polling (3 s to 12 s, only while on screen), send box, copy / delete, tap a red message to send again, offline bar. |
| `util/ChatRules.java` | NEW. Pure rules (merge, poll delay, ticks, time labels, preview, badge). |
| `ui/ShellFragment.java` | CHANGED. The Chats tab opens `ChatsFragment` instead of the placeholder. |
| `ui/BhDetailFragment.java`, `res/layout/fragment_bh_detail.xml` | CHANGED. **Chat with owner** works (disabled on your own place). |
| `app/BofoApp.java` | CHANGED. Sends waiting chat messages when the connection returns. |
| `res/navigation/nav_graph.xml` | CHANGED. New destination `chatFragment`. |
| `res/values/strings.xml` | CHANGED. Chat texts; removed `placeholder_chats` and `detail_chat_soon`. |
| `res/drawable/ic_send.xml`, `bg_bubble_mine.xml`, `bg_bubble_theirs.xml`, `bg_unread_badge.xml`, `bg_chat_input.xml`, `bg_send_button.xml` | NEW. Small chat drawables. |
| `app/build.gradle` | CHANGED. Version 2.0.7 / 207. No new libraries. |
| `app/src/test/.../ChatRulesTest.java` | NEW. 9 tests (102 in total). |
| `CHANGES.md`, `HANDOFF_ANDROID.md`, `ANDROID_PORT_PLAN.md`, `bofo-android/README.md`, `docs/HANDOFF_ANDROID_ARCHIVE.md` | CHANGED. Version 2.0.7 notes; the Stage 8 resume prompt is at the top of `HANDOFF_ANDROID.md`. |

No Supabase change. No new Google Cloud or Supabase step.

## How to open each feature
1. Unzip `BOFO-android-v2_0_7-changes.zip` over the repo root, open `bofo-android` in Android Studio, **Sync Gradle**, run.
2. **Chat with owner:** Home or Explore > any place > scroll to the contact buttons > **Chat with owner**.
3. **Chats list:** bottom bar > **Chats**. Tap a row to open, long-press to delete.
4. **Message options:** long-press a bubble. **Offline test:** airplane mode, send, then turn it off.
5. Tests: `gradlew.bat testDebugUnitTest` (expect 102 passed).

## What was checked
- All new and changed XML is well-formed; every string / colour used by the new Java exists; every view id used through view binding exists in its layout.
- The Android Java files pass a `javac` parse (only missing Android / AndroidX / binding symbols are left).
- `ChatRules`: 9 tests run on the JVM with stand-ins for `org.junit` and `org.json`: 9 passed, 0 failed. The 93 older tests were not re-run.
- NOT checked: Gradle build, how it looks on a phone, the real Supabase answers, the keyboard behaviour, scroll keeping when older pages load.

## Simple fix guide
| Symptom | Likely cause / fix |
|---|---|
| Gradle error "cannot find symbol ... ItemMsgMineBinding" | Sync Gradle / Build > Rebuild Project (bindings are generated). |
| Chat list shows no names | `inbox_v` column names differ: send one row of `inbox_v`. |
| "Chat with owner" says could not open | RPC `get_or_create_conversation` refused: send the red text. |
| Messages stay with a clock | Offline, or the server is slow: they send by themselves later. Red = refused: tap to try again. |
| Send box hidden by the keyboard | Check `windowSoftInputMode=adjustResize` and the insets in `MainActivity`; send me a screenshot. |
| New messages arrive late | Polling is 3 s, up to 12 s when quiet; it only runs while the chat is open. |
