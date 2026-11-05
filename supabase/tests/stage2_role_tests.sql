-- Stage 2 role tests. Run in the Supabase SQL editor AFTER 039 + 040 (TEST/STAGING project preferred).
-- Everything runs inside one transaction and is ROLLED BACK at the end: no data is kept. It creates fake @example.com accounts only.
-- Output: one table at the end. Every row must say PASS. A FAIL row names the thing that is wrong.
-- NOT RUN by the author (no Postgres in the build sandbox): run it once and send me any FAIL rows.
-- If your project does not allow inserting into auth.users from the editor, run it from the Supabase CLI (supabase db ... psql) instead.

begin;

create or replace function public.zz_test_try(sql_ text) returns boolean language plpgsql as $$
begin execute sql_; return true; exception when others then return false; end $$;


do $$
declare
  uid_owner uuid := gen_random_uuid(); uid_admin uuid := gen_random_uuid(); uid_mod uuid := gen_random_uuid();
  uid_sup uuid := gen_random_uuid();   uid_none uuid := gen_random_uuid(); uid_o2 uuid := gen_random_uuid(); uid_o3 uuid := gen_random_uuid();
  tgt uuid[] := '{}'; rep uuid[] := '{}'; rid uuid[] := '{}'; i int; res jsonb := '[]'::jsonb; ok boolean; tok text; inv jsonb; t uuid; r uuid; cl text; apv1 uuid; apv2 uuid; rid_staff uuid;

  -- sets the session to a signed-in user (aal1, token issued "now")
