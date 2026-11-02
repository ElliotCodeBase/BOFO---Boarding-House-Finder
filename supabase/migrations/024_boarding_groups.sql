-- 024: Boarding group chats (v2.2.0, "Boarding" series step 1 of 5). Needs 022 + 023 (is_suspended covers ban + timed suspension).
-- One group chat per boarding house. Members = tenants (students who joined with the owner's invite code) + that BH's owner.
-- Adds: bh_invite_codes, bh_members, bh_group_messages, bh_announcement_acks, view bh_group_feed_v,
--       RPCs (join / leave / mark read / my groups / post, pin, edit announcements / ack / stats / mute / remove / members / regenerate code),
--       RESTRICTIVE policies so muted, banned or suspended users cannot write.
-- Nothing existing is changed or deleted. NOT applied live: needs the user's OK.

-- ===================== 1) invite codes (kept OUT of boarding_houses: that table is readable by every signed-in user) =====================
create or replace function public.gen_invite_code() returns text
language plpgsql volatile set search_path = public, extensions as $$
declare alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; b bytea := gen_random_bytes(8); s text := ''; i int;
begin
  for i in 0..7 loop s := s || substr(alphabet, (get_byte(b, i) % 32) + 1, 1); end loop;
  return s;
end $$;

create table if not exists public.bh_invite_codes (
  bh_id uuid primary key references public.boarding_houses(id) on delete cascade,
  code text not null unique check (code ~ '^[A-Z2-9]{8}$'),
  rotated_at timestamptz not null default now()
);
alter table public.bh_invite_codes enable row level security;
create policy invite_owner_read on public.bh_invite_codes for select to authenticated
  using (exists (select 1 from public.boarding_houses b where b.id = bh_id and b.owner_id = auth.uid()));
revoke all on public.bh_invite_codes from anon;
revoke insert, update, delete, truncate on public.bh_invite_codes from authenticated;   -- changed only by regenerate_invite_code() / the trigger below

-- ===================== 2) members =====================
create table if not exists public.bh_members (
  bh_id uuid not null references public.boarding_houses(id) on delete cascade,
  user_id uuid not null references public.users(id) on delete cascade,
  role text not null check (role in ('owner','student')),
  status text not null default 'active' check (status in ('active','left','removed')),
  joined_at timestamptz not null default now(),
  muted boolean not null default false,
  last_read_at timestamptz not null default now(),          -- unread counter = messages newer than this
  primary key (bh_id, user_id)
);
create index if not exists bh_members_user on public.bh_members (user_id, status);
-- a student lives in ONE boarding house at a time (leave first to join another)
create unique index if not exists bh_members_one_active_student on public.bh_members (user_id) where role = 'student' and status = 'active';
alter table public.bh_members enable row level security;
revoke all on public.bh_members from anon;
revoke insert, update, delete, truncate on public.bh_members from authenticated;       -- only the RPCs below change members

