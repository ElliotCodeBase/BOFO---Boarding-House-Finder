-- 047: admin_approvals_list() updates expired requests, so it must not be marked STABLE ("UPDATE is not allowed in a non-volatile function").
-- Already applied live on 2026-11-08. Safe to run twice. 039 in this repo is corrected too.
create or replace function public.admin_approvals_list() returns setof public.admin_approvals
language plpgsql security definer set search_path = public as $$
begin
  if not (public.has_permission('approvals_decide') or public.has_permission('approvals_request')) then raise exception 'admins only' using errcode = '42501'; end if;
  update public.admin_approvals set status = 'expired' where status = 'pending' and expires_at <= now();
  if public.has_permission('approvals_decide') then
    return query select * from public.admin_approvals order by requested_at desc limit 100;
  end if;
  return query select * from public.admin_approvals where requested_by = auth.uid() order by requested_at desc limit 100;
end $$;
revoke all on function public.admin_approvals_list() from public, anon;
grant execute on function public.admin_approvals_list() to authenticated;
notify pgrst, 'reload schema';
