-- 023: ban by hashed ID, Banned users list, Unban (v2.1.9, safety series step 3 of 3).
-- Scope A (owners only): only owners give an ID, so only owner IDs can be matched. A banned student is blocked on that account only.
-- The ID number is NEVER stored: the server keeps only HMAC-SHA256(pepper, TYPE:NORMALIZED_NUMBER). The pepper lives in app_secrets
-- (RLS on, no policy, no grant) and is read only inside SECURITY DEFINER functions. The app never hashes.
-- Nothing is deleted. Needs 022 (is_suspended, my_suspension, admin_review_report, reports).

create extension if not exists pgcrypto with schema extensions;

-- ===================== 1) ban columns on users =====================
alter table public.users add column if not exists banned_at timestamptz;
alter table public.users add column if not exists banned_by uuid references public.users(id) on delete set null;
alter table public.users add column if not exists ban_reason text check (ban_reason is null or char_length(ban_reason) <= 300);

create or replace function public.protect_users() returns trigger language plpgsql as $$
begin
  if current_user in ('authenticated','anon') then
    new.role := old.role; new.is_verified := old.is_verified; new.verification_status := old.verification_status; new.id := old.id;
    new.suspended_until := old.suspended_until; new.suspension_reason := old.suspension_reason;
    new.banned_at := old.banned_at; new.banned_by := old.banned_by; new.ban_reason := old.ban_reason;
  end if;
  return new;
end $$;

-- ===================== 2) secret pepper (private) =====================
create table if not exists public.app_secrets (name text primary key, value text not null);
alter table public.app_secrets enable row level security;           -- no policy: nobody but the owner role can read it
revoke all on public.app_secrets from public, anon, authenticated;
insert into public.app_secrets (name, value) values ('id_pepper', encode(extensions.gen_random_bytes(32), 'hex')) on conflict (name) do nothing;

-- Normalize: trim, upper, drop spaces and dashes. Hash input = UPPER(id_type) || ':' || number. Server side only.
create or replace function public.id_hash(id_type text, id_number text) returns text
language plpgsql stable security definer set search_path = public, extensions as $$
declare n text := upper(regexp_replace(btrim(coalesce(id_number, '')), '[\s-]', '', 'g')); t text := upper(btrim(coalesce(id_type, ''))); p text;
begin
  if id_number is null or id_number !~ '^[A-Za-z0-9 -]{4,30}$' or char_length(n) < 4 then raise exception 'invalid ID number'; end if;
  if t = '' or char_length(t) > 60 then raise exception 'invalid ID type'; end if;
  select value into p from public.app_secrets where name = 'id_pepper';
  if p is null then raise exception 'not configured'; end if;
  return encode(hmac(t || ':' || n, p, 'sha256'), 'hex');
end $$;
revoke all on function public.id_hash(text, text) from public, anon, authenticated;

-- ===================== 3) owner_verifications.id_hash =====================
alter table public.owner_verifications add column if not exists id_hash text;
create index if not exists owner_verifications_id_hash on public.owner_verifications (id_hash) where id_hash is not null;
-- the hash is written only by submit_id_number() / admin_approve_verification(): a client can neither insert nor change it
create or replace function public.protect_ov() returns trigger language plpgsql as $$
begin
  if current_user in ('authenticated','anon') then
    if tg_op = 'INSERT' then new.id_hash := null; else new.id_hash := old.id_hash; end if;
  end if;
  return new;
end $$;
drop trigger if exists protect_ov on public.owner_verifications;
create trigger protect_ov before insert or update on public.owner_verifications for each row execute function public.protect_ov();
-- a suspended or banned owner cannot start a verification
create policy susp_block_ov_insert on public.owner_verifications as restrictive for insert to authenticated with check (not public.is_suspended());

-- ===================== 4) banned identities =====================
create table if not exists public.banned_identities (
  id uuid primary key default gen_random_uuid(),
  id_hash text not null,
  id_type text,
  user_id uuid references public.users(id) on delete set null,
  banned_by uuid references public.users(id) on delete set null,
  banned_at timestamptz not null default now(),
  reason text check (reason is null or char_length(reason) <= 300),
  active boolean not null default true,
  unbanned_at timestamptz,
  unbanned_by uuid references public.users(id) on delete set null
);
create unique index if not exists banned_identities_active on public.banned_identities (id_hash) where active;
alter table public.banned_identities enable row level security;
create policy banned_identities_admin_read on public.banned_identities for select to authenticated using (public.is_admin());
revoke all on public.banned_identities from anon;
revoke insert, update, delete, truncate on public.banned_identities from authenticated;

-- ===================== 5) suspension helpers also cover bans =====================
create or replace function public.is_suspended() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.users where id = auth.uid()
                 and (banned_at is not null or (suspended_until is not null and suspended_until > now())));
$$;

