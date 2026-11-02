-- 021: show the owner's NAME on a boarding house page (v2.1.7, safety series step 1 of 3).
-- public.users stays private. This view exposes only id, name and photo of the owner of a place the caller may see.
-- Like reviews_v it runs with the view owner's rights, so the WHERE clause is the access check.
create or replace view public.bh_owner_v as
  select b.id as boarding_house_id, b.owner_id,
         nullif(u.full_name, '') as owner_name, u.photo_url as owner_photo
  from public.boarding_houses b
  join public.users u on u.id = b.owner_id
  where b.status = 'active' or b.owner_id = auth.uid() or public.is_admin();

grant select on public.bh_owner_v to authenticated;
notify pgrst, 'reload schema';
