-- 026: break-glass reset for admin two-step verification (v2.2.1). Run in the SQL editor ONLY. Safe to run twice.
--
-- Use it when an admin lost the authenticator code (app data cleared, entry deleted, new phone).
-- The app cannot do this itself: the server only lets a session that already passed the code remove the factor.
--
--   select public.reset_admin_mfa('admin@example.com');
--
-- What it does
--   1. Removes every authenticator (TOTP) factor of that account from auth.mfa_factors.
--   2. If no admin has a verified authenticator left, switches 'require_admin_mfa' OFF
--      (otherwise nobody would count as admin: is_admin() needs aal2 while the switch is on).
--   3. Writes the reset to audit_logs.
-- Afterwards: sign in again, Settings > Two-step verification > set it up again, SAVE THE SETUP KEY, then (optional)
--   select public.set_admin_mfa_required(true);

create or replace function public.reset_admin_mfa(email_ text) returns text
language plpgsql security definer set search_path = public, auth as $$
declare uid_ uuid; n int; sw_off boolean := false;
begin
  select id into uid_ from auth.users where lower(email) = lower(trim(email_));
  if uid_ is null then raise exception 'No account with the email %', email_; end if;

  delete from auth.mfa_factors where user_id = uid_;
  get diagnostics n = row_count;

  if not exists (
       select 1 from auth.mfa_factors f join public.users u on u.id = f.user_id
        where u.role = 'admin' and f.status = 'verified' and f.factor_type = 'totp') then
    insert into public.app_flags (key, value, updated_at) values ('require_admin_mfa', false, now())
      on conflict (key) do update set value = false, updated_at = now();
    sw_off := true;
  end if;

  insert into public.audit_logs (actor_id, action, target_type, details)
    values (null, 'admin_mfa_reset', 'auth.users', jsonb_build_object('user_id', uid_, 'factors_removed', n, 'requirement_switched_off', sw_off));

  return format('Removed %s authenticator(s) for %s.%s', n, email_,
                case when sw_off then ' Admin two-step requirement is now OFF.' else '' end);
end $$;
revoke all on function public.reset_admin_mfa(text) from public, anon, authenticated;
notify pgrst, 'reload schema';
