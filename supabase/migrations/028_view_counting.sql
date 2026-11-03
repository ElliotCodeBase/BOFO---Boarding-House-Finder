-- 028: a view counts only when someone opens the place page, and never for the place's own owner.
-- (The app now calls bump_view from the place page only. This also protects the desktop app and anything else that calls it.)
-- Still one view per person per place per hour (bh_view_seen, migration 015).
create or replace function public.bump_view(bh uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare uid uuid := auth.uid(); n int;
begin
  if uid is null then return '{}'::jsonb; end if;
  if not exists (select 1 from public.users where id = uid) then return '{}'::jsonb; end if;
  if not exists (select 1 from public.boarding_houses where id = bh and status = 'active' and owner_id <> uid) then return '{}'::jsonb; end if;
  insert into public.bh_view_seen (user_id, boarding_house_id) values (uid, bh)
    on conflict (user_id, boarding_house_id) do update set last_at = now()
      where public.bh_view_seen.last_at < now() - interval '1 hour';
  get diagnostics n = row_count;
  if n > 0 then
    update public.bh_activity set views = views + 1, updated_at = now() where boarding_house_id = bh;
  end if;
  return '{}'::jsonb;
end $$;
revoke all on function public.bump_view(uuid) from public, anon;
grant execute on function public.bump_view(uuid) to authenticated;
notify pgrst, 'reload schema';
