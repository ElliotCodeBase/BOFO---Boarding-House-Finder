-- 044: BOFO Admin, Stage 4i (v2.12.0): queue claiming, escalation, strikes ladder, appeals, break-glass, session list, unusual activity, push triggers.
-- Run AFTER 039, 040, 042 (and 043). Safe to run twice. NOT applied live: run it yourself (take a backup / restore point first).
-- Run BEFORE 045 (045 re-creates has_permission and expects public.break_glass_keys() and public.admin_break_glass from this file).
--
-- WHAT THIS ADDS
--   Tables (RLS on, ALL grants revoked: only the functions below touch them)
--     admin_item_marks   one row per report / ID request: who has it open (30 min heads-up, NOT a lock) and whether it is escalated
--     user_strikes       the ladder history per person (warning > 3-day > 7-day > permanent ban)
--     appeals            one appeal per ban / suspension decision, decided by a DIFFERENT staff member
--     admin_break_glass  an owner lends ONE permission for 1 hour
--   Functions (staff, each with its own permission check)
--     admin_claim_item(kind, id) / admin_release_item(kind, id) / admin_item_marks_list(kind)
--     admin_escalate_report(rid, note) / admin_clear_escalation(rid)
--     admin_user_strikes(uid) / admin_issue_strike(rid, reason)
--     admin_appeals_list(status) / admin_appeal_decide(id, accept, note)
--     admin_break_glass_grant(uid, perm, reason) / admin_break_glass_end(id) / admin_break_glass_list()
--     admin_member_sessions(uid)
--     admin_unusual_activity()
--   Functions (the person being moderated, own data only)
--     submit_appeal(message) / my_appeal()
--   has_permission(): re-created = the 039 body + ONE extra branch: an active break-glass grant for exactly that key (6 lendable keys only).
--   Triggers (never block the action: errors are swallowed)
--     admin_audit_log AFTER INSERT  -> push 'unusual' when a non-owner reaches 10 risky actions within one hour
--     admin_approvals AFTER UPDATE  -> push 'decided' to the person who asked
--     (the 'escalated' push is sent by admin_escalate_report)
--
-- Lock-screen safe pushes: type only, no names, no ID data (same rule as 033 / 042). Redeploy supabase/functions/send-push after this.
--
-- ROLLBACK (in this order)
--   drop trigger if exists push_unusual on public.admin_audit_log;  drop trigger if exists push_decided on public.admin_approvals;
--   drop function if exists public.trg_push_unusual(), public.trg_push_decided();
--   drop function if exists public.admin_claim_item(text, uuid), public.admin_release_item(text, uuid), public.admin_item_marks_list(text),
--     public.admin_escalate_report(uuid, text), public.admin_clear_escalation(uuid), public.admin_user_strikes(uuid), public.admin_issue_strike(uuid, text),
--     public.admin_appeals_list(text), public.admin_appeal_decide(uuid, boolean, text), public.admin_break_glass_grant(uuid, text, text),
--     public.admin_break_glass_end(uuid), public.admin_break_glass_list(), public.admin_member_sessions(uuid), public.admin_unusual_activity(),
--     public.submit_appeal(text), public.my_appeal();
--   re-run the has_permission body of 039 section 4 (or of 045 if 045 was applied, minus the break-glass lines), then:
--   drop function if exists public.break_glass_keys();
--   drop table if exists public.admin_break_glass, public.appeals, public.user_strikes, public.admin_item_marks;   -- destroys appeal and strike history: export first

-- ===================== 1) tables =====================
create table if not exists public.admin_item_marks (
  kind        text not null check (kind in ('report', 'id')),
  item_id     uuid not null,
  claimed_by  uuid references public.users(id) on delete set null,
  claimed_at  timestamptz,
  escalated_by uuid references public.users(id) on delete set null,
  escalated_at timestamptz,
  escalation_note text check (escalation_note is null or char_length(escalation_note) <= 300),
  escalation_cleared_at timestamptz,
  primary key (kind, item_id)
);
alter table public.admin_item_marks enable row level security;
revoke all on public.admin_item_marks from public, anon, authenticated;

