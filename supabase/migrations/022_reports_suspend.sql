-- 022: user reports + admin review + timed suspension (v2.1.8, safety series step 2 of 3).
-- Adds: table reports, users.suspended_until / suspension_reason, is_suspended(), my_suspension(),
--       admin_review_report(), view reports_admin_v, and RESTRICTIVE write policies so a suspended user
--       cannot send messages, review, list or edit places, or report. Ban comes in step 3 (023).
-- Nothing is deleted. blocks_reports (chat blocks) stays as it is.

-- ===================== 1) suspension columns on users =====================
alter table public.users add column if not exists suspended_until timestamptz;
alter table public.users add column if not exists suspension_reason text check (suspension_reason is null or char_length(suspension_reason) <= 300);

-- A user must never be able to lift their own suspension: protect_users() (001) now also resets these two columns.
-- Only SECURITY DEFINER functions (which run as the owner) can change them.
create or replace function public.protect_users() returns trigger language plpgsql as $$
begin
  if current_user in ('authenticated','anon') then
    new.role := old.role; new.is_verified := old.is_verified; new.verification_status := old.verification_status; new.id := old.id;
    new.suspended_until := old.suspended_until; new.suspension_reason := old.suspension_reason;
  end if;
  return new;
end $$;

-- ===================== 2) helpers =====================
create or replace function public.is_suspended() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.users where id = auth.uid() and suspended_until is not null and suspended_until > now());
$$;

-- What the app asks at every sign-in / start. Expired suspensions simply read as "not suspended" (no cron needed).
create or replace function public.my_suspension() returns jsonb
language sql stable security definer set search_path = public as $$
  select case when u.suspended_until is not null and u.suspended_until > now()
              then jsonb_build_object('suspended', true, 'until', u.suspended_until, 'reason', coalesce(u.suspension_reason, ''))
              else jsonb_build_object('suspended', false) end
  from public.users u where u.id = auth.uid();
$$;

-- ===================== 3) reports =====================
create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.users(id) on delete cascade,
  reported_user_id uuid not null references public.users(id) on delete cascade,
  bh_id uuid references public.boarding_houses(id) on delete set null,
  reason text not null check (reason in ('scam','fake_listing','harassment','inappropriate','wrong_info','other')),
  details text check (details is null or char_length(details) <= 500),
  status text not null default 'pending' check (status in ('pending','reviewed','dismissed','actioned')),
  created_at timestamptz not null default now(),
  reviewed_by uuid references public.users(id) on delete set null,
  reviewed_at timestamptz,
  admin_notes text check (admin_notes is null or char_length(admin_notes) <= 500),
  check (reporter_id <> reported_user_id)
);
create index if not exists reports_status_created on public.reports (status, created_at desc);
-- one waiting report per reporter + person + place
create unique index if not exists reports_one_pending on public.reports
  (reporter_id, reported_user_id, coalesce(bh_id, '00000000-0000-0000-0000-000000000000'::uuid)) where status = 'pending';

alter table public.reports enable row level security;
create policy reports_insert on public.reports for insert to authenticated
  with check (reporter_id = auth.uid() and status = 'pending' and reviewed_by is null and reviewed_at is null and admin_notes is null);
create policy reports_read on public.reports for select to authenticated
  using (reporter_id = auth.uid() or public.is_admin());
-- no update / delete policy: only admin_review_report() changes a report
revoke all on public.reports from anon;
revoke update, delete, truncate on public.reports from authenticated;

-- Admin list: report + names. Runs with the owner's rights, so the WHERE is the access check; names only, no email or phone.
create or replace view public.reports_admin_v as
  select r.id, r.reporter_id, r.reported_user_id, r.bh_id, r.reason, r.details, r.status, r.created_at,
         r.reviewed_by, r.reviewed_at, r.admin_notes,
         nullif(rp.full_name, '') as reporter_name, nullif(ru.full_name, '') as reported_name, b.name as bh_name,
         ru.suspended_until as reported_suspended_until
  from public.reports r
  left join public.users rp on rp.id = r.reporter_id
  left join public.users ru on ru.id = r.reported_user_id
  left join public.boarding_houses b on b.id = r.bh_id
  where public.is_admin();
revoke all on public.reports_admin_v from anon, authenticated;
grant select on public.reports_admin_v to authenticated;

-- ===================== 4) admin decision =====================
-- act: 'dismiss' | 'reviewed' | 'suspend' (ban is added in step 3). hrs = suspension length in hours (1 hour .. 10 years).
create or replace function public.admin_review_report(rid uuid, act text, hrs int default null, reason text default null, notes text default null)
returns jsonb language plpgsql security definer set search_path = public as $$
declare r public.reports; t public.users; why text := left(btrim(coalesce(reason, '')), 300); nt text := nullif(left(btrim(coalesce(notes, '')), 500), '');
        new_status text; until_ts timestamptz;
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
  else raise exception 'unknown action';
  end if;

  update public.reports set status = new_status, reviewed_by = auth.uid(), reviewed_at = now(), admin_notes = coalesce(nt, admin_notes) where id = rid;
  insert into public.audit_logs (actor_id, action, target_type, target_id, details)
    values (auth.uid(), 'report_' || act, 'user', r.reported_user_id::text,
            jsonb_build_object('report_id', rid, 'reason', nullif(why, ''), 'hours', hrs, 'until', until_ts));
  return jsonb_build_object('status', new_status, 'until', until_ts);
end $$;

revoke all on function public.admin_review_report, public.is_suspended, public.my_suspension from public, anon;
grant execute on function public.admin_review_report, public.is_suspended, public.my_suspension to authenticated;

-- ===================== 5) enforcement: a suspended user cannot write =====================
-- RESTRICTIVE policies are ANDed with the existing ones, so nothing else changes for normal users.
create policy susp_block_msg_insert on public.messages as restrictive for insert to authenticated with check (not public.is_suspended());
create policy susp_block_rev_insert on public.reviews as restrictive for insert to authenticated with check (not public.is_suspended());
create policy susp_block_rev_update on public.reviews as restrictive for update to authenticated using (not public.is_suspended());
create policy susp_block_bh_insert on public.boarding_houses as restrictive for insert to authenticated with check (not public.is_suspended());
create policy susp_block_bh_update on public.boarding_houses as restrictive for update to authenticated using (not public.is_suspended());
create policy susp_block_br_insert on public.blocks_reports as restrictive for insert to authenticated with check (not public.is_suspended());
create policy susp_block_rep_insert on public.reports as restrictive for insert to authenticated with check (not public.is_suspended());

notify pgrst, 'reload schema';
