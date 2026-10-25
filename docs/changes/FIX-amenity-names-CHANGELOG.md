# Fix: "Something went wrong" on Home (column bh_cards.amenity_names does not exist)

## What happened
The app asks Supabase for `bh_cards.amenity_names`, which only exists after `supabase/migrations/005_amenities.sql` is run.
The code is supposed to fall back to a query without that column, but Home loads two lists at once. The first one switched the
fallback on, and the second one (query already built with the column) refused to retry and showed the error.

## What changed
- `src/main/java/com/bofo/finder/service/BhService.java` - `cards()` now retries without the amenity columns whenever the
  failed query contained `amenity_names`, even if another request already flipped the fallback flag.

## How to apply
```powershell
cd C:\Users\user\bofo-finder
Expand-Archive -Path "$HOME\Downloads\BOFO-amenity-fix.zip" -DestinationPath . -Force
Remove-Item .\FIX-amenity-names-CHANGELOG.md -ErrorAction SilentlyContinue   # optional, or keep it
mvn clean javafx:run
```
Home, Explore and Saved should load again (without amenity tags until the migration is run).

## The permanent fix (do this too)
1. Supabase -> your project -> SQL Editor -> New query.
2. Paste the whole of `supabase/migrations/005_amenities.sql` and click Run. It needs to run after migrations 001-004.
3. Wait about a minute, or run `NOTIFY pgrst, 'reload schema';`, then restart the app.
After this the amenity tags, the amenity filter in Explore and the console message about missing amenity columns all work.

## If problems occur
- Still "Something went wrong": press Try again once, then check the terminal for a different error and paste it here.
- Migration fails with "relation public.users / is_admin does not exist": run migrations 001-004 first, in order.
- `amenities_v` 404 (PGRST205) after running it: the API cache has not refreshed, run `NOTIFY pgrst, 'reload schema';`.
- Roll back this fix: `git checkout -- src/main/java/com/bofo/finder/service/BhService.java`
