-- BOFO Finder gender policy (v2.0.87). Run AFTER 011_rules.sql.
-- Owners say who may stay in the boarding house: 'female' (girls only), 'male' (boys only) or 'any' (boys and girls).
-- Students see it on the card and the place page, and can filter by it. Existing RLS on boarding_houses already lets the owner write it.
alter table public.boarding_houses add column if not exists gender_policy text not null default 'any';
alter table public.boarding_houses drop constraint if exists boarding_houses_gender_policy_chk;
alter table public.boarding_houses add constraint boarding_houses_gender_policy_chk check (gender_policy in ('any','female','male'));

-- Places that already ticked the "Female only" / "Male only" amenity keep that meaning.
update public.boarding_houses b set gender_policy = 'female'
 where b.gender_policy = 'any' and exists (select 1 from public.bh_amenities x join public.amenities m on m.id = x.amenity_id
                                            where x.boarding_house_id = b.id and lower(m.name) = 'female only');
update public.boarding_houses b set gender_policy = 'male'
 where b.gender_policy = 'any' and exists (select 1 from public.bh_amenities x join public.amenities m on m.id = x.amenity_id
                                            where x.boarding_house_id = b.id and lower(m.name) = 'male only');
-- The two amenities are replaced by the setting above (their links go with them, the meaning was copied just now).
delete from public.amenities where lower(name) in ('female only', 'male only');

-- Card view = the 005 view + gender_policy (same columns, same order, one extra at the end).
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
                    where x.boarding_house_id = b.id), '{}'::text[]) as amenity_names,
         b.gender_policy
  from public.boarding_houses b
  join public.users u on u.id = b.owner_id
  left join public.bh_activity a on a.boarding_house_id = b.id
  left join lateral (select max(x.ends_at) as ends_at from public.boosts x
                      where x.boarding_house_id = b.id and x.starts_at <= now() and x.ends_at > now()) bo on true
  where b.status = 'active' or b.owner_id = (select auth.uid()) or (select public.is_admin());
grant select on public.bh_cards to authenticated;
notify pgrst, 'reload schema';
