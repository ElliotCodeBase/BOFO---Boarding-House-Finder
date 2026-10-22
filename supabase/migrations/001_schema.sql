-- BOFO Finder schema. Run in the Supabase SQL editor (or `supabase db push`).
-- Every table has RLS ENABLED. The app only ever uses the anon key + the user's JWT.

create extension if not exists pgcrypto;

-- ===================== helper =====================
create table public.colleges (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  lat double precision not null,
  lng double precision not null
);

create table public.users (
  id uuid primary key references auth.users(id) on delete cascade,
  auth_provider text not null default 'google',
  role text not null default 'student' check (role in ('student','owner','admin')),
  is_verified boolean not null default false,
  verification_status text not null default 'unverified'
     check (verification_status in ('unverified','pending','verified','rejected')),
  full_name text, photo_url text,
  phone text, sex text, age int check (age is null or age between 10 and 100),
  default_college_id uuid references public.colleges(id) on delete set null,
  created_at timestamptz not null default now()
);

create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.users where id = auth.uid() and role = 'admin');
$$;

create or replace function public.is_verified_owner(uid uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.users where id = uid and role = 'owner' and is_verified);
$$;

-- ===================== core tables =====================
create table public.boarding_houses (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.users(id) on delete cascade,
  name text not null check (char_length(name) between 2 and 120),
  address text not null check (char_length(address) between 3 and 250),
  lat double precision not null check (lat between -90 and 90),
  lng double precision not null check (lng between -180 and 180),
  price numeric(10,2) not null check (price > 0),
  price_mode text not null default 'per_bed' check (price_mode in ('per_bed','per_room')),
  free_water boolean not null default false,        -- "amenities" kept as columns for simple filtering
  free_electricity boolean not null default false,
  free_wifi boolean not null default false,
  status text not null default 'active' check (status in ('active','unlisted','taken_down')),
  created_at timestamptz not null default now()
);
create index on public.boarding_houses (owner_id);
create index on public.boarding_houses (status, created_at desc);
create index on public.boarding_houses (lat, lng);

create table public.rooms (
  id uuid primary key default gen_random_uuid(),
  boarding_house_id uuid not null references public.boarding_houses(id) on delete cascade,
  beds int not null check (beds between 1 and 50),
  occupants int not null default 0,
  check (occupants between 0 and beds)
);
create index on public.rooms (boarding_house_id);

create table public.bh_contacts (
  boarding_house_id uuid primary key references public.boarding_houses(id) on delete cascade,
  phone text check (phone is null or char_length(phone) <= 30),
  facebook text check (facebook is null or char_length(facebook) <= 200),
  email text check (email is null or char_length(email) <= 120),
  check (coalesce(nullif(phone,''), nullif(facebook,''), nullif(email,'')) is not null)
);

create table public.photos (
  id uuid primary key default gen_random_uuid(),
  boarding_house_id uuid not null references public.boarding_houses(id) on delete cascade,
  url text not null,
  sort_order int not null default 0
);
create index on public.photos (boarding_house_id, sort_order);

create table public.favorites (
  user_id uuid not null references public.users(id) on delete cascade,
  boarding_house_id uuid not null references public.boarding_houses(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, boarding_house_id)
);

create table public.reviews (
  id uuid primary key default gen_random_uuid(),
  boarding_house_id uuid not null references public.boarding_houses(id) on delete cascade,
  student_id uuid not null references public.users(id) on delete cascade,
  rating int not null check (rating between 1 and 5),
  comment text check (comment is null or char_length(comment) <= 1000),
  owner_reply text check (owner_reply is null or char_length(owner_reply) <= 1000),
  created_at timestamptz not null default now(),
  unique (boarding_house_id, student_id)
);

create table public.bh_activity (
  boarding_house_id uuid primary key references public.boarding_houses(id) on delete cascade,
  views int not null default 0,
  favorites_count int not null default 0,
  chats_count int not null default 0,
  updated_at timestamptz not null default now()
);

create table public.conversations (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references public.users(id) on delete cascade,
  owner_id uuid not null references public.users(id) on delete cascade,
  boarding_house_id uuid not null references public.boarding_houses(id) on delete cascade,
  last_message_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  unique (student_id, owner_id, boarding_house_id)
);
create index on public.conversations (student_id, last_message_at desc);
create index on public.conversations (owner_id, last_message_at desc);

create table public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_id uuid not null references public.users(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 1000),
  created_at timestamptz not null default now(),
  read_at timestamptz
);
create index on public.messages (conversation_id, created_at desc);

