-- 031: tell the AI usage rows apart (v2.4.0 prep, AI Announcement Writer). Safe to run twice. Needs 001 (ai_usage).
-- Why: bofo-announce limits 40 announcement drafts per owner per day, and bofo-chat (unchanged) writes rows with no kind.
-- Old rows and bofo-chat inserts get the default 'chat'; bofo-announce inserts kind = 'announce'. No content is stored, only user_id / kind / time.
-- Before this is applied bofo-announce still works: it counts all of the user's rows for 10/min and does NOT enforce 40/day.
-- Side effect: bofo-chat's 10/min check counts every row of the user (any kind), so announce drafts share that minute budget.
alter table public.ai_usage add column if not exists kind text not null default 'chat';
create index if not exists ai_usage_user_kind_created on public.ai_usage (user_id, kind, created_at desc);
notify pgrst, 'reload schema';
-- Undo: drop index if exists public.ai_usage_user_kind_created; alter table public.ai_usage drop column if exists kind;