create table if not exists public.user_strikes (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references public.users(id) on delete cascade,
  report_id   uuid references public.reports(id) on delete set null,
  step        text not null check (step in ('warning', 'suspend_3d', 'suspend_7d', 'ban')),
  reason      text not null check (char_length(reason) between 5 and 300),
  issued_by   uuid references public.users(id) on delete set null,
  created_at  timestamptz not null default now()
);
create index if not exists user_strikes_user on public.user_strikes (user_id, created_at desc);
alter table public.user_strikes enable row level security;
revoke all on public.user_strikes from public, anon, authenticated;

create table if not exists public.appeals (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references public.users(id) on delete cascade,
  kind             text not null check (kind in ('ban', 'suspension')),
  ref_at           timestamptz not null,                     -- users.banned_at / users.suspended_until at the time: identifies THE decision
  message          text not null check (char_length(message) between 10 and 1000),
  status           text not null default 'pending' check (status in ('pending', 'accepted', 'rejected')),
  original_decider uuid,                                     -- no FK on purpose; null = unknown (then the "different person" rule cannot apply)
  created_at       timestamptz not null default now(),
  decided_by       uuid references public.users(id) on delete set null,
  decided_at       timestamptz,
  decision_note    text check (decision_note is null or char_length(decision_note) <= 300)
);
create unique index if not exists appeals_one_per_decision on public.appeals (user_id, kind, ref_at);
create index if not exists appeals_status on public.appeals (status, created_at desc);
alter table public.appeals enable row level security;
revoke all on public.appeals from public, anon, authenticated;

create table if not exists public.admin_break_glass (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references public.users(id) on delete cascade,     -- who may use the lent permission
  permission  text not null,
  reason      text not null check (char_length(reason) between 5 and 300),
  granted_by  uuid references public.users(id) on delete set null,
  created_at  timestamptz not null default now(),
  expires_at  timestamptz not null default now() + interval '1 hour',
  revoked_at  timestamptz
);
create index if not exists admin_break_glass_active on public.admin_break_glass (user_id, permission) where revoked_at is null;
alter table public.admin_break_glass enable row level security;
revoke all on public.admin_break_glass from public, anon, authenticated;

-- the ONLY keys an owner can lend (never members, MFA, config, data deletion, raw IDs)
create or replace function public.break_glass_keys() returns text[] language sql immutable as $$
  select array['ban_permanent', 'unban', 'listing_takedown', 'suspend_long', 'payments_view', 'csv_export']::text[];
$$;

-- ===================== 2) has_permission = 039 body + break-glass branch =====================
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
    if p = any (coalesce(perms, '{}') || m.extra_grant) and not (p = any (m.extra_deny)) then return true; end if;
    return p = any (public.break_glass_keys()) and exists (          -- an active lend for exactly this key
      select 1 from public.admin_break_glass g where g.user_id = auth.uid() and g.permission = p and g.revoked_at is null and g.expires_at > now());
  end if;
  -- LEGACY (removed by 045): the old single-admin flag, full access, exactly like before
  return exists (select 1 from public.users u where u.id = auth.uid() and u.role = 'admin');
end $$;
revoke all on function public.has_permission(text) from public, anon;
grant execute on function public.has_permission(text) to authenticated, service_role;

