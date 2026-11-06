-- 043: admin_refund_boost only refunds a PAID payment, and needs a reason (BOFO Admin, Stage 4 part 5, v2.9.12). Run AFTER 040. Safe to run twice.
-- Before: the function (040) marked ANY payment 'refunded' (even pending/failed/already refunded) and accepted an empty reason.
-- Now: payments_manage (Owner only) is still required; the payment row is locked; status must be 'paid'; reason must have 5+ characters.
-- It still only does the BOOKKEEPING (status + ends the boost + audit row). The money itself is refunded in the PayMongo dashboard.
-- ROLLBACK: re-run the admin_refund_boost body from 040_admin_permission_gates.sql.
create or replace function public.admin_refund_boost(pid uuid, reason text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare st text;
begin
  perform public.require_permission('payments_manage');
  if reason is null or char_length(btrim(reason)) < 5 then raise exception 'A reason is required (at least 5 characters)'; end if;
  select p.status into st from public.payments p where p.id = pid for update;
  if st is null then raise exception 'Payment not found'; end if;
  if st <> 'paid' then raise exception 'Only a paid payment can be refunded (this one is %)', st; end if;
  update public.payments set status = 'refunded' where id = pid;
  update public.boosts set ends_at = least(ends_at, now()), starts_at = least(starts_at, now() - interval '1 second') where payment_id = pid;
  insert into public.audit_logs (actor_id, action, target_type, target_id, details)
    values (auth.uid(), 'boost_refunded', 'payment', pid::text, jsonb_build_object('reason', left(btrim(reason), 300)));
  return '{}'::jsonb;
end $$;
revoke all on function public.admin_refund_boost(uuid, text) from public, anon;
grant execute on function public.admin_refund_boost(uuid, text) to authenticated;
notify pgrst, 'reload schema';
