-- 045: BOFO Admin, Stage 5 (v2.11.0): remove the LEGACY admin rule. Run AFTER 039-044.
--
-- !!! RUN ONLY AFTER THE OLD USER-APP VERSIONS (the ones that still contain the admin tabs, 2.10.x and older) ARE RETIRED !!!
-- !!! Not applied. Needs the owner's OK in the chat. Do not run it together with 042-044.                              !!!
--
-- WHAT IT DOES
--   1) refuses to run (RAISE) unless: every users.role = 'admin' account already has an admin_members row, at least one ACTIVE owner exists,
--      and min_app_version is set (so old user apps are already blocked).
--   2) has_permission / is_staff / staff_has / my_admin_access: the fallback "users.role = 'admin' with no member row has FULL access" is gone.
--      A person is staff only through admin_members. is_admin() (= is_staff()) follows automatically. 044's break-glass is kept.
--   3) drops three functions only the OLD in-app admin called: admin_takedown(uuid, text), send_test_push(), push_test_result(bigint).
--      (the BOFO Admin app uses admin_report_takedown; the test push button was removed from the user app in 2.11.0.)
--   4) removes the 'legacy_admin' role preset (no member can hold it: guard_admin_members already refuses it).
--
-- WHAT IT KEEPS (on purpose)
--   * 018 / 019 / 041: the minimum-admin-app-version gate and "hide an admin's profile row from old apps". Do not drop these.
--   * users.role = 'admin' as a plain marker on the account (the admin app still signs these accounts in). It just no longer GRANTS anything.
--   * admin_audit_log rows with actor_role = 'legacy' (history).
--   * every other admin_* function: the BOFO Admin app uses them.
--
-- AFTER RUNNING: test the Owner, Admin, Moderator and Support accounts in the admin app (RELEASE_CHECKLIST.md section 9).
--
-- ROLLBACK (the changes are only function bodies, so it is quick):
--   a) has_permission: run the has_permission body from 044 section 4 again (it still ends with the LEGACY line).
--   b) is_staff and my_admin_access: run their bodies from 039_admin_roles.sql (sections 4 and 7).
--   c) staff_has: run its body from 042_admin_push.sql (section 3).
--   d) admin_takedown: run "from 001_schema.sql" / 040 body; send_test_push + push_test_result: 037_push_test_admin.sql (then re-apply the
--      permission gate from 040 lines 195-230). Grants for all three: revoke from public, anon; grant execute to authenticated.
--   e) legacy_admin preset: insert it again from 039 line 85.
--   f) notify pgrst, 'reload schema';

-- ===================== 0) safety stops =====================
do $$
declare n int; owners int; minv text;
begin
  select count(*) into n from public.users u
   where u.role = 'admin' and not exists (select 1 from public.admin_members m where m.user_id = u.id);
  if n > 0 then
    raise exception 'Stage 5 stop: % account(s) with users.role = admin have no admin_members row and would lose ALL access. Add them to the team first (invite / bootstrap_owner) or change their role.', n;
  end if;
  select count(*) into owners from public.admin_members where role = 'owner' and status = 'active';
  if owners < 1 then
    raise exception 'Stage 5 stop: there is no active owner in admin_members. Run bootstrap_owner first.';
  end if;
  select s.value into minv from public.app_settings s where s.key = 'min_app_version';
  if coalesce(minv, '') = '' then
    raise exception 'Stage 5 stop: min_app_version is empty. Set it to the first user-app version without admin screens (2.11.0) with set_min_app_version, and wait until old apps are blocked.';
  end if;
end $$;

