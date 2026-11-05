-- 038: Stage 1 emergency fix (BOFO Admin rebuild). Safe to run twice. Nothing a normal user does today changes.
-- FIX S-02 (High): an owner could undo an admin take-down of their own listing, because bh_update only checks owner_id.
--   An owner could run:  update boarding_houses set status = 'active' where id = '<taken down listing>'   and the listing was live again.
--   Now: a signed-in user (role 'authenticated' / 'anon') can never leave 'taken_down', can never set 'taken_down',
--   can only switch between 'active' and 'unlisted', and can never change owner_id.
--   SECURITY DEFINER functions (admin_takedown, admin_review_report, ban, ...) run as the function owner, so they are not affected.
--   Same pattern as protect_users() in 001 / 022 / 023.
-- Old app keeps working: it only ever sets 'active' or 'unlisted' (BhService.setStatus).
-- ROLLBACK:  drop trigger if exists trg_protect_bh on public.boarding_houses;  drop function if exists public.protect_bh();

create or replace function public.protect_bh() returns trigger language plpgsql as $$
begin
  if current_user in ('authenticated', 'anon') then
    new.owner_id := old.owner_id;
    if old.status = 'taken_down' then
      new.status := old.status;                                   -- only an admin function can change a take-down
    elsif new.status is distinct from old.status and new.status not in ('active', 'unlisted') then
      new.status := old.status;                                   -- a client can never set 'taken_down' itself
    end if;
  end if;
  return new;
end $$;

drop trigger if exists trg_protect_bh on public.boarding_houses;
create trigger trg_protect_bh before update on public.boarding_houses
  for each row execute function public.protect_bh();

notify pgrst, 'reload schema';

-- ===================== TEST (run in the SQL editor; replace the two uuids with a TEST owner and one of their listings) =====================
-- begin;
--   update public.boarding_houses set status = 'taken_down' where id = '<listing-uuid>';           -- runs as postgres (like an admin function): allowed
--   set local role authenticated;
--   select set_config('request.jwt.claims', '{"sub":"<owner-uuid>","role":"authenticated"}', true);
--   update public.boarding_houses set status = 'active' where id = '<listing-uuid>';                -- the owner tries to undo it
--   reset role;
--   select status from public.boarding_houses where id = '<listing-uuid>';                          -- EXPECT: taken_down
-- rollback;
