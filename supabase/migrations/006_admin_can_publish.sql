-- Run AFTER 005_amenities.sql.
-- Admins could not publish a listing: bh_insert needs is_verified_owner(), which was true only for role='owner' + verified.
-- Treat an admin like a verified owner. This one function also feeds listing updates, "chat with owner",
-- boosts and adding custom amenities, so those work for admin-owned listings too.
create or replace function public.is_verified_owner(uid uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.users
                  where id = uid and ((role = 'owner' and is_verified) or role = 'admin'));
$$;
