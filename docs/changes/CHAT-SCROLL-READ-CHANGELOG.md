# Chat: auto-scroll to the newest message + live "read" ticks (v1.14.3)

This zip also contains the earlier chat refresh fix (encoded timestamp), so it replaces `BOFO-chat-refresh-fix.zip`. Apply only this one.

## What changed
| What | Before | After |
|------|--------|-------|
| New message arrives | list stayed where it was, the new bubble was hidden below | view jumps to the bottom automatically (also after you send, and when the chat opens) |
| Read ticks | one grey tick stayed forever until you left and re-opened the chat | when the other person reads your message, the tick becomes two blue ticks within about 3 seconds |
| Chat refresh | (earlier fix) | included: timestamp URL-encoded, errors printed as `[ChatView] ...` |

Files: `ui/ChatView.java`, `service/ChatService.java` (new `fetchMyRead`, encoded `fetchNewer`).

### How it works
- Scrolling: the content is laid out a moment after a bubble is added, so the old "scroll to 100%" ran too early. The view now
  stays glued to the bottom for about 0.7 seconds after new content, so it ends up at the real bottom.
- Read ticks: while some of your messages are unread, the chat checks every 3 seconds which of them have been read and redraws those bubbles.

## How to apply and test
```powershell
cd C:\Users\user\bofo-finder
Expand-Archive -Path "$HOME\Downloads\BOFO-chat-scroll-read.zip" -DestinationPath . -Force
mvn clean javafx:run
```
Test with two windows (student and owner) in the same chat: send a message from one. In the other it should appear and the view should
jump to the bottom. In the sender's window the single tick should turn into two blue ticks about 3 seconds after the other person has the chat open.

## If problems occur
- Does not scroll to the bottom: raise `700` in `toBottom()` (ChatView.java) to `1200`.
- Annoying when reading old messages: tell me and I can make it scroll only when you are already near the bottom.
- Ticks never turn blue: watch the terminal for `[ChatView] could not refresh read ticks:` and send me that line (the Supabase `mark_read` function or the messages policy may be the cause).
- Messages sent before this update stay with one tick if the other person never opened them: expected.
- Compile error: `git checkout -- src/main/java/com/bofo/finder/ui/ChatView.java src/main/java/com/bofo/finder/service/ChatService.java` and send me the error.
- Not covered: the unread count / bold on the Chats list and the dock badge. Tell me if those stay "unread" while you are inside the chat.
