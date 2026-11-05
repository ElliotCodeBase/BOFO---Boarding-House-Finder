-- 039: BOFO Admin rebuild, Stage 2 (part 1 of 2): server-enforced roles and permissions. Run BEFORE 040. Safe to run twice.
--
-- WHAT THIS ADDS (nothing the current app does is removed; the old app keeps working, see "LEGACY" below)
--   * admin_roles       role presets (owner, admin, moderator, support, legacy_admin) = lists of permission keys
--   * admin_members     who is staff: role, status, optional expiry, per-person grants/denies, "log out everywhere" marker
--   * admin_invites     one-time invites, token stored as a hash, 48 h, bound to the invited Google email
--   * admin_audit_log   append-only log (no update/delete for anyone through the API, a trigger refuses it too)
--   * admin_approvals   a moderator asks, an admin/owner approves or denies; approval runs the action as the approver
--   * rate_limits       small per-user counters (fixes S-05, S-06)
--   * has_permission(p), require_permission(p), my_admin_access(), is_staff()
--   * is_admin() is REDEFINED: true for any active staff member or a legacy admin (users.role = 'admin' with no member row).
--     Every action that matters is gated by a specific permission in 040, not by is_admin().
--
-- LEGACY: a user with users.role = 'admin' and NO row in admin_members keeps FULL access (same as before) until Stage 5.
--   The day you run bootstrap_owner('you@gmail.com') below, that person gets a member row and the member rules apply to them.
--   After Stage 5 the legacy rule is removed.
--
-- AFTER RUNNING (SQL editor, once):   select public.bootstrap_owner('YOUR-OWNER-GOOGLE-EMAIL');
--   (max 2 owners; owners cannot be changed or removed through the API.)
--
-- ROLLBACK (returns to the 038 state; the old is_admin from 018 is restored):
--   create or replace function public.is_admin() returns boolean language sql stable security definer set search_path = public as $f$
--     select exists (select 1 from public.users where id = auth.uid() and role = 'admin')
--        and (not coalesce((select f.value from public.app_flags f where f.key = 'require_admin_mfa'), false) or coalesce(auth.jwt() ->> 'aal', 'aal1') = 'aal2')
--        and public.app_version_ok(); $f$;
--   then run the rollback of 040, then:
--   drop table if exists public.admin_approvals, public.admin_invites, public.admin_sessions, public.admin_members, public.admin_roles, public.rate_limits cascade;
--   drop table if exists public.admin_audit_log cascade;   -- (this DESTROYS the new audit trail: export it first)
--   drop function if exists public.has_permission(text), public.require_permission(text), public.my_admin_access(), public.is_staff(),
--     public.admin_permission_keys(), public.admin_owner_only_keys(), public.admin_log(text, text, text, jsonb), public.hit_rate_limit(text, int, int),
--     public.bootstrap_owner(text), public.admin_session_ok() cascade;

-- ===================== 1) permission keys (one list, used by checks) =====================
create or replace function public.admin_permission_keys() returns text[] language sql immutable as $$
  select array[
    'legacy_admin',                                  -- old app compatibility (is_admin() for old builds)
    'dashboard_view', 'id_review', 'id_view_raw',
    'reports_view', 'reports_act', 'suspend_short', 'suspend_long', 'ban_permanent', 'unban', 'banned_view',
    'listing_takedown',
    'ai_answer', 'ai_log_view', 'ai_settings',
    'payments_view', 'payments_manage',
    'csv_export',
    'audit_view_own', 'audit_view_all',
    'members_manage', 'announce_push', 'mfa_manage', 'app_config', 'user_data_delete',
    'approvals_request', 'approvals_decide'
  ]::text[];
$$;

-- the 12 OWNER-ONLY items from the master prompt, as keys. Never grantable to a non-owner (not by preset, not by per-person toggle).
create or replace function public.admin_owner_only_keys() returns text[] language sql immutable as $$
  select array[
    'members_manage',                  -- 1  add / remove / suspend / change sub-admins
    'payments_view', 'payments_manage',-- 2  payments
    'csv_export',                      -- 3  CSV export
    'ban_permanent',                   -- 4  permanent ban
    'unban',                           -- 5  lifting a ban
    'listing_takedown',                -- 6  permanent take-down of a listing
    'audit_view_all',                  -- 7  other admins' activity
    'ai_settings', 'announce_push', 'ai_log_view', -- 8  AI settings, announcements, push broadcasts (+ AI chat log: S-17)
    'mfa_manage',                      -- 9  another admin's MFA
    'app_config',                      -- 10 min version, flags, config
    'user_data_delete',                -- 11 delete user data
    'id_view_raw'                      -- 12 raw ID images outside the review screen
  ]::text[];
$$;

