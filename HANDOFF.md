# Handoff - v1.4.1 compile fix (2026-10-24)
- `CampusPicker`: fixed "variable sync might not have been initialized" (state listener lambda was a field initializer reading the final `sync`; now assigned in the constructor). v1.4.0 compiled-failed, so this supersedes it. Version bumped to 1.4.1 (versionCode 5).

---
# Handoff - v1.4.0 campus search with autocomplete (2026-10-24)
- NEW `ui/CampusPicker`: campus search box with our OWN autocomplete (local matching in Java on the cached campus list; no per-keystroke API, no Google Places). Order: starts-with, word starts-with, contains, initials (e.g. "norsu"). Dropdown arrow lists all campuses; Up/Down/Enter/Esc work; typed part is bold.
- Explore: picker above the search bar. Choosing a campus sets `State.college`, so the LIST sorts nearest-first and the MAP recenters/zooms (works in both List and Map modes). Clearing it goes back to GPS. Count reads "N places near <campus>".
- Home: the fake search pill is now a CampusPicker (`syncWithState=false`): pick a campus -> opens Explore near it; Enter on non-campus text -> Explore name search.
- Version: `app.version` in config.properties is now the one number shown in Profile > About (was hard-coded "v1.3.0"); pom 1.4.0 / versionCode 4. RULE (CLAUDE.md): bump all of these in EVERY pull request.
- NOT compiled/run (no Maven/JavaFX in sandbox). Risks: `TextFlow` cells in the ListView, `sceneProperty` listener add/remove in ExploreView/CampusPicker.
- Campus list still comes from Supabase `colleges` (1 h cache, same list the map uses); no schema change needed.