-- ===================== 3) helpers (SECURITY DEFINER so policies never recurse) =====================
create or replace function public.is_bh_owner(bh uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.boarding_houses where id = bh and owner_id = auth.uid());
$$;
create or replace function public.is_bh_member(bh uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select public.is_bh_owner(bh)
      or exists (select 1 from public.bh_members where bh_id = bh and user_id = auth.uid() and status = 'active');
$$;
-- may write a normal message: member, not muted, not banned / suspended
create or replace function public.can_post_group(bh uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select public.is_bh_member(bh) and not public.is_suspended()
     and not exists (select 1 from public.bh_members where bh_id = bh and user_id = auth.uid() and muted);
$$;

-- owner row + invite code for every NEW boarding house (backfill for existing ones is at the end of this file)
create or replace function public.bh_after_insert() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.bh_members (bh_id, user_id, role) values (new.id, new.owner_id, 'owner') on conflict do nothing;
  insert into public.bh_invite_codes (bh_id, code) values (new.id, public.gen_invite_code()) on conflict do nothing;
  return new;
end $$;
drop trigger if exists trg_bh_group_init on public.boarding_houses;
create trigger trg_bh_group_init after insert on public.boarding_houses for each row execute function public.bh_after_insert();

-- ===================== 4) messages =====================
create table if not exists public.bh_group_messages (
  id uuid primary key default gen_random_uuid(),
  bh_id uuid not null references public.boarding_houses(id) on delete cascade,
  sender_id uuid not null references public.users(id) on delete cascade,
  body text not null check (char_length(btrim(body)) between 1 and 1000),
  created_at timestamptz not null default now(),
  is_announcement boolean not null default false,
  title text check (title is null or char_length(btrim(title)) between 1 and 120),
  pinned boolean not null default false,
  pinned_at timestamptz,
  edited_at timestamptz,
  check (not is_announcement or title is not null),
  check (pinned = false or is_announcement),
  check (pinned = (pinned_at is not null))
);
create index if not exists bh_group_messages_bh_created on public.bh_group_messages (bh_id, created_at desc);
create index if not exists bh_group_messages_pinned on public.bh_group_messages (bh_id, pinned_at desc) where pinned;

alter table public.bh_group_messages enable row level security;
create policy bgm_read on public.bh_group_messages for select to authenticated using (public.is_bh_member(bh_id));
-- plain chat messages only; announcements are created by post_announcement() (owner only)
create policy bgm_insert on public.bh_group_messages for insert to authenticated
  with check (sender_id = auth.uid() and public.can_post_group(bh_id)
              and not is_announcement and not pinned and pinned_at is null and title is null and edited_at is null);
-- delete: my own message, or ANY message in a house I own
create policy bgm_delete on public.bh_group_messages for delete to authenticated
  using ((sender_id = auth.uid() and public.is_bh_member(bh_id)) or public.is_bh_owner(bh_id));
-- no update policy: pin / unpin / edit go through the RPCs
revoke all on public.bh_group_messages from anon;
revoke update, truncate on public.bh_group_messages from authenticated;

create policy susp_block_bgm_insert on public.bh_group_messages as restrictive for insert to authenticated with check (not public.is_suspended());

-- ===================== 5) acknowledgments ("Got it") =====================
create table if not exists public.bh_announcement_acks (
  message_id uuid not null references public.bh_group_messages(id) on delete cascade,
  user_id uuid not null references public.users(id) on delete cascade,
  acked_at timestamptz not null default now(),
  primary key (message_id, user_id)
);
alter table public.bh_announcement_acks enable row level security;
create policy acks_read on public.bh_announcement_acks for select to authenticated
  using (user_id = auth.uid()
         or exists (select 1 from public.bh_group_messages m where m.id = message_id and public.is_bh_owner(m.bh_id)));
revoke all on public.bh_announcement_acks from anon;
revoke insert, update, delete, truncate on public.bh_announcement_acks from authenticated;   -- ack_announcement() only

-- ===================== 6) feed view: messages + sender name + role badge =====================
-- Runs with the owner's rights (public.users stays private), so the WHERE clause is the access check.
create or replace view public.bh_group_feed_v as
  select m.id, m.bh_id, m.sender_id, m.body, m.created_at, m.is_announcement, m.title, m.pinned, m.pinned_at, m.edited_at,
         coalesce(nullif(u.full_name, ''), case when b.owner_id = m.sender_id then 'Owner' else 'Student' end) as sender_name,
         u.photo_url as sender_photo,
         case when b.owner_id = m.sender_id then 'owner' else 'student' end as sender_role,
         exists (select 1 from public.bh_announcement_acks a where a.message_id = m.id and a.user_id = auth.uid()) as i_acked,
         case when m.is_announcement and b.owner_id = auth.uid()
              then (select count(*) from public.bh_announcement_acks a where a.message_id = m.id)::int end as ack_count
  from public.bh_group_messages m
  join public.boarding_houses b on b.id = m.bh_id
  left join public.users u on u.id = m.sender_id
  where public.is_bh_member(m.bh_id);
revoke all on public.bh_group_feed_v from anon, authenticated;
grant select on public.bh_group_feed_v to authenticated;