-- ===================== 2) tables =====================
create table if not exists public.admin_roles (
  key         text primary key,
  label       text not null,
  permissions text[] not null default '{}',
  sort        int not null default 0,
  constraint admin_roles_known check (permissions <@ public.admin_permission_keys())
);
alter table public.admin_roles enable row level security;
revoke all on public.admin_roles from anon, authenticated;     -- read only through my_admin_access() / admin_members_list()

insert into public.admin_roles (key, label, permissions, sort) values
 ('owner',        'Owner',      public.admin_permission_keys(), 1),
 ('admin',        'Admin',      array['dashboard_view','id_review','reports_view','reports_act','suspend_short','suspend_long','banned_view',
                                      'ai_answer','audit_view_own','approvals_request','approvals_decide'], 2),
 ('moderator',    'Moderator',  array['dashboard_view','id_review','reports_view','reports_act','suspend_short','banned_view',
                                      'ai_answer','audit_view_own','approvals_request'], 3),
 ('support',      'Support',    array['dashboard_view','reports_view','banned_view','ai_answer','audit_view_own'], 4),
 ('legacy_admin', 'Legacy admin (old app)', public.admin_permission_keys(), 9)
on conflict (key) do update set label = excluded.label, permissions = excluded.permissions, sort = excluded.sort;

create table if not exists public.admin_members (
  user_id        uuid primary key references public.users(id) on delete cascade,
  email          text not null,
  role           text not null references public.admin_roles(key),
  status         text not null default 'active' check (status in ('active','suspended')),
  invited_by     uuid references public.users(id) on delete set null,
  created_at     timestamptz not null default now(),
  expires_at     timestamptz,                                  -- null = no expiry
  extra_grant    text[] not null default '{}',                 -- per-person toggles on top of the preset
  extra_deny     text[] not null default '{}',
  revoked_before timestamptz,                                  -- sessions (JWT iat) older than this stop working
  suspended_reason text,
  last_active_at timestamptz,
  constraint admin_members_known check (extra_grant <@ public.admin_permission_keys() and extra_deny <@ public.admin_permission_keys())
);
create unique index if not exists admin_members_email_uq on public.admin_members (lower(email));
alter table public.admin_members enable row level security;
revoke all on public.admin_members from anon, authenticated;

create table if not exists public.admin_sessions (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references public.users(id) on delete cascade,
  device      text,
  started_at  timestamptz not null default now(),
  last_seen_at timestamptz not null default now()
);
create index if not exists admin_sessions_user on public.admin_sessions (user_id, last_seen_at desc);
alter table public.admin_sessions enable row level security;
revoke all on public.admin_sessions from anon, authenticated;

create table if not exists public.admin_invites (
  id           uuid primary key default gen_random_uuid(),
  email        text not null,
  role         text not null references public.admin_roles(key),
  extra_grant  text[] not null default '{}',
  member_expires_at timestamptz,
  token_hash   text not null unique,                           -- sha256 of the one-time token; the token itself is never stored
  created_by   uuid references public.users(id) on delete set null,
  created_at   timestamptz not null default now(),
  expires_at   timestamptz not null default now() + interval '48 hours',
  accepted_at  timestamptz,
  accepted_by  uuid references public.users(id) on delete set null,
  revoked_at   timestamptz,
  constraint admin_invites_not_owner check (role <> 'owner'),
  constraint admin_invites_known check (extra_grant <@ public.admin_permission_keys())
);
alter table public.admin_invites enable row level security;
revoke all on public.admin_invites from anon, authenticated;

create table if not exists public.admin_audit_log (
  id          bigserial primary key,
  created_at  timestamptz not null default now(),
  actor_id    uuid,                                            -- no FK on purpose: the trail must survive account deletion
  actor_email text,
  actor_role  text,
  action      text not null,
  target_type text,
  target_id   text,
  details     jsonb
);
create index if not exists admin_audit_actor on public.admin_audit_log (actor_id, created_at desc);
create index if not exists admin_audit_time  on public.admin_audit_log (created_at desc);
alter table public.admin_audit_log enable row level security;
revoke all on public.admin_audit_log from anon, authenticated;
grant select on public.admin_audit_log to authenticated;       -- RLS decides which rows (policy below, created after has_permission exists)

create or replace function public.admin_audit_immutable() returns trigger language plpgsql as $$
begin
  raise exception 'the admin audit log is append-only' using errcode = '42501';
end $$;
drop trigger if exists trg_audit_no_change on public.admin_audit_log;
create trigger trg_audit_no_change before update or delete on public.admin_audit_log
  for each row execute function public.admin_audit_immutable();
drop trigger if exists trg_audit_no_truncate on public.admin_audit_log;
create trigger trg_audit_no_truncate before truncate on public.admin_audit_log
  for each statement execute function public.admin_audit_immutable();

