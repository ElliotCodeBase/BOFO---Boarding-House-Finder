-- 020: BOFO AI learning loop (v2.0.132). Safe to run twice. Nothing existing is changed.
--   * public.ai_unanswered  what BOFO could not help with (written ONLY by the bofo-chat edge function, service role). The question text is stored only while app_flags 'ai_chat_monitor_enabled' is true.
--   * public.ai_feedback    thumbs up / down on an answer (a student can add and read only their own rows; admins read all)

create table if not exists public.ai_unanswered (
  id         uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now(),
  reason     text not null check (reason in ('degraded','clarify','not_found','off_topic','thumbs_down')),
  intent     text,
  lang       text,
  message    text            -- null when the AI chat monitor flag is off
);
create index if not exists ai_unanswered_created_idx on public.ai_unanswered (created_at desc);
alter table public.ai_unanswered enable row level security;
drop policy if exists ai_unanswered_admin_read on public.ai_unanswered;
create policy ai_unanswered_admin_read on public.ai_unanswered for select to authenticated using (public.is_admin());
revoke all on public.ai_unanswered from anon, authenticated;
grant select on public.ai_unanswered to authenticated;     -- still filtered by the policy: non-admins get 0 rows

create table if not exists public.ai_feedback (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid() references public.users(id) on delete cascade,
  chat_log_id  uuid,                                       -- = log_id returned by bofo-chat (no FK: the log row exists only while the monitor is on)
  rating       smallint not null check (rating in (-1, 1)),
  created_at   timestamptz not null default now()
);
create index if not exists ai_feedback_created_idx on public.ai_feedback (created_at desc);
alter table public.ai_feedback enable row level security;
drop policy if exists ai_feedback_insert_own on public.ai_feedback;
create policy ai_feedback_insert_own on public.ai_feedback for insert to authenticated with check (user_id = auth.uid());
drop policy if exists ai_feedback_read on public.ai_feedback;
create policy ai_feedback_read on public.ai_feedback for select to authenticated using (user_id = auth.uid() or public.is_admin());
revoke all on public.ai_feedback from anon, authenticated;
grant select, insert on public.ai_feedback to authenticated;

-- a thumbs down also lands in "needs attention" (no text, only the fact)
create or replace function public.ai_feedback_to_unanswered() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.rating = -1 then insert into public.ai_unanswered (reason) values ('thumbs_down'); end if;
  return new;
end $$;
drop trigger if exists ai_feedback_unanswered on public.ai_feedback;
create trigger ai_feedback_unanswered after insert on public.ai_feedback for each row execute function public.ai_feedback_to_unanswered();
