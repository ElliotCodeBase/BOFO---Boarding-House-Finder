-- 016: a locked-down database role for AI helpers (v2.0.118). Run AFTER 015. Safe to run twice.
--
-- WHY: the Supabase connector / dashboard login runs as the database OWNER (postgres): it ignores row-level security and can read
-- auth.users (emails), messages, ID scans and everything else. Rules in a prompt are not a lock. This role is the lock.
--
-- What bofo_ai_dev CAN do
--   * read the public catalog (colleges, amenities, listings, rooms, photos, activity) and reviews WITHOUT who wrote them
--   * create / edit / delete DEMO listings, but only listings owned by a test account (an @example.com login)
--   * add demo reviews, but only as a test account
-- What it can NEVER do (no grant, and RLS has no policy for it)
--   * read auth.users, public.users, conversations, messages, owner_verifications, user_agreements, favorites, blocks_reports,
--     audit_logs, payments, boosts, ai_chat_log, ai_usage, storage objects (ID scans / selfies)
--   * change a schema, a policy, a function, or any other role (it is not a superuser and owns nothing)
--
-- HOW TO USE (you, once, in the Supabase SQL Editor):
--   alter role bofo_ai_dev with login password '<long random password>';
--   then connect the AI with that role's connection string (Database > Connect > use user "bofo_ai_dev.<project-ref>").
--   Never give an AI the postgres password, the service_role key, or the owner-level connector token.

do $$ begin
  if not exists (select 1 from pg_roles where rolname = 'bofo_ai_dev') then
    create role bofo_ai_dev nologin noinherit nosuperuser nocreatedb nocreaterole noreplication nobypassrls connection limit 3;
  end if;
end $$;
alter role bofo_ai_dev set statement_timeout = '10s';
alter role bofo_ai_dev set default_transaction_read_only = off;

-- start from zero: take back anything granted before (safe if there is none)
revoke all on all tables    in schema public from bofo_ai_dev;
revoke all on all sequences in schema public from bofo_ai_dev;
revoke all on schema auth, storage, extensions, vault from bofo_ai_dev;
grant usage on schema public to bofo_ai_dev;

-- ===================== who counts as a test account =====================
-- Only the id comes out; no email, no name, no profile. Runs as the view owner, so the role never touches auth.users itself.
create or replace view public.test_users_v as
  select u.id, u.role
  from public.users u join auth.users a on a.id = u.id
  where a.email ilike '%@example.com';
revoke all on public.test_users_v from public, anon, authenticated;
grant select on public.test_users_v to bofo_ai_dev;

-- ===================== grants =====================
grant select on public.colleges, public.amenities, public.boost_plans, public.bh_activity to bofo_ai_dev;
grant select, insert, update, delete on public.boarding_houses, public.rooms, public.bh_contacts, public.photos, public.bh_amenities to bofo_ai_dev;
grant insert, update on public.bh_activity to bofo_ai_dev;
grant insert on public.amenities to bofo_ai_dev;
-- reviews: no student_id column on read (who wrote a review is user data), insert only for test accounts
grant select (id, boarding_house_id, rating, comment, owner_reply, created_at) on public.reviews to bofo_ai_dev;
grant insert (boarding_house_id, student_id, rating, comment, created_at), delete on public.reviews to bofo_ai_dev;
do $$ begin
  if to_regclass('public.bh_cards') is not null then execute 'grant select on public.bh_cards to bofo_ai_dev'; end if;
  if to_regclass('public.amenities_v') is not null then execute 'grant select on public.amenities_v to bofo_ai_dev'; end if;
end $$;

-- ===================== row level security: policies for this role only =====================
-- (existing policies are "to authenticated" and do not apply to this role; with RLS on and no policy, a role sees nothing)
drop policy if exists colleges_ai on public.colleges;
create policy colleges_ai on public.colleges for select to bofo_ai_dev using (true);
drop policy if exists amenities_ai_read on public.amenities;
create policy amenities_ai_read on public.amenities for select to bofo_ai_dev using (true);
drop policy if exists amenities_ai_write on public.amenities;
create policy amenities_ai_write on public.amenities for insert to bofo_ai_dev with check (created_by is null);
drop policy if exists boost_plans_ai on public.boost_plans;
create policy boost_plans_ai on public.boost_plans for select to bofo_ai_dev using (true);

drop policy if exists bh_ai_read on public.boarding_houses;
create policy bh_ai_read on public.boarding_houses for select to bofo_ai_dev using (true);
drop policy if exists bh_ai_write on public.boarding_houses;
create policy bh_ai_write on public.boarding_houses for all to bofo_ai_dev
  using (owner_id in (select id from public.test_users_v))
  with check (owner_id in (select id from public.test_users_v));

drop policy if exists rooms_ai on public.rooms;
create policy rooms_ai on public.rooms for all to bofo_ai_dev
  using (boarding_house_id in (select id from public.boarding_houses where owner_id in (select id from public.test_users_v)))
  with check (boarding_house_id in (select id from public.boarding_houses where owner_id in (select id from public.test_users_v)));
drop policy if exists contacts_ai on public.bh_contacts;
create policy contacts_ai on public.bh_contacts for all to bofo_ai_dev
  using (boarding_house_id in (select id from public.boarding_houses where owner_id in (select id from public.test_users_v)))
  with check (boarding_house_id in (select id from public.boarding_houses where owner_id in (select id from public.test_users_v)));
drop policy if exists photos_ai on public.photos;
create policy photos_ai on public.photos for all to bofo_ai_dev
  using (boarding_house_id in (select id from public.boarding_houses where owner_id in (select id from public.test_users_v)))
  with check (boarding_house_id in (select id from public.boarding_houses where owner_id in (select id from public.test_users_v)));
drop policy if exists bha_ai on public.bh_amenities;
create policy bha_ai on public.bh_amenities for all to bofo_ai_dev
  using (boarding_house_id in (select id from public.boarding_houses where owner_id in (select id from public.test_users_v)))
  with check (boarding_house_id in (select id from public.boarding_houses where owner_id in (select id from public.test_users_v)));
drop policy if exists activity_ai on public.bh_activity;
create policy activity_ai on public.bh_activity for all to bofo_ai_dev
  using (boarding_house_id in (select id from public.boarding_houses where owner_id in (select id from public.test_users_v)))
  with check (boarding_house_id in (select id from public.boarding_houses where owner_id in (select id from public.test_users_v)));

drop policy if exists reviews_ai_read on public.reviews;
create policy reviews_ai_read on public.reviews for select to bofo_ai_dev using (true);
drop policy if exists reviews_ai_insert on public.reviews;
create policy reviews_ai_insert on public.reviews for insert to bofo_ai_dev with check (student_id in (select id from public.test_users_v));
drop policy if exists reviews_ai_delete on public.reviews;
create policy reviews_ai_delete on public.reviews for delete to bofo_ai_dev using (student_id in (select id from public.test_users_v));