create table if not exists public.admin_approvals (
  id            uuid primary key default gen_random_uuid(),
  kind          text not null check (kind in ('report_ban','report_suspend','report_takedown')),
  payload       jsonb not null,
  note          text,
  requested_by  uuid not null references public.users(id) on delete cascade,
  requested_at  timestamptz not null default now(),
  expires_at    timestamptz not null default now() + interval '48 hours',
  status        text not null default 'pending' check (status in ('pending','approved','denied','expired')),
  decided_by    uuid references public.users(id) on delete set null,
  decided_at    timestamptz,
  decision_note text
);
create index if not exists admin_approvals_status on public.admin_approvals (status, requested_at desc);
alter table public.admin_approvals enable row level security;
revoke all on public.admin_approvals from anon, authenticated;

create table if not exists public.rate_limits (
  user_id uuid not null, bucket text not null, at timestamptz not null default now()
);
create index if not exists rate_limits_idx on public.rate_limits (user_id, bucket, at desc);
alter table public.rate_limits enable row level security;
revoke all on public.rate_limits from anon, authenticated;

-- ===================== 3) owner rules (trigger: also applies to the SQL editor unless break-glass is set) =====================
-- Break-glass for YOU only, in one SQL editor transaction:  select set_config('bofo.break_glass', 'on', true);
create or replace function public.guard_admin_members() returns trigger language plpgsql as $$
declare bg boolean := coalesce(current_setting('bofo.break_glass', true), '') = 'on';
begin
  if tg_op = 'INSERT' then
    if new.role = 'owner' and (select count(*) from public.admin_members where role = 'owner') >= 2 and not bg then
      raise exception 'there can be at most 2 owners' using errcode = '42501';
    end if;
    if new.role <> 'owner' and new.extra_grant && public.admin_owner_only_keys() then
      raise exception 'owner-only permissions cannot be granted to a sub-admin' using errcode = '42501';
    end if;
    return new;
  elsif tg_op = 'UPDATE' then
    if old.role = 'owner' and not bg then
      if new.role <> 'owner' or new.status <> 'active' or new.expires_at is not null or new.user_id <> old.user_id
         or new.extra_deny <> '{}' then
        raise exception 'an owner cannot be changed, demoted, suspended or limited' using errcode = '42501';
      end if;
    end if;
    if new.role = 'owner' and old.role <> 'owner' and not bg then
      raise exception 'owners are created only with bootstrap_owner() in the SQL editor' using errcode = '42501';
    end if;
    if new.role <> 'owner' and new.extra_grant && public.admin_owner_only_keys() then
      raise exception 'owner-only permissions cannot be granted to a sub-admin' using errcode = '42501';
    end if;
    return new;
  else  -- DELETE
    if old.role = 'owner' and not bg then
      raise exception 'an owner cannot be removed' using errcode = '42501';
    end if;
    return old;
  end if;
end $$;
drop trigger if exists trg_guard_admin_members on public.admin_members;
create trigger trg_guard_admin_members before insert or update or delete on public.admin_members
  for each row execute function public.guard_admin_members();

-- ===================== 4) the permission check =====================
-- MFA + (old) admin app version rule, taken from 017/018. Not tied to a role.
create or replace function public.admin_session_ok() returns boolean language sql stable security definer set search_path = public as $$
  select (
       not coalesce((select f.value from public.app_flags f where f.key = 'require_admin_mfa'), false)
       or coalesce(auth.jwt() ->> 'aal', 'aal1') = 'aal2'
     ) and public.app_version_ok();
$$;

create or replace function public.has_permission(p text) returns boolean
language plpgsql stable security definer set search_path = public as $$
declare m public.admin_members; perms text[]; iat_ bigint;
begin
  if auth.uid() is null or p is null or not (p = any (public.admin_permission_keys())) then return false; end if;
  if not public.admin_session_ok() then return false; end if;
  select * into m from public.admin_members where user_id = auth.uid();
  if found then                                                    -- a member row always wins over the legacy rule
    if m.status <> 'active' then return false; end if;
    if m.expires_at is not null and m.expires_at <= now() then return false; end if;
    begin iat_ := (auth.jwt() ->> 'iat')::bigint; exception when others then iat_ := 0; end;
    if m.revoked_before is not null and coalesce(iat_, 0) < extract(epoch from m.revoked_before)::bigint then return false; end if;
    if m.role = 'owner' then return true; end if;
    select r.permissions into perms from public.admin_roles r where r.key = m.role;
    return p = any (coalesce(perms, '{}') || m.extra_grant) and not (p = any (m.extra_deny));
  end if;
  -- LEGACY (removed in Stage 5): the old single-admin flag, full access, exactly like before
  return exists (select 1 from public.users u where u.id = auth.uid() and u.role = 'admin');
