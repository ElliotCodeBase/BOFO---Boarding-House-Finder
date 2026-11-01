-- =====================================================================================================
-- RUN ONLY TO REMOVE the AI Chats monitor. DO NOT run this as part of the normal migration order.
-- (It is kept here only so it is easy to find. If you ever use `supabase db push`, move this file out of
--  supabase/migrations first, or it would be applied as a migration.)
-- It permanently deletes every stored AI chat. Steps and order: REMOVE_AI_CHATS.md (turn the flag off FIRST).
-- Safe to run twice.
-- =====================================================================================================

-- 1) stop the daily purge job
do $$
begin
  if to_regnamespace('cron') is not null then
    if exists (select 1 from cron.job where jobname = 'ai-chat-log-retention') then
      perform cron.unschedule('ai-chat-log-retention');
    end if;
  end if;
exception when others then
  raise notice 'could not unschedule the cron job (%): remove it by hand: select cron.unschedule(''ai-chat-log-retention'');', sqlerrm;
end $$;

-- 2) policies and indexes (explicitly, then the table itself)
do $$
begin
  if to_regclass('public.ai_chat_log') is not null then
    drop policy if exists ai_chat_log_admin_read on public.ai_chat_log;
  end if;
end $$;
drop index if exists public.ai_chat_log_created_idx;
drop index if exists public.ai_chat_log_user_idx;
drop function if exists public.ai_chat_log_wipe();
drop function if exists public.ai_chat_log_purge(int);
drop table if exists public.ai_chat_log;

-- 3) the on/off flag row; the app_flags table goes too, but only when nothing else uses it
delete from public.app_flags where key = 'ai_chat_monitor_enabled';
do $$
begin
  if to_regclass('public.app_flags') is not null and not exists (select 1 from public.app_flags) then
    drop policy if exists app_flags_read on public.app_flags;
    drop table public.app_flags;
  end if;
end $$;

-- Not removed on purpose: audit_logs rows with action 'ai_chat_wipe' (history of who wiped the chats).
