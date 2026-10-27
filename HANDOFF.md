# Handoff - v1.16.0 Delete messages and chats (2026-10-27)
- **PR title:** `v1.16.0 - Delete messages and chat cards for students and owners`. Version in `config.properties`, `pom.xml` (versionCode 57), CHANGES.md.
- **Supabase: run `supabase/migrations/008_delete_chat.sql` first** (adds `messages.hidden_for`, `conversations.student_cleared_at/owner_cleared_at`, new `msg_read` policy, new `inbox_v`, RPCs `delete_message(msg, everyone)` and `delete_conversation(conv)`). Without it the delete buttons fail with a toast and nothing else changes.
- `ChatService.deleteMessage/deleteConversation` (also clear the device copies); `ChatView` message menu + `chatMenu` + `dropDeletedByOther` (every 3rd poll); `ChatsView` context menu on cards. Not compiled/run by Claude.

---

# Handoff - v1.15.5 Card wider than screen (2026-10-27)
- **PR title:** `v1.15.5 - Fix listing card wider than the screen in My listings`. Version in `config.properties`, `pom.xml` (versionCode 56), CHANGES.md.
- `CardGrid.flow`: single-column cells (BhCard etc.) use `min(w, maxWidth)` instead of `max(minWidth, ...)`. Follow-up to v1.15.4 (`ListingsView` now a one-column `CardGrid`). Not compiled/run by Claude.

---

# Handoff - v1.15.4 My listings fits the screen (2026-10-27)
- **PR title:** `v1.15.4 - Fix My listings card overflow and button overlap`. Version in `config.properties`, `pom.xml` (versionCode 55), CHANGES.md.
- `ListingsView`: rows is now `CardGrid(10, 150, 1)` (was a VBox, so the card took the photo's width and ran off the screen, and its height was measured for the wrong width); the action buttons are equal-width with `Priority.ALWAYS`. Not compiled/run by Claude.
