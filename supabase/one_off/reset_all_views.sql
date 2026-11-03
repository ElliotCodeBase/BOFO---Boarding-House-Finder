-- ONE-TIME, IRREVERSIBLE: sets the view count of every boarding house to 0 and forgets who looked when,
-- so counting starts clean. Run once in the Supabase SQL editor of the BOFO project (after 028).
-- Saves, chats, ratings and reviews are NOT touched. "Hot" ranking drops until new views come in.
begin;
update public.bh_activity set views = 0, updated_at = now();
delete from public.bh_view_seen;
commit;
-- check: should return 0
select coalesce(sum(views), 0) as total_views from public.bh_activity;
