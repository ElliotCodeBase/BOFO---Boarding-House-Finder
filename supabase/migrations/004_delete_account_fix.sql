-- 004: make "Delete account" work.
-- 1) recompute_bh() re-created a bh_activity row for a listing that was being deleted (cascade from rooms/reviews),
--    which broke deleting any owner account or listing (FK violation). Only insert while the listing still exists.
create or replace function public.recompute_bh(bh uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not exists (select 1 from public.boarding_houses where id = bh) then return; end if;
  insert into public.bh_activity (boarding_house_id) values (bh) on conflict do nothing;
  update public.bh_activity a set
    avg_rating     = coalesce((select round(avg(rating)::numeric, 1) from public.reviews where boarding_house_id = bh), 0),
    review_count   = (select count(*) from public.reviews where boarding_house_id = bh),
    available_beds = coalesce((select sum(beds - occupants) from public.rooms where boarding_house_id = bh), 0),
    updated_at     = now()
  where a.boarding_house_id = bh;
end $$;

-- 2) Supabase forbids DELETE on storage.objects from SQL ("Use the Storage API instead"), so the old function always failed.
--    Files are now removed by the `delete-account` Edge Function (Storage API + auth admin). This RPC only removes data rows.
create or replace function public.delete_my_account() returns jsonb language plpgsql security definer set search_path = public, auth as $$
begin
  delete from auth.users where id = auth.uid();
  return '{}'::jsonb;
end $$;

-- 3) Deleting an admin who reviewed an owner must not be blocked.
alter table public.owner_verifications drop constraint if exists owner_verifications_reviewed_by_fkey;
alter table public.owner_verifications add constraint owner_verifications_reviewed_by_fkey
  foreign key (reviewed_by) references public.users(id) on delete set null;