begin
  -- ---------- fixtures (as postgres) ----------
  for i in 1..7 loop
    t := case i when 1 then uid_owner when 2 then uid_admin when 3 then uid_mod when 4 then uid_sup when 5 then uid_none when 6 then uid_o2 else uid_o3 end;
    insert into auth.users (id, email, email_confirmed_at, aud, role) values (t, 't' || i || '-' || left(t::text, 6) || '@example.com', now(), 'authenticated', 'authenticated');
    insert into public.users (id, full_name) values (t, 'Test ' || i);
  end loop;
  perform public.bootstrap_owner((select email from auth.users where id = uid_owner));
  insert into public.admin_members (user_id, email, role) values
    (uid_admin, (select email from auth.users where id = uid_admin), 'admin'),
    (uid_mod,   (select email from auth.users where id = uid_mod),   'moderator'),
    (uid_sup,   (select email from auth.users where id = uid_sup),   'support');
  for i in 1..14 loop                       -- 14 reported users + 14 reporters + 14 pending reports
    tgt := tgt || gen_random_uuid(); rep := rep || gen_random_uuid();
    insert into auth.users (id, email, email_confirmed_at, aud, role) values (tgt[i], 'tg' || i || '-' || left(tgt[i]::text, 6) || '@example.com', now(), 'authenticated', 'authenticated');
    insert into auth.users (id, email, email_confirmed_at, aud, role) values (rep[i], 'rp' || i || '-' || left(rep[i]::text, 6) || '@example.com', now(), 'authenticated', 'authenticated');
    insert into public.users (id, full_name) values (tgt[i], 'Target ' || i), (rep[i], 'Reporter ' || i);
    insert into public.reports (reporter_id, reported_user_id, reason) values (rep[i], tgt[i], 'scam') returning id into r;
    rid := rid || r;
  end loop;

  insert into public.reports (reporter_id, reported_user_id, reason) values (rep[8], uid_admin, 'scam') returning id into rid_staff;
  -- ---------- helper: act as a user ----------
  -- (inline: reset role; set local role authenticated; set_config('request.jwt.claims', ...))

  -- ===== O = owner, A = admin, M = moderator, S = support, N = signed in but not staff =====
  -- each check: (who, what, expected, got)
  -- 1) my_admin_access
  for cl in select unnest(array['O','A','M','S','N']) loop
    execute 'reset role';
    execute 'set local role authenticated';
    perform set_config('request.jwt.claims', json_build_object('sub',
      case cl when 'O' then uid_owner when 'A' then uid_admin when 'M' then uid_mod when 'S' then uid_sup else uid_none end,
      'role','authenticated','iat', extract(epoch from now())::bigint)::text, true);
    res := res || jsonb_build_array(jsonb_build_object('who',cl,'what','is staff', 'expect', cl <> 'N', 'got', (public.my_admin_access() ->> 'active')::boolean));
    -- permission matrix: expected sets per role
    res := res || jsonb_build_array(
      jsonb_build_object('who',cl,'what','has id_review',        'expect', cl in ('O','A','M'), 'got', public.has_permission('id_review')),
      jsonb_build_object('who',cl,'what','has reports_act',      'expect', cl in ('O','A','M'), 'got', public.has_permission('reports_act')),
      jsonb_build_object('who',cl,'what','has suspend_short',    'expect', cl in ('O','A','M'), 'got', public.has_permission('suspend_short')),
      jsonb_build_object('who',cl,'what','has suspend_long',     'expect', cl in ('O','A'),     'got', public.has_permission('suspend_long')),
      jsonb_build_object('who',cl,'what','has ban_permanent',    'expect', cl = 'O',            'got', public.has_permission('ban_permanent')),
      jsonb_build_object('who',cl,'what','has unban',            'expect', cl = 'O',            'got', public.has_permission('unban')),
      jsonb_build_object('who',cl,'what','has listing_takedown', 'expect', cl = 'O',            'got', public.has_permission('listing_takedown')),
      jsonb_build_object('who',cl,'what','has payments_view',    'expect', cl = 'O',            'got', public.has_permission('payments_view')),
      jsonb_build_object('who',cl,'what','has csv_export',       'expect', cl = 'O',            'got', public.has_permission('csv_export')),
      jsonb_build_object('who',cl,'what','has audit_view_all',   'expect', cl = 'O',            'got', public.has_permission('audit_view_all')),
      jsonb_build_object('who',cl,'what','has members_manage',   'expect', cl = 'O',            'got', public.has_permission('members_manage')),
      jsonb_build_object('who',cl,'what','has ai_settings',      'expect', cl = 'O',            'got', public.has_permission('ai_settings')),
      jsonb_build_object('who',cl,'what','has app_config',       'expect', cl = 'O',            'got', public.has_permission('app_config')),
      jsonb_build_object('who',cl,'what','has id_view_raw',      'expect', cl = 'O',            'got', public.has_permission('id_view_raw')),
      jsonb_build_object('who',cl,'what','has audit_view_own',   'expect', cl in ('O','A','M','S'), 'got', public.has_permission('audit_view_own')));
  end loop;

  -- 2) actions through the real RPCs (one fresh report each; "expect" = the call is allowed)
  -- moderator
  execute 'reset role'; execute 'set local role authenticated';
  perform set_config('request.jwt.claims', json_build_object('sub', uid_mod, 'role','authenticated','iat', extract(epoch from now())::bigint)::text, true);
  res := res || jsonb_build_array(
    jsonb_build_object('who','M','what','suspend 24h',        'expect', true,  'got', public.zz_test_try(format('select public.admin_review_report(%L,''suspend'',24,''test'',null)', rid[1]))),
    jsonb_build_object('who','M','what','suspend 10 days',    'expect', false, 'got', public.zz_test_try(format('select public.admin_review_report(%L,''suspend'',240,''test'',null)', rid[2]))),
    jsonb_build_object('who','M','what','ban',                'expect', false, 'got', public.zz_test_try(format('select public.admin_review_report(%L,''ban'',null,''test'',null)', rid[3]))),
    jsonb_build_object('who','M','what','dismiss',            'expect', true,  'got', public.zz_test_try(format('select public.admin_review_report(%L,''dismiss'',null,null,null)', rid[4]))),
    jsonb_build_object('who','M','what','unban',              'expect', false, 'got', public.zz_test_try(format('select public.admin_unban(%L,null)', tgt[3]))),
    jsonb_build_object('who','M','what','takedown (needs a listing)', 'expect', false, 'got', public.zz_test_try(format('select public.admin_takedown(%L,''x'')', gen_random_uuid()))),
    jsonb_build_object('who','M','what','refund',             'expect', false, 'got', public.zz_test_try(format('select public.admin_refund_boost(%L,''x'')', gen_random_uuid()))),
    jsonb_build_object('who','M','what','set_ai_enabled',     'expect', false, 'got', public.zz_test_try('select public.set_ai_enabled(false)')),
    jsonb_build_object('who','M','what','list members',       'expect', false, 'got', public.zz_test_try('select * from public.admin_members_list()')),
    jsonb_build_object('who','M','what','invite',             'expect', false, 'got', public.zz_test_try('select public.admin_invite_create(''x@example.com'',''support'')')),
    jsonb_build_object('who','M','what','csv log',            'expect', false, 'got', public.zz_test_try('select public.admin_log_export(''reports'',3)')),
    jsonb_build_object('who','M','what','all activity',       'expect', false, 'got', public.zz_test_try('select * from public.admin_activity_all(10)')),
    jsonb_build_object('who','M','what','my activity',        'expect', true,  'got', public.zz_test_try('select * from public.admin_my_activity(10)')),
    jsonb_build_object('who','M','what','set min version',    'expect', false, 'got', public.zz_test_try('select public.admin_set_min_app_version('''')')),
    jsonb_build_object('who','M','what','request ban approval','expect', true, 'got', public.zz_test_try(format('select public.request_admin_approval(''report_ban'', jsonb_build_object(''rid'',%L,''reason'',''scam''), null)', rid[5]))),
    jsonb_build_object('who','M','what','request a 2nd ban approval','expect', true, 'got', public.zz_test_try(format('select public.request_admin_approval(''report_ban'', jsonb_build_object(''rid'',%L,''reason'',''scam''), null)', rid[9]))));
  execute 'reset role';
  select id into apv1 from public.admin_approvals where payload ->> 'rid' = rid[5]::text;
  select id into apv2 from public.admin_approvals where payload ->> 'rid' = rid[9]::text;
  execute 'set local role authenticated';
  perform set_config('request.jwt.claims', json_build_object('sub', uid_mod, 'role','authenticated','iat', extract(epoch from now())::bigint)::text, true);
  res := res || jsonb_build_array(
    jsonb_build_object('who','M','what','decide an approval (no permission)','expect', false, 'got', public.zz_test_try(format('select public.decide_admin_approval(%L, true, null)', apv1))));

  -- admin
  execute 'reset role'; execute 'set local role authenticated';
  perform set_config('request.jwt.claims', json_build_object('sub', uid_admin, 'role','authenticated','iat', extract(epoch from now())::bigint)::text, true);
  res := res || jsonb_build_array(
    jsonb_build_object('who','A','what','suspend 10 days',    'expect', true,  'got', public.zz_test_try(format('select public.admin_review_report(%L,''suspend'',240,''test'',null)', rid[2]))),
    jsonb_build_object('who','A','what','ban',                'expect', false, 'got', public.zz_test_try(format('select public.admin_review_report(%L,''ban'',null,''test'',null)', rid[3]))),
    jsonb_build_object('who','A','what','approve a ban request','expect', false, 'got', public.zz_test_try(format('select public.decide_admin_approval(%L, true, null)', apv1))),
    jsonb_build_object('who','A','what','deny a ban request','expect', true,  'got', public.zz_test_try(format('select public.decide_admin_approval(%L, false, ''no'')', apv1))),
    jsonb_build_object('who','A','what','unban',              'expect', false, 'got', public.zz_test_try(format('select public.admin_unban(%L,null)', tgt[3]))),
    jsonb_build_object('who','A','what','list members',       'expect', false, 'got', public.zz_test_try('select * from public.admin_members_list()')),
    jsonb_build_object('who','A','what','insert into admin_members directly','expect', false, 'got', public.zz_test_try(format('insert into public.admin_members (user_id,email,role) values (%L,''z@example.com'',''owner'')', uid_none))),
    jsonb_build_object('who','A','what','write admin_audit_log directly','expect', false, 'got', public.zz_test_try('insert into public.admin_audit_log (action) values (''fake'')')));

  -- owner
  execute 'reset role'; execute 'set local role authenticated';
  perform set_config('request.jwt.claims', json_build_object('sub', uid_owner, 'role','authenticated','iat', extract(epoch from now())::bigint)::text, true);
  res := res || jsonb_build_array(
    jsonb_build_object('who','O','what','ban',                'expect', true,  'got', public.zz_test_try(format('select public.admin_review_report(%L,''ban'',null,''test'',null)', rid[6]))),
    jsonb_build_object('who','O','what','unban',              'expect', true,  'got', public.zz_test_try(format('select public.admin_unban(%L,null)', tgt[6]))),
    jsonb_build_object('who','O','what','ban a staff member', 'expect', false, 'got', public.zz_test_try(format('select public.admin_review_report(%L,''ban'',null,''x'',null)', rid_staff))),
    jsonb_build_object('who','O','what','approve a moderator ban request','expect', true, 'got', public.zz_test_try(format('select public.decide_admin_approval(%L, true, null)', apv2))),
    jsonb_build_object('who','O','what','list members',       'expect', true,  'got', public.zz_test_try('select * from public.admin_members_list()')),
    jsonb_build_object('who','O','what','suspend self',       'expect', false, 'got', public.zz_test_try(format('select public.admin_member_suspend(%L,true,''x'')', uid_owner))),
    jsonb_build_object('who','O','what','demote self',        'expect', false, 'got', public.zz_test_try(format('select public.admin_member_update(%L,''support'')', uid_owner))),
    jsonb_build_object('who','O','what','csv log',            'expect', true,  'got', public.zz_test_try('select public.admin_log_export(''reports'',3)')),
    jsonb_build_object('who','O','what','all activity',       'expect', true,  'got', public.zz_test_try('select * from public.admin_activity_all(10)')));

  -- invites: the owner invites a moderator; wrong account cannot redeem; right account can, only once
  inv := public.admin_invite_create((select email from auth.users where id = uid_none), 'moderator');
  tok := inv ->> 'token';
  res := res || jsonb_build_array(jsonb_build_object('who','O','what','invite as owner role','expect', false, 'got', public.zz_test_try('select public.admin_invite_create(''o@example.com'',''owner'')')),
                                  jsonb_build_object('who','O','what','invite with owner-only grant','expect', false, 'got', public.zz_test_try('select public.admin_invite_create(''g@example.com'',''support'',array[''csv_export''])')));
  execute 'reset role'; execute 'set local role authenticated';
  perform set_config('request.jwt.claims', json_build_object('sub', uid_sup, 'role','authenticated','iat', extract(epoch from now())::bigint)::text, true);
  res := res || jsonb_build_array(jsonb_build_object('who','S','what','redeem invite meant for someone else','expect', false, 'got', public.zz_test_try(format('select public.admin_invite_accept(%L)', tok))));
  execute 'reset role'; execute 'set local role authenticated';
  perform set_config('request.jwt.claims', json_build_object('sub', uid_none, 'role','authenticated','iat', extract(epoch from now())::bigint)::text, true);
  res := res || jsonb_build_array(jsonb_build_object('who','N','what','redeem wrong token','expect', false, 'got', public.zz_test_try('select public.admin_invite_accept(''nope'')')),
                                  jsonb_build_object('who','N','what','redeem own invite','expect', true,  'got', public.zz_test_try(format('select public.admin_invite_accept(%L)', tok))),
                                  jsonb_build_object('who','N','what','redeem same invite twice','expect', false, 'got', public.zz_test_try(format('select public.admin_invite_accept(%L)', tok))));

  -- expiry and logout-all (as postgres, then check as the user)
  execute 'reset role';
  update public.admin_members set expires_at = now() - interval '1 minute' where user_id = uid_none;
  execute 'set local role authenticated';
  perform set_config('request.jwt.claims', json_build_object('sub', uid_none, 'role','authenticated','iat', extract(epoch from now())::bigint)::text, true);
  res := res || jsonb_build_array(jsonb_build_object('who','N(expired member)','what','has dashboard_view','expect', false, 'got', public.has_permission('dashboard_view')));
  execute 'reset role';
  update public.admin_members set expires_at = null, revoked_before = now() + interval '1 hour' where user_id = uid_mod;   -- "log out all devices"
  execute 'set local role authenticated';
  perform set_config('request.jwt.claims', json_build_object('sub', uid_mod, 'role','authenticated','iat', extract(epoch from now())::bigint)::text, true);
  res := res || jsonb_build_array(jsonb_build_object('who','M(logged out)','what','has dashboard_view','expect', false, 'got', public.has_permission('dashboard_view')));
  execute 'reset role';
  update public.admin_members set revoked_before = null, status = 'suspended' where user_id = uid_mod;
  execute 'set local role authenticated';
  perform set_config('request.jwt.claims', json_build_object('sub', uid_mod, 'role','authenticated','iat', extract(epoch from now())::bigint)::text, true);
  res := res || jsonb_build_array(jsonb_build_object('who','M(suspended)','what','has dashboard_view','expect', false, 'got', public.has_permission('dashboard_view')));

  -- owner rules (as postgres: the trigger applies to the SQL editor too)
  execute 'reset role';
  res := res || jsonb_build_array(
    jsonb_build_object('who','SQL','what','delete owner row',     'expect', false, 'got', public.zz_test_try(format('delete from public.admin_members where user_id = %L', uid_owner))),
    jsonb_build_object('who','SQL','what','demote owner row',     'expect', false, 'got', public.zz_test_try(format('update public.admin_members set role = ''support'' where user_id = %L', uid_owner))),
    jsonb_build_object('who','SQL','what','suspend owner row',    'expect', false, 'got', public.zz_test_try(format('update public.admin_members set status = ''suspended'' where user_id = %L', uid_owner))),
    jsonb_build_object('who','SQL','what','second owner',         'expect', true,  'got', public.zz_test_try(format('select public.bootstrap_owner(%L)', (select email from auth.users where id = uid_o2)))),
    jsonb_build_object('who','SQL','what','third owner (max 2)',  'expect', false, 'got', public.zz_test_try(format('select public.bootstrap_owner(%L)', (select email from auth.users where id = uid_o3)))),
    jsonb_build_object('who','SQL','what','grant csv to a moderator','expect', false, 'got', public.zz_test_try(format('update public.admin_members set extra_grant = array[''csv_export''] where user_id = %L', uid_mod))),
    jsonb_build_object('who','SQL','what','update audit log',     'expect', false, 'got', public.zz_test_try('update public.admin_audit_log set action = ''x''')),
    jsonb_build_object('who','SQL','what','delete audit log',     'expect', false, 'got', public.zz_test_try('delete from public.admin_audit_log')),
    jsonb_build_object('who','SQL','what','truncate audit log',   'expect', false, 'got', public.zz_test_try('truncate public.admin_audit_log')));

  -- store the result so the select after the block can show it
  perform set_config('bofo.test_result', res::text, true);
end $$;

reset role;
select r ->> 'who' as who, r ->> 'what' as what, (r ->> 'expect') as expected, (r ->> 'got') as got,
       case when (r ->> 'expect') = (r ->> 'got') then 'PASS' else 'FAIL' end as result
  from jsonb_array_elements(current_setting('bofo.test_result')::jsonb) r
 order by (case when (r ->> 'expect') = (r ->> 'got') then 1 else 0 end), 1, 2;

rollback;
