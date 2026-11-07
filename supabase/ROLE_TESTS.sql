-- ROLE_TESTS.sql: what each staff role CAN and CANNOT do, checked against the server. READ-ONLY (one transaction, rolled back).
-- Run in the SQL editor AFTER 039 + 040 (TEST/STAGING project preferred) with FOUR real test accounts that are already in the team:
--   one Owner, one Admin, one Moderator, one Support (create them with the invite flow or bootstrap_owner; use @example.com or your own test Google accounts, never real users' data).
-- Paste their user ids (select id, email from public.users where email in (...)) into the four lines marked PASTE. Put 'none' account = any normal user id that is NOT staff.
-- Output: one row per check. Every row must say PASS. Send me the FAIL rows.
-- This file complements supabase/tests/stage2_role_tests.sql (that one builds fake accounts; this one tests your real test accounts and the expected permission matrix).
-- NOT RUN by the author (no Postgres in the build sandbox).

begin;
create temp table zz_res (area text, what text, result text) on commit drop;
grant all on zz_res to authenticated;   -- the impersonated role must be able to write results

do $$
declare
  u_owner uuid := '00000000-0000-0000-0000-000000000000';  -- PASTE owner id
  u_admin uuid := '00000000-0000-0000-0000-000000000000';  -- PASTE admin id
  u_mod   uuid := '00000000-0000-0000-0000-000000000000';  -- PASTE moderator id
  u_sup   uuid := '00000000-0000-0000-0000-000000000000';  -- PASTE support id
  u_none  uuid := '00000000-0000-0000-0000-000000000000';  -- PASTE a normal (non-staff) user id
  r record; ok boolean; uid uuid;
begin
  for r in select * from (values
    ('owner', 'dashboard_view', true),
    ('owner', 'id_review', true),
    ('owner', 'id_view_raw', true),
    ('owner', 'reports_view', true),
    ('owner', 'reports_act', true),
    ('owner', 'suspend_short', true),
    ('owner', 'suspend_long', true),
    ('owner', 'ban_permanent', true),
    ('owner', 'unban', true),
    ('owner', 'banned_view', true),
    ('owner', 'listing_takedown', true),
    ('owner', 'ai_answer', true),
    ('owner', 'ai_log_view', true),
    ('owner', 'ai_settings', true),
    ('owner', 'payments_view', true),
    ('owner', 'payments_manage', true),
    ('owner', 'csv_export', true),
    ('owner', 'audit_view_own', true),
    ('owner', 'audit_view_all', true),
    ('owner', 'members_manage', true),
    ('owner', 'announce_push', true),
    ('owner', 'mfa_manage', true),
    ('owner', 'app_config', true),
    ('owner', 'user_data_delete', true),
    ('owner', 'approvals_request', true),
    ('owner', 'approvals_decide', true),
    ('admin', 'dashboard_view', true),
    ('admin', 'id_review', true),
    ('admin', 'id_view_raw', false),
    ('admin', 'reports_view', true),
    ('admin', 'reports_act', true),
    ('admin', 'suspend_short', true),
    ('admin', 'suspend_long', true),
    ('admin', 'ban_permanent', false),
    ('admin', 'unban', false),
    ('admin', 'banned_view', true),
    ('admin', 'listing_takedown', false),
    ('admin', 'ai_answer', true),
    ('admin', 'ai_log_view', false),
    ('admin', 'ai_settings', false),
    ('admin', 'payments_view', false),
    ('admin', 'payments_manage', false),
    ('admin', 'csv_export', false),
    ('admin', 'audit_view_own', true),
    ('admin', 'audit_view_all', false),
    ('admin', 'members_manage', false),
    ('admin', 'announce_push', false),
    ('admin', 'mfa_manage', false),
    ('admin', 'app_config', false),
    ('admin', 'user_data_delete', false),
    ('admin', 'approvals_request', true),
    ('admin', 'approvals_decide', true),
    ('moderator', 'dashboard_view', true),
    ('moderator', 'id_review', true),
    ('moderator', 'id_view_raw', false),
    ('moderator', 'reports_view', true),
    ('moderator', 'reports_act', true),
    ('moderator', 'suspend_short', true),
    ('moderator', 'suspend_long', false),
    ('moderator', 'ban_permanent', false),
    ('moderator', 'unban', false),
    ('moderator', 'banned_view', true),
    ('moderator', 'listing_takedown', false),
    ('moderator', 'ai_answer', true),
    ('moderator', 'ai_log_view', false),
    ('moderator', 'ai_settings', false),
    ('moderator', 'payments_view', false),
    ('moderator', 'payments_manage', false),
    ('moderator', 'csv_export', false),
    ('moderator', 'audit_view_own', true),
    ('moderator', 'audit_view_all', false),
    ('moderator', 'members_manage', false),
    ('moderator', 'announce_push', false),
    ('moderator', 'mfa_manage', false),
    ('moderator', 'app_config', false),
    ('moderator', 'user_data_delete', false),
    ('moderator', 'approvals_request', true),
    ('moderator', 'approvals_decide', false),
    ('support', 'dashboard_view', true),
    ('support', 'id_review', false),
    ('support', 'id_view_raw', false),
    ('support', 'reports_view', true),
    ('support', 'reports_act', false),
    ('support', 'suspend_short', false),
    ('support', 'suspend_long', false),
    ('support', 'ban_permanent', false),
    ('support', 'unban', false),
    ('support', 'banned_view', true),
    ('support', 'listing_takedown', false),
    ('support', 'ai_answer', true),
    ('support', 'ai_log_view', false),
    ('support', 'ai_settings', false),
    ('support', 'payments_view', false),
    ('support', 'payments_manage', false),
    ('support', 'csv_export', false),
    ('support', 'audit_view_own', true),
    ('support', 'audit_view_all', false),
    ('support', 'members_manage', false),
    ('support', 'announce_push', false),
    ('support', 'mfa_manage', false),
    ('support', 'app_config', false),
    ('support', 'user_data_delete', false),
    ('support', 'approvals_request', false),
    ('support', 'approvals_decide', false)
  ) as e(role_, perm, expected) loop
    uid := case r.role_ when 'owner' then u_owner when 'admin' then u_admin when 'moderator' then u_mod else u_sup end;
    perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated', 'aal', 'aal2', 'iat', extract(epoch from now())::bigint)::text, true);
    set local role authenticated;
    ok := public.has_permission(r.perm);
    reset role;
    insert into zz_res values ('matrix', r.role_ || ' / ' || r.perm || ' expected ' || r.expected, case when ok = r.expected then 'PASS' else 'FAIL (got ' || coalesce(ok::text, 'null') || ')' end);
  end loop;

  -- a normal user has NO permission at all
  perform set_config('request.jwt.claims', json_build_object('sub', u_none, 'role', 'authenticated', 'aal', 'aal2', 'iat', extract(epoch from now())::bigint)::text, true);
  set local role authenticated;
  ok := public.has_permission('dashboard_view') or public.is_staff();
  reset role;
  insert into zz_res values ('non-staff', 'normal user has no permission and is not staff', case when not ok then 'PASS' else 'FAIL' end);

  -- direct table reads are closed for every signed-in role, including Owner
  foreach uid in array array[u_owner, u_admin, u_mod, u_sup, u_none] loop
    perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated', 'aal', 'aal2', 'iat', extract(epoch from now())::bigint)::text, true);
    set local role authenticated;
    begin perform 1 from public.admin_members limit 1; reset role; insert into zz_res values ('direct table', 'admin_members readable by ' || uid, 'FAIL');
    exception when insufficient_privilege then reset role; insert into zz_res values ('direct table', 'admin_members blocked for ' || uid, 'PASS'); end;
  end loop;

  -- the logged-out role cannot run admin functions
  set local role anon;
  begin perform public.admin_members_list(); reset role; insert into zz_res values ('anon', 'anon ran admin_members_list', 'FAIL');
  exception when others then reset role; insert into zz_res values ('anon', 'anon blocked from admin_members_list', 'PASS'); end;

  -- Support must not approve / ban / refund; Moderator must not refund or permanent-ban (the function raises before touching data, wrong id is fine)
  foreach uid in array array[u_sup, u_mod] loop
    perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated', 'aal', 'aal2', 'iat', extract(epoch from now())::bigint)::text, true);
    set local role authenticated;
    begin perform public.admin_refund_boost(gen_random_uuid(), 'role test, must be refused'); reset role; insert into zz_res values ('action', 'admin_refund_boost allowed for ' || uid, 'FAIL');
    exception when others then reset role; insert into zz_res values ('action', 'admin_refund_boost refused for ' || uid || ' (' || left(sqlerrm, 40) || ')', case when sqlerrm ilike '%permission%' or sqlerrm ilike '%not allowed%' or sqlerrm ilike '%denied%' then 'PASS' else 'CHECK: refused, but for another reason' end); end;
  end loop;
end $$;

select * from zz_res order by case when result like 'PASS' then 2 else 1 end, area, what;
rollback;