create table public.blocks_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.users(id) on delete cascade,
  reported_user_id uuid not null references public.users(id) on delete cascade,
  reason text check (reason is null or char_length(reason) <= 300),
  is_block boolean not null default true,
  created_at timestamptz not null default now(),
  check (reporter_id <> reported_user_id)
);

create table public.user_agreements (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.users(id) on delete cascade,
  document_type text not null check (document_type in ('terms','privacy','owner_agreement','verification_consent','community','ai_notice')),
  document_version text not null,
  accepted_at timestamptz not null default now()
);

create table public.owner_verifications (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.users(id) on delete cascade,
  id_type text not null,
  id_front_url text not null, id_back_url text not null, selfie_url text not null,   -- storage PATHS (private bucket)
  status text not null default 'pending' check (status in ('pending','verified','rejected')),
  reviewed_by uuid references public.users(id),
  rejection_reason text,
  submitted_at timestamptz not null default now(),
  reviewed_at timestamptz
);
create index on public.owner_verifications (status, submitted_at);

create table public.audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_id uuid references public.users(id) on delete set null,
  action text not null, target_type text not null, target_id text,
  details jsonb, created_at timestamptz not null default now()
);

create table public.ai_usage (            -- rate limiting for BOFO (written by the Edge Function with the service role)
  id bigserial primary key,
  user_id uuid not null, created_at timestamptz not null default now()
);
create index on public.ai_usage (user_id, created_at desc);

-- ===================== views (definer views expose only safe columns) =====================
create view public.profiles_v as
  select id, role, is_verified, verification_status, full_name, photo_url, phone, default_college_id
  from public.users where id = auth.uid();

create view public.bh_cards as
  select b.id, b.owner_id, b.name, b.address, b.lat, b.lng, b.price, b.price_mode,
         b.free_water, b.free_electricity, b.free_wifi, b.status, b.created_at,
         coalesce((select sum(r.beds - r.occupants) from public.rooms r where r.boarding_house_id = b.id), 0)::int as available_beds,
         coalesce((select round(avg(v.rating)::numeric, 1) from public.reviews v where v.boarding_house_id = b.id), 0) as avg_rating,
         (select count(*) from public.reviews v where v.boarding_house_id = b.id)::int as review_count,
         (select p.url from public.photos p where p.boarding_house_id = b.id order by p.sort_order limit 1) as photo_url,
         (u.role = 'owner' and u.is_verified) as owner_verified,
         coalesce(a.views + 3 * a.favorites_count + 5 * a.chats_count, 0) as hot_score
  from public.boarding_houses b
  join public.users u on u.id = b.owner_id
  left join public.bh_activity a on a.boarding_house_id = b.id
  where b.status = 'active' or b.owner_id = auth.uid() or public.is_admin();

create view public.inbox_v as
  select c.id, c.student_id, c.owner_id, c.boarding_house_id, c.last_message_at,
         b.name as bh_name,
         case when c.student_id = auth.uid() then ow.full_name else st.full_name end as other_name,
         (select m.body from public.messages m where m.conversation_id = c.id order by m.created_at desc limit 1) as last_message,
         (select count(*) from public.messages m where m.conversation_id = c.id and m.sender_id <> auth.uid() and m.read_at is null)::int as unread
  from public.conversations c
  join public.boarding_houses b on b.id = c.boarding_house_id
  join public.users st on st.id = c.student_id
  join public.users ow on ow.id = c.owner_id
  where auth.uid() in (c.student_id, c.owner_id);

grant select on public.profiles_v, public.bh_cards, public.inbox_v to authenticated;

-- ===================== triggers =====================
-- 1) Nobody can self-assign role / verification. Only SECURITY DEFINER functions (run as owner) may change them.
create or replace function public.protect_users() returns trigger language plpgsql as $$
begin
  if current_user in ('authenticated','anon') then
    new.role := old.role; new.is_verified := old.is_verified; new.verification_status := old.verification_status; new.id := old.id;
  end if;
  return new;
end $$;
create trigger trg_protect_users before update on public.users for each row execute function public.protect_users();

-- 2) Owners may only edit owner_reply on reviews; students may not touch owner_reply.
create or replace function public.protect_reviews() returns trigger language plpgsql as $$
declare owner uuid;
begin
  select owner_id into owner from public.boarding_houses where id = old.boarding_house_id;
  if auth.uid() = owner then
    new.rating := old.rating; new.comment := old.comment; new.student_id := old.student_id; new.boarding_house_id := old.boarding_house_id;
  else
    new.owner_reply := old.owner_reply; new.student_id := old.student_id; new.boarding_house_id := old.boarding_house_id;
  end if;
  return new;
