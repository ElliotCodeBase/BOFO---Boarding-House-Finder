# STATE after Stage 4, part 6 (admin app 2.9.13 / 20913): AI chats + AI settings

Builds on STATE_STAGE4d.md (push, migration 042) and STATE_STAGE4e.md (refunds, migration 043); either may still be unapplied.

## Done (app-admin + shared only; NO new SQL)
- **More > AI chats** (`ai_log_view`, Owner only): newest first, 30 per page ("Show more"), range chips (24 h / 7 days / all), search box over question, answer and name. Read-only. The search text is cleaned to letters, digits, space, @ . - so it cannot change the PostgREST filter (`shared/AiChatLogRules.cleanSearch`, tested). E-mail addresses are not shown, only the name.
- **More > AI settings** (`ai_settings`, Owner only): (1) BOFO AI on/off for everyone (`set_ai_enabled`, confirmation + fingerprint); (2) chat monitor status, read-only (changing it stays a SQL-editor decision because it decides whether chats are stored at all); (3) delete all stored AI chats (`ai_chat_log_wipe`, type DELETE + fingerprint, shows the count). The server writes the audit rows.
- `shared/AiChatLogRules` (+ `AiChatLogRulesTest`). Server gates already exist in migration 040 (policy `ai_chat_log_admin_read` = ai_log_view; both functions = ai_settings).
- Version 2.9.13 in both `build.gradle`.

## What was and was NOT run
- NOT run: Gradle, the Java tests (no JDK/JUnit in this sandbox), a phone. Your last build compiled stage 4c; expect possible small errors in these new files: send me the first.

## How to test (Owner)
1. More: "AI chats" and "AI settings" appear. Admin / Moderator / Support: neither appears.
2. AI chats: rows load; Last 24 h / 7 days / All change the list; search "rent" filters; a search with commas or brackets does not error. If the monitor is off and the log is empty: "No chats found."
3. AI settings: Turn off, fingerprint, the user app no longer shows the assistant; Turn on again. The monitor pill matches `select value from app_flags where key='ai_chat_monitor_enabled'`.
4. Delete all: wrong word refused; right word + fingerprint -> "Deleted N chats."; More > All admin activity shows the audit rows.

## NOT done (backlog)
Strikes ladder, Escalate, queue claiming, saved replies, unusual-activity alert, session list + revoke, break-glass, appeals (migrations); push when an approval is decided; two-step enrolment inside the admin app; AI announcements; then Stage 5.
