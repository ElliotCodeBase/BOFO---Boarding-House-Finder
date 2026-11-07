-- SECURITY_CHECKS.sql: READ-ONLY. Run in the Supabase SQL editor. Changes nothing. NOT RUN by the author (no Postgres in the sandbox).
-- GOOD result is written in each comment. Rows say PASS / FAIL where a test is possible.

-- ===== N-1 (appeal must match the CURRENT decision) =====
-- GOOD: PASS (needs 044 as shipped; a FAIL means an older 044 body is live: re-run 044)
select 'N-1' as item, case when pg_get_functiondef('public.admin_appeal_decide(uuid,boolean,text)'::regprocedure) ilike '%older decision%' then 'PASS' else 'FAIL' end as result;

-- ===== N-3 (run before AND after 046: FAIL before, PASS after) =====
select 'N-3 anon cannot run ' || f as item, case when has_function_privilege('anon', f::regprocedure, 'execute') then 'FAIL' else 'PASS' end as result
from unnest(array['public.recompute_bh(uuid)','public.admin_session_ok()','public.is_owner_or_admin(uuid)','public.can_list_bh(uuid)']) f;
select 'N-3 signed-in cannot run recompute_bh' as item, case when has_function_privilege('authenticated', 'public.recompute_bh(uuid)'::regprocedure, 'execute') then 'FAIL' else 'PASS' end as result;
select 'N-3 ' || p.proname || ' answers only for the caller' as item, case when pg_get_functiondef(p.oid) like '%auth.uid()%' then 'PASS' else 'FAIL' end as result
from pg_proc p where p.pronamespace = 'public'::regnamespace and p.proname in ('is_owner_or_admin','can_list_bh');

-- ===== Re-check: RLS and grants on EVERY table in public =====
-- R1. Tables without row level security. GOOD: 0 rows.
select 'R1 NO RLS' as item, c.relname from pg_class c where c.relnamespace = 'public'::regnamespace and c.relkind = 'r' and not c.relrowsecurity;
-- R2. Tables with RLS but no policy (= closed to everyone except functions and the service role). GOOD: only tables you meant to close (admin_*, user_strikes, appeals, device_tokens, push_settings, app_secrets, rate_limits...).
select 'R2 RLS, NO POLICY' as item, c.relname from pg_class c where c.relnamespace = 'public'::regnamespace and c.relkind = 'r' and c.relrowsecurity
  and not exists (select 1 from pg_policies p where p.schemaname = 'public' and p.tablename = c.relname) order by 2;
-- R3. Closed tables that still have a direct grant to anon or signed-in users (a grant does not open them, RLS blocks, but remove it anyway). GOOD: 0 rows.
select 'R3 GRANT ON CLOSED TABLE' as item, g.table_name, g.grantee, g.privilege_type from information_schema.role_table_grants g
where g.table_schema = 'public' and g.grantee in ('anon','authenticated')
  and exists (select 1 from pg_class c where c.relnamespace = 'public'::regnamespace and c.relname = g.table_name and c.relrowsecurity)
  and not exists (select 1 from pg_policies p where p.schemaname = 'public' and p.tablename = g.table_name);
-- R4. Any table where the logged-out role has a grant AND a policy that applies to anon/public. GOOD: only what you intend to be public (e.g. listings).
select 'R4 ANON POLICY' as item, tablename, policyname, cmd from pg_policies where schemaname = 'public' and (roles && array['anon','public']::name[]);
-- R5. SECURITY DEFINER functions the logged-out role can run (all migrations, not only new). GOOD: 0 rows, or helpers you accept.
select 'R5 ANON CAN EXECUTE' as item, p.proname from pg_proc p where p.pronamespace = 'public'::regnamespace and p.prosecdef and has_function_privilege('anon', p.oid, 'execute') order by 2;

-- ===== S-03 (admin MFA) =====
-- M1. Is the switch on? GOOD (after you turn it on): true.
select 'S-03 flag' as item, value from public.app_flags where key = 'require_admin_mfa';
-- M2. Team members and whether each has a VERIFIED authenticator (counts by role, no names). GOOD before switching on: every owner/admin row has_mfa = true.
select 'S-03 team' as item, m.role, m.status, exists (select 1 from auth.mfa_factors f where f.user_id = m.user_id and f.status = 'verified') as has_mfa, count(*)
from public.admin_members m group by 2,3,4 order by 2;

-- ===== S-04 (dev / test accounts live) =====
-- D1. Test-looking accounts (email, role, last sign-in). GOOD: 0 rows after you delete them. Only these three known dev emails and any other @example.com test accounts.
select 'S-04 dev account' as item, u.email, pu.role, u.last_sign_in_at
from auth.users u left join public.users pu on pu.id = u.id
where u.email in ('bofo.dev.admin@example.com','bofo.dev.student@example.com','bofo.dev.owner@example.com') or u.email like '%@example.com';
-- D2. Any dev account that is staff right now. GOOD: 0 rows.
select 'S-04 dev account is staff' as item, m.role, m.status from public.admin_members m join auth.users u on u.id = m.user_id where u.email like '%@example.com';