end $$;
revoke all on function public.has_permission(text) from public, anon;
grant execute on function public.has_permission(text) to authenticated, service_role;

create or replace function public.require_permission(p text) returns void
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.has_permission(p) then
    raise exception 'admins only' using errcode = '42501', detail = 'missing permission: ' || coalesce(p, '?');
  end if;
end $$;
revoke all on function public.require_permission(text) from public, anon;
grant execute on function public.require_permission(text) to authenticated, service_role;

-- "is any kind of staff" (used by the old read policies/views). Reads only; every write/sensitive read has its own permission.
create or replace function public.is_staff() returns boolean language sql stable security definer set search_path = public as $$
  select auth.uid() is not null and public.admin_session_ok() and (
    exists (select 1 from public.admin_members m
             where m.user_id = auth.uid() and m.status = 'active'
               and (m.expires_at is null or m.expires_at > now())
               and (m.revoked_before is null or coalesce((auth.jwt() ->> 'iat')::bigint, 0) >= extract(epoch from m.revoked_before)::bigint))
    or (not exists (select 1 from public.admin_members m where m.user_id = auth.uid())
        and exists (select 1 from public.users u where u.id = auth.uid() and u.role = 'admin'))
  );
$$;
revoke all on function public.is_staff() from public, anon;
grant execute on function public.is_staff() to authenticated, service_role;

-- is_admin(): old name, new meaning (any active staff). The old app and all old policies/views keep working.
create or replace function public.is_admin() returns boolean language sql stable security definer set search_path = public as $$
  select public.is_staff();
$$;
revoke all on function public.is_admin() from public, anon;
grant execute on function public.is_admin() to authenticated, service_role;

-- audit log read rules: everybody sees their own rows (audit_view_own), only audit_view_all sees the rest
drop policy if exists admin_audit_read on public.admin_audit_log;
create policy admin_audit_read on public.admin_audit_log for select to authenticated
  using ((actor_id = (select auth.uid()) and (select public.has_permission('audit_view_own'))) or (select public.has_permission('audit_view_all')));

-- ===================== 5) audit writer + copy of the old audit_logs =====================
create or replace function public.admin_log(act text, ttype text default null, tid text default null, det jsonb default null) returns void
language plpgsql security definer set search_path = public as $$
declare em text; rl text;
begin
  select m.email, m.role into em, rl from public.admin_members m where m.user_id = auth.uid();
  insert into public.admin_audit_log (actor_id, actor_email, actor_role, action, target_type, target_id, details)
    values (auth.uid(), em, coalesce(rl, case when auth.uid() is null then null else 'legacy' end), act, ttype, tid, det);
  update public.admin_members set last_active_at = now() where user_id = auth.uid();
end $$;
revoke all on function public.admin_log(text, text, text, jsonb) from public, anon, authenticated;

-- every row the old functions write to audit_logs is also copied into the append-only log
create or replace function public.copy_audit_log() returns trigger language plpgsql security definer set search_path = public as $$
declare em text; rl text;
begin
  select m.email, m.role into em, rl from public.admin_members m where m.user_id = new.actor_id;
  insert into public.admin_audit_log (created_at, actor_id, actor_email, actor_role, action, target_type, target_id, details)
    values (new.created_at, new.actor_id, em, coalesce(rl, case when new.actor_id is null then null else 'legacy' end), new.action, new.target_type, new.target_id, new.details);
  return new;
end $$;
drop trigger if exists trg_copy_audit_log on public.audit_logs;
create trigger trg_copy_audit_log after insert on public.audit_logs for each row execute function public.copy_audit_log();

-- ===================== 6) rate limits (S-05, S-06) =====================
create or replace function public.hit_rate_limit(bucket_ text, max_ int, window_secs int) returns void
language plpgsql security definer set search_path = public as $$
declare n int; uid_ uuid := auth.uid();
begin
  if uid_ is null then return; end if;                           -- service role / SQL editor: no limit
  if random() < 0.02 then delete from public.rate_limits where at < now() - interval '2 days'; end if;
  select count(*) into n from public.rate_limits where user_id = uid_ and bucket = bucket_ and at > now() - make_interval(secs => window_secs);
  if n >= max_ then raise exception 'Too many requests. Please try again later.' using errcode = 'PT429'; end if;
  insert into public.rate_limits (user_id, bucket) values (uid_, bucket_);
end $$;
revoke all on function public.hit_rate_limit(text, int, int) from public, anon;
grant execute on function public.hit_rate_limit(text, int, int) to authenticated;   -- a user can only spend their own budget

-- S-06: caps on report / chat spam. Only applies to app users (role authenticated); server tools are not limited.
create or replace function public.trg_rate_reports() returns trigger language plpgsql security definer set search_path = public as $$
begin
  if current_user = 'authenticated' or auth.role() = 'authenticated' then perform public.hit_rate_limit('report', 10, 3600); end if;
  return new;