-- ===================== 7) RPCs (all return jsonb so the client can always parse) =====================
-- Student joins with the owner's code. Wrong code and "house unavailable" give the same answer.
create or replace function public.join_bh_group(code text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare c text := upper(regexp_replace(btrim(coalesce(code, '')), '[\s-]', '', 'g')); b public.boarding_houses; m public.bh_members;
begin
  if public.is_suspended() then raise exception 'your account cannot join groups right now'; end if;
  if not exists (select 1 from public.users where id = auth.uid() and role = 'student') then raise exception 'students only'; end if;
  select bh.* into b from public.boarding_houses bh join public.bh_invite_codes ic on ic.bh_id = bh.id
    where ic.code = c and bh.status <> 'taken_down';
  if b.id is null then raise exception 'invalid code'; end if;
  select * into m from public.bh_members where bh_id = b.id and user_id = auth.uid();
  if m.status = 'removed' then raise exception 'you were removed from this group'; end if;
  if m.status = 'active' then return jsonb_build_object('bh_id', b.id, 'bh_name', b.name, 'already', true); end if;
  if exists (select 1 from public.bh_members where user_id = auth.uid() and role = 'student' and status = 'active') then
    raise exception 'leave your current group first';
  end if;
  insert into public.bh_members (bh_id, user_id, role) values (b.id, auth.uid(), 'student')
    on conflict (bh_id, user_id) do update set status = 'active', joined_at = now(), last_read_at = now();
  return jsonb_build_object('bh_id', b.id, 'bh_name', b.name, 'already', false);
end $$;

create or replace function public.leave_bh_group(bh uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  update public.bh_members set status = 'left' where bh_id = bh and user_id = auth.uid() and role = 'student' and status = 'active';
  return '{}'::jsonb;
end $$;

create or replace function public.mark_group_read(bh uuid) returns jsonb
language sql security definer set search_path = public as $$
  update public.bh_members set last_read_at = now() where bh_id = bh and user_id = auth.uid() and status = 'active';
  select '{}'::jsonb;
$$;

-- Group list for the caller: student = the house they joined, owner = one row per house they own.
create or replace function public.my_bh_groups() returns table (
  bh_id uuid, bh_name text, my_role text, member_count int, unread int, pinned_count int,
  last_message text, last_message_at timestamptz, last_sender_name text, muted boolean)
language sql stable security definer set search_path = public as $$
  select b.id, b.name, m.role,
         (select count(*) from public.bh_members x where x.bh_id = b.id and x.status = 'active')::int,
         (select count(*) from public.bh_group_messages g where g.bh_id = b.id and g.created_at > m.last_read_at and g.sender_id <> auth.uid())::int,
         (select count(*) from public.bh_group_messages g where g.bh_id = b.id and g.pinned)::int,
         lm.body, lm.created_at,
         coalesce(nullif(lu.full_name, ''), case when lm.sender_id = b.owner_id then 'Owner' else 'Student' end),
         m.muted
  from public.bh_members m
  join public.boarding_houses b on b.id = m.bh_id
  left join lateral (select g.body, g.created_at, g.sender_id from public.bh_group_messages g where g.bh_id = b.id order by g.created_at desc limit 1) lm on true
  left join public.users lu on lu.id = lm.sender_id
  where m.user_id = auth.uid() and m.status = 'active' and b.status <> 'taken_down'
  order by coalesce(lm.created_at, m.joined_at) desc;
$$;

-- Owner posts ONE announcement to one or several of their houses. Each copy is pinned; a house keeps at most 3 pinned (the oldest is unpinned).
create or replace function public.post_announcement(bhs uuid[], ttl text, msg text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare ids uuid[] := '{}'; uniq uuid[]; b uuid; nid uuid; t text := btrim(coalesce(ttl, '')); x text := btrim(coalesce(msg, ''));
begin
  if public.is_suspended() then raise exception 'your account cannot post right now'; end if;
  if bhs is null or cardinality(bhs) = 0 or cardinality(bhs) > 20 then raise exception 'choose 1 to 20 houses'; end if;
  if char_length(t) not between 1 and 120 then raise exception 'title must be 1 to 120 characters'; end if;
  if char_length(x) not between 1 and 1000 then raise exception 'message must be 1 to 1000 characters'; end if;
  uniq := array(select distinct u.v from unnest(bhs) as u(v));
  foreach b in array uniq loop
    if not public.is_bh_owner(b) then raise exception 'not your boarding house'; end if;
  end loop;
  foreach b in array uniq loop
    insert into public.bh_group_messages (bh_id, sender_id, body, is_announcement, title, pinned, pinned_at)
      values (b, auth.uid(), x, true, t, true, now()) returning id into nid;
    ids := ids || nid;
    update public.bh_group_messages set pinned = false, pinned_at = null
      where id in (select id from public.bh_group_messages where bh_id = b and pinned order by pinned_at desc, created_at desc offset 3);
  end loop;
  return jsonb_build_object('ids', to_jsonb(ids));
end $$;

create or replace function public.pin_announcement(msg uuid, pin boolean) returns jsonb
language plpgsql security definer set search_path = public as $$
declare m public.bh_group_messages;
begin
  select * into m from public.bh_group_messages where id = msg for update;
  if m.id is null or not m.is_announcement or not public.is_bh_owner(m.bh_id) then raise exception 'announcement not found'; end if;
  if pin and not m.pinned then
    if (select count(*) from public.bh_group_messages where bh_id = m.bh_id and pinned) >= 3 then raise exception 'only 3 pinned announcements: unpin one first'; end if;
    update public.bh_group_messages set pinned = true, pinned_at = now() where id = msg;
  elsif not pin and m.pinned then
    update public.bh_group_messages set pinned = false, pinned_at = null where id = msg;
  end if;
  return '{}'::jsonb;
end $$;

create or replace function public.edit_announcement(msg uuid, ttl text, body_text text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare m public.bh_group_messages; t text := btrim(coalesce(ttl, '')); x text := btrim(coalesce(body_text, ''));
begin
  if public.is_suspended() then raise exception 'your account cannot post right now'; end if;
  select * into m from public.bh_group_messages where id = msg for update;
  if m.id is null or not m.is_announcement or not public.is_bh_owner(m.bh_id) then raise exception 'announcement not found'; end if;
  if char_length(t) not between 1 and 120 then raise exception 'title must be 1 to 120 characters'; end if;
  if char_length(x) not between 1 and 1000 then raise exception 'message must be 1 to 1000 characters'; end if;
  update public.bh_group_messages set title = t, body = x, edited_at = now() where id = msg;
  return '{}'::jsonb;
end $$;

-- "Got it": any active member (the owner too) can acknowledge an announcement once.
create or replace function public.ack_announcement(msg uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare m public.bh_group_messages;
begin
  select * into m from public.bh_group_messages where id = msg;
  if m.id is null or not m.is_announcement or not public.is_bh_member(m.bh_id) then raise exception 'announcement not found'; end if;
  insert into public.bh_announcement_acks (message_id, user_id) values (msg, auth.uid()) on conflict do nothing;
  return '{}'::jsonb;
end $$;

-- Owner only: how many members saw ("Got it") each announcement of a house.
create or replace function public.announcement_stats(bh uuid) returns table (message_id uuid, acked int, members int)
language sql stable security definer set search_path = public as $$
  select m.id, (select count(*) from public.bh_announcement_acks a where a.message_id = m.id)::int,
         (select count(*) from public.bh_members x where x.bh_id = bh and x.status = 'active')::int
  from public.bh_group_messages m where m.bh_id = bh and m.is_announcement and public.is_bh_owner(bh);
$$;

-- Owner only: member list for moderation (name + role + muted; no email or phone).
create or replace function public.bh_members_list(bh uuid) returns table (user_id uuid, full_name text, role text, status text, muted boolean, joined_at timestamptz)
language sql stable security definer set search_path = public as $$
  select m.user_id, coalesce(nullif(u.full_name, ''), case when m.role = 'owner' then 'Owner' else 'Student' end), m.role, m.status, m.muted, m.joined_at
  from public.bh_members m left join public.users u on u.id = m.user_id
  where m.bh_id = bh and m.status <> 'left' and public.is_bh_owner(bh)
  order by (m.role = 'owner') desc, m.joined_at;
$$;

create or replace function public.set_member_muted(bh uuid, uid uuid, mute boolean) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_bh_owner(bh) then raise exception 'owner only'; end if;
  update public.bh_members set muted = mute where bh_id = bh and user_id = uid and role = 'student';
  return '{}'::jsonb;
end $$;

-- Removed members cannot read, post or rejoin with the code.
create or replace function public.remove_member(bh uuid, uid uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_bh_owner(bh) then raise exception 'owner only'; end if;
  update public.bh_members set status = 'removed' where bh_id = bh and user_id = uid and role = 'student';
  return '{}'::jsonb;
end $$;

create or replace function public.regenerate_invite_code(bh uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare c text;
begin
  if not public.is_bh_owner(bh) then raise exception 'owner only'; end if;
  loop
    c := public.gen_invite_code();
    begin
      insert into public.bh_invite_codes (bh_id, code) values (bh, c)
        on conflict (bh_id) do update set code = excluded.code, rotated_at = now();
      exit;
    exception when unique_violation then null;      -- the (very rare) code clash: draw again
    end;
  end loop;
  return jsonb_build_object('code', c);
end $$;

revoke all on function public.gen_invite_code, public.bh_after_insert from public, anon, authenticated;
revoke all on function public.is_bh_owner, public.is_bh_member, public.can_post_group, public.join_bh_group, public.leave_bh_group,
  public.mark_group_read, public.my_bh_groups, public.post_announcement, public.pin_announcement, public.edit_announcement,
  public.ack_announcement, public.announcement_stats, public.bh_members_list, public.set_member_muted, public.remove_member,
  public.regenerate_invite_code from public, anon;
grant execute on function public.is_bh_owner, public.is_bh_member, public.can_post_group, public.join_bh_group, public.leave_bh_group,
  public.mark_group_read, public.my_bh_groups, public.post_announcement, public.pin_announcement, public.edit_announcement,
  public.ack_announcement, public.announcement_stats, public.bh_members_list, public.set_member_muted, public.remove_member,
  public.regenerate_invite_code to authenticated;

-- ===================== 8) backfill: existing houses get their owner row + invite code =====================
insert into public.bh_members (bh_id, user_id, role) select id, owner_id, 'owner' from public.boarding_houses on conflict do nothing;
insert into public.bh_invite_codes (bh_id, code) select id, public.gen_invite_code() from public.boarding_houses on conflict do nothing;

notify pgrst, 'reload schema';
