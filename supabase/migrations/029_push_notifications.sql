-- 029: phone push notifications (v2.3.0). Safe to run twice. NOT applied live: run it yourself (or ask for approval to run it).
-- What it adds
--   * device_tokens   one row per phone (FCM token) -> the signed-in user. Only the RPCs below touch it.
--   * push_settings   the URL of the send-push edge function + a shared secret (read only by the triggers).
--   * register_push_token / unregister_push_token   called by the app after sign-in / at sign-out / when Settings > Notifications is switched.
--   * triggers on messages (student <-> owner chats), bh_group_messages (boarding group chats) and reports (admins only)
--     that call the send-push edge function through pg_net. A failing push NEVER blocks the message or the report.
-- Admin-only reports: the edge function sends a report notification ONLY to tokens of users whose users.role = 'admin' (checked at send time),
--   and the app also drops a report push unless the signed-in account is an admin. Signing out deletes the phone's token.
-- Undo: drop trigger push_dm on public.messages; drop trigger push_group on public.bh_group_messages; drop trigger push_report on public.reports;
--       drop function public.trg_push_dm(), public.trg_push_group(), public.trg_push_report(), public.push_notify(text, jsonb),
--       public.register_push_token(text, text), public.unregister_push_token(text); drop table public.device_tokens, public.push_settings;

create extension if not exists pg_net;

-- ===================== 1) tables (no direct access from the app) =====================
create table if not exists public.device_tokens (
  token text primary key check (char_length(token) between 20 and 4096),
  user_id uuid not null references public.users(id) on delete cascade,
  platform text not null default 'android' check (platform in ('android')),
  app_version text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists device_tokens_user on public.device_tokens (user_id);
alter table public.device_tokens enable row level security;          -- no policies: service role + the functions below only
revoke all on public.device_tokens from anon, authenticated;

create table if not exists public.push_settings (
  key text primary key,
  value text not null
);
alter table public.push_settings enable row level security;          -- no policies: service role + push_notify() only
revoke all on public.push_settings from anon, authenticated;

-- ===================== 2) RPCs called by the app =====================
-- A token always belongs to ONE user: signing in with another account on the same phone moves it.
create or replace function public.register_push_token(p_token text, p_app_version text default null)
returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'sign in first'; end if;
  if p_token is null or char_length(p_token) < 20 or char_length(p_token) > 4096 then raise exception 'bad token'; end if;
  insert into public.device_tokens (token, user_id, app_version)
    values (p_token, auth.uid(), left(p_app_version, 20))
    on conflict (token) do update set user_id = auth.uid(), app_version = excluded.app_version, updated_at = now();
  -- keep the 5 newest phones per user (old reinstalls leave dead tokens behind)
  delete from public.device_tokens d
   where d.user_id = auth.uid()
     and d.token in (select token from public.device_tokens where user_id = auth.uid() order by updated_at desc offset 5);
end $$;

create or replace function public.unregister_push_token(p_token text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then return; end if;
  delete from public.device_tokens where token = p_token and user_id = auth.uid();
end $$;

revoke all on function public.register_push_token(text, text) from public, anon;
revoke all on function public.unregister_push_token(text) from public, anon;
grant execute on function public.register_push_token(text, text) to authenticated;
grant execute on function public.unregister_push_token(text) to authenticated;

-- ===================== 3) the call to the edge function =====================
create or replace function public.push_notify(p_kind text, p_record jsonb)
returns void language plpgsql security definer set search_path = public, extensions as $$
declare u text; s text;
begin
  select value into u from public.push_settings where key = 'url';
  select value into s from public.push_settings where key = 'secret';
  if u is null or s is null then return; end if;                     -- not set up yet: silently do nothing
  perform net.http_post(
    url := u,
    body := jsonb_build_object('kind', p_kind, 'record', p_record),
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-bofo-push-secret', s));
exception when others then
  raise warning 'push_notify failed: %', sqlerrm;                    -- never block the insert
end $$;
revoke all on function public.push_notify(text, jsonb) from public, anon, authenticated;

-- ===================== 4) triggers =====================
create or replace function public.trg_push_dm() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform public.push_notify('dm', jsonb_build_object(
    'id', new.id, 'conversation_id', new.conversation_id, 'sender_id', new.sender_id, 'body', left(new.body, 200)));
  return new;
end $$;
drop trigger if exists push_dm on public.messages;
create trigger push_dm after insert on public.messages for each row execute function public.trg_push_dm();

create or replace function public.trg_push_group() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform public.push_notify('group', jsonb_build_object(
    'id', new.id, 'bh_id', new.bh_id, 'sender_id', new.sender_id, 'body', left(new.body, 200),
    'is_announcement', new.is_announcement, 'title', new.title));
  return new;
end $$;
drop trigger if exists push_group on public.bh_group_messages;
create trigger push_group after insert on public.bh_group_messages for each row execute function public.trg_push_group();

-- reports: only the reason KEY goes out (never the details or the names)
create or replace function public.trg_push_report() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform public.push_notify('report', jsonb_build_object('id', new.id, 'reason', new.reason, 'reporter_id', new.reporter_id));
  return new;
end $$;
drop trigger if exists push_report on public.reports;
create trigger push_report after insert on public.reports for each row execute function public.trg_push_report();

-- ===================== 5) ONE-TIME SETUP (run by hand, with your own secret; do NOT commit the secret) =====================
-- insert into public.push_settings (key, value) values
--   ('url',    'https://zfxldzcoirqolflkkhrh.supabase.co/functions/v1/send-push'),
--   ('secret', '<paste the same long random string you set as PUSH_WEBHOOK_SECRET>')
-- on conflict (key) do update set value = excluded.value;
