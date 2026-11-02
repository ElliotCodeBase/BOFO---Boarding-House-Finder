# v2.2.4 - Boarding group chat: cleaner look

## What changed (look only, no database change)
- **Header:** taller (60 dp), bigger house avatar, hairline under it.
- **Bubbles:** rounder (20 dp), a little less padding, narrower so long lines read better.
- **Less repetition:** several messages in a row from the same person show the name once; **your own messages no longer show "You / Student"**; the time appears under the last message of a run only (a message that is still sending or failed always shows its status).
- **Spacing:** tight inside a run, more air between people; extra space under the last message.
- **Announcement card:** speaker icon + ANNOUNCEMENT label, amber "Pinned" chip (same colours as the Owner badge), bigger title, rounder card with a soft accent border.
- **Role badges:** rounder pills.
- **Message bar:** page-coloured bar with a hairline on top, so the white input pill and the send button stand out; slightly bigger input and send button.
- Works in light and dark theme (only theme colours are used).
- Files: `res/layout/fragment_boarding_chat.xml`, `res/layout/item_group_msg.xml`, `res/drawable/bg_ann_card.xml`, `bg_bubble_mine.xml`, `bg_bubble_theirs.xml`, `bg_role_owner.xml`, `bg_role_student.xml`, new `bg_chat_header.xml`, `bg_chat_composer.xml`, `ui/BoardingChatFragment.java` (grouping in the adapter), `app/build.gradle` (2.2.4, versionCode 20204).

## How to see it
Student or owner > Boarding chat of a house.

## Install (PowerShell)
```
Expand-Archive "$HOME\Downloads\BOFO-v2.2.4-changes.zip" "C:\Users\user\bofo-finder" -Force
cd C:\Users\user\bofo-finder\bofo-android
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat installDebug
cd ..; git add -A; git commit -m "v2.2.4 - Boarding chat UI polish"
```

## If a problem occurs
| Problem | Fix |
|---|---|
| Build error "attribute drawableTint not found" | minSdk is 26 so it is fine; run Build > Clean Project, then build again. If it persists, delete the `android:drawableTint` line in `item_group_msg.xml`. |
| Time missing under a message | By design, only the last message of a run shows it. To show it on every message, remove the `h.v.meta.setVisibility(...)` line in `BoardingChatFragment.onBindViewHolder`. |
| Want the "You" label back | Remove `|| (r.mine && !r.announcement)` in the `h.v.who.setVisibility(...)` line of the same method. |
| Want the old look | Reinstall v2.2.3. |

## Not run
Not built or run by Claude (no Android SDK, no screenshot of the result). XML files parsed and Java braces checked by script only.
