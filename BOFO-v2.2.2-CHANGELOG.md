# v2.2.2 - Taken-down places leave the lists + Admin Reports "Answered" section

## What was wrong
1. **Taken-down place still in the list.** `admin_takedown` correctly sets the place to `taken_down`, but the `bh_cards` view (and RLS `bh_read`) deliberately still returns non-active places to admins and to the owner. The app's Home / Explore / map list asked for "every card" without a status filter, so an admin (and the owner) kept seeing it.
2. **Answered reports had no home.** A report answered with Take down stayed "Waiting", and Suspended / Banned / Dismissed were only visible as the generic status "Actioned" / "Dismissed". Banned users lived in Profile.

## What changed
**App (bofo-android)**
- `service/BhService.java`: `list()` and `favorites()` now ask for `status=eq.active`. Owner screens (My listings, boost, detail by id) are untouched, so the owner still sees and manages the taken-down place.
- `ui/ReportsFragment.java`: tap the title (`Reports · ...`) and pick: **Waiting**, **Answered (all)**, **Answered: Suspended**, **Answered: Banned users**, **Answered: Dismissed**, **Answered: Taken down**, **All reports**. Each row shows how it was answered (Suspended / Banned / Dismissed / Taken down). **Take down a place** from a waiting report now also answers that report.
- **Banned users moved:** Reports > title > *Answered: Banned users* opens the same banned list (search, reason, who banned, ID blocked tag, Unban). The entry in Profile is removed.
- `ui/ProfileFragment.java`: the Banned users row is no longer shown.
- `res/values/strings.xml`: new `rep_filters` and `rep_outcomes`.
- `app/build.gradle`: 2.2.2 (versionCode 20202).

**Supabase (already applied live to project zfxldzcoirqolflkkhrh, file `supabase/migrations/027_reports_answered.sql`)**
- `reports.action` column (suspend / ban / dismiss / takedown / reviewed), old rows filled from `audit_logs`.
- `admin_review_report` now writes `action` (same signature, nothing in the app breaks).
- New `admin_report_takedown(rid, bh, reason)`: takes the place down and answers the report in one step; refuses a place that is not the reported user's.
- `reports_admin_v` has the new `action` column.

## How to access
Admin > Reports > tap the title > pick a section. Banned users: same menu > *Answered: Banned users*.

## Install (PowerShell, repo root C:\Users\user\bofo-finder)
```
Expand-Archive "$HOME\Downloads\BOFO-v2.2.2-changes.zip" "C:\Users\user\bofo-finder" -Force
cd C:\Users\user\bofo-finder\bofo-android
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat installDebug
cd ..; git add -A; git commit -m "v2.2.2 - taken-down places hidden from lists, Reports > Answered section"
```
The database part is already live: nothing to run in Supabase.

## If a problem occurs
| Problem | Fix |
|---|---|
| Taken-down place still listed | Pull to refresh (lists cache 30 s). Check the place: `select name,status from boarding_houses where name ilike '%NAME%';` must be `taken_down`; if it says `active`, take it down again from Reports. Make sure v2.2.2 is installed (Settings > About). |
| Old answered reports show only "Actioned" | They were answered before 027 and had no audit row. Set by hand: `update reports set action='suspend' where id='...';` |
| Reports screen error "column action does not exist" | 027 not applied on that project: run `027_reports_answered.sql` in the SQL editor. |
| "Banned users" opens but is empty | Nobody is banned yet; or the signed-in account is not admin / has not passed two-step (see v2.2.1 notes). |
| Take down says "does not belong to the reported user" | Pick a place of the reported user from the list (the report may have been filed on another user's place). |
| Go back to the old behaviour | Reinstall v2.2.1. The database is backward compatible (the old app ignores `action`). |

## Not run
Not built or run by Claude (no Android SDK). Java brace/paren balance and strings.xml checked by script only. The SQL was applied live and checked: column, both functions and the view column exist.