end $$;
drop trigger if exists rate_reports on public.reports;
create trigger rate_reports before insert on public.reports for each row execute function public.trg_rate_reports();

create or replace function public.trg_rate_chat() returns trigger language plpgsql security definer set search_path = public as $$
begin
  if auth.role() = 'authenticated' then perform public.hit_rate_limit('chat_msg', 300, 3600); end if;
  return new;
end $$;
drop trigger if exists rate_dm on public.messages;
create trigger rate_dm before insert on public.messages for each row execute function public.trg_rate_chat();
drop trigger if exists rate_group on public.bh_group_messages;
create trigger rate_group before insert on public.bh_group_messages for each row execute function public.trg_rate_chat();

-- S-06: storage count caps (restrictive = on top of the existing insert policies)
drop policy if exists idv_cap on storage.objects;
create policy idv_cap on storage.objects as restrictive for insert to authenticated
  with check (bucket_id <> 'id-verification' or (select count(*) from storage.objects o
     where o.bucket_id = 'id-verification' and o.name like auth.uid()::text || '/%') < 60);
drop policy if exists bhp_cap on storage.objects;
create policy bhp_cap on storage.objects as restrictive for insert to authenticated
  with check (bucket_id <> 'bh-photos' or (select count(*) from storage.objects o
     where o.bucket_id = 'bh-photos' and o.name like auth.uid()::text || '/%') < 400);

-- ===================== 7) who am I =====================
create or replace function public.my_admin_access() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare m public.admin_members; r public.admin_roles; perms text[]; k text; out_ text[] := '{}';
begin
  if auth.uid() is null then return jsonb_build_object('active', false); end if;
  select * into m from public.admin_members where user_id = auth.uid();
  if found then
    select * into r from public.admin_roles where key = m.role;
    foreach k in array public.admin_permission_keys() loop
      if public.has_permission(k) then out_ := out_ || k; end if;
    end loop;
    return jsonb_build_object('active', coalesce(array_length(out_, 1), 0) > 0, 'role', m.role, 'label', r.label, 'status', m.status,
                              'expires_at', m.expires_at, 'permissions', to_jsonb(out_), 'legacy', false);
  end if;
  if exists (select 1 from public.users where id = auth.uid() and role = 'admin') then
    foreach k in array public.admin_permission_keys() loop
      if public.has_permission(k) then out_ := out_ || k; end if;
    end loop;
    return jsonb_build_object('active', coalesce(array_length(out_, 1), 0) > 0, 'role', 'legacy_admin', 'label', 'Legacy admin', 'status', 'active',
                              'permissions', to_jsonb(out_), 'legacy', true);
  end if;
  return jsonb_build_object('active', false);
end $$;
revoke all on function public.my_admin_access() from public, anon;
grant execute on function public.my_admin_access() to authenticated;

-- ===================== 8) owner bootstrap (SQL editor only) =====================
create or replace function public.bootstrap_owner(email_ text) returns text
language plpgsql security definer set search_path = public, auth as $$
declare uid_ uuid; e text := lower(btrim(email_));
begin
  select id into uid_ from auth.users where lower(email) = e and email_confirmed_at is not null;
  if uid_ is null then raise exception 'No confirmed account with that email: sign in with Google once first'; end if;
  if not exists (select 1 from public.users where id = uid_) then raise exception 'That account has no BOFO profile yet: open the app once first'; end if;
  insert into public.admin_members (user_id, email, role, status) values (uid_, e, 'owner', 'active')
    on conflict (user_id) do nothing;
  insert into public.admin_audit_log (actor_id, actor_email, actor_role, action, target_type, target_id)
    values (null, e, 'owner', 'owner_bootstrap', 'user', uid_::text);
  return 'Owner set: ' || e;
end $$;
revoke all on function public.bootstrap_owner(text) from public, anon, authenticated;

-- ===================== 9) members and invites (members_manage = owner only) =====================
create or replace function public.admin_members_list() returns table (user_id uuid, email text, full_name text, role text, status text,
  expires_at timestamptz, extra_grant text[], extra_deny text[], last_active_at timestamptz, actions int, invited_by uuid)
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.require_permission('members_manage');
  return query select m.user_id, m.email, nullif(u.full_name, ''), m.role, m.status, m.expires_at, m.extra_grant, m.extra_deny, m.last_active_at,
                      (select count(*)::int from public.admin_audit_log a where a.actor_id = m.user_id), m.invited_by
               from public.admin_members m left join public.users u on u.id = m.user_id
               order by (m.role = 'owner') desc, m.created_at;
end $$;

