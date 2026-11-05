-- 036 (v2.9.4): an owner always sees ONE group per listing in the Groups list.
-- Cause of "I have two groups but only one shows": my_bh_groups() read only bh_members rows, so a house without its owner row was missing.
-- 1) backfill the missing owner rows + invite codes, 2) my_bh_groups() also lists every house the caller owns.
-- Groups stay tied to the listing: bh_members / bh_group_messages cascade from boarding_houses; there is NO delete-group function or policy,
-- so a group chat disappears only when its listing is deleted.
-- NOT applied live: needs the user's OK.
insert into public.bh_members (bh_id, user_id, role) select id, owner_id, 'owner' from public.boarding_houses on conflict do nothing;
update public.bh_members m set status = 'active' from public.boarding_houses b where b.id = m.bh_id and m.user_id = b.owner_id and m.role = 'owner' and m.status <> 'active';
insert into public.bh_invite_codes (bh_id, code) select id, public.gen_invite_code() from public.boarding_houses on conflict do nothing;

create or replace function public.my_bh_groups() returns table (
  bh_id uuid, bh_name text, my_role text, member_count int, unread int, pinned_count int,
  last_message text, last_message_at timestamptz, last_sender_name text, muted boolean)
language sql stable security definer set search_path = public as $$
  select b.id, b.name, mm.role,
         (select count(*) from public.bh_members x where x.bh_id = b.id and x.status = 'active')::int,
         (select count(*) from public.bh_group_messages g where g.bh_id = b.id and g.created_at > mm.last_read_at and g.sender_id <> auth.uid())::int,
         (select count(*) from public.bh_group_messages g where g.bh_id = b.id and g.pinned)::int,
         lm.body, lm.created_at,
         coalesce(nullif(lu.full_name, ''), case when lm.sender_id = b.owner_id then 'Owner' else 'Student' end),
         mm.muted
  from public.boarding_houses b
  left join public.bh_members m on m.bh_id = b.id and m.user_id = auth.uid() and m.status = 'active'
  cross join lateral (select case when b.owner_id = auth.uid() then 'owner' else m.role end as role,
                             coalesce(m.muted, false) as muted, coalesce(m.last_read_at, now()) as last_read_at) mm
  left join lateral (select g.body, g.created_at, g.sender_id from public.bh_group_messages g where g.bh_id = b.id order by g.created_at desc limit 1) lm on true
  left join public.users lu on lu.id = lm.sender_id
  where (b.owner_id = auth.uid() or m.user_id is not null) and b.status <> 'taken_down'
  order by coalesce(lm.created_at, now()) desc, b.name;
$$;
revoke all on function public.my_bh_groups from public, anon;
grant execute on function public.my_bh_groups to authenticated;
notify pgrst, 'reload schema';
