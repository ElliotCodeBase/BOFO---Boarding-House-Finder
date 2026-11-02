# v2.1.3 - AI smarter, step 3 of 3: chat UI, admin "Needs attention", docs

## What changed
| Area | Change |
|---|---|
| Ask BOFO chat | Opening the chat shows the **last conversation again** (same bubbles, thread kept for BOFO). No saved chat = the hello. |
| Ask BOFO chat | **Clarify chips**: when BOFO asks back, its quick answers show as chips (`quickReplies`; if none, `suggestions`; never both). |
| Ask BOFO chat | **Thumbs up / down** under an answer that has a `log_id` (saved silently in `ai_feedback`; a thumbs down also lands in "needs attention"). Nothing shows for offline answers. |
| Ask BOFO chat | **New chat** button (arrow in the header): clears the thread, the remembered budget / wifi / language and the saved copy, then says hello. |
| Texts | Hello and input hint say English, Tagalog or Bisaya and that BOFO remembers budget and wifi in this chat. |
| Admin | **Profile > AI needs attention** (admin only): newest 50 rows of `ai_unanswered`, filter by reason (no smart reply, asked back, not found, off topic, thumbs down), pull to refresh. The question text shows only while the AI Chats monitor is on. |
| Version | 2.1.3, versionCode 20103. |

## Files
`ui/BofoChatOverlay.java`, `res/layout/view_bofo_chat.xml`, `res/layout/item_bofo_rate.xml` (new), `res/drawable/ic_bofo_thumb_up|thumb_down|new_chat.xml` (new), `res/values/strings.xml`, `strings_bofo.xml`, `strings_ai_unanswered.xml` (new), `ui/AiUnansweredFragment.java` (new), `service/AiUnansweredService.java` (new), `util/AiUnansweredRules.java` (new), `res/layout/fragment_ai_unanswered.xml` + `item_ai_unanswered.xml` (new), `res/navigation/nav_graph.xml`, `res/layout/fragment_profile.xml`, `ui/ProfileFragment.java`, `test/.../AiUnansweredRulesTest.java` (new), `app/build.gradle`. Also `view_directions_card.xml` (leftover merge conflict resolved, keeps the v2.1.0 loading bar + caption).

## Checked
5 logic tests of `AiUnansweredRules` pass on a plain JVM. XML files parse. Java files only syntax-checked (no Android SDK, no Gradle). **Not built or run on a phone.**

## Before testing
1. `supabase functions deploy bofo-chat` (step 1 code is not deployed yet). Without it the server sends no `log_id`, so no thumbs, and no server clarify chips.
2. Migration 020 is already applied (`ai_unanswered`, `ai_feedback`).

## Acceptance list (on the phone)
- [ ] `adb shell dumpsys package com.bofo.finder | findstr versionName` says 2.1.3
- [ ] Ask "cheapest with wifi", close the chat, open it again: both bubbles are back
- [ ] Ask something vague ("hello po"): a question with up to 4 chips; tapping a chip sends it
- [ ] Under a server answer: thumbs appear; tap one: it turns green/red, the other fades, "Thanks" shows, no second tap
- [ ] A thumbs down creates a row in `ai_unanswered` with reason `thumbs_down`
- [ ] Airplane mode: answers come from the phone, no thumbs, no crash
- [ ] New chat: bubbles gone, hello again, "under 2k" is forgotten (ask "cheapest" and no budget is applied)
- [ ] Sign out and in: the old chat is gone
- [ ] Admin account: Profile shows "AI needs attention", the list loads, chips filter it; a student account does not show the button
- [ ] Explore directions card: the loading bar + caption shows while the exact roads load

## If something goes wrong
| Problem | Fix |
|---|---|
| No thumbs ever | The function is not deployed, or the monitor is off and `log_id` is null: deploy `bofo-chat`. |
| Needs attention is empty | Normal until the function writes rows; check `select * from ai_unanswered order by created_at desc limit 5` |
| Old chat should not come back | Tap New chat, or sign out |
| Undo | `git revert` the merge commit of v2.1.3 |
