-- 032: admin Undo (v2.5.0, admin next steps, step 1 of 5).
-- Adds three admin-only SECURITY DEFINER functions. Each checks is_admin(), refuses with a clear error when the state is no longer
-- the one the decision produced ("nothing to undo"), and writes an audit_logs row. No table, column or policy changes. Nothing is deleted.
--   admin_unsuspend(uid)         clears suspended_until / suspension_reason of a user who is suspended right now
--   admin_relist(bh)             puts a taken_down place back to active
--   admin_reopen_report(rid)     puts a dismissed report back to pending
-- Ban Undo already exists (admin_unban, 023). Refunds are not undoable.
-- Undo: drop function public.admin_unsuspend(uuid); drop function public.admin_relist(uuid); drop function public.admin_reopen_report(uuid);

create or replace function public.admin_unsuspend(uid uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'admins only'; end if;
  if not exists (select 1 from public.users where id = uid and suspended_until is not null and suspended_until > now()) then
    raise exception 'nothing to undo: this user is not suspended';
  end if;
  update public.users set suspended_until = null, suspension_reason = null where id = uid;
  insert into public.audit_logs (actor_id, action, target_type, target_id, details)
    values (auth.uid(), 'user_unsuspend', 'user', uid::text, '{}'::jsonb);
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.admin_relist(bh uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'admins only'; end if;
  if not exists (select 1 from public.boarding_houses where id = bh) then raise exception 'place not found'; end if;
  if not exists (select 1 from public.boarding_houses where id = bh and status = 'taken_down') then
    raise exception 'nothing to undo: this place is not taken down';
  end if;
  update public.boarding_houses set status = 'active' where id = bh;
  insert into public.audit_logs (actor_id, action, target_type, target_id, details)
    values (auth.uid(), 'listing_relist', 'boarding_house', bh::text, '{}'::jsonb);
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.admin_reopen_report(rid uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare r public.reports;
begin
  if not public.is_admin() then raise exception 'admins only'; end if;
  select * into r from public.reports where id = rid for update;
  if not found then raise exception 'report not found'; end if;
  if r.status <> 'dismissed' then raise exception 'nothing to undo: this report is not dismissed'; end if;
  -- the unique index reports_one_pending allows one waiting report per reporter + person + place
  if exists (select 1 from public.reports p where p.status = 'pending' and p.reporter_id = r.reporter_id and p.reported_user_id = r.reported_user_id
             and coalesce(p.bh_id, '00000000-0000-0000-0000-000000000000'::uuid) = coalesce(r.bh_id, '00000000-0000-0000-0000-000000000000'::uuid)) then
    raise exception 'nothing to undo: a newer waiting report from the same person already exists';
  end if;
  update public.reports set status = 'pending', action = null, reviewed_by = null, reviewed_at = null where id = rid;
  insert into public.audit_logs (actor_id, action, target_type, target_id, details)
    values (auth.uid(), 'report_reopen', 'user', r.reported_user_id::text, jsonb_build_object('report_id', rid));
  return jsonb_build_object('ok', true);
end $$;

revoke all on function public.admin_unsuspend(uuid), public.admin_relist(uuid), public.admin_reopen_report(uuid) from public, anon;
grant execute on function public.admin_unsuspend(uuid), public.admin_relist(uuid), public.admin_reopen_report(uuid) to authenticated;

notify pgrst, 'reload schema';
