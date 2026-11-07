-- VERIFY_AFTER.sql: READ-ONLY proof queries for migrations 038..045. Run in the Supabase SQL editor after each migration step (see RUN_ORDER.md).
-- Changes nothing. Every query says in its first column what a good result looks like.  NOT RUN by the author (no Postgres here): run it and send me any row that is not OK.

-- ===== A. Run after 038..044 (before 045) =====

-- A1. Every function the migrations create exists. GOOD: 0 rows.
with want(name) as (select unnest(array[
    'admin_activity_all',
    'admin_appeal_decide',
    'admin_appeals_list',
    'admin_approvals_list',
    'admin_approve_verification',
    'admin_banned_list',
    'admin_break_glass_end',
    'admin_break_glass_grant',
    'admin_break_glass_list',
    'admin_claim_item',
    'admin_clear_escalation',
    'admin_escalate_report',
    'admin_flag_second_opinion',
    'admin_invite_accept',
    'admin_invite_create',
    'admin_invite_revoke',
    'admin_issue_strike',
    'admin_item_marks_list',
    'admin_log',
    'admin_log_export',
    'admin_member_logout_all',
    'admin_member_remove',
    'admin_member_sessions',
    'admin_member_suspend',
    'admin_member_update',
    'admin_members_list',
    'admin_my_activity',
    'admin_owner_only_keys',
    'admin_permission_keys',
    'admin_refund_boost',
    'admin_release_item',
    'admin_report_takedown',
    'admin_reset_mfa',
    'admin_review_report',
    'admin_risky_actions',
    'admin_session_ok',
    'admin_set_min_admin_app_version',
    'admin_set_min_admin_client_version',
    'admin_set_min_app_version',
    'admin_takedown',
    'admin_touch_session',
    'admin_unban',
    'admin_unusual_activity',
    'admin_user_strikes',
    'ai_chat_log_wipe',
    'app_version_ok',
    'bootstrap_owner',
    'break_glass_keys',
    'check_id_number',
    'decide_admin_approval',
    'has_permission',
    'hit_rate_limit',
    'is_admin',
    'is_staff',
    'my_admin_access',
    'my_appeal',
    'push_admin_tokens',
    'push_test_result',
    'register_push_token',
    'request_admin_approval',
    'require_permission',
    'review_verification',
    'send_test_push',
    'set_ai_enabled',
    'set_min_admin_client_version',
    'staff_has',
    'strike_step_for',
    'submit_appeal']))
select 'A1 MISSING function' as check, w.name from want w
where not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = w.name);

-- A2. Trigger functions exist. GOOD: 0 rows.
with want(name) as (select unnest(array[
    'admin_audit_immutable',
    'copy_audit_log',
    'guard_admin_members',
    'protect_bh',
    'trg_push_approval',
    'trg_push_decided',
    'trg_push_unusual',
    'trg_rate_chat',
    'trg_rate_reports']))
select 'A2 MISSING trigger function' as check, w.name from want w
where not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = w.name);

-- A3. Row level security is ON for every new table. GOOD: every row rls_on = true.
select 'A3 RLS' as check, c.relname as tbl, c.relrowsecurity as rls_on
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relname in ('admin_roles',
    'admin_members',
    'admin_sessions',
    'admin_invites',
    'admin_audit_log',
    'admin_approvals',
    'rate_limits',
    'admin_item_marks',
    'user_strikes',
    'appeals',
    'admin_break_glass') order by 2;   -- 11 rows expected

-- A4. Anon / signed-in roles have NO direct table rights on the new tables. GOOD: 0 rows.
select 'A4 DIRECT TABLE GRANT' as check, table_name, grantee, privilege_type
from information_schema.role_table_grants
where table_schema = 'public' and grantee in ('anon', 'authenticated', 'PUBLIC')
  and table_name in ('admin_roles',
    'admin_members',
    'admin_sessions',
    'admin_invites',
    'admin_audit_log',
    'admin_approvals',
    'rate_limits',
    'admin_item_marks',
    'user_strikes',
    'appeals',
    'admin_break_glass');

