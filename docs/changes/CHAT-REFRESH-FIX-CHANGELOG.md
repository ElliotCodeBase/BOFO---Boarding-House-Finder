# Fix: chat does not refresh when a new message arrives (v1.14.3)

## Cause (most likely)
The chat checks for new messages every 3 seconds. The request asks Supabase for messages "after" the newest one, using that message's
timestamp, which looks like `2026-10-26T01:42:11.123456+00:00`. The `+` was sent un-encoded, and a web server reads a raw `+` as a
space, so Supabase rejected the request (HTTP 400). The code then swallowed the error, so it failed silently every 3 seconds and
new messages only appeared after leaving and re-opening the chat.

## What changed
- `service/ChatService.java`: the timestamp is URL-encoded (`+` becomes `%2B`) in `fetchNewer`.
- `ui/ChatView.java`: a failed check now prints `[ChatView] could not fetch new messages: ...` in the terminal, and no error can
  stop the 3-second polling for good.

## How to apply and test
```powershell
cd C:\Users\user\bofo-finder
Expand-Archive -Path "$HOME\Downloads\BOFO-chat-refresh-fix.zip" -DestinationPath . -Force
mvn clean javafx:run
```
Open the same chat in two windows (student and owner), send a message in one, and it should appear in the other within about
3 seconds (up to 12 seconds after a long quiet period, because polling slows down when the chat is idle).

## If problems occur
- Still not refreshing: look in the terminal for a line starting with `[ChatView]` and send it to me; that line names the real cause.
- Messages appear but never get the blue "read" ticks: that is a separate `mark_read` function in Supabase; send me the terminal error.
- Slow to update: in `ChatView.tick()` the delays are `3 / 6 / 12` seconds; lower them (not below 2) for faster checks.
- Compile error: `git checkout -- src/main/java/com/bofo/finder/ui/ChatView.java src/main/java/com/bofo/finder/service/ChatService.java` and send me the error.
- Not covered here: the Chats list (inbox) does not auto-refresh either; tell me if you want that added.