-- returns the one-time token (show it once; only its hash is stored). Send it to the person; the app builds a link/code from it.
create or replace function public.admin_invite_create(email_ text, role_ text, extra_grant_ text[] default '{}', member_days int default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare tok text; e text := lower(btrim(coalesce(email_, ''))); id_ uuid;
begin
  perform public.require_permission('members_manage');
  perform public.hit_rate_limit('invite_create', 20, 3600);
  if e !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then raise exception 'enter a valid email'; end if;
  if role_ is null or role_ in ('owner','legacy_admin') or not exists (select 1 from public.admin_roles where key = role_) then raise exception 'unknown role'; end if;
  if coalesce(extra_grant_, '{}') && public.admin_owner_only_keys() then raise exception 'owner-only permissions cannot be granted'; end if;
  if not (coalesce(extra_grant_, '{}') <@ public.admin_permission_keys()) then raise exception 'unknown permission'; end if;
  if exists (select 1 from public.admin_members where lower(email) = e) then raise exception 'this person is already staff'; end if;
  update public.admin_invites set revoked_at = now() where lower(email) = e and accepted_at is null and revoked_at is null;   -- one live invite per email
  tok := replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '');
  insert into public.admin_invites (email, role, extra_grant, member_expires_at, token_hash, created_by)
    values (e, role_, coalesce(extra_grant_, '{}'), case when member_days is null then null else now() + make_interval(days => member_days) end,
            encode(sha256(convert_to(tok, 'UTF8')), 'hex'), auth.uid())
    returning id into id_;
  perform public.admin_log('invite_created', 'admin_invite', id_::text, jsonb_build_object('email', e, 'role', role_, 'extra_grant', extra_grant_));
  return jsonb_build_object('invite_id', id_, 'token', tok, 'expires_in_hours', 48);
end $$;