-- A5. Functions callable by the logged-out role (anon). GOOD: only helpers you accept (today: see STATE_VERIFY_2.md, admin_session_ok = Low, fix in Stage 3 N-3). Admin_* must never appear.
select 'A5 ANON CAN EXECUTE' as check, p.proname, pg_get_function_identity_arguments(p.oid) as args
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.prosecdef and has_function_privilege('anon', p.oid, 'execute')
  and p.proname in ('admin_activity_all',
    'admin_appeal_decide',
    'admin_appeals_list',
    'admin_approvals_list',
    'admin_approve_verification',
    'admin_banned_list',
    'admin_break_glass_end',
    'admin_break_glass_grant',
    'admin_break_glass_list',
    'admin_claim_item',
    'admin_clear_escalation',
    'admin_escalate_report',
    'admin_flag_second_opinion',
    'admin_invite_accept',
    'admin_invite_create',
    'admin_invite_revoke',
    'admin_issue_strike',
    'admin_item_marks_list',
    'admin_log',
    'admin_log_export',
    'admin_member_logout_all',
    'admin_member_remove',
    'admin_member_sessions',
    'admin_member_suspend',
    'admin_member_update',
    'admin_members_list',
    'admin_my_activity',
    'admin_owner_only_keys',
    'admin_permission_keys',
    'admin_refund_boost',
    'admin_release_item',
    'admin_report_takedown',
    'admin_reset_mfa',
    'admin_review_report',
    'admin_risky_actions',
    'admin_session_ok',
    'admin_set_min_admin_app_version',
    'admin_set_min_admin_client_version',
    'admin_set_min_app_version',
    'admin_takedown',
    'admin_touch_session',
    'admin_unban',
    'admin_unusual_activity',
    'admin_user_strikes',
    'ai_chat_log_wipe',
    'app_version_ok',
    'bootstrap_owner',
    'break_glass_keys',
    'check_id_number',
    'decide_admin_approval',
    'has_permission',
    'hit_rate_limit',
    'is_admin',
    'is_staff',
    'my_admin_access',
    'my_appeal',
    'push_admin_tokens',
    'push_test_result',
    'register_push_token',
    'request_admin_approval',
    'require_permission',
    'review_verification',
    'send_test_push',
    'set_ai_enabled',
    'set_min_admin_client_version',
    'staff_has',
    'strike_step_for',
    'submit_appeal') order by 2;

-- A6. SECURITY DEFINER functions without a fixed search_path. GOOD: 0 rows.
select 'A6 NO search_path' as check, p.proname
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.prosecdef and p.proname in ('admin_activity_all',
    'admin_appeal_decide',
    'admin_appeals_list',
    'admin_approvals_list',
    'admin_approve_verification',
    'admin_banned_list',
    'admin_break_glass_end',
    'admin_break_glass_grant',
    'admin_break_glass_list',
    'admin_claim_item',
    'admin_clear_escalation',
    'admin_escalate_report',
    'admin_flag_second_opinion',
    'admin_invite_accept',
    'admin_invite_create',
    'admin_invite_revoke',
    'admin_issue_strike',
    'admin_item_marks_list',
    'admin_log',
    'admin_log_export',
    'admin_member_logout_all',
    'admin_member_remove',
    'admin_member_sessions',
    'admin_member_suspend',
    'admin_member_update',
    'admin_members_list',
    'admin_my_activity',
    'admin_owner_only_keys',
    'admin_permission_keys',
    'admin_refund_boost',
    'admin_release_item',
    'admin_report_takedown',
    'admin_reset_mfa',
    'admin_review_report',
    'admin_risky_actions',
    'admin_session_ok',
    'admin_set_min_admin_app_version',
    'admin_set_min_admin_client_version',
    'admin_set_min_app_version',
    'admin_takedown',
    'admin_touch_session',
    'admin_unban',
    'admin_unusual_activity',
    'admin_user_strikes',
    'ai_chat_log_wipe',
    'app_version_ok',
    'bootstrap_owner',
    'break_glass_keys',
    'check_id_number',
    'decide_admin_approval',
    'has_permission',
    'hit_rate_limit',
    'is_admin',
    'is_staff',
    'my_admin_access',
    'my_appeal',
    'push_admin_tokens',
    'push_test_result',
    'register_push_token',
    'request_admin_approval',
    'require_permission',
    'review_verification',
    'send_test_push',
    'set_ai_enabled',
    'set_min_admin_client_version',
    'staff_has',
    'strike_step_for',
    'submit_appeal',
    'admin_audit_immutable',
    'copy_audit_log',
    'guard_admin_members',
    'protect_bh',
    'trg_push_approval',
    'trg_push_decided',
    'trg_push_unusual',
    'trg_rate_chat',
    'trg_rate_reports')
  and not exists (select 1 from unnest(coalesce(p.proconfig, '{}')) c where c like 'search_path=%');

