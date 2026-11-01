-- BOFO Finder: owners can list before they are verified (v2.0.89). Run AFTER 012_gender_policy.sql.
-- Before: bh_insert / bh_update needed is_verified_owner(). Now any owner (or admin) can create and edit their own places.
-- Verification stays: the app shows a "not verified" icon on unverified places, and boosts / featured stay verified-only
-- (bh_cards.is_featured and the boost rules still check is_verified).
create or replace function public.is_owner_or_admin(uid uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.users where id = uid and role in ('owner', 'admin'));
$$;

drop policy if exists bh_insert on public.boarding_houses;
create policy bh_insert on public.boarding_houses for insert to authenticated
  with check (owner_id = (select auth.uid()) and public.is_owner_or_admin((select auth.uid())));
drop policy if exists bh_update on public.boarding_houses;
create policy bh_update on public.boarding_houses for update to authenticated
  using (owner_id = (select auth.uid()) and public.is_owner_or_admin((select auth.uid())))
  with check (owner_id = (select auth.uid()));

-- Students can chat with the owner of an unverified place too (the place page tells them it is not verified).
create or replace function public.get_or_create_conversation(bh uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare b public.boarding_houses; cid uuid;
begin
  select * into b from public.boarding_houses where id = bh and status = 'active';
  if b.id is null then raise exception 'listing unavailable'; end if;
  if not exists (select 1 from public.users where id = auth.uid() and role = 'student') then raise exception 'students only'; end if;
  insert into public.conversations (student_id, owner_id, boarding_house_id) values (auth.uid(), b.owner_id, bh)
    on conflict (student_id, owner_id, boarding_house_id) do update set last_message_at = public.conversations.last_message_at
    returning id into cid;
  return jsonb_build_object('id', cid);
end $$;
notify pgrst, 'reload schema';
