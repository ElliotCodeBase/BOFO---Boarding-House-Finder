-- 008: delete a message / delete a chat card.
--  * Delete a message "for me" (hidden only for the person who deleted it) or "for everyone" (sender only, row removed).
--  * Delete a chat "for me": the card leaves my inbox and the old messages are hidden for me. The other person keeps their copy.
--    If a new message arrives later, the chat comes back with only the new messages (like WhatsApp).
-- Run once in Supabase > SQL Editor. Safe to run twice.

alter table public.messages      add column if not exists hidden_for uuid[] not null default '{}';
alter table public.conversations add column if not exists student_cleared_at timestamptz;
alter table public.conversations add column if not exists owner_cleared_at   timestamptz;

-- Messages: a participant sees a message unless they hid it or cleared the chat after it was written.
drop policy if exists msg_read on public.messages;
create policy msg_read on public.messages for select to authenticated
  using (exists (
    select 1 from public.conversations c
     where c.id = messages.conversation_id
       and (select auth.uid()) in (c.student_id, c.owner_id)
       and not ((select auth.uid()) = any (messages.hidden_for))
       and messages.created_at > coalesce(case when (select auth.uid()) = c.student_id then c.student_cleared_at else c.owner_cleared_at end, '-infinity'::timestamptz)
  ));

-- Inbox: same columns as before. last_message / unread ignore hidden and cleared messages; a cleared chat with nothing new disappears.
create or replace view public.inbox_v as
  select c.id, c.student_id, c.owner_id, c.boarding_house_id, c.last_message_at,
         b.name as bh_name,
         case when c.student_id = auth.uid() then ow.full_name else st.full_name end as other_name,
         (select m.body from public.messages m
           where m.conversation_id = c.id and not (auth.uid() = any (m.hidden_for)) and m.created_at > k.cleared
           order by m.created_at desc limit 1) as last_message,
         (select count(*) from public.messages m
           where m.conversation_id = c.id and m.sender_id <> auth.uid() and m.read_at is null
             and not (auth.uid() = any (m.hidden_for)) and m.created_at > k.cleared)::int as unread
  from public.conversations c
  cross join lateral (select coalesce(case when c.student_id = auth.uid() then c.student_cleared_at else c.owner_cleared_at end, '-infinity'::timestamptz) as cleared) k
  join public.boarding_houses b on b.id = c.boarding_house_id
  join public.users st on st.id = c.student_id
  join public.users ow on ow.id = c.owner_id
  where auth.uid() in (c.student_id, c.owner_id)
    and (k.cleared = '-infinity'::timestamptz
         or exists (select 1 from public.messages m
                     where m.conversation_id = c.id and not (auth.uid() = any (m.hidden_for)) and m.created_at > k.cleared));

grant select on public.inbox_v to authenticated;

create or replace function public.delete_message(msg uuid, everyone boolean default false) returns jsonb
language plpgsql security definer set search_path = public as $$
declare m public.messages; c public.conversations;
begin
  select * into m from public.messages where id = msg;
  if not found then return jsonb_build_object('deleted', false); end if;
  select * into c from public.conversations where id = m.conversation_id;
  if auth.uid() is null or auth.uid() not in (c.student_id, c.owner_id) then raise exception 'Not allowed'; end if;
  if everyone then
    if m.sender_id <> auth.uid() then raise exception 'You can only delete your own messages for everyone'; end if;
    delete from public.messages where id = msg;
  else
    update public.messages set hidden_for = array_append(hidden_for, auth.uid())
     where id = msg and not (auth.uid() = any (hidden_for));
  end if;
  return jsonb_build_object('deleted', true);
end $$;

create or replace function public.delete_conversation(conv uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'Not allowed'; end if;
  update public.conversations set student_cleared_at = now() where id = conv and student_id = auth.uid();
  update public.conversations set owner_cleared_at   = now() where id = conv and owner_id   = auth.uid();
  return jsonb_build_object('deleted', true);
end $$;

revoke all on function public.delete_message(uuid, boolean), public.delete_conversation(uuid) from public, anon;
grant execute on function public.delete_message(uuid, boolean), public.delete_conversation(uuid) to authenticated;
