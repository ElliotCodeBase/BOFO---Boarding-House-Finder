-- 027: answered reports keep HOW they were answered (v2.2.2). Safe to run twice.
--   * reports.action: suspend | ban | dismiss | takedown | reviewed  (the app's Reports > Answered section filters on it)
--   * admin_review_report now writes the action (same signature as before)
--   * NEW admin_report_takedown(rid, bh, reason): takes a place of the reported user down AND answers the report in one step
--   * reports_admin_v gets the action column (added at the end)
--   * old rows are filled in from audit_logs (report_suspend / report_ban / report_dismiss / report_reviewed)
-- Undo: drop function public.admin_report_takedown(uuid, uuid, text); alter table public.reports drop column action;
--       (then re-run the previous admin_review_report from 023_ban.sql / live and recreate reports_admin_v without "r.action")

alter table public.reports add column if not exists action text;
alter table public.reports drop constraint if exists reports_action_check;
alter table public.reports add constraint reports_action_check
  check (action is null or action in ('suspend','ban','dismiss','takedown','reviewed'));

update public.reports r set action = replace(a.action, 'report_', '')
  from public.audit_logs a
 where r.action is null and a.details ->> 'report_id' = r.id::text
   and a.action in ('report_suspend','report_ban','report_dismiss','report_reviewed');
update public.reports set action = 'dismiss' where action is null and status = 'dismissed';

create or replace function public.admin_review_report(rid uuid, act text, hrs integer default null, reason text default null, notes text default null)
returns jsonb language plpgsql security definer set search_path to 'public' as $$
declare r public.reports; t public.users; why text := left(btrim(coalesce(reason, '')), 300); nt text := nullif(left(btrim(coalesce(notes, '')), 500), '');
        new_status text; until_ts timestamptz; id_banned boolean := false; h text;
begin
  if not public.is_admin() then raise exception 'admins only'; end if;
  select * into r from public.reports where id = rid for update;
  if not found then raise exception 'report not found'; end if;
  if r.status <> 'pending' then raise exception 'this report was already handled'; end if;

  if act = 'dismiss' then new_status := 'dismissed';
  elsif act = 'reviewed' then new_status := 'reviewed';
  elsif act = 'suspend' then
    if hrs is null or hrs < 1 or hrs > 87600 then raise exception 'suspension must be between 1 hour and 10 years'; end if;
    if why = '' then raise exception 'a reason is required'; end if;
    select * into t from public.users where id = r.reported_user_id;
    if not found then raise exception 'user not found'; end if;
    if t.role = 'admin' then raise exception 'an admin cannot be suspended'; end if;
    until_ts := now() + make_interval(hours => hrs);
    update public.users set suspended_until = until_ts, suspension_reason = why where id = r.reported_user_id;
    new_status := 'actioned';
  elsif act = 'ban' then
    if why = '' then raise exception 'a reason is required'; end if;
    select * into t from public.users where id = r.reported_user_id;
    if not found then raise exception 'user not found'; end if;
    if t.role = 'admin' then raise exception 'an admin cannot be banned'; end if;
    update public.users set banned_at = now(), banned_by = auth.uid(), ban_reason = why where id = t.id;
    for h in select distinct ov.id_hash from public.owner_verifications ov where ov.owner_id = t.id and ov.id_hash is not null loop
      if exists (select 1 from public.banned_identities where id_hash = h and active) then
        update public.banned_identities set user_id = t.id where id_hash = h and active;
      else
        insert into public.banned_identities (id_hash, id_type, user_id, banned_by, reason)
          values (h, (select ov.id_type from public.owner_verifications ov where ov.owner_id = t.id and ov.id_hash = h order by ov.submitted_at desc limit 1), t.id, auth.uid(), why);
      end if;
      id_banned := true;
    end loop;
    update public.boarding_houses set status = 'taken_down' where owner_id = t.id and status = 'active';
    new_status := 'actioned';
  else raise exception 'unknown action';
  end if;

  update public.reports set status = new_status, action = act, reviewed_by = auth.uid(), reviewed_at = now(), admin_notes = coalesce(nt, admin_notes) where id = rid;
  insert into public.audit_logs (actor_id, action, target_type, target_id, details)
    values (auth.uid(), 'report_' || act, 'user', r.reported_user_id::text,
            jsonb_build_object('report_id', rid, 'reason', nullif(why, ''), 'hours', hrs, 'until', until_ts, 'id_banned', id_banned));
  return jsonb_build_object('status', new_status, 'until', until_ts, 'id_banned', id_banned);
end $$;

create or replace function public.admin_report_takedown(rid uuid, bh uuid, reason text)
returns jsonb language plpgsql security definer set search_path to 'public' as $$
declare r public.reports; p public.boarding_houses; why text := left(btrim(coalesce(reason, '')), 300);
begin
  if not public.is_admin() then raise exception 'admins only'; end if;
  if why = '' then raise exception 'a reason is required'; end if;
  select * into r from public.reports where id = rid for update;
  if not found then raise exception 'report not found'; end if;
  if r.status <> 'pending' then raise exception 'this report was already handled'; end if;
  select * into p from public.boarding_houses where id = bh;
  if not found then raise exception 'place not found'; end if;
  if p.owner_id is distinct from r.reported_user_id then raise exception 'this place does not belong to the reported user'; end if;
  update public.boarding_houses set status = 'taken_down' where id = bh;
  update public.reports set status = 'actioned', action = 'takedown', reviewed_by = auth.uid(), reviewed_at = now(),
         admin_notes = left('Taken down: ' || p.name || ' - ' || why, 500) where id = rid;
  insert into public.audit_logs (actor_id, action, target_type, target_id, details)
    values (auth.uid(), 'report_takedown', 'boarding_house', bh::text, jsonb_build_object('report_id', rid, 'reason', why));
  return jsonb_build_object('status', 'actioned');
end $$;
revoke all on function public.admin_report_takedown(uuid, uuid, text) from public, anon;
grant execute on function public.admin_report_takedown(uuid, uuid, text) to authenticated;

create or replace view public.reports_admin_v as
 select r.id, r.reporter_id, r.reported_user_id, r.bh_id, r.reason, r.details, r.status, r.created_at, r.reviewed_by, r.reviewed_at, r.admin_notes,
        nullif(rp.full_name, '') as reporter_name, nullif(ru.full_name, '') as reported_name, b.name as bh_name,
        ru.suspended_until as reported_suspended_until, r.action
   from public.reports r
   left join public.users rp on rp.id = r.reporter_id
   left join public.users ru on ru.id = r.reported_user_id
   left join public.boarding_houses b on b.id = r.bh_id
  where public.is_admin();
notify pgrst, 'reload schema';
