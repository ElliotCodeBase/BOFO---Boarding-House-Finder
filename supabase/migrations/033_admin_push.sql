-- 033: admin push for new ID reviews and for items waiting more than 3 days (v2.6.0, admin next steps, step 2 of 5).
-- Needs 029 (push_notify, device_tokens, push_settings) and 023 (owner_verifications). Safe to run twice. NOT applied live: run it yourself.
-- What it adds
--   * trigger push_idreview on owner_verifications (AFTER INSERT): calls push_notify('idreview', {id, owner_id}). Only ids: no name, no ID data.
--   * function push_stale_check(): counts IDs / reports / payments that have waited more than 3 days (the same "waiting" meaning as Admin Home:
--     IDs and reports = pending, payments = pending or failed) and, when the total is above 0, calls push_notify('stale', {ids, reports, payments}).
--     Counts only. Not callable by the app (no grant): only the database scheduler (see docs/PUSH_SETUP.md, "Daily reminder") or you in the SQL editor.
-- The send-push edge function sends both kinds ONLY to accounts whose users.role = 'admin' (checked at send time), and the phone drops them otherwise.
-- A failing push NEVER blocks the ID submission (push_notify swallows errors).
-- Undo: drop trigger push_idreview on public.owner_verifications; drop function public.trg_push_idreview(), public.push_stale_check();
--       (and, if you scheduled it: select cron.unschedule('bofo-stale-push');)

create or replace function public.trg_push_idreview() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.status = 'pending' then
    perform public.push_notify('idreview', jsonb_build_object('id', new.id, 'owner_id', new.owner_id));
  end if;
  return new;
end $$;
drop trigger if exists push_idreview on public.owner_verifications;
create trigger push_idreview after insert on public.owner_verifications for each row execute function public.trg_push_idreview();

create or replace function public.push_stale_check() returns void
language plpgsql security definer set search_path = public as $$
declare cutoff timestamptz := now() - interval '3 days'; n_ids int; n_rep int; n_pay int;
begin
  select count(*) into n_ids from public.owner_verifications where status = 'pending' and submitted_at < cutoff;
  select count(*) into n_rep from public.reports where status = 'pending' and created_at < cutoff;
  select count(*) into n_pay from public.payments where status in ('pending', 'failed') and created_at < cutoff;
  if n_ids + n_rep + n_pay > 0 then
    perform public.push_notify('stale', jsonb_build_object('ids', n_ids, 'reports', n_rep, 'payments', n_pay));
  end if;
end $$;
revoke all on function public.push_stale_check() from public, anon, authenticated;

notify pgrst, 'reload schema';
