-- Owners may list and edit boarding houses without being verified; the app shows a "not verified" label instead.
-- Banned / suspended users are still blocked by the restrictive susp_block_bh_* policies.
-- APPLIED LIVE 2026-11-02 (project BOFO) with the user's OK.
create or replace function public.can_list_bh(uid uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.users where id = uid and role in ('owner','admin'));
$$;
revoke all on function public.can_list_bh(uuid) from public, anon;
grant execute on function public.can_list_bh(uuid) to authenticated;

drop policy if exists bh_insert on public.boarding_houses;
create policy bh_insert on public.boarding_houses for insert to authenticated
  with check (owner_id = auth.uid() and public.can_list_bh(auth.uid()));

drop policy if exists bh_update on public.boarding_houses;
create policy bh_update on public.boarding_houses for update to authenticated
  using (owner_id = auth.uid() and public.can_list_bh(auth.uid()))
  with check (owner_id = auth.uid());

notify pgrst, 'reload schema';

-- Also applied live as "013b_students_can_chat_unverified" (the chat part of 013 was missing live): students can open a chat with an unverified owner.
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