create or replace function public.my_suspension() returns jsonb
language sql stable security definer set search_path = public as $$
  select case when u.banned_at is not null
              then jsonb_build_object('suspended', false, 'banned', true, 'reason', coalesce(u.ban_reason, ''))
              when u.suspended_until is not null and u.suspended_until > now()
              then jsonb_build_object('suspended', true, 'until', u.suspended_until, 'reason', coalesce(u.suspension_reason, ''))
              else jsonb_build_object('suspended', false) end
  from public.users u where u.id = auth.uid();
$$;

-- ===================== 6) ID checks for the owner (signed-in user) =====================
-- Called BEFORE any photo is uploaded. Never says which account matched.
create or replace function public.check_id_number(id_type text, id_number text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare h text := public.id_hash(id_type, id_number);
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  if exists (select 1 from public.banned_identities where id_hash = h and active) then
    insert into public.audit_logs (actor_id, action, target_type, target_id, details)
      values (auth.uid(), 'id_ban_hit', 'user', auth.uid()::text, jsonb_build_object('hash_prefix', left(h, 8), 'stage', 'check'));
    return jsonb_build_object('banned', true);
  end if;
  return jsonb_build_object('banned', false);
end $$;

-- Called right after the owner_verifications insert, for the caller's own pending row. Stores only the hash.
create or replace function public.submit_id_number(vid uuid, id_type text, id_number text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v public.owner_verifications; h text;
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  select * into v from public.owner_verifications where id = vid and owner_id = auth.uid() and status = 'pending';
  if v.id is null then raise exception 'verification not found'; end if;
  h := public.id_hash(v.id_type, id_number);                        -- the type stored on the row is the one hashed
  if exists (select 1 from public.banned_identities where id_hash = h and active) then
    update public.owner_verifications set status = 'rejected', rejection_reason = 'Banned', reviewed_at = now() where id = vid;
    insert into public.audit_logs (actor_id, action, target_type, target_id, details)
      values (auth.uid(), 'id_ban_hit', 'owner_verification', vid::text, jsonb_build_object('hash_prefix', left(h, 8), 'stage', 'submit'));
    return jsonb_build_object('banned', true);
  end if;
  update public.owner_verifications set id_hash = h where id = vid;
  return jsonb_build_object('banned', false);
end $$;

-- ===================== 7) admin: approve a verification only with the number retyped =====================
create or replace function public.admin_approve_verification(vid uuid, id_number text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v public.owner_verifications; h text;
begin
  if not public.is_admin() then raise exception 'admins only'; end if;
  select * into v from public.owner_verifications where id = vid and status = 'pending';
  if v.id is null then raise exception 'not pending'; end if;
  h := public.id_hash(v.id_type, id_number);
  if v.id_hash is not null and v.id_hash <> h then raise exception 'ID number does not match the number the owner typed'; end if;
  if exists (select 1 from public.banned_identities where id_hash = h and active) then raise exception 'this ID is banned'; end if;
  -- a request sent before 2.1.9 has no stored hash: the admin's number is stored so a later ban can still block this ID
  update public.owner_verifications set status = 'verified', id_hash = h, reviewed_by = auth.uid(), reviewed_at = now(), rejection_reason = null where id = vid;
  update public.users set is_verified = true, verification_status = 'verified' where id = v.owner_id;
  insert into public.audit_logs (actor_id, action, target_type, target_id, details)
    values (auth.uid(), 'verification_approved', 'owner_verification', vid::text, jsonb_build_object('owner_id', v.owner_id, 'id_checked', true));
  return '{}'::jsonb;
end $$;

-- the old approve path would skip the number check: approving now goes through admin_approve_verification only (reject is unchanged)
create or replace function public.review_verification(vid uuid, approve boolean, reason text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v public.owner_verifications;
begin
  if not public.is_admin() then raise exception 'admins only'; end if;
  if approve then raise exception 'use admin_approve_verification'; end if;
  select * into v from public.owner_verifications where id = vid and status = 'pending';
  if v.id is null then raise exception 'not pending'; end if;
  if coalesce(trim(reason), '') = '' then raise exception 'reason required'; end if;
  update public.owner_verifications set status = 'rejected', reviewed_by = auth.uid(), reviewed_at = now(), rejection_reason = reason where id = vid;
  update public.users set is_verified = false, verification_status = 'rejected' where id = v.owner_id;
  insert into public.audit_logs (actor_id, action, target_type, target_id, details)
    values (auth.uid(), 'verification_rejected', 'owner_verification', vid::text, jsonb_build_object('owner_id', v.owner_id, 'reason', reason));
  return '{}'::jsonb;
end $$;

-- ===================== 8) admin_review_report: add 'ban' =====================
create or replace function public.admin_review_report(rid uuid, act text, hrs int default null, reason text default null, notes text default null)
returns jsonb language plpgsql security definer set search_path = public as $$
declare r public.reports; t public.users; why text := left(btrim(coalesce(reason, '')), 300); nt text := nullif(left(btrim(coalesce(notes, '')), 500), '');
        new_status text; until_ts timestamptz; id_banned boolean := false; h text;
begin
  if not public.is_admin() then raise exception 'admins only'; end if;
  select * into r from public.reports where id = rid for update;
  if not found then raise exception 'report not found'; end if;
  if r.status <> 'pending' then raise exception 'this report was already handled'; end if;

  if act = 'dismiss' then new_status := 'dismissed';
  elsif act = 'reviewed' then new_status := 'reviewed';
  elsif act = 'suspend' then
    if hrs is null or hrs < 1 or hrs > 87600 then raise exception 'suspension must be between 1 hour and 10 years'; end if;
    if why = '' then raise exception 'a reason is required'; end if;
    select * into t from public.users where id = r.reported_user_id;
    if not found then raise exception 'user not found'; end if;
    if t.role = 'admin' then raise exception 'an admin cannot be suspended'; end if;
    until_ts := now() + make_interval(hours => hrs);
    update public.users set suspended_until = until_ts, suspension_reason = why where id = r.reported_user_id;
    new_status := 'actioned';
  elsif act = 'ban' then
    if why = '' then raise exception 'a reason is required'; end if;
    select * into t from public.users where id = r.reported_user_id;
    if not found then raise exception 'user not found'; end if;
    if t.role = 'admin' then raise exception 'an admin cannot be banned'; end if;
    update public.users set banned_at = now(), banned_by = auth.uid(), ban_reason = why where id = t.id;
    -- every different ID this person ever sent becomes a blocked identity
    for h in select distinct ov.id_hash from public.owner_verifications ov where ov.owner_id = t.id and ov.id_hash is not null loop
      if exists (select 1 from public.banned_identities where id_hash = h and active) then
        update public.banned_identities set user_id = t.id where id_hash = h and active;
      else
        insert into public.banned_identities (id_hash, id_type, user_id, banned_by, reason)
          values (h, (select ov.id_type from public.owner_verifications ov where ov.owner_id = t.id and ov.id_hash = h order by ov.submitted_at desc limit 1), t.id, auth.uid(), why);
      end if;
      id_banned := true;
    end loop;
    -- their active places leave the search (same effect as admin_takedown)
    update public.boarding_houses set status = 'taken_down' where owner_id = t.id and status = 'active';
    new_status := 'actioned';
  else raise exception 'unknown action';
  end if;

  update public.reports set status = new_status, reviewed_by = auth.uid(), reviewed_at = now(), admin_notes = coalesce(nt, admin_notes) where id = rid;
  insert into public.audit_logs (actor_id, action, target_type, target_id, details)
    values (auth.uid(), 'report_' || act, 'user', r.reported_user_id::text,
            jsonb_build_object('report_id', rid, 'reason', nullif(why, ''), 'hours', hrs, 'until', until_ts, 'id_banned', id_banned));
  return jsonb_build_object('status', new_status, 'until', until_ts, 'id_banned', id_banned);
end $$;

-- ===================== 9) admin: unban and list =====================
create or replace function public.admin_unban(uid uuid, note text default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare nt text := nullif(left(btrim(coalesce(note, '')), 300), '');
begin
  if not public.is_admin() then raise exception 'admins only'; end if;
  if not exists (select 1 from public.users where id = uid and banned_at is not null) then raise exception 'this user is not banned'; end if;
  update public.users set banned_at = null, banned_by = null, ban_reason = null where id = uid;
  update public.banned_identities set active = false, unbanned_at = now(), unbanned_by = auth.uid() where user_id = uid and active;
  insert into public.audit_logs (actor_id, action, target_type, target_id, details)
    values (auth.uid(), 'user_unban', 'user', uid::text, jsonb_build_object('note', nt));
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.admin_banned_list()
returns table (user_id uuid, full_name text, email text, ban_reason text, banned_at timestamptz, banned_by_name text, id_banned boolean)
language plpgsql stable security definer set search_path = public, auth as $$
begin
  if not public.is_admin() then raise exception 'admins only'; end if;
  return query
    select u.id, nullif(u.full_name, ''), au.email::text, u.ban_reason, u.banned_at, nullif(b.full_name, ''),
           exists (select 1 from public.banned_identities bi where bi.user_id = u.id and bi.active)
    from public.users u
    left join auth.users au on au.id = u.id
    left join public.users b on b.id = u.banned_by
    where u.banned_at is not null
    order by u.banned_at desc;
end $$;

revoke all on function public.check_id_number, public.submit_id_number, public.admin_approve_verification, public.review_verification,
  public.admin_review_report, public.admin_unban, public.admin_banned_list, public.is_suspended, public.my_suspension from public, anon;
grant execute on function public.check_id_number, public.submit_id_number, public.admin_approve_verification, public.review_verification,
  public.admin_review_report, public.admin_unban, public.admin_banned_list, public.is_suspended, public.my_suspension to authenticated;

notify pgrst, 'reload schema';
