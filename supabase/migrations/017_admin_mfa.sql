-- 017: two-step verification for admins (v2.0.119). Run AFTER 014 (needs public.app_flags). Safe to run twice.
--
-- What it does
--   * is_admin() stays exactly as before UNTIL the switch 'require_admin_mfa' is turned on.
--   * Once it is on, an admin counts as admin only in a session that passed the 6-digit authenticator check ("aal2" in the token).
--     Every admin policy and RPC (ID-scan review, takedowns, AI chat log, boosts...) goes through is_admin(), so one function covers them all.
--   * The SQL editor, the service role and the database owner are not affected (they have no auth.uid()).
--
-- Turn it ON (only after an admin finished the in-app setup: Settings > Two-step verification, and signed in again with the code):
--     select public.set_admin_mfa_required(true);
--   It refuses to switch on unless at least one admin already has a verified authenticator, so you cannot lock everyone out.
-- Turn it OFF again (break-glass, works from the SQL editor any time):
--     select public.set_admin_mfa_required(false);
--
-- Supabase dashboard: Authentication > Sign In / Providers > Multi-Factor: "TOTP" must be enabled (it is on by default for new projects).

insert into public.app_flags (key, value) values ('require_admin_mfa', false) on conflict (key) do nothing;

create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.users where id = auth.uid() and role = 'admin')
     and (
       not coalesce((select f.value from public.app_flags f where f.key = 'require_admin_mfa'), false)
       or coalesce(auth.jwt() ->> 'aal', 'aal1') = 'aal2'
     );
$$;
revoke all on function public.is_admin() from public, anon;
grant execute on function public.is_admin() to authenticated, service_role;

-- the switch: owner / SQL editor only (the app can never call it: no grant to anon or authenticated)
create or replace function public.set_admin_mfa_required(on_ boolean) returns text
language plpgsql security definer set search_path = public, auth as $$
begin
  if on_ and not exists (
       select 1 from auth.mfa_factors f join public.users u on u.id = f.user_id
        where u.role = 'admin' and f.status = 'verified' and f.factor_type = 'totp') then
    raise exception 'No admin has a verified authenticator yet. Set it up in the app first (Settings > Two-step verification).';
  end if;
  insert into public.app_flags (key, value, updated_at) values ('require_admin_mfa', on_, now())
    on conflict (key) do update set value = excluded.value, updated_at = now();
  insert into public.audit_logs (actor_id, action, target_type, details)
    values (auth.uid(), case when on_ then 'admin_mfa_required_on' else 'admin_mfa_required_off' end, 'app_flags', jsonb_build_object('value', on_));
  return case when on_ then 'Admin two-step verification is now REQUIRED' else 'Admin two-step verification is now OFF' end;
end $$;
revoke all on function public.set_admin_mfa_required(boolean) from public, anon, authenticated;
notify pgrst, 'reload schema';