end $$;
create trigger trg_protect_reviews before update on public.reviews for each row execute function public.protect_reviews();

-- 3) Keep conversation.last_message_at fresh.
create or replace function public.touch_conversation() returns trigger language plpgsql security definer set search_path = public as $$
begin update public.conversations set last_message_at = new.created_at where id = new.conversation_id; return new; end $$;
create trigger trg_touch_conv after insert on public.messages for each row execute function public.touch_conversation();

-- 4) Activity counters for the "Hot" tab (pair with a daily pg_cron job that halves the counts to keep it "recent").
create or replace function public.ensure_activity() returns trigger language plpgsql security definer set search_path = public as $$
begin insert into public.bh_activity (boarding_house_id) values (new.id) on conflict do nothing; return new; end $$;
create trigger trg_activity_row after insert on public.boarding_houses for each row execute function public.ensure_activity();

create or replace function public.count_favorite() returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' then update public.bh_activity set favorites_count = favorites_count + 1, updated_at = now() where boarding_house_id = new.boarding_house_id;
  else update public.bh_activity set favorites_count = greatest(0, favorites_count - 1), updated_at = now() where boarding_house_id = old.boarding_house_id; end if;
  return null;
end $$;
create trigger trg_fav_count after insert or delete on public.favorites for each row execute function public.count_favorite();

create or replace function public.count_chat() returns trigger language plpgsql security definer set search_path = public as $$
begin update public.bh_activity set chats_count = chats_count + 1, updated_at = now() where boarding_house_id = new.boarding_house_id; return null; end $$;
create trigger trg_chat_count after insert on public.conversations for each row execute function public.count_chat();

-- 5) Messages: only participants, not blocked either way.
create or replace function public.check_message() returns trigger language plpgsql security definer set search_path = public as $$
declare c public.conversations;
begin
  select * into c from public.conversations where id = new.conversation_id;
  if c.id is null or auth.uid() not in (c.student_id, c.owner_id) or new.sender_id <> auth.uid() then raise exception 'not a participant'; end if;
  if exists (select 1 from public.blocks_reports r where r.is_block and
       ((r.reporter_id = c.student_id and r.reported_user_id = c.owner_id) or (r.reporter_id = c.owner_id and r.reported_user_id = c.student_id))) then
    raise exception 'blocked';
  end if;
  return new;
end $$;
create trigger trg_check_message before insert on public.messages for each row execute function public.check_message();

-- ===================== RPC functions (all return jsonb so the client can always parse) =====================
create or replace function public.bump_view(bh uuid) returns jsonb language sql security definer set search_path = public as $$
  update public.bh_activity set views = views + 1, updated_at = now() where boarding_house_id = bh; select '{}'::jsonb;
$$;

