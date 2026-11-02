# BOFO AI smarter: status (read with BOFO-AI-SMARTER-PROMPT.md)
| Step | Status | Version | Notes for the next AI |
|---|---|---|---|
| 1 Backend brain | DONE (function not deployed by Claude) | 2.0.132 | `index.ts`: PLAN prompt, `planSteps`/`chain`, `sessionParams`, `KNOWLEDGE`/`knowledgeFor`, `INJECT_ASK`, `noteUnanswered`, `log_id` (= ai_chat_log.id when monitor on). Migration 020 applied live. Checked: tsc syntax, node run of helpers. Not run against Supabase. Not done: `response_format` JSON mode (free models may reject it). |
| 2 Android data layer | NOT DONE | next = 2.0.133 | Send `session` {filters_in_effect:{max_price,free_*}}, history 10x300, parse `clarify`/`quick_replies`/`lang`/`log_id`, `rate()` -> insert into `ai_feedback` (user_id defaults to auth.uid()), BofoMemory, smarter BofoIntentLocal, tests. |
| 3 Chat UI + admin + docs | NOT DONE | 2.0.134 | clarify chips, thumbs, New chat, admin "Needs attention" from `ai_unanswered`, docs, acceptance list. |