create or replace function public.admin_invite_revoke(invite_id uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  perform public.require_permission('members_manage');
  update public.admin_invites set revoked_at = now() where id = invite_id and accepted_at is null and revoked_at is null;
  perform public.admin_log('invite_revoked', 'admin_invite', invite_id::text, null);
end $$;

-- the invited person (signed in with Google) redeems the token. Bound to the invited email, one time, 48 h.
create or replace function public.admin_invite_accept(token_ text) returns jsonb
language plpgsql security definer set search_path = public, auth as $$
declare inv public.admin_invites; au_email text; au_ok timestamptz;
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  perform public.hit_rate_limit('invite_accept', 10, 3600);
  select email, email_confirmed_at into au_email, au_ok from auth.users where id = auth.uid();
  select * into inv from public.admin_invites
   where token_hash = encode(sha256(convert_to(coalesce(token_, ''), 'UTF8')), 'hex')
     and accepted_at is null and revoked_at is null and expires_at > now() for update;
  if inv.id is null or au_ok is null or lower(au_email) <> lower(inv.email) then
    raise exception 'this invite is not valid for this account';          -- one message for every failure: no hints
  end if;
  if not exists (select 1 from public.users where id = auth.uid()) then raise exception 'this invite is not valid for this account'; end if;
  insert into public.admin_members (user_id, email, role, invited_by, expires_at, extra_grant)
    values (auth.uid(), lower(inv.email), inv.role, inv.created_by, inv.member_expires_at, inv.extra_grant)
    on conflict (user_id) do nothing;
  update public.admin_invites set accepted_at = now(), accepted_by = auth.uid() where id = inv.id;
  perform public.admin_log('invite_accepted', 'admin_invite', inv.id::text, jsonb_build_object('role', inv.role));
  return public.my_admin_access();
end $$;
revoke all on function public.admin_invite_accept(text) from public, anon;
grant execute on function public.admin_invite_accept(text) to authenticated;

create or replace function public.admin_member_update(uid uuid, role_ text default null, extra_grant_ text[] default null,
  extra_deny_ text[] default null, expires_ timestamptz default null, clear_expiry boolean default false) returns void
language plpgsql security definer set search_path = public as $$
declare m public.admin_members;
begin
  perform public.require_permission('members_manage');
  select * into m from public.admin_members where user_id = uid for update;
  if m.user_id is null then raise exception 'not a member'; end if;
  if m.role = 'owner' or uid = auth.uid() then raise exception 'an owner or your own account cannot be changed here' using errcode = '42501'; end if;
  if role_ is not null and (role_ in ('owner','legacy_admin') or not exists (select 1 from public.admin_roles where key = role_)) then raise exception 'unknown role'; end if;
  update public.admin_members set role = coalesce(role_, role), extra_grant = coalesce(extra_grant_, extra_grant),
         extra_deny = coalesce(extra_deny_, extra_deny),
         expires_at = case when clear_expiry then null else coalesce(expires_, expires_at) end
   where user_id = uid;
  perform public.admin_log('member_updated', 'user', uid::text, jsonb_build_object('role', role_, 'grant', extra_grant_, 'deny', extra_deny_, 'expires', expires_, 'clear_expiry', clear_expiry));
end $$;

create or replace function public.admin_member_suspend(uid uuid, on_ boolean, reason text default null) returns void
language plpgsql security definer set search_path = public as $$
declare m public.admin_members;
begin
  perform public.require_permission('members_manage');
  select * into m from public.admin_members where user_id = uid for update;
  if m.user_id is null then raise exception 'not a member'; end if;
  if m.role = 'owner' or uid = auth.uid() then raise exception 'an owner or your own account cannot be suspended' using errcode = '42501'; end if;
  update public.admin_members set status = case when on_ then 'suspended' else 'active' end,
         suspended_reason = case when on_ then left(btrim(coalesce(reason, '')), 300) else null end,
         revoked_before = case when on_ then now() else revoked_before end
   where user_id = uid;
  perform public.admin_log(case when on_ then 'member_suspended' else 'member_reinstated' end, 'user', uid::text, jsonb_build_object('reason', reason));
end $$;

create or replace function public.admin_member_remove(uid uuid) returns void
language plpgsql security definer set search_path = public as $$
declare m public.admin_members;
begin
  perform public.require_permission('members_manage');
  select * into m from public.admin_members where user_id = uid for update;
  if m.user_id is null then raise exception 'not a member'; end if;
  if m.role = 'owner' or uid = auth.uid() then raise exception 'an owner or your own account cannot be removed' using errcode = '42501'; end if;
  delete from public.admin_members where user_id = uid;
  perform public.admin_log('member_removed', 'user', uid::text, jsonb_build_object('email', m.email, 'role', m.role));
end $$;

-- "log out all devices": every token issued before now stops working for admin actions
create or replace function public.admin_member_logout_all(uid uuid) returns void
language plpgsql security definer set search_path = public as $$
declare m public.admin_members;
begin
  perform public.require_permission('members_manage');
  select * into m from public.admin_members where user_id = uid;
  if m.user_id is null then raise exception 'not a member'; end if;
  if m.role = 'owner' and uid <> auth.uid() then raise exception 'an owner cannot be changed here' using errcode = '42501'; end if;
  update public.admin_members set revoked_before = now() where user_id = uid;
  perform public.admin_log('member_logout_all', 'user', uid::text, null);
end $$;

-- a member records a device session (for the owner's session list)
create or replace function public.admin_touch_session(device_ text default null) returns void
language plpgsql security definer set search_path = public as $$
begin
  perform public.require_permission('dashboard_view');
  insert into public.admin_sessions (user_id, device) values (auth.uid(), left(coalesce(device_, ''), 80));
  delete from public.admin_sessions where user_id = auth.uid() and last_seen_at < now() - interval '30 days';
  update public.admin_members set last_active_at = now() where user_id = auth.uid();
end $$;

-- ===================== 10) activity log reads =====================
create or replace function public.admin_my_activity(lim int default 50) returns setof public.admin_audit_log
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.require_permission('audit_view_own');
  return query select * from public.admin_audit_log where actor_id = auth.uid() order by id desc limit least(greatest(coalesce(lim, 50), 1), 200);
end $$;

create or replace function public.admin_activity_all(lim int default 100, before_id bigint default null) returns setof public.admin_audit_log
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.require_permission('audit_view_all');
  return query select * from public.admin_audit_log where (before_id is null or id < before_id) order by id desc limit least(greatest(coalesce(lim, 100), 1), 500);
end $$;

-- CSV export is built on the phone; the server cannot see it. The app MUST call this first; it refuses without the permission and records the export.
create or replace function public.admin_log_export(kind text, row_count int) returns void
language plpgsql security definer set search_path = public as $$
begin
  perform public.require_permission('csv_export');
  perform public.admin_log('csv_export', 'export', left(coalesce(kind, ''), 40), jsonb_build_object('rows', row_count));
end $$;

-- ===================== 11) approvals: moderator asks, owner/admin decides =====================
-- payload by kind:
--   report_ban      {"rid": uuid, "reason": text, "notes": text}
--   report_suspend  {"rid": uuid, "hrs": int, "reason": text, "notes": text}
--   report_takedown {"rid": uuid, "bh": uuid, "reason": text}
create or replace function public.request_admin_approval(kind_ text, payload_ jsonb, note_ text default null) returns uuid
language plpgsql security definer set search_path = public as $$
declare id_ uuid; rid_ uuid;
begin
  perform public.require_permission('approvals_request');
  perform public.hit_rate_limit('approval_request', 30, 3600);
  if kind_ not in ('report_ban','report_suspend','report_takedown') then raise exception 'unknown request'; end if;
  begin rid_ := (payload_ ->> 'rid')::uuid; exception when others then raise exception 'bad request'; end;
  if rid_ is null or not exists (select 1 from public.reports where id = rid_ and status = 'pending') then raise exception 'this report is not waiting'; end if;
  if coalesce(btrim(payload_ ->> 'reason'), '') = '' then raise exception 'a reason is required'; end if;
  if kind_ = 'report_suspend' and coalesce((payload_ ->> 'hrs')::int, 0) < 1 then raise exception 'bad request'; end if;
  if exists (select 1 from public.admin_approvals where kind = kind_ and status = 'pending' and payload ->> 'rid' = rid_::text) then raise exception 'already requested'; end if;
  insert into public.admin_approvals (kind, payload, note, requested_by) values (kind_, payload_, left(btrim(coalesce(note_, '')), 300), auth.uid()) returning id into id_;
  perform public.admin_log('approval_requested', 'admin_approval', id_::text, jsonb_build_object('kind', kind_, 'rid', rid_));
  return id_;
