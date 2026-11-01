-- 015: security hardening (v2.0.118). Run AFTER 014. Safe to run twice. Nothing the app does today changes.
--  1) Helper functions (is_admin, is_verified_owner, is_owner_or_admin) are no longer callable by signed-out visitors (anon).
--  2) bump_view() counts one view per person per place per hour, so nobody can inflate "Hot" by calling it in a loop.
-- See docs/SECURITY.md for the full audit and the things only you can do (dashboard settings).

-- ===================== 1) helper functions: signed-in users and the service role only =====================
-- Postgres gives every new function EXECUTE to PUBLIC. Policies run as the signed-in user ("authenticated"), so that role keeps access.
do $$
declare f text;
begin
  foreach f in array array['public.is_admin()', 'public.is_verified_owner(uuid)', 'public.is_owner_or_admin(uuid)'] loop
    if to_regprocedure(f) is not null then
      execute format('revoke all on function %s from public, anon', f);
      execute format('grant execute on function %s to authenticated, service_role', f);
    end if;
  end loop;
end $$;

-- The app's own RPCs (all check auth.uid() inside) are for signed-in users only, not anon and not other database roles.
do $$
declare f text;
begin
  foreach f in array array['public.get_or_create_conversation(uuid)', 'public.mark_read(uuid)', 'public.switch_role(text)', 'public.delete_my_account()'] loop
    if to_regprocedure(f) is not null then
      execute format('revoke all on function %s from public, anon', f);
      execute format('grant execute on function %s to authenticated, service_role', f);
    end if;
  end loop;
end $$;

-- ===================== 2) view counter throttle =====================
create table if not exists public.bh_view_seen (
  user_id           uuid not null references public.users(id) on delete cascade,   -- deleting an account removes its rows
  boarding_house_id uuid not null references public.boarding_houses(id) on delete cascade,
  last_at           timestamptz not null default now(),
  primary key (user_id, boarding_house_id)
);
alter table public.bh_view_seen enable row level security;          -- no policies: only bump_view() (definer) touches it
revoke all on public.bh_view_seen from anon, authenticated;

create or replace function public.bump_view(bh uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare uid uuid := auth.uid(); n int;
begin
  if uid is null then return '{}'::jsonb; end if;
  if not exists (select 1 from public.users where id = uid) then return '{}'::jsonb; end if;
  if not exists (select 1 from public.boarding_houses where id = bh and status = 'active') then return '{}'::jsonb; end if;
  insert into public.bh_view_seen (user_id, boarding_house_id) values (uid, bh)
    on conflict (user_id, boarding_house_id) do update set last_at = now()
      where public.bh_view_seen.last_at < now() - interval '1 hour';
  get diagnostics n = row_count;                                     -- 1 = new or older than an hour, 0 = counted recently
  if n > 0 then
    update public.bh_activity set views = views + 1, updated_at = now() where boarding_house_id = bh;
  end if;
  return '{}'::jsonb;
end $$;
revoke all on function public.bump_view(uuid) from public, anon;
grant execute on function public.bump_view(uuid) to authenticated;
notify pgrst, 'reload schema';

-- ===================== before you publish the app (run by hand, once) =====================
-- The three dev accounts below are test logins. Delete them (their data goes with them) or give them strong passwords in
-- Supabase > Authentication > Users. The Dev Admin is a full admin account on the live project.
--   delete from auth.users where email in ('bofo.dev.admin@example.com', 'bofo.dev.student@example.com', 'bofo.dev.owner@example.com');
-- Keep the demo listings by moving them to a real owner first, or delete them: delete from public.boarding_houses where name like 'Demo BH %';
