-- 014: AI Chats monitor (v2.0.91). Admins can read what users ask the BOFO assistant.
-- Everything for this feature is in THIS file (+ 014_ai_chat_log_REMOVE.sql to take it out again). See REMOVE_AI_CHATS.md.
-- Run once in Supabase > SQL Editor. Safe to run twice.
--
-- What this adds (nothing existing is changed):
--   * public.app_flags            tiny on/off table (the switch is the row 'ai_chat_monitor_enabled'); any signed-in user may READ it, nobody can write it from the app
--   * public.ai_chat_log          one row per BOFO answer (written ONLY by the bofo-chat edge function with the service role)
--   * public.ai_chat_log_wipe()   admin-only "delete all stored AI chats" (the app calls it as an RPC)
--   * public.ai_chat_log_purge()  deletes rows older than N days (default 90); run daily by pg_cron, see the bottom of this file

-- ===================== on / off switch =====================
create table if not exists public.app_flags (
  key        text primary key,
  value      boolean not null default false,
  updated_at timestamptz not null default now()
);
alter table public.app_flags enable row level security;
drop policy if exists app_flags_read on public.app_flags;
create policy app_flags_read on public.app_flags for select to authenticated using (true);   -- flags are not secret; no insert / update / delete policy = app cannot change them
revoke all on public.app_flags from anon, authenticated;
grant select on public.app_flags to authenticated;

-- OFF until you switch it on (so nothing is stored before the app version that tells users about it is out).
-- To switch ON:   update public.app_flags set value = true,  updated_at = now() where key = 'ai_chat_monitor_enabled';
-- To switch OFF:  update public.app_flags set value = false, updated_at = now() where key = 'ai_chat_monitor_enabled';
insert into public.app_flags (key, value) values ('ai_chat_monitor_enabled', false) on conflict (key) do nothing;

-- ===================== the log =====================
create table if not exists public.ai_chat_log (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references public.users(id) on delete cascade,   -- deleting an account also deletes that person's AI chats
  user_name  text not null default '',
  user_email text not null default '',
  question   text not null,
  answer     text not null,
  created_at timestamptz not null default now()
);
create index if not exists ai_chat_log_created_idx on public.ai_chat_log (created_at desc);
create index if not exists ai_chat_log_user_idx    on public.ai_chat_log (user_id);

alter table public.ai_chat_log enable row level security;
drop policy if exists ai_chat_log_admin_read on public.ai_chat_log;
create policy ai_chat_log_admin_read on public.ai_chat_log for select to authenticated using (public.is_admin());
-- No insert / update / delete policy: the app (anon + signed-in users, admins included) can never write, edit or delete a row.
-- The edge function uses the service role, which bypasses RLS, so it is the only writer.
revoke all on public.ai_chat_log from anon, authenticated;
grant select on public.ai_chat_log to authenticated;   -- still filtered by the policy above: non-admins get 0 rows

-- ===================== delete everything (admin button) =====================
-- Returns a JSON object ({"deleted": n}) because the app's rpc() helper reads an object, not a bare number.
create or replace function public.ai_chat_log_wipe() returns jsonb
language plpgsql security definer set search_path = public as $$
declare n bigint;
begin
  if not public.is_admin() then raise exception 'admins only' using errcode = '42501'; end if;
  delete from public.ai_chat_log where true;
  get diagnostics n = row_count;
  insert into public.audit_logs (actor_id, action, target_type, details)
    values (auth.uid(), 'ai_chat_wipe', 'ai_chat_log', jsonb_build_object('rows', n));
  return jsonb_build_object('deleted', n);
end $$;
revoke all on function public.ai_chat_log_wipe() from public, anon;
grant execute on function public.ai_chat_log_wipe() to authenticated;      -- the is_admin() check inside is the real gate

-- ===================== retention: rows older than 90 days =====================
create or replace function public.ai_chat_log_purge(days int default 90) returns bigint
language plpgsql security definer set search_path = public as $$
declare n bigint;
begin
  delete from public.ai_chat_log where created_at < now() - make_interval(days => greatest(days, 1));
  get diagnostics n = row_count;
  return n;
end $$;
revoke all on function public.ai_chat_log_purge(int) from public, anon, authenticated;
grant execute on function public.ai_chat_log_purge(int) to service_role;   -- cron runs as the database owner; the edge function (service role) calls it as a backstop

-- Daily at 03:15 UTC with pg_cron. If pg_cron is not enabled yet: Supabase > Database > Extensions > enable "pg_cron", then run this file again.
do $$
begin
  create extension if not exists pg_cron;
  if exists (select 1 from cron.job where jobname = 'ai-chat-log-retention') then perform cron.unschedule('ai-chat-log-retention'); end if;
  perform cron.schedule('ai-chat-log-retention', '15 3 * * *', 'select public.ai_chat_log_purge(90)');
exception when others then
  raise notice 'pg_cron is not available (%): the 90-day rule is NOT scheduled yet. The bofo-chat function still purges now and then as a backstop.', sqlerrm;
end $$;