create or replace function public.get_or_create_conversation(bh uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare b public.boarding_houses; cid uuid;
begin
  select * into b from public.boarding_houses where id = bh and status = 'active';
  if b.id is null then raise exception 'listing unavailable'; end if;
  if not public.is_verified_owner(b.owner_id) then raise exception 'owner not verified'; end if;
  if not exists (select 1 from public.users where id = auth.uid() and role = 'student') then raise exception 'students only'; end if;
  insert into public.conversations (student_id, owner_id, boarding_house_id) values (auth.uid(), b.owner_id, bh)
    on conflict (student_id, owner_id, boarding_house_id) do update set last_message_at = public.conversations.last_message_at
    returning id into cid;
  return jsonb_build_object('id', cid);
end $$;

create or replace function public.mark_read(conv uuid) returns jsonb language plpgsql security definer set search_path = public as $$
begin
  update public.messages set read_at = now()
   where conversation_id = conv and sender_id <> auth.uid() and read_at is null
     and exists (select 1 from public.conversations c where c.id = conv and auth.uid() in (c.student_id, c.owner_id));
  return '{}'::jsonb;
end $$;

create or replace function public.switch_role(new_role text) returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if new_role not in ('student','owner') then raise exception 'invalid role'; end if;
  update public.users set role = new_role where id = auth.uid() and role in ('student','owner');
  return '{}'::jsonb;
end $$;

create or replace function public.review_verification(vid uuid, approve boolean, reason text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v public.owner_verifications;
begin
  if not public.is_admin() then raise exception 'admins only'; end if;
  select * into v from public.owner_verifications where id = vid and status = 'pending';
  if v.id is null then raise exception 'not pending'; end if;
  if not approve and coalesce(trim(reason), '') = '' then raise exception 'reason required'; end if;
  update public.owner_verifications set status = case when approve then 'verified' else 'rejected' end,
         reviewed_by = auth.uid(), reviewed_at = now(), rejection_reason = case when approve then null else reason end where id = vid;
  update public.users set is_verified = approve, verification_status = case when approve then 'verified' else 'rejected' end where id = v.owner_id;
  insert into public.audit_logs (actor_id, action, target_type, target_id, details)
    values (auth.uid(), case when approve then 'verification_approved' else 'verification_rejected' end, 'owner_verification', vid::text,
            jsonb_build_object('owner_id', v.owner_id, 'reason', reason));
  return '{}'::jsonb;
end $$;

create or replace function public.admin_takedown(bh uuid, reason text) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'admins only'; end if;
  update public.boarding_houses set status = 'taken_down' where id = bh;
  insert into public.audit_logs (actor_id, action, target_type, target_id, details) values (auth.uid(), 'listing_takedown', 'boarding_house', bh::text, jsonb_build_object('reason', reason));
  return '{}'::jsonb;
end $$;

-- Account deletion (Google Play requirement): removes the user row (cascades) and the auth user.
create or replace function public.delete_my_account() returns jsonb language plpgsql security definer set search_path = public, auth as $$
begin
  delete from storage.objects where bucket_id in ('id-verification','bh-photos') and (storage.foldername(name))[1] = auth.uid()::text;
  delete from auth.users where id = auth.uid();
  return '{}'::jsonb;
end $$;

revoke all on function public.review_verification, public.admin_takedown from public, anon;
grant execute on function public.bump_view, public.get_or_create_conversation, public.mark_read, public.switch_role,
  public.review_verification, public.admin_takedown, public.delete_my_account to authenticated;

-- ===================== ROW LEVEL SECURITY =====================
alter table public.colleges enable row level security;
alter table public.users enable row level security;
alter table public.boarding_houses enable row level security;
alter table public.rooms enable row level security;
alter table public.bh_contacts enable row level security;
alter table public.photos enable row level security;
alter table public.favorites enable row level security;
alter table public.reviews enable row level security;
alter table public.bh_activity enable row level security;
alter table public.conversations enable row level security;
alter table public.messages enable row level security;
alter table public.blocks_reports enable row level security;
alter table public.user_agreements enable row level security;
alter table public.owner_verifications enable row level security;
alter table public.audit_logs enable row level security;
alter table public.ai_usage enable row level security;      -- no policies: service role only

create policy colleges_read on public.colleges for select to authenticated using (true);
create policy colleges_admin on public.colleges for all to authenticated using (public.is_admin()) with check (public.is_admin());

create policy users_self_read on public.users for select to authenticated using (id = auth.uid() or public.is_admin());
create policy users_self_insert on public.users for insert to authenticated
  with check (id = auth.uid() and role in ('student','owner') and is_verified = false and verification_status = 'unverified');
create policy users_self_update on public.users for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

create policy bh_read on public.boarding_houses for select to authenticated
  using (status = 'active' or owner_id = auth.uid() or public.is_admin());
create policy bh_insert on public.boarding_houses for insert to authenticated
  with check (owner_id = auth.uid() and public.is_verified_owner(auth.uid()));
create policy bh_update on public.boarding_houses for update to authenticated
  using (owner_id = auth.uid() and public.is_verified_owner(auth.uid())) with check (owner_id = auth.uid());
create policy bh_delete on public.boarding_houses for delete to authenticated using (owner_id = auth.uid() or public.is_admin());

create policy rooms_read on public.rooms for select to authenticated
  using (exists (select 1 from public.boarding_houses b where b.id = boarding_house_id and (b.status = 'active' or b.owner_id = auth.uid() or public.is_admin())));
create policy rooms_write on public.rooms for all to authenticated
  using (exists (select 1 from public.boarding_houses b where b.id = boarding_house_id and b.owner_id = auth.uid()))
  with check (exists (select 1 from public.boarding_houses b where b.id = boarding_house_id and b.owner_id = auth.uid()));

create policy contacts_read on public.bh_contacts for select to authenticated
  using (exists (select 1 from public.boarding_houses b where b.id = boarding_house_id and (b.status = 'active' or b.owner_id = auth.uid() or public.is_admin())));
create policy contacts_write on public.bh_contacts for all to authenticated
  using (exists (select 1 from public.boarding_houses b where b.id = boarding_house_id and b.owner_id = auth.uid()))
  with check (exists (select 1 from public.boarding_houses b where b.id = boarding_house_id and b.owner_id = auth.uid()));

create policy photos_read on public.photos for select to authenticated
  using (exists (select 1 from public.boarding_houses b where b.id = boarding_house_id and (b.status = 'active' or b.owner_id = auth.uid() or public.is_admin())));
create policy photos_write on public.photos for all to authenticated
  using (exists (select 1 from public.boarding_houses b where b.id = boarding_house_id and b.owner_id = auth.uid()))
  with check (exists (select 1 from public.boarding_houses b where b.id = boarding_house_id and b.owner_id = auth.uid()));

create policy fav_own on public.favorites for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy reviews_read on public.reviews for select to authenticated using (true);
create policy reviews_insert on public.reviews for insert to authenticated
  with check (student_id = auth.uid() and exists (select 1 from public.users u where u.id = auth.uid() and u.role = 'student'));
create policy reviews_update on public.reviews for update to authenticated
  using (student_id = auth.uid() or exists (select 1 from public.boarding_houses b where b.id = boarding_house_id and b.owner_id = auth.uid()));
create policy reviews_delete on public.reviews for delete to authenticated using (student_id = auth.uid() or public.is_admin());

create policy activity_read on public.bh_activity for select to authenticated using (true);

create policy conv_read on public.conversations for select to authenticated using (auth.uid() in (student_id, owner_id));
-- no insert/update policy: conversations are created only through get_or_create_conversation()

create policy msg_read on public.messages for select to authenticated
  using (exists (select 1 from public.conversations c where c.id = conversation_id and auth.uid() in (c.student_id, c.owner_id)));
create policy msg_insert on public.messages for insert to authenticated
  with check (sender_id = auth.uid() and exists (select 1 from public.conversations c where c.id = conversation_id and auth.uid() in (c.student_id, c.owner_id)));
-- read_at is changed only by mark_read()

create policy br_insert on public.blocks_reports for insert to authenticated with check (reporter_id = auth.uid());
create policy br_read on public.blocks_reports for select to authenticated using (reporter_id = auth.uid() or public.is_admin());

create policy ua_insert on public.user_agreements for insert to authenticated with check (user_id = auth.uid());
create policy ua_read on public.user_agreements for select to authenticated using (user_id = auth.uid() or public.is_admin());

create policy ov_insert on public.owner_verifications for insert to authenticated
  with check (owner_id = auth.uid() and status = 'pending' and exists (select 1 from public.users u where u.id = auth.uid() and u.role = 'owner'));
create policy ov_read on public.owner_verifications for select to authenticated using (owner_id = auth.uid() or public.is_admin());
-- admin decisions go through review_verification() only

create policy audit_admin_read on public.audit_logs for select to authenticated using (public.is_admin());

-- ===================== realtime =====================
alter publication supabase_realtime add table public.messages;

-- ===================== STORAGE =====================
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('id-verification', 'id-verification', false, 5242880, array['image/png','image/jpeg']),
       ('bh-photos',       'bh-photos',       true,  5242880, array['image/png','image/jpeg'])
on conflict (id) do nothing;

-- Private ID/selfie bucket: owner writes into <uid>/..., only the owner and admins can read. Nobody can overwrite/delete.
create policy idv_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'id-verification' and (storage.foldername(name))[1] = auth.uid()::text);
create policy idv_read on storage.objects for select to authenticated
  using (bucket_id = 'id-verification' and ((storage.foldername(name))[1] = auth.uid()::text or public.is_admin()));

-- Listing photos: public read, only the owner folder is writable.
create policy bhp_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'bh-photos' and (storage.foldername(name))[1] = auth.uid()::text);
create policy bhp_update on storage.objects for update to authenticated
  using (bucket_id = 'bh-photos' and (storage.foldername(name))[1] = auth.uid()::text);
create policy bhp_delete on storage.objects for delete to authenticated
  using (bucket_id = 'bh-photos' and (storage.foldername(name))[1] = auth.uid()::text);

-- ===================== retention (run daily with pg_cron) =====================
-- Delete ID/selfie files 30 days after a decision (edit to match your Privacy Policy):
-- select cron.schedule('purge-id-images', '0 3 * * *', $$
--   delete from storage.objects where bucket_id='id-verification' and name in (
--     select unnest(array[id_front_url,id_back_url,selfie_url]) from public.owner_verifications
--      where reviewed_at < now() - interval '30 days');
-- $$);
-- Halve Hot counters daily so "Hot" reflects recent days:
-- select cron.schedule('decay-hot', '0 4 * * *', $$ update public.bh_activity set views=views/2, favorites_count=favorites_count/2, chats_count=chats_count/2; $$);

-- ===================== first admin (run manually, once) =====================
-- update public.users set role='admin' where id = '<your-auth-user-uuid>';   -- trigger allows this from the SQL editor (runs as postgres)
-- insert into public.colleges (name, lat, lng) values ('Your College', 0.0, 0.0);