-- ===================== 3) queue claiming (heads-up only: the server still lets either person decide) =====================
create or replace function public.admin_claim_item(kind_ text, item uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare mk public.admin_item_marks; nm text;
begin
  if kind_ = 'report' then perform public.require_permission('reports_view');
  elsif kind_ = 'id' then perform public.require_permission('id_review');
  else raise exception 'unknown item type'; end if;
  perform public.hit_rate_limit('claim', 300, 3600);
  insert into public.admin_item_marks (kind, item_id) values (kind_, item) on conflict (kind, item_id) do nothing;
  select * into mk from public.admin_item_marks where kind = kind_ and item_id = item for update;
  if mk.claimed_by is null or mk.claimed_by = auth.uid() or mk.claimed_at is null or mk.claimed_at < now() - interval '30 minutes' then
    update public.admin_item_marks set claimed_by = auth.uid(), claimed_at = now() where kind = kind_ and item_id = item;
    return jsonb_build_object('mine', true);
  end if;
  select coalesce(nullif(u.full_name, ''), 'a colleague') into nm from public.users u where u.id = mk.claimed_by;
  return jsonb_build_object('mine', false, 'by', coalesce(nm, 'a colleague'), 'since', mk.claimed_at);
end $$;

create or replace function public.admin_release_item(kind_ text, item uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if kind_ = 'report' then perform public.require_permission('reports_view');
  elsif kind_ = 'id' then perform public.require_permission('id_review');
  else raise exception 'unknown item type'; end if;
  update public.admin_item_marks set claimed_by = null, claimed_at = null where kind = kind_ and item_id = item and claimed_by = auth.uid();
end $$;

-- who has what open + what is escalated. Names only. Items already decided are not listed.
create or replace function public.admin_item_marks_list(kind_ text)
returns table (item_id uuid, claimed_by_name text, claimed_mine boolean, claimed_at timestamptz, escalated boolean, escalation_note text, escalated_at timestamptz)
language plpgsql stable security definer set search_path = public as $$
begin
  if kind_ = 'report' then perform public.require_permission('reports_view');
  elsif kind_ = 'id' then perform public.require_permission('id_review');
  else raise exception 'unknown item type'; end if;
  return query
    select k.item_id,
           case when k.claimed_by is not null and k.claimed_at > now() - interval '30 minutes' then coalesce(nullif(u.full_name, ''), 'a colleague') end,
           coalesce(k.claimed_by = auth.uid() and k.claimed_at > now() - interval '30 minutes', false),
           case when k.claimed_by is not null and k.claimed_at > now() - interval '30 minutes' then k.claimed_at end,
           (k.escalated_at is not null and k.escalation_cleared_at is null),
           case when k.escalated_at is not null and k.escalation_cleared_at is null then k.escalation_note end,
           case when k.escalated_at is not null and k.escalation_cleared_at is null then k.escalated_at end
      from public.admin_item_marks k
      left join public.users u on u.id = k.claimed_by
     where k.kind = kind_
       and ((k.claimed_by is not null and k.claimed_at > now() - interval '30 minutes') or (k.escalated_at is not null and k.escalation_cleared_at is null))
       and ((kind_ = 'report' and exists (select 1 from public.reports r where r.id = k.item_id and r.status = 'pending'))
         or (kind_ = 'id' and exists (select 1 from public.owner_verifications v where v.id = k.item_id and v.status = 'pending')));
end $$;

-- ===================== 4) escalate a report to a senior =====================
create or replace function public.admin_escalate_report(rid uuid, note text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare nt text := left(btrim(coalesce(note, '')), 300);
begin
  perform public.require_permission('reports_act');
  perform public.hit_rate_limit('escalate', 30, 3600);
  if char_length(nt) < 5 then raise exception 'write a short note (5+ characters) so the senior knows why'; end if;
  if not exists (select 1 from public.reports where id = rid and status = 'pending') then raise exception 'this report is not waiting'; end if;
  insert into public.admin_item_marks (kind, item_id, escalated_by, escalated_at, escalation_note, escalation_cleared_at)
    values ('report', rid, auth.uid(), now(), nt, null)
    on conflict (kind, item_id) do update set escalated_by = auth.uid(), escalated_at = now(), escalation_note = nt, escalation_cleared_at = null;
  perform public.admin_log('report_escalated', 'report', rid::text, null);
  perform public.push_notify('escalated', jsonb_build_object('id', rid, 'by', auth.uid()));       -- ids only; swallows its own errors
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.admin_clear_escalation(rid uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  perform public.require_permission('approvals_decide');
  update public.admin_item_marks set escalation_cleared_at = now() where kind = 'report' and item_id = rid and escalated_at is not null and escalation_cleared_at is null;
  perform public.admin_log('report_escalation_cleared', 'report', rid::text, null);
  return jsonb_build_object('ok', true);
end $$;

-- ===================== 5) strikes ladder =====================
-- n prior strikes -> next step: 0 warning, 1 three-day suspension, 2 seven-day suspension, 3+ permanent ban. Strikes never expire.
create or replace function public.strike_step_for(n int) returns text language sql immutable as $$
  select case when n <= 0 then 'warning' when n = 1 then 'suspend_3d' when n = 2 then 'suspend_7d' else 'ban' end;
$$;

create or replace function public.admin_user_strikes(uid uuid) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare n int; last_ timestamptz;
begin
  perform public.require_permission('reports_view');
  select count(*)::int, max(created_at) into n, last_ from public.user_strikes where user_id = uid;
  return jsonb_build_object('count', n, 'next', public.strike_step_for(n), 'last_at', last_);
end $$;

-- Applies the next step through the NORMAL report rules (admin_review_report), so permissions, conflict checks and audit rows are unchanged.
-- A moderator at the ban step gets "admins only" (42501): the app then tells them to use Ban (request approval).
create or replace function public.admin_issue_strike(rid uuid, reason text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare r public.reports; n int; step text; why text := left(btrim(coalesce(reason, '')), 300); res jsonb;
begin
  perform public.require_permission('reports_act');
  perform public.hit_rate_limit('strike', 30, 3600);
  if char_length(why) < 5 then raise exception 'a reason of at least 5 characters is required'; end if;
  select * into r from public.reports where id = rid for update;
  if not found then raise exception 'report not found'; end if;
  if r.status <> 'pending' then raise exception 'this report was already handled'; end if;
  if r.reported_user_id is null then raise exception 'this report is about a place, not a person: use Take down instead'; end if;      -- N-4
  if exists (select 1 from public.admin_members m where m.user_id = r.reported_user_id) or exists (select 1 from public.users u where u.id = r.reported_user_id and u.role = 'admin') then
    raise exception 'staff cannot receive strikes';
  end if;
  perform 1 from public.users where id = r.reported_user_id for update;                       -- two people striking at once: one waits, then counts again
  select count(*)::int into n from public.user_strikes where user_id = r.reported_user_id;
  step := public.strike_step_for(n);
  if step = 'warning' then res := public.admin_review_report(rid, 'reviewed', null, null, 'Warning: ' || why);
  elsif step = 'suspend_3d' then res := public.admin_review_report(rid, 'suspend', 72, why, null);
  elsif step = 'suspend_7d' then res := public.admin_review_report(rid, 'suspend', 168, why, null);
  else res := public.admin_review_report(rid, 'ban', null, why, null);
  end if;
  insert into public.user_strikes (user_id, report_id, step, reason, issued_by) values (r.reported_user_id, rid, step, why, auth.uid());
  perform public.admin_log('strike_issued', 'user', r.reported_user_id::text, jsonb_build_object('step', step, 'count', n + 1, 'report_id', rid));
  return jsonb_build_object('step', step, 'count', n + 1, 'status', res ->> 'status');
end $$;

-- ===================== 6) appeals =====================
-- the person's own side
create or replace function public.submit_appeal(msg text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare u public.users; k text; ref timestamptz; m text := left(btrim(coalesce(msg, '')), 1000); decider uuid;
begin
  if auth.uid() is null then raise exception 'sign in first'; end if;
  if char_length(m) < 10 then raise exception 'please write at least 10 characters'; end if;
  perform public.hit_rate_limit('appeal', 5, 86400);
  select * into u from public.users where id = auth.uid() for update;
  if not found then raise exception 'profile not found'; end if;
  if u.banned_at is not null then k := 'ban'; ref := u.banned_at; decider := u.banned_by;
  elsif u.suspended_until is not null and u.suspended_until > now() then
    k := 'suspension'; ref := u.suspended_until;
    select r.reviewed_by into decider from public.reports r
     where r.reported_user_id = u.id and r.action = 'suspend' order by r.reviewed_at desc nulls last limit 1;
  else raise exception 'you have no active ban or suspension to appeal'; end if;
  if exists (select 1 from public.appeals a where a.user_id = u.id and a.kind = k and a.ref_at = ref) then
    raise exception 'you already appealed this decision';
  end if;
  insert into public.appeals (user_id, kind, ref_at, message, original_decider) values (u.id, k, ref, m, decider);
  return jsonb_build_object('status', 'pending');
end $$;
revoke all on function public.submit_appeal(text) from public, anon;
grant execute on function public.submit_appeal(text) to authenticated;

create or replace function public.my_appeal() returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce((
    select jsonb_build_object('exists', true, 'status', a.status, 'note', coalesce(a.decision_note, ''))
      from public.appeals a join public.users u on u.id = a.user_id
     where a.user_id = auth.uid()
       and ((a.kind = 'ban' and u.banned_at is not distinct from a.ref_at) or (a.kind = 'suspension' and u.suspended_until is not distinct from a.ref_at))
     order by a.created_at desc limit 1), jsonb_build_object('exists', false));
$$;
revoke all on function public.my_appeal() from public, anon;
grant execute on function public.my_appeal() to authenticated;

-- the deciders' side (approvals_decide: Owner, Admin)
create or replace function public.admin_appeals_list(status_ text default 'pending')
returns table (id uuid, user_id uuid, full_name text, kind text, message text, status text, created_at timestamptz, decided_at timestamptz,
               decision_note text, stale boolean, mine boolean)
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.require_permission('approvals_decide');
  return query
    select a.id, a.user_id, nullif(u.full_name, ''), a.kind, a.message, a.status, a.created_at, a.decided_at, a.decision_note,
           (a.status = 'pending' and not ((a.kind = 'ban' and u.banned_at is not distinct from a.ref_at) or (a.kind = 'suspension' and u.suspended_until is not distinct from a.ref_at))),
           (a.original_decider is not null and a.original_decider = auth.uid())
      from public.appeals a left join public.users u on u.id = a.user_id
     where status_ = 'all' or a.status = status_ or (status_ = 'decided' and a.status <> 'pending')
     order by (a.status = 'pending') desc, a.created_at desc limit 100;
end $$;

create or replace function public.admin_appeal_decide(id_ uuid, accept boolean, note_ text default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.appeals; nt text := nullif(left(btrim(coalesce(note_, '')), 300), '');
begin
  perform public.require_permission('approvals_decide');
  select * into a from public.appeals where id = id_ for update;
  if not found or a.status <> 'pending' then raise exception 'this appeal is not waiting'; end if;
  if a.original_decider is not null and a.original_decider = auth.uid() then
    raise exception 'a different person must decide this appeal (you made the original decision)' using errcode = '42501';
  end if;
  if accept then
    -- N-1: the appeal must still be about the CURRENT decision, otherwise accepting it would lift a newer one
    if (a.kind = 'ban' and not exists (select 1 from public.users where id = a.user_id and banned_at is not distinct from a.ref_at))
    or (a.kind = 'suspension' and not exists (select 1 from public.users where id = a.user_id and suspended_until is not distinct from a.ref_at)) then
      raise exception 'This appeal is about an older decision. Reject it; the person can appeal the current one.';
    end if;
    if a.kind = 'ban' then
      perform public.require_permission('unban');                                   -- Owner, or lent by break-glass
      perform public.admin_unban(a.user_id, coalesce(nt, 'Appeal accepted'));
    else
      perform public.require_permission('reports_act');
      update public.users set suspended_until = null, suspension_reason = null where id = a.user_id;
    end if;
  end if;
  update public.appeals set status = case when accept then 'accepted' else 'rejected' end, decided_by = auth.uid(), decided_at = now(), decision_note = nt where id = id_;
  perform public.admin_log(case when accept then 'appeal_accepted' else 'appeal_rejected' end, 'appeal', id_::text, jsonb_build_object('kind', a.kind));
  return jsonb_build_object('status', case when accept then 'accepted' else 'rejected' end);
end $$;

-- ===================== 7) break-glass (owner lends one permission for 1 hour) =====================
create or replace function public.admin_break_glass_grant(uid uuid, perm text, reason_ text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare m public.admin_members; why text := left(btrim(coalesce(reason_, '')), 300); gid uuid; exp timestamptz;
begin
  perform public.require_permission('members_manage');
  perform public.hit_rate_limit('break_glass', 20, 3600);
  if perm is null or not (perm = any (public.break_glass_keys())) then raise exception 'this permission cannot be lent'; end if;
  if char_length(why) < 5 then raise exception 'a reason of at least 5 characters is required'; end if;
  if uid = auth.uid() then raise exception 'you cannot lend a permission to yourself'; end if;
  select * into m from public.admin_members where user_id = uid;
  if not found or m.status <> 'active' or m.role = 'owner' then raise exception 'only an active non-owner team member can be lent a permission'; end if;
  update public.admin_break_glass set revoked_at = now() where user_id = uid and permission = perm and revoked_at is null;
  insert into public.admin_break_glass (user_id, permission, reason, granted_by) values (uid, perm, why, auth.uid()) returning id, expires_at into gid, exp;
  perform public.admin_log('break_glass_granted', 'user', uid::text, jsonb_build_object('permission', perm, 'reason', why, 'until', exp));
  return jsonb_build_object('id', gid, 'expires_at', exp);
end $$;

create or replace function public.admin_break_glass_end(gid uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare g public.admin_break_glass;
begin
  perform public.require_permission('members_manage');
  select * into g from public.admin_break_glass where id = gid for update;
  if not found or g.revoked_at is not null then raise exception 'this grant is not active'; end if;
  update public.admin_break_glass set revoked_at = now() where id = gid;
  perform public.admin_log('break_glass_ended', 'user', g.user_id::text, jsonb_build_object('permission', g.permission));
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.admin_break_glass_list()
returns table (id uuid, user_id uuid, email text, permission text, reason text, created_at timestamptz, expires_at timestamptz)
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.require_permission('members_manage');
  return query select g.id, g.user_id, m.email, g.permission, g.reason, g.created_at, g.expires_at
    from public.admin_break_glass g left join public.admin_members m on m.user_id = g.user_id
   where g.revoked_at is null and g.expires_at > now() order by g.created_at desc;
end $$;

-- ===================== 8) recent sign-ins of one person (written by admin_touch_session at each app start) =====================
create or replace function public.admin_member_sessions(uid uuid)
returns table (device text, started_at timestamptz, last_seen_at timestamptz)
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.require_permission('members_manage');
  return query select s.device, s.started_at, s.last_seen_at from public.admin_sessions s where s.user_id = uid order by s.started_at desc limit 20;
end $$;

-- ===================== 9) unusual activity =====================
-- risky = the action names the admin functions really write (040 / 043 / this file). Threshold 10 in one hour, owners excluded.
create or replace function public.admin_risky_actions() returns text[] language sql immutable as $$
  select array['report_ban', 'report_suspend', 'report_takedown', 'listing_takedown', 'verification_rejected', 'user_unban', 'boost_refunded', 'strike_issued']::text[];
$$;

create or replace function public.admin_unusual_activity()
returns table (actor_id uuid, email text, role text, n_1h int, n_24h int, last_at timestamptz, flagged boolean)
language plpgsql stable security definer set search_path = public as $$
begin
  perform public.require_permission('audit_view_all');
  return query
    select l.actor_id, max(l.actor_email), max(l.actor_role),
           (count(*) filter (where l.created_at > now() - interval '1 hour'))::int,
           count(*)::int, max(l.created_at),
           (count(*) filter (where l.created_at > now() - interval '1 hour')) >= 10
      from public.admin_audit_log l
     where l.created_at > now() - interval '24 hours' and l.action = any (public.admin_risky_actions()) and l.actor_id is not null
       and coalesce(l.actor_role, '') <> 'owner'
     group by l.actor_id
     order by 4 desc, 5 desc limit 50;
end $$;

create or replace function public.trg_push_unusual() returns trigger
language plpgsql security definer set search_path = public as $$
declare n int;
begin
  begin
    if new.actor_id is not null and coalesce(new.actor_role, '') <> 'owner' and new.action = any (public.admin_risky_actions()) then
      select count(*)::int into n from public.admin_audit_log where actor_id = new.actor_id and created_at > now() - interval '1 hour' and action = any (public.admin_risky_actions());
      if n = 10 then perform public.push_notify('unusual', jsonb_build_object('actor_id', new.actor_id)); end if;     -- once per crossing, no names
    end if;
  exception when others then null;                                                      -- an alert must never block the action
  end;
  return new;
end $$;
revoke all on function public.trg_push_unusual() from public, anon, authenticated;
drop trigger if exists push_unusual on public.admin_audit_log;
create trigger push_unusual after insert on public.admin_audit_log for each row execute function public.trg_push_unusual();

-- ===================== 10) push to the person whose approval request was decided =====================
create or replace function public.trg_push_decided() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  begin
    if old.status = 'pending' and new.status in ('approved', 'denied') then
      perform public.push_notify('decided', jsonb_build_object('id', new.id, 'requested_by', new.requested_by, 'status', new.status));
    end if;
  exception when others then null;
  end;
  return new;
end $$;
revoke all on function public.trg_push_decided() from public, anon, authenticated;
drop trigger if exists push_decided on public.admin_approvals;
create trigger push_decided after update of status on public.admin_approvals for each row execute function public.trg_push_decided();

-- ===================== 11) grants: staff functions are callable by signed-in users, each checks its own permission =====================
revoke all on function public.admin_claim_item(text, uuid), public.admin_release_item(text, uuid), public.admin_item_marks_list(text),
  public.admin_escalate_report(uuid, text), public.admin_clear_escalation(uuid), public.admin_user_strikes(uuid), public.admin_issue_strike(uuid, text),
  public.admin_appeals_list(text), public.admin_appeal_decide(uuid, boolean, text), public.admin_break_glass_grant(uuid, text, text),
  public.admin_break_glass_end(uuid), public.admin_break_glass_list(), public.admin_member_sessions(uuid), public.admin_unusual_activity(),
  public.strike_step_for(int), public.admin_risky_actions(), public.break_glass_keys() from public, anon;
grant execute on function public.admin_claim_item(text, uuid), public.admin_release_item(text, uuid), public.admin_item_marks_list(text),
  public.admin_escalate_report(uuid, text), public.admin_clear_escalation(uuid), public.admin_user_strikes(uuid), public.admin_issue_strike(uuid, text),
  public.admin_appeals_list(text), public.admin_appeal_decide(uuid, boolean, text), public.admin_break_glass_grant(uuid, text, text),
  public.admin_break_glass_end(uuid), public.admin_break_glass_list(), public.admin_member_sessions(uuid), public.admin_unusual_activity() to authenticated;
grant execute on function public.strike_step_for(int), public.admin_risky_actions(), public.break_glass_keys() to authenticated, service_role;

notify pgrst, 'reload schema';
