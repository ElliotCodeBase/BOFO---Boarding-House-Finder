# Chats list: live refresh + clear unread highlight (v1.14.3)

## What was wrong
The Chats list loaded once when you opened the tab and never again. A new message arrived in the database, but the list kept showing the old
last message and no unread badge until you left and came back.

## What changed
| What | Before | After |
|------|--------|-------|
| Refresh | loaded once | checks every 4 seconds while the Chats tab is open, redraws only if something changed |
| New message | not shown | the row jumps to the top with the new preview, new time, a purple unread count badge, and bold preview |
| Unread row look | thin purple border only | tinted lavender background, purple border and soft glow |
| After reading in a chat | n/a | go back to the list: the highlight is gone (the read mark is saved when you open the chat) |

Files: `ui/ChatsView.java`, `bofo.css` (block "Chats list" at the end; this bofo.css also contains the Home styling from `BOFO-home-all.zip`).

## How to apply and test
```powershell
cd C:\Users\user\bofo-finder
Expand-Archive -Path "$HOME\Downloads\BOFO-chats-live.zip" -DestinationPath . -Force
mvn clean javafx:run
```
Test with two accounts: leave one window on the Chats tab, send a message from the other. Within about 4 seconds the row should light up with a count badge.

## If problems occur
- No highlight even though the preview changes: the unread count comes from the Supabase view `inbox_v` (column `unread`); send me what `select * from inbox_v` returns in the SQL Editor.
- List flickers or feels slow: change `Duration.seconds(4)` in `ChatsView.java` to `6`.
- Compile error: `git checkout -- src/main/java/com/bofo/finder/ui/ChatsView.java src/main/resources/com/bofo/finder/bofo.css` and send me the error.
- Not covered: the small badge on the Chats icon in the bottom bar. Tell me if you want that live too.
