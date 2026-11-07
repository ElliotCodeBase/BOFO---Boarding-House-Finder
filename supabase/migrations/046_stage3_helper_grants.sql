-- 046: Stage 3 (N-3): helper functions stop answering for other people, and admin_session_ok / recompute_bh are no longer open. Run AFTER 039-045 (or after 044). Safe to run twice.
-- NOT applied live: run it yourself, then run supabase/SECURITY_CHECKS.sql (section N-3: every row must say PASS).
-- What changes (nothing a normal user sees):
--   * is_owner_or_admin(uid) and can_list_bh(uid): answer true only when uid is the CALLER (auth.uid()). Both are used only in RLS policies as f(auth.uid()),
--     so the policies behave exactly as before; a signed-in user can no longer ask "is this uuid an owner/admin?" about someone else.
--   * recompute_bh(uuid): only called by SECURITY DEFINER trigger functions (trg_recompute), which run as the function owner, so signed-in users and anon lose execute.
--   * admin_session_ok(): logged-out role (anon) loses execute. Signed-in roles keep it (has_permission / is_admin run as definer, policies keep working).
-- NOT changed on purpose: is_verified_owner(uuid). get_or_create_conversation calls it for a listing's owner (not the caller), so it must keep answering for any uid.
-- ROLLBACK:
--   create or replace function public.is_owner_or_admin(uid uuid) returns boolean language sql stable security definer set search_path = public as $f$
--     select exists (select 1 from public.users where id = uid and role in ('owner', 'admin')); $f$;
--   create or replace function public.can_list_bh(uid uuid) returns boolean language sql stable security definer set search_path = public as $f$
--     select exists (select 1 from public.users where id = uid and role in ('owner','admin')); $f$;
--   grant execute on function public.recompute_bh(uuid) to authenticated;   grant execute on function public.admin_session_ok() to anon;

create or replace function public.is_owner_or_admin(uid uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select uid is not null and uid = auth.uid() and exists (select 1 from public.users where id = uid and role in ('owner', 'admin'));
$$;

create or replace function public.can_list_bh(uid uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select uid is not null and uid = auth.uid() and exists (select 1 from public.users where id = uid and role in ('owner','admin'));
$$;

revoke all on function public.is_owner_or_admin(uuid) from public, anon;
grant execute on function public.is_owner_or_admin(uuid) to authenticated, service_role;
revoke all on function public.can_list_bh(uuid) from public, anon;
grant execute on function public.can_list_bh(uuid) to authenticated, service_role;
revoke all on function public.recompute_bh(uuid) from public, anon, authenticated;
revoke all on function public.admin_session_ok() from public, anon;
grant execute on function public.admin_session_ok() to authenticated, service_role;
notify pgrst, 'reload schema';
