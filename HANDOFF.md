# Handoff - v1.16.1 Chat menu UI (2026-10-27)
- **PR title:** `v1.16.1 - Fix chat popup menus: styled, inside the window`. Version in `config.properties`, `pom.xml` (versionCode 58), CHANGES.md.
- `Ui.menu()` creates a ContextMenu and attaches `bofo.css` to its popup scene; CSS block `.context-menu` at the end of bofo.css; `ChatView.chatMenu` aligns the menu to the button's right edge. Not compiled/run by Claude.

---

# Handoff - v1.16.0 Delete messages and chats (2026-10-27)
- **PR title:** `v1.16.0 - Delete messages and chat cards for students and owners`. Version in `config.properties`, `pom.xml` (versionCode 57), CHANGES.md.
- **Supabase: run `supabase/migrations/008_delete_chat.sql` first** (adds `messages.hidden_for`, `conversations.student_cleared_at/owner_cleared_at`, new `msg_read` policy, new `inbox_v`, RPCs `delete_message(msg, everyone)` and `delete_conversation(conv)`). Without it the delete buttons fail with a toast and nothing else changes.
- `ChatService.deleteMessage/deleteConversation` (also clear the device copies); `ChatView` message menu + `chatMenu` + `dropDeletedByOther` (every 3rd poll); `ChatsView` context menu on cards. Not compiled/run by Claude.

---

# Handoff - v1.15.5 Card wider than screen (2026-10-27)
- **PR title:** `v1.15.5 - Fix listing card wider than the screen in My listings`. Version in `config.properties`, `pom.xml` (versionCode 56), CHANGES.md.
- `CardGrid.flow`: single-column cells (BhCard etc.) use `min(w, maxWidth)` instead of `max(minWidth, ...)`. Follow-up to v1.15.4 (`ListingsView` now a one-column `CardGrid`). Not compiled/run by Claude.
