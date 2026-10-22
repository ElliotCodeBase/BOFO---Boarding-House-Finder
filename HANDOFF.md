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
