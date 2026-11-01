-- 019: an admin's own profile row is hidden from app versions below the minimum (v2.0.121). Run AFTER 018. Safe to run twice.
--
-- Why: 018 already makes is_admin() false for old apps (no admin data, no admin action). But an old app still read its own profile row,
-- saw role = 'admin' and opened empty admin screens. Now the row is not returned to an old app, so it cannot tell the account is admin
-- (it falls back to its role screen and cannot go on). Same switch as 018: set_min_admin_app_version('') turns this off too.
-- Students and owners are not affected (the extra rule applies only to rows with role = 'admin').
drop policy if exists users_self_read on public.users;
create policy users_self_read on public.users for select to authenticated
  using ((id = (select auth.uid()) and (role <> 'admin' or (select public.app_version_ok())))
         or (select public.is_admin()));
