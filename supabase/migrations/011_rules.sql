-- BOFO Finder house rules (v2.0.64). Run AFTER 010_reviews_with_names.sql.
-- Owners list the rules of their boarding house (one text per rule); students see them on the place page.
-- Existing RLS on boarding_houses already lets the owner write and active listings be read, so no policy change is needed.
alter table public.boarding_houses add column if not exists rules text[] not null default '{}';
alter table public.boarding_houses drop constraint if exists boarding_houses_rules_max;
alter table public.boarding_houses add constraint boarding_houses_rules_max check (coalesce(array_length(rules, 1), 0) <= 20);
