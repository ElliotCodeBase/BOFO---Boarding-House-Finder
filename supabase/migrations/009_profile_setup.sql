-- 009: profile setup (first login + Settings > Personal information).
--  * users.username (unique, case-insensitive, 3-20 letters/digits/_/.), users.region (key of util/Regions.java; the map opens there).
--  * profiles_v now also returns username, sex, age, region (age/sex were saved before but never read back).
--  * username_available(u): lets the app say "taken" before saving.
--  * Public "avatars" bucket: each user writes only into their own <uid>/ folder (profile picture).
-- Run once in Supabase > SQL Editor. Safe to run twice.

alter table public.users add column if not exists username text;
alter table public.users add column if not exists region text;

do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'users_username_fmt') then
    alter table public.users add constraint users_username_fmt check (username is null or username ~ '^[A-Za-z0-9_.]{3,20}$');
  end if;
  if not exists (select 1 from pg_constraint where conname = 'users_region_len') then
    alter table public.users add constraint users_region_len check (region is null or char_length(region) <= 12);
  end if;
end $$;

create unique index if not exists users_username_key on public.users (lower(username)) where username is not null;

create or replace view public.profiles_v as
  select id, role, is_verified, verification_status, full_name, photo_url, phone, default_college_id,
         username, sex, age, region
  from public.users where id = auth.uid();
grant select on public.profiles_v to authenticated;

create or replace function public.username_available(u text) returns table(available boolean)
language sql stable security definer set search_path = public as $$
  select not exists (select 1 from public.users where lower(username) = lower(u) and id <> auth.uid());
$$;
revoke all on function public.username_available(text) from public, anon;
grant execute on function public.username_available(text) to authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 2097152, array['image/png','image/jpeg'])
on conflict (id) do nothing;

drop policy if exists avatars_insert on storage.objects;
drop policy if exists avatars_update on storage.objects;
drop policy if exists avatars_select on storage.objects;
create policy avatars_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
create policy avatars_update on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
create policy avatars_select on storage.objects for select to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
