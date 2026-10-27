# Handoff - v1.16.3 Chat side from the chat + separate login per window (2026-10-27)
- **PR title:** `v1.16.3 - Chat header follows the chat, second test window with its own login`. Version in `config.properties`, `pom.xml` (versionCode 60), CHANGES.md. **Zip is cumulative since v1.16.0** (the user's folder only got part of v1.16.1: Maven compiled 85 files, Popup.java missing).
- Bug: two app windows showed the same account because the saved login is one folder (`~/.bofo/session`), and `ChatView` chose the "Student / Boarding house owner" label and quick replies from the account's current role. Now `ChatView(convId, title, photo, otherUserId, iAmOwner, onBack)` takes the side from the chat (`ChatsView`: `c.ownerId().equals(me)`; `BhDetailView`: false).
- PC testing: `DiskCache.slot()` reads env `BOFO_PROFILE` (or `-Dbofo.profile`); `SessionStore` and the disk cache use `session-<name>` / `cache-<name>`. PowerShell: `$env:BOFO_PROFILE="owner"; mvn javafx:run`. Empty = normal; Android unaffected.
- Data note (Supabase): test accounts have swapped roles: "Dev Student" = owner, "Dev Owner" = student (from "Switch to ... mode"). Not changed.
- Not compiled/run by Claude.

---

# Handoff - v1.16.2 Action sheets replace native menus (2026-10-27)
- **PR title:** `v1.16.2 - In-app action sheets instead of grey right-click menus`. Version in `config.properties`, `pom.xml` (versionCode 59), CHANGES.md. **Zip is cumulative with v1.16.1** (user had not applied 1.16.1 yet).
- `Popup.menu(title, Popup.Action...)` = bottom sheet (`popup-sheet*` css). Replaced all 3 `ContextMenu`s: message long-press (`ChatView.messageMenu`), chat 3-dots (`ChatView.chatMenu`), chat card (`ChatsView`). No `ContextMenu`/`Alert`/`TextInputDialog` left in the code: use `Popup` for every new menu/dialog. ComboBoxes were already styled in css (`combo-box-*`) and left alone; FileChooser is the OS picker (cannot be styled). Not compiled/run by Claude.

---

# Handoff - v1.16.1 Chat delete fix + one pop-up style (2026-10-27)
- **PR title:** `v1.16.1 - Fix "Could not delete the chat" and use one in-app pop-up everywhere`. Version in `config.properties`, `pom.xml` (versionCode 58), CHANGES.md.
- **Cause of "Could not delete the chat":** migration 008 was never run on Supabase (no `delete_conversation` RPC, no `hidden_for`/`*_cleared_at` columns). Claude applied it to project `zfxldzcoirqolflkkhrh` via MCP as migration `delete_chat`; file `supabase/migrations/008_delete_chat.sql` unchanged. Nothing to run by hand.
- New `ui/Popup.java` (in-app card pop-up: `confirm`, `ask`, `alert`, `input`; classes `popup-*` at the end of `bofo.css`). It replaces every JavaFX `Alert` / `TextInputDialog`: `ChatView` (delete chat, delete message, report), `ChatsView`, `ListingsView` (delete + error), `ProfileView` (delete account), `PaymentsView` (refund reason), `QueueView` (reject reason), `Locator.ask` (location prompt). New dialogs must use `Popup`, never `Alert`.
- Only `javac` syntax-checked, not built or run by Claude.

---
