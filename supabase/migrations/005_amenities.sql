-- BOFO Finder amenities (v1.6.0). Run AFTER 004_delete_account_fix.sql.
-- A shared catalog of amenities. Verified owners can add NEW ones; students find them in the Explore filter.

-- 1) catalog + which listing has what
create table if not exists public.amenities (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) between 2 and 40),
  icon text not null default '✨' check (char_length(icon) between 1 and 8),
  is_popular boolean not null default false,          -- curated "popular" starters; real popularity = how many listings use it
  created_by uuid references public.users(id) on delete set null,
  created_at timestamptz not null default now()
);
create unique index if not exists amenities_name_uq on public.amenities (lower(name));

create table if not exists public.bh_amenities (
  boarding_house_id uuid not null references public.boarding_houses(id) on delete cascade,
  amenity_id uuid not null references public.amenities(id) on delete cascade,
  primary key (boarding_house_id, amenity_id)
);
create index if not exists bh_amenities_amenity_idx on public.bh_amenities (amenity_id);

alter table public.amenities enable row level security;
alter table public.bh_amenities enable row level security;

drop policy if exists amenities_read on public.amenities;
create policy amenities_read on public.amenities for select to authenticated using (true);
drop policy if exists amenities_admin on public.amenities;
create policy amenities_admin on public.amenities for all to authenticated using (public.is_admin()) with check (public.is_admin());
-- owners never insert directly: they go through get_or_create_amenity() below (dedupes + limits)

drop policy if exists bha_read on public.bh_amenities;
create policy bha_read on public.bh_amenities for select to authenticated
  using (exists (select 1 from public.boarding_houses b where b.id = boarding_house_id and (b.status = 'active' or b.owner_id = (select auth.uid()) or (select public.is_admin()))));
drop policy if exists bha_write on public.bh_amenities;
create policy bha_write on public.bh_amenities for all to authenticated
  using (exists (select 1 from public.boarding_houses b where b.id = boarding_house_id and b.owner_id = (select auth.uid())))
  with check (exists (select 1 from public.boarding_houses b where b.id = boarding_house_id and b.owner_id = (select auth.uid())));

-- 2) catalog with usage counts (the app sorts: most used first, curated popular as tie-break)
create or replace view public.amenities_v as
  select a.id, a.name, a.icon, a.is_popular,
         (select count(*) from public.bh_amenities x join public.boarding_houses b on b.id = x.boarding_house_id
           where x.amenity_id = a.id and b.status = 'active')::int as uses
  from public.amenities a;
grant select on public.amenities_v to authenticated;

-- 3) owners add their own amenity: same name (any case) returns the existing one; max 30 own amenities per owner
create or replace function public.get_or_create_amenity(p_name text, p_icon text default null)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  nm text := left(btrim(regexp_replace(coalesce(p_name, ''), '\s+', ' ', 'g')), 40);
  ic text := coalesce(nullif(btrim(p_icon), ''), '✨');
  row_ public.amenities;
begin
  if char_length(nm) < 2 then raise exception 'Amenity name is too short'; end if;
  select * into row_ from public.amenities where lower(name) = lower(nm);
  if not found then
    if not (public.is_verified_owner(auth.uid()) or public.is_admin()) then raise exception 'Only verified owners can add amenities'; end if;
    if (select count(*) from public.amenities where created_by = auth.uid()) >= 30 then raise exception 'You reached the limit of custom amenities'; end if;
    insert into public.amenities (name, icon, created_by) values (nm, left(ic, 8), auth.uid())
      on conflict (lower(name)) do nothing
      returning * into row_;
    if row_.id is null then select * into row_ from public.amenities where lower(name) = lower(nm); end if;
  end if;
  return jsonb_build_object('id', row_.id, 'name', row_.name, 'icon', row_.icon);
end $$;
revoke all on function public.get_or_create_amenity(text, text) from public, anon;
grant execute on function public.get_or_create_amenity(text, text) to authenticated;

-- 4) starter catalog (the first three match the old free_water / free_electricity / free_wifi columns)
insert into public.amenities (name, icon, is_popular) values
  ('Free water',       '💧', true),
  ('Free electricity', '⚡', true),
  ('Free Wi-Fi',       '📶', true),
  ('Air conditioning', '❄️', true),
  ('Own bathroom',     '🚿', true),
  ('Kitchen access',   '🍳', true),
  ('Laundry area',     '🧺', true),
  ('Parking',          '🅿️', true),
  ('CCTV',             '📹', true),
  ('Study area',       '📚', true),
  ('Hot shower',       '🔥', false),
  ('Electric fan',     '🌀', false),
  ('Bed with mattress','🛏️', false),
  ('Cabinet / closet', '🚪', false),
  ('Lockable room',    '🔒', false),
  ('Visitors allowed', '👥', false),
  ('No curfew',        '🌙', false),
  ('Pet friendly',     '🐾', false),
  ('Female only',      '👩', false),
  ('Male only',        '👨', false)
on conflict (lower(name)) do nothing;

-- 5) existing listings keep their water / electricity / Wi-Fi as amenities
insert into public.bh_amenities (boarding_house_id, amenity_id)
  select b.id, a.id from public.boarding_houses b join public.amenities a on lower(a.name) = 'free water' where b.free_water
  on conflict do nothing;
insert into public.bh_amenities (boarding_house_id, amenity_id)
  select b.id, a.id from public.boarding_houses b join public.amenities a on lower(a.name) = 'free electricity' where b.free_electricity
  on conflict do nothing;
insert into public.bh_amenities (boarding_house_id, amenity_id)
  select b.id, a.id from public.boarding_houses b join public.amenities a on lower(a.name) = 'free wi-fi' where b.free_wifi
  on conflict do nothing;

-- 6) card view now carries the amenities (amenity_ids lets the app filter with amenity_ids=cs.{id1,id2} = has ALL of them)
drop view if exists public.bh_cards;
create view public.bh_cards as
  select b.id, b.owner_id, b.name, b.address, b.lat, b.lng, b.price, b.price_mode,
         b.free_water, b.free_electricity, b.free_wifi, b.status, b.created_at,
         coalesce(a.available_beds, 0) as available_beds,
         coalesce(a.avg_rating, 0)     as avg_rating,
         coalesce(a.review_count, 0)   as review_count,
         (select p.url from public.photos p where p.boarding_house_id = b.id order by p.sort_order limit 1) as photo_url,
         (u.role = 'owner' and u.is_verified) as owner_verified,
         coalesce(a.views + 3 * a.favorites_count + 5 * a.chats_count, 0) as hot_score,
         (u.role = 'owner' and u.is_verified and b.status = 'active' and bo.ends_at is not null) as is_featured,
         bo.ends_at as featured_until,
         coalesce((select array_agg(x.amenity_id) from public.bh_amenities x where x.boarding_house_id = b.id), '{}'::uuid[]) as amenity_ids,
         coalesce((select array_agg(m.name order by m.is_popular desc, m.name)
                     from public.bh_amenities x join public.amenities m on m.id = x.amenity_id
                    where x.boarding_house_id = b.id), '{}'::text[]) as amenity_names
  from public.boarding_houses b
  join public.users u on u.id = b.owner_id
  left join public.bh_activity a on a.boarding_house_id = b.id
  left join lateral (select max(x.ends_at) as ends_at from public.boosts x
                      where x.boarding_house_id = b.id and x.starts_at <= now() and x.ends_at > now()) bo on true
  where b.status = 'active' or b.owner_id = (select auth.uid()) or (select public.is_admin());
grant select on public.bh_cards to authenticated;