---
# Handoff - Vector map from OpenFreeMap (2026-10-24)
- NEW `util/VectorTileRetriever` (registered in META-INF/services, replaces MinimalTileRetriever as the Gluon Maps provider) + `util/Mvt` (own MVT decoder). Draws land (faint lavender), parks/wood/grass (soft green), water (light blue), buildings (light, outlined from z16), roads (white + thin outline; main roads outlined in brand-purple tint #C5BAF7) into a 512 px Canvas image per tile. Gluon shows tiles at 256 px (`TileImageView.fitWidth=256`, checked in maps 2.0.0-ea+6 source), so it is 2x sharp. No labels, no POI icons: the layers place/poi/*_name/housenumber/boundary are skipped while decoding.
- Config: `map.renderer=vector|raster` (config.properties, default vector), `map.vector.tilejson`, `map.attribution.vector`. `AppConfig.mapAttribution()` follows the renderer, so the MapPane label now reads "OpenFreeMap (c) OpenMapTiles, data from OpenStreetMap". `raster` = old MinimalTileRetriever (CARTO/Esri URL still from map.tiles.* / config.local.properties). A single tile also falls back to raster if TileJSON/tile/draw fails.
- Verified by fetching (2026-10-24): TileJSON `https://tiles.openfreemap.org/planet` = TileJSON 3.0.0, tiles URL is VERSIONED (`.../planet/20261018_080001_pt/{z}/{x}/{y}.pbf`; the build id changes, so the app reads the TileJSON at run time, caches it 24 h, and re-reads it on a 404), minzoom 0 / maxzoom 14 (zoom 15-19 is drawn by enlarging the z14 tile). 16 layers: aerodrome_label aeroway boundary building housenumber landcover landuse mountain_peak park place poi transportation transportation_name water water_name waterway. Fields used: transportation `class` (motorway trunk primary secondary tertiary minor service track path pedestrian ...), `brunnel`, `ramp`; landcover `class` (wood grass wetland sand ice); landuse `class` (pitch cemetery school hospital); waterway `class` (river ...). Class lists cross-checked with OpenFreeMap's own `styles/liberty`.
- Decision: NO decoder dependency in pom.xml (asked for one). protobuf-java/JTS based MVT libraries use reflection and are not proven under GluonFX/GraalVM on Android; `Mvt.java` reads the protobuf wire format directly (no reflection, JDK only). pom changed only in `reflectionList` (+VectorTileRetriever). Checked: `Mvt` output (geometry + properties) is identical to @mapbox/vector-tile on 16 real fixture tiles incl. a 2,759-feature tile.
- Disk cache: raw tiles in `<private storage>/tiles/ofm/{z}/{x}/{y}.pbf`, 30 days fresh, older copy used when offline (same rule as before); TileJSON in `.../tiles/ofm/tilejson.json`. Max 4 parallel downloads (OpenFreeMap asks for <= 4).
- Tune the look in `VectorTileRetriever` (palette constants at the top, `W14` road widths, `width()` growth 1.5x per zoom).
- Compiled only against JavaFX STUBS (signatures checked in the openjfx jfx21 source) + real Gluon `TileRetriever`/org.json; behaviour tested with a fake local OpenFreeMap server (cache, gunzip, offline, 404, overzoom math, fallback). NOT run with real JavaFX, NOT run against the real server (sandbox cannot reach it), NOT seen on screen.
- UNTESTED: how it looks (colors, road widths, whether tiles join without seams); first `mvn clean javafx:run` compile with real JavaFX; Canvas.snapshot speed/behaviour on Android; GluonFX native-image build (no reflection used apart from the ServiceLoader entry, `java.util.zip` and org.json were already fine); whether OpenFreeMap answers 404 for ocean-only tiles (we draw plain land then); real-tile layer fields beyond those listed above.
- If the map stays on the old look: check console for "bofo-vtiles" errors and that `map.renderer` is not overridden in config.local.properties.

---
# Handoff - v1.2.0 location, Profile switch, map tiles (2026-10-24)
- Version bumped to 1.2.0 (versionCode 3). Fixed AuthService compile error (parenthesize the HTML string before .getBytes).
- Location: `util/Locator` has a saved on/off choice (`~/.bofo/location.properties`), a non-blocking "Allow location?" prompt after login, and `setEnabled()`. Android uses Attach PositionService. PC asks Windows through PowerShell (System.Device GeoCoordinateWatcher), then falls back to a browser page calling navigator.geolocation. `dev.location` in config.local.properties overrides everything (remove it to use the real location).
- `ProfileView`: "Use my location" switch under "Show BOFO mascot". `MapPane`: opens centered on the user's first fix.
- Map tiles: CARTO now returns an "API KEY REQUIRED" image without a key (HTTP 200, so no fallback). config.local.properties currently points at Esri tiles (no key). The MapTiler key was removed on purpose.
- NEXT: draw the map inside the app (vector tiles from OpenFreeMap, own style, no labels, sharp) as a new TileRetriever. Not started.
- NOT compiled by the assistant; the user ran the app on Windows. Android build not tested.

---
# Handoff - Map style, location, in-app Google sign-in (2026-10-24)
- Files: `util/MinimalTileRetriever`, `util/Locator`, `service/NativeGoogle`, `ui/MapPane` (rewritten), `ui/HomeView`, `service/AuthService`, `ui/LoginView`, `app/Main` (focusApp), `config/AppConfig`, `config.properties`, `bofo.css`, `Glyphs`, `pom.xml` (resourcesList + reflectionList), `AndroidManifest.xml`, `src/android/java/com/bofo/finder/android/GoogleSignInActivity.java`, `docs/NATIVE_GOOGLE_SIGNIN.md`.
- NOT compiled, NOT run. Likely first compile risks: Attach `PositionService` / `Position` calls in `Locator` (same calls HomeView used before: `create()`, `start()`, `positionProperty()`, `getLatitude/getLongitude`), `MapLayer.markDirty()` called through `PinLayer.refresh()`.
- Map: get a free CARTO key (dashboard.basemaps.carto.com) and put `map.tiles.key=...` in `config.local.properties` (git-ignored). Without it the map falls back to plain OpenStreetMap tiles.
- Android Google sheet: unverified assumptions = `HostServices.showDocument("bofo://google-signin...")` launches our own activity; Attach `getPrivateStorage()` is the same folder as Android `getFilesDir()`; GluonFX compiles `src/android/java` and uses `src/android/build.gradle` for libraries. Failure of the first one is handled (falls back to the browser after 4 s).
- The Android browser flow still cannot finish on a phone (README: the `bofo://auth-callback` link is never delivered into Java). The in-app sheet avoids that problem.

---
# Handoff - Delete account fix (2026-10-23)
- Cause 1: `delete_my_account()` ran `delete from storage.objects`; Supabase forbids that from SQL ("Use the Storage API instead"), so it ALWAYS failed.
- Cause 2: `recompute_bh()` re-inserted a `bh_activity` row for a listing being cascade-deleted -> FK violation (also broke deleting listings/owner accounts).
- Fix (already applied to the live Supabase project BOFO): migration `supabase/migrations/004_delete_account_fix.sql` (fixes recompute_bh, strips storage SQL from the RPC, `owner_verifications.reviewed_by` -> ON DELETE SET NULL) and Edge Function `supabase/functions/delete-account` (deployed, verify_jwt on): deletes the user's files in `id-verification` and `bh-photos` via the Storage API, then the auth user (everything cascades).
- App: `ProfileView.confirmDelete` now calls `SupabaseClient.function("delete-account", ...)` instead of the RPC.
- Tested: DB deletion of the dev owner (with listings) succeeded in a rolled-back transaction. NOT tested: the Edge Function call and the Java change (no network/Maven in Claude's sandbox).
- If delete still fails: Supabase dashboard > Edge Functions > delete-account > Logs (look for "delete-account failed"), and paste the app's toast/console text.

---
# Handoff - Online mode: real Supabase sign-in for dev (2026-10-23)

## What changed
- Supabase project **BOFO** (ref `zfxldzcoirqolflkkhrh`, ap-southeast-1) is live: 001-003 migrations applied, 15 colleges, 3 boost plans, buckets `bh-photos` (public) and `id-verification` (private). `config.properties` already points to it.
- Created 3 REAL test accounts (auth.users + public.users): student, owner (verified), admin. Emails/passwords are in `src/main/resources/com/bofo/finder/config.local.properties` (git-ignored, NEVER commit).
- Seeded 6 demo boarding houses (names start with "Demo BH", owned by the dev owner) near NORSU, Silliman, Foundation, St. Paul, La Salle and CHMSU Alijis, so Home/Explore/map are not empty.
- `AppConfig`: loads optional `config.local.properties`; `devEmail(role)` / `devPassword(role)`.
- `AuthService.devSignIn(Role)`: real password sign-in (`/auth/v1/token?grant_type=password`), saves the session, loads the profile. Gives a genuine JWT, so RLS, favorites, chat, listings and storage work online (the old fake `devForce`/`devLogin` could not).
- `LoginView`: the Student / Owner / Admin dev buttons now call `devSignIn` and show "No internet connection" or the error text on failure.
- Removed the stale duplicate nested folder `BOFO---Boarding-House-Finder-main/BOFO---Boarding-House-Finder-main/` from the zip (older copy of the same sources).

## Google login (see docs/GOOGLE_LOGIN_SETUP.md)
Steps done 2026-10-23: Google Cloud project "BOFO Finder", consent screen (External), test user, Web OAuth client "BOFO Supabase" -> then paste Client ID/secret into Supabase Auth > Providers > Google and add redirect URLs. Keys are in `KEYS.local.md` (git-ignored). Status: finish steps 4-6 of that doc if not yet done.

## NOT verified (no Maven/network access in Claude's sandbox)
- Not compiled; the password login was not tested against Supabase from here. If the dev button shows "Dev sign-in failed", paste the message.
- Edge functions (`bofo-chat`, `create-boost-checkout`, `paymongo-webhook`) are NOT deployed and need secrets (LLM key, PayMongo). BOFO AI chat and Boost payments will fail until then.
- Google login still needs the Google provider + redirect URLs (`bofo://auth-callback`, `http://localhost:54321/callback`) in Supabase Auth settings.

## Before release
- Delete the dev buttons in `LoginView` and `devSignIn`/`devLogin`/`devForce` in `AuthService`; delete the 3 test users and "Demo BH" rows in Supabase.

## Git
Follow the "Clean repo update guide" below. Check `git status` first: `config.local.properties` must NOT appear (it is in `.gitignore`).

---
# Handoff – Dev login bypass (2026-10-22)

## What changed
- `config.properties`: added `dev.bypassLogin=true` and `dev.role=student` (student | owner | admin).
- `AppConfig`: `devBypass()` / `devRole()`.
- `AuthService.devLogin(Role)`: fake local profile (verified, fixed UUID `00000000-0000-0000-0000-0000000de000`), no token.
- `Main.start`: when bypass is on, skips splash + Google login and routes straight to the home for `dev.role`.
- `LoginView`: when bypass is on (e.g. after Sign out) shows "DEV: skip login as Student / Owner / Admin" buttons.

## Caveats
- No real Supabase session: requests use the anon key, so anything behind RLS (saving, chat, publishing listings, owner/admin data) may return 401/403 or empty. Public reads (colleges, listings) should work.
- The fake user id has no row in `users`, so inserts with FK to `users` will fail. For full testing, use a real login or create a test user row in Supabase.
- NOT compiled here (no Maven network access) – run `mvn javafx:run` / your usual build to verify.

## Before release
Set `dev.bypassLogin=false` in `src/main/resources/com/bofo/finder/config.properties` (or remove the dev code).

## Local folder & update commands
User's local repo folder: `C:\Users\user\bofo-finder`

PowerShell (adjust the zip path if it is not in Downloads):
```powershell
cd C:\Users\user\bofo-finder
Expand-Archive -Force $HOME\Downloads\bofo-dev-bypass.zip $env:TEMP\bofo-new
Copy-Item -Recurse -Force "$env:TEMP\bofo-new\BOFO---Boarding-House-Finder-main\*" .
git pull origin main
git checkout -b dev-login-bypass
git add -A
git status
git commit -m "Add dev login bypass for testing"
git push -u origin dev-login-bypass
```
Notes: use `master` instead of `main` if that is the default branch; `git stash` / `git stash pop` if pull complains about uncommitted changes; check `git status` for stray files (target/, __pycache__) before committing.

---
# Session log, run guide & known issues (updated 2026-10-22)

## Environment (user)
- OS: Windows, PowerShell. Repo folder: `C:\Users\user\bofo-finder`
- GitHub repo: https://github.com/ElliotCodeBase/BOFO---Boarding-House-Finder.git
- Run on desktop: `cd C:\Users\user\bofo-finder` then `mvn javafx:run` (needs JDK 17+ and Maven; first run downloads deps)
- Claude's sandbox CANNOT reach Maven Central or compile this project (network blocked). Every compile error comes from the user's terminal; ask for the pasted output. Don't guess API names: have the user run
  `javap -cp (Get-ChildItem $HOME\.m2 -Recurse -Filter "maps-2.0.0-ea+6.jar").FullName com.gluonhq.maps.MapView`
  and read the real method list.

## Git setup (folder was a ZIP download, not a clone)
PowerShell: do NOT type the `<` `>` around the URL (PowerShell treats `<` as an operator).
```powershell
cd C:\Users\user\bofo-finder
git init -b main
git remote add origin https://github.com/ElliotCodeBase/BOFO---Boarding-House-Finder.git
git fetch origin
git reset origin/main        # keeps working files, links history (use origin/master if default is master)
git status                   # should list only the changed files; check the untracked bofo/ folder first
git checkout -b dev-login-bypass
git add -A
git commit -m "Add dev login bypass for testing"
git push -u origin dev-login-bypass
```
After this one-time setup the folder is a normal clone: later updates are just `git add -A`, `git commit -m "..."`, `git push`.

## Compile errors hit so far
1. `MapPane.java:41` `map.getMapPoint(...)` does not exist on `com.gluonhq.maps.MapView` (Gluon Maps 2.0.0-ea+6).
2. First attempted fix `map.centerLat()/centerLon()` ALSO does not exist.
3. Current fix (in this zip, UNVERIFIED against ea+6): convert the map's center to scene coords and call `map.getMapPosition(sceneX, sceneY)`:
   `Point2D s = map.localToScene(w/2, h/2); MapPoint p = map.getMapPosition(s.getX(), s.getY());`
   If that fails too, try `map.getCenter()` (MapPoint) or check the javap output above.
   One-line PowerShell patch for an older copy: replace the body of `center()` in `ui/MapPane.java` (about line 41).

## Supabase status
- User's Supabase account has NO BOFO project (only "Coffee Shop" and "ryhar-portfolio", unrelated: never modify them).
- `config.properties` still holds placeholders (`supabase.url`, `supabase.anonKey`). With the dev bypass the app opens but data screens are empty/error until a real project is configured.
- To finish: create a new Supabase project "BOFO" (may add cost: ask the user first), run `supabase/migrations/001_schema.sql`, `002_performance.sql`, `003_boosts.sql`, put the Project URL + ANON/publishable key (never service_role) in config.properties, insert rows into `colleges`. User has granted Claude access to their Supabase for this.
- Google login (non-bypass) additionally needs the Google provider + redirect URLs (see README Setup).

## Dev bypass reminder
`dev.bypassLogin=true` and `dev.role=student|owner|admin` in `src/main/resources/com/bofo/finder/config.properties`. Set to false before release.

---
# Clean repo update guide (for the AI to follow on the next update)

## Current git state (as of 2026-10-22)
- `C:\Users\user\bofo-finder` started as a ZIP download. It was linked with `git init` + `git remote add origin ...`.
- The first `dev-login-bypass` commit was made BEFORE linking to origin, so GitHub said "main and dev-login-bypass are entirely different commit histories" and refused a pull request.
- Fix given: `git fetch origin` -> `git reset --soft origin/main` -> commit -> `git tag -fa v1.0.1` -> `git push --force-with-lease -u origin dev-login-bypass` -> `git push -f origin v1.0.1`.
- Once that works the folder is a normal clone sharing history with `main`. Do NOT run `git init` again.

## Rules for every next update
1. FIRST ask the user to run `git status` and `git branch -vv` in `C:\Users\user\bofo-finder` and paste the output; don't assume the state.
2. Never `git init` in a folder that already has `.git`. Never commit before the folder shares history with origin (`git log --oneline` should show origin/main's commits underneath).
3. Never type `<` `>` around URLs/placeholders in PowerShell commands; give ready-to-paste commands with real values.
4. Always start from fresh remote state: `git fetch origin` and base new work on `origin/main` (or the current feature branch), not on a stale ZIP copy.
5. Deliver code changes as either (a) a short PowerShell patch for 1-3 lines, or (b) a new zip. If a zip is copied over the folder, run `git status` and review the file list BEFORE `git add -A`. Expected files are only the ones changed this session.
6. Never commit: `target/`, `__pycache__/`, `*.class`, the stray `bofo/` folder (ask what it is), real keys/secrets (service_role key, LLM_API_KEY, `.env`). The anon key in config.properties is public by design, but still prefer not to commit real project values to a public repo.
7. Work on a feature branch (e.g. `dev-login-bypass`), then open a pull request into `main`. Don't push dev-only changes straight to `main`.
8. Each update: commit message starts with the version (`v1.0.2: ...`), tag it (`git tag -a v1.0.2 -m "..."`), push branch then `git push origin v1.0.2`. Semver: patch = fix, minor = feature, major = breaking. Also bump `<version>` in `pom.xml` to match when asked.
9. Before merging to `main`: set `dev.bypassLogin=false` in config.properties.
10. Force push only on the user's own feature branch/tag and only with `--force-with-lease`; never force push `main`.
11. If a push needs auth, the user signs in to GitHub as the account owning `ElliotCodeBase` (browser prompt or personal access token). If git asks who they are: `git config --global user.name` / `user.email`.

## Standard update sequence (after the folder is a proper clone)
```powershell
cd C:\Users\user\bofo-finder
git status
git fetch origin
git checkout -b <branch-name>        # or: git checkout <existing-branch>
# ...apply changes / copy files...
git status                            # review file list
git add -A
git commit -m "vX.Y.Z: <what changed>"
git tag -a vX.Y.Z -m "vX.Y.Z - <what changed>"
git push -u origin <branch-name>
git push origin vX.Y.Z
```
Then open the pull request link printed by `git push` (or GitHub > Pull requests > New).

## Common problems
- "not a git repository": run commands inside `C:\Users\user\bofo-finder`; if no `.git`, do the link steps in "Git setup" above.
- "entirely different commit histories": use the `git reset --soft origin/main` fix above.
- "origin/main unknown revision": run `git fetch origin` first; check if the default branch is `master`.
- `<` operator error: remove the angle brackets.
- Maven compile errors: paste the output; Claude's sandbox can't compile (Maven Central blocked); see compile-error history above.