-- ===================== 1) the permission check, without the legacy fallback =====================
create or replace function public.has_permission(p text) returns boolean
language plpgsql stable security definer set search_path = public as $$
declare m public.admin_members; perms text[]; iat_ bigint;
begin
  if auth.uid() is null or p is null or not (p = any (public.admin_permission_keys())) then return false; end if;
  if not public.admin_session_ok() then return false; end if;
  select * into m from public.admin_members where user_id = auth.uid();
  if not found then return false; end if;                              -- v2.11.0: no member row = no access (the legacy rule is gone)
  if m.status <> 'active' then return false; end if;
  if m.expires_at is not null and m.expires_at <= now() then return false; end if;
  begin iat_ := (auth.jwt() ->> 'iat')::bigint; exception when others then iat_ := 0; end;
  if m.revoked_before is not null and coalesce(iat_, 0) < extract(epoch from m.revoked_before)::bigint then return false; end if;
  if m.role = 'owner' then return true; end if;
  select r.permissions into perms from public.admin_roles r where r.key = m.role;
  if p = any (coalesce(perms, '{}') || m.extra_grant) and not (p = any (m.extra_deny)) then return true; end if;
  return p = any (public.break_glass_keys()) and exists (                -- 044: an active break-glass grant for exactly this key
    select 1 from public.admin_break_glass g where g.user_id = auth.uid() and g.permission = p and g.revoked_at is null and g.expires_at > now());
end $$;
revoke all on function public.has_permission(text) from public, anon;
grant execute on function public.has_permission(text) to authenticated, service_role;

create or replace function public.is_staff() returns boolean language sql stable security definer set search_path = public as $$
  select auth.uid() is not null and public.admin_session_ok() and exists (
    select 1 from public.admin_members m
     where m.user_id = auth.uid() and m.status = 'active'
       and (m.expires_at is null or m.expires_at > now())
       and (m.revoked_before is null or coalesce((auth.jwt() ->> 'iat')::bigint, 0) >= extract(epoch from m.revoked_before)::bigint));
$$;
revoke all on function public.is_staff() from public, anon;
grant execute on function public.is_staff() to authenticated, service_role;

create or replace function public.staff_has(p_user uuid, p text) returns boolean
language plpgsql stable security definer set search_path = public as $$
declare m public.admin_members; perms text[];
begin
  if p_user is null or p is null or not (p = any (public.admin_permission_keys())) then return false; end if;
  select * into m from public.admin_members where user_id = p_user;
  if not found then return false; end if;                              -- v2.11.0: legacy rule removed
  if m.status <> 'active' then return false; end if;
  if m.expires_at is not null and m.expires_at <= now() then return false; end if;
  if m.role = 'owner' then return true; end if;
  select r.permissions into perms from public.admin_roles r where r.key = m.role;
  return p = any (coalesce(perms, '{}') || m.extra_grant) and not (p = any (m.extra_deny));
end $$;
revoke all on function public.staff_has(uuid, text) from public, anon, authenticated;
grant execute on function public.staff_has(uuid, text) to service_role;

create or replace function public.my_admin_access() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare m public.admin_members; r public.admin_roles; k text; out_ text[] := '{}';
begin
  if auth.uid() is null then return jsonb_build_object('active', false); end if;
  select * into m from public.admin_members where user_id = auth.uid();
  if not found then return jsonb_build_object('active', false); end if;         -- v2.11.0: legacy rule removed
  select * into r from public.admin_roles where key = m.role;
  foreach k in array public.admin_permission_keys() loop
    if public.has_permission(k) then out_ := out_ || k; end if;
  end loop;
  return jsonb_build_object('active', coalesce(array_length(out_, 1), 0) > 0, 'role', m.role, 'label', r.label, 'status', m.status,
                            'expires_at', m.expires_at, 'permissions', to_jsonb(out_), 'legacy', false);
end $$;
revoke all on function public.my_admin_access() from public, anon;
grant execute on function public.my_admin_access() to authenticated;

-- ===================== 2) functions only the old in-app admin used =====================
drop function if exists public.admin_takedown(uuid, text);
drop function if exists public.send_test_push();
drop function if exists public.push_test_result(bigint);

-- ===================== 3) the legacy preset =====================
delete from public.admin_roles r where r.key = 'legacy_admin' and not exists (select 1 from public.admin_members m where m.role = 'legacy_admin');

notify pgrst, 'reload schema';
