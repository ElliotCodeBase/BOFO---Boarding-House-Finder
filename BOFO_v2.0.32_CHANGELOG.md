# BOFO Android v2.0.32 - Step 10 changelog

Not built or run by Claude. Build it in Android Studio first (see the fix guide if it fails).

## What I did
**1. Chats tab (blank screen fix + new look)**
- Title "Chats" with the subtitle "Press and hold a chat (or right-click) to delete it".
- While loading: six grey placeholder rows that pulse (replaces the empty page with a spinner).
- Chats are white rounded rows: lavender initials circle, name, time, purple house name, last message, unread badge.
- No chats: mascot, "No chats yet", "Open a place and tap Chat with owner." Load error: message + Try again.

**2. Chat screen**
- Header: back, initials avatar, name, purple "Boarding house owner".
- Empty chat: mascot, "Say hello", hint text, and two quick replies ("Is it still available?", "Can I visit this week?"). Tapping one sends it.
- Composer "Write a message..."; the send button is grey until you type, then purple.
- A chip ("Today", "Yesterday", "Oct 28") appears above each day of messages.

**3. Boarding house detail screen**
- Pinned top bar "Boarding house" with back and save heart.
- Photo area shows "No photos yet" when there are none.
- Bold name, "P1,550 / month - per bed", chips (Verified owner, "6 beds available", distance when known).
- Three tiles: Beds free, Rooms, Rating. Then amenities, contact, directions, reviews.
- "Chat with owner" is a purple pill fixed at the bottom.

## Files
- New: item_chat_skeleton.xml, strings_chats.xml, strings_chat.xml, strings_detail.xml, drawables bg_chat_row, bg_avatar_tint, bg_skeleton_circle, bg_day_chip, bg_send_off, bg_quick_reply.
- Changed: ChatsFragment, ChatFragment, BhDetailFragment, ChatRules (+ test), fragment_chats, item_conversation, fragment_chat, item_msg_mine, item_msg_theirs, fragment_bh_detail, build.gradle (2.0.32 / 232).
- Not touched: Login, list card, Supabase, services, JavaFX source.

## How to access
1. Unzip the changes zip into `C:\Users\user\bofo-finder` (overwrite), open `bofo-android` in Android Studio, Run.
2. Chats: bottom tab "Chats". Chat: tap a row, or open a place and tap "Chat with owner". Detail: tap any boarding house card.
3. Profile > About should show 2.0.32.

## If a problem occurs (simple fix guide)
| Problem | Fix |
|---|---|
| Build error "cannot find symbol ... binding.xxx" | Build > Clean Project, then Build > Rebuild Project. If it stays, check the id in the layout exists (e.g. `skeleton`, `empty_title`, `quick1`, `info_chips`, `tile_beds_value`, `chat_bar`). |
| Build error about a missing string | Make sure the three new `strings_*.xml` files were unzipped into `res/values/`. |
| Unit test fails | Run `ChatRulesTest.dayLabelNamesTodayYesterdayAndDate`; it needs the new `dayLabel` in `ChatRules.java` (both files must come from this zip). |
| Chats still blank | Pull down to refresh; check internet; run `adb logcat -s BOFO`. If the list is empty but you have chats, check the Supabase `inbox` call. |
| Chat button hidden behind keyboard/bar | Detail: the scroll area has 76dp bottom margin; raise it in `fragment_bh_detail.xml` if your phone cuts it. |
| Distance chip missing | Expected: it only shows when the place has a distance. |
| Layout looks wrong | Undo with Git: `git checkout -- bofo-android` then re-run, or `git revert <commit>`. |

## Test on the emulator
1. Open Chats with the network slow: grey pulsing rows, then your chats.
2. Open an empty chat: mascot, "Say hello", tap a quick reply and see it send.
3. Type text: send turns purple; clear it: grey.
4. Open a place: top bar, tiles, pinned purple "Chat with owner".
5. Open a place with no photos: "No photos yet".

## Next
Step 11: final pass (list unused layouts/strings to delete).
