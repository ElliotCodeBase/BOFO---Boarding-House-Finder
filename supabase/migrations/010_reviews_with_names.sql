-- 010: show WHO rated a boarding house.
-- reviews_v = every review plus the reviewer's name and photo. Like the other views it runs with the owner's rights,
-- so it exposes only these safe columns (no email, phone or role) while public.users stays private.
create or replace view public.reviews_v as
  select r.id, r.boarding_house_id, r.student_id, r.rating, r.comment, r.owner_reply, r.created_at,
         nullif(u.full_name, '') as reviewer_name, u.photo_url as reviewer_photo
  from public.reviews r
  join public.users u on u.id = r.student_id;

grant select on public.reviews_v to authenticated;