-- A7. Policies on the sensitive tables (read them: each must name a permission, not just is_admin()). GOOD: eyeball.
select 'A7 POLICY' as check, tablename, policyname, cmd, roles, qual
from pg_policies
where schemaname = 'public' and tablename in ('payments','boosts','audit_logs','ai_chat_log','ai_unanswered','ai_feedback','banned_identities','blocks_reports','user_agreements','owner_verifications','admin_audit_log','colleges','amenities')
order by tablename, policyname;

-- A8. Role presets. GOOD: owner, admin, moderator, support (+ legacy_admin until 045).
select 'A8 ROLE' as check, key, array_length(permissions, 1) as perms from public.admin_roles order by sort;

-- A9. Team state. GOOD: owners >= 1, admins_without_member_row = 0 BEFORE you run 045.
select 'A9 TEAM' as check,
  (select count(*) from public.admin_members where role = 'owner' and status = 'active') as active_owners,
  (select count(*) from public.users u where u.role = 'admin' and not exists (select 1 from public.admin_members m where m.user_id = u.id)) as admins_without_member_row;

-- A10. Settings that 045 and the version gates read. GOOD: min_app_version is NOT empty before 045.
select 'A10 SETTING' as check, key, value from public.app_settings
where key in ('min_app_version','min_admin_app_version','min_admin_client_version') order by key;

-- A11. device_tokens.app column and 3-argument register_push_token only (042). GOOD: 1 row for app, 1 row (3 args) for the function.
select 'A11a COLUMN' as check, column_name, column_default from information_schema.columns where table_schema = 'public' and table_name = 'device_tokens' and column_name = 'app';
select 'A11b FUNCTION' as check, pg_get_function_identity_arguments(oid) from pg_proc where proname = 'register_push_token' and pronamespace = 'public'::regnamespace;

-- ===== B. Run after 045 only =====

-- B1. Functions 045 drops are gone. GOOD: 0 rows.
select 'B1 STILL THERE' as check, proname from pg_proc where pronamespace = 'public'::regnamespace and proname in ('admin_takedown',
    'send_test_push',
    'push_test_result');

-- B2. legacy_admin preset removed. GOOD: 0 rows.
select 'B2 PRESET' as check, key from public.admin_roles where key = 'legacy_admin';

-- B3. has_permission no longer has the legacy fallback. GOOD: 0 rows (the word 'legacy' only in comments is fine; read the row if one appears).
select 'B3 LEGACY TEXT' as check, proname from pg_proc
where pronamespace = 'public'::regnamespace and proname in ('has_permission','is_staff','staff_has','my_admin_access')
  and regexp_replace(pg_get_functiondef(oid), '--[^\n]*', '', 'g') ~* 'role\s*=\s*''admin''' ;

-- B4. Break-glass kept. GOOD: 2 rows.
select 'B4 BREAK-GLASS' as check, proname from pg_proc where pronamespace = 'public'::regnamespace and proname in ('break_glass_keys','admin_break_glass_grant');
