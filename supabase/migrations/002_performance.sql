-- BOFO Finder performance migration. Run AFTER 001_schema.sql.

-- 1) Indexes for the hot queries
create index if not exists reviews_bh_idx        on public.reviews (boarding_house_id);
create index if not exists favorites_bh_idx      on public.favorites (boarding_house_id);
create index if not exists messages_unread_idx   on public.messages (conversation_id) where read_at is null;
create index if not exists bh_active_geo_idx     on public.boarding_houses (lat, lng) where status = 'active';
create index if not exists users_admin_idx       on public.users (id) where role = 'admin';

-- 2) Pre-computed aggregates (rating, review count, free beds) instead of per-row subqueries in bh_cards
alter table public.bh_activity
  add column if not exists avg_rating numeric(2,1) not null default 0,
  add column if not exists review_count int not null default 0,
  add column if not exists available_beds int not null default 0;

create or replace function public.recompute_bh(bh uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  insert into public.bh_activity (boarding_house_id) values (bh) on conflict do nothing;
  update public.bh_activity a set
    avg_rating     = coalesce((select round(avg(rating)::numeric, 1) from public.reviews where boarding_house_id = bh), 0),
    review_count   = (select count(*) from public.reviews where boarding_house_id = bh),
    available_beds = coalesce((select sum(beds - occupants) from public.rooms where boarding_house_id = bh), 0),
    updated_at     = now()
  where a.boarding_house_id = bh;
end $$;

create or replace function public.trg_recompute() returns trigger
language plpgsql security definer set search_path = public as $$
begin perform public.recompute_bh(coalesce(new.boarding_house_id, old.boarding_house_id)); return null; end $$;

drop trigger if exists trg_reviews_agg on public.reviews;
create trigger trg_reviews_agg after insert or update or delete on public.reviews for each row execute function public.trg_recompute();
drop trigger if exists trg_rooms_agg on public.rooms;
create trigger trg_rooms_agg after insert or update or delete on public.rooms for each row execute function public.trg_recompute();

-- backfill existing rows
select public.recompute_bh(id) from public.boarding_houses;

drop view if exists public.bh_cards;
create view public.bh_cards as
  select b.id, b.owner_id, b.name, b.address, b.lat, b.lng, b.price, b.price_mode,
         b.free_water, b.free_electricity, b.free_wifi, b.status, b.created_at,
         coalesce(a.available_beds, 0) as available_beds,
         coalesce(a.avg_rating, 0)     as avg_rating,
         coalesce(a.review_count, 0)   as review_count,
         (select p.url from public.photos p where p.boarding_house_id = b.id order by p.sort_order limit 1) as photo_url,
         (u.role = 'owner' and u.is_verified) as owner_verified,
         coalesce(a.views + 3 * a.favorites_count + 5 * a.chats_count, 0) as hot_score
  from public.boarding_houses b
  join public.users u on u.id = b.owner_id
  left join public.bh_activity a on a.boarding_house_id = b.id
  where b.status = 'active' or b.owner_id = (select auth.uid()) or (select public.is_admin());
grant select on public.bh_cards to authenticated;

-- 3) RLS: wrap auth.uid()/is_admin() in (select ...) so Postgres evaluates them once per query, not per row
drop policy if exists bh_read on public.boarding_houses;
create policy bh_read on public.boarding_houses for select to authenticated
  using (status = 'active' or owner_id = (select auth.uid()) or (select public.is_admin()));

drop policy if exists conv_read on public.conversations;
create policy conv_read on public.conversations for select to authenticated
  using ((select auth.uid()) in (student_id, owner_id));

drop policy if exists msg_read on public.messages;
create policy msg_read on public.messages for select to authenticated
  using (exists (select 1 from public.conversations c where c.id = conversation_id and (select auth.uid()) in (c.student_id, c.owner_id)));

drop policy if exists fav_own on public.favorites;
create policy fav_own on public.favorites for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists users_self_read on public.users;
create policy users_self_read on public.users for select to authenticated
  using (id = (select auth.uid()) or (select public.is_admin()));

analyze;