end $$;

create or replace function public.admin_approvals_list() returns setof public.admin_approvals
language plpgsql stable security definer set search_path = public as $$
begin
  if not (public.has_permission('approvals_decide') or public.has_permission('approvals_request')) then raise exception 'admins only' using errcode = '42501'; end if;
  update public.admin_approvals set status = 'expired' where status = 'pending' and expires_at <= now();
  if public.has_permission('approvals_decide') then
    return query select * from public.admin_approvals order by requested_at desc limit 100;
  end if;
  return query select * from public.admin_approvals where requested_by = auth.uid() order by requested_at desc limit 100;
end $$;

-- approving RUNS the action as the approver, so the approver needs the real permission (an Admin cannot approve a permanent ban)
create or replace function public.decide_admin_approval(id_ uuid, approve boolean, note_ text default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.admin_approvals; res jsonb := '{}'::jsonb;
begin
  perform public.require_permission('approvals_decide');
  select * into a from public.admin_approvals where id = id_ for update;
  if a.id is null or a.status <> 'pending' then raise exception 'this request is not waiting'; end if;
  if a.expires_at <= now() then update public.admin_approvals set status = 'expired' where id = id_; raise exception 'this request expired'; end if;
  if a.requested_by = auth.uid() then raise exception 'you cannot decide your own request' using errcode = '42501'; end if;
  if approve then
    if a.kind = 'report_ban' then
      perform public.require_permission('ban_permanent');
      res := public.admin_review_report((a.payload ->> 'rid')::uuid, 'ban', null, a.payload ->> 'reason', a.payload ->> 'notes');
    elsif a.kind = 'report_suspend' then
      perform public.require_permission('suspend_long');
      res := public.admin_review_report((a.payload ->> 'rid')::uuid, 'suspend', (a.payload ->> 'hrs')::int, a.payload ->> 'reason', a.payload ->> 'notes');
    elsif a.kind = 'report_takedown' then
      perform public.require_permission('listing_takedown');
      res := public.admin_report_takedown((a.payload ->> 'rid')::uuid, (a.payload ->> 'bh')::uuid, a.payload ->> 'reason');
    end if;
  end if;
  update public.admin_approvals set status = case when approve then 'approved' else 'denied' end, decided_by = auth.uid(), decided_at = now(),
         decision_note = left(btrim(coalesce(note_, '')), 300) where id = id_;
  perform public.admin_log(case when approve then 'approval_approved' else 'approval_denied' end, 'admin_approval', id_::text, jsonb_build_object('kind', a.kind));
  return res;
end $$;

-- ===================== 12) grants =====================
revoke all on function public.admin_members_list(), public.admin_invite_create(text, text, text[], int), public.admin_invite_revoke(uuid),
  public.admin_member_update(uuid, text, text[], text[], timestamptz, boolean), public.admin_member_suspend(uuid, boolean, text),
  public.admin_member_remove(uuid), public.admin_member_logout_all(uuid), public.admin_touch_session(text),
  public.admin_my_activity(int), public.admin_activity_all(int, bigint), public.admin_log_export(text, int),
  public.request_admin_approval(text, jsonb, text), public.admin_approvals_list(), public.decide_admin_approval(uuid, boolean, text)
  from public, anon;
grant execute on function public.admin_members_list(), public.admin_invite_create(text, text, text[], int), public.admin_invite_revoke(uuid),
  public.admin_member_update(uuid, text, text[], text[], timestamptz, boolean), public.admin_member_suspend(uuid, boolean, text),
  public.admin_member_remove(uuid), public.admin_member_logout_all(uuid), public.admin_touch_session(text),
  public.admin_my_activity(int), public.admin_activity_all(int, bigint), public.admin_log_export(text, int),
  public.request_admin_approval(text, jsonb, text), public.admin_approvals_list(), public.decide_admin_approval(uuid, boolean, text)
  to authenticated;   -- each one checks its own permission inside

notify pgrst, 'reload schema';
