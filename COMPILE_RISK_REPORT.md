# Compile-risk report (Stage 1) - repo as uploaded 2026-11-07, both apps 2.12.0 / 21200

Labels: RAN = executed here, saw output. READ = only read. NOT CHECKED = needs Gradle / SDK / phone.
Sandbox: JDK 21 (javac via `jdk.compiler`), Python 3. No Android SDK, no Gradle, no network.

## Result
No compile-risk defect found by the checks below. This does NOT mean it builds: Gradle has still not run (see "Not covered").

| # | Check | Level | Result |
|---|-------|-------|--------|
| 1 | 91 files in `DELETED_FILES.txt` are gone from the tree | RAN | 0 still present |
| 2 | Leftover references to deleted classes / layouts / Binding classes (Java, XML, proguard), comments ignored | RAN | 0 |
| 3 | Every import of the three modules matches a declared dependency (admin, shared, app) | RAN | 0 problems |
| 4 | Every `com.bofo.*` import resolves to an existing class; app does not import shared; admin imports no finder class | RAN | 0 |
| 5 | All 354 XML files parse | RAN | 0 errors |
| 6 | Every `R.type.name` (own, not `android.R`) and `@type/name` exists in module resources; ids declared via `<item type="id">` counted | RAN | 0 |
| 7 | 28 app files with a single ViewBinding: layout exists and each `binding.x` is an id in it (includes followed) | RAN | 0 |
| 8 | Manifests (app, admin, shared): components exist as classes, theme/icon/xml resources exist, `allowBackup=false`, cleartext off | RAN | 0 |
| 9 | Gradle wiring: settings includes `:shared`, `:app-admin`; proguard/wrapper files exist; both apps same version; Gradle 8.13 vs AGP 8.11.1; compileSdk 36; Java 17 | RAN / READ | OK |
| 10 | `javac` on all 3 modules (SDK missing -> 8,787 expected errors) filtered for syntax errors | RAN | 0 syntax errors |
| 11 | Static calls on own classes (`Class.method(...)`): method exists, argument count matches an overload; classes resolved by package/import | RAN | 1,766 calls, 0 problems |

Check 11 exists because check 10 is blind when a call has an argument of an SDK type (proved with a deliberate bad call: javac filter did not report it, check 11 did).

## Findings
1. **Stray duplicate folders (harmless, clean up).** `bofo-android/app-admin`, `bofo-android/shared`, `bofo-android/supabase`, `bofo-android/bofo-android` hold 23 files, all byte-identical to the real ones (checked). They come from unzipping stage zips one level too deep. Gradle ignores them (settings use `../shared`, `../app-admin`). Delete them:
   `Remove-Item -Recurse -Force bofo-android\app-admin, bofo-android\shared, bofo-android\supabase, bofo-android\bofo-android`
2. **Docs behind the code.** Code is 2.12.0 / 21200 in both apps; `STATE_STAGE5.md`, `NEXT_AI_PROMPT.md` and `RELEASE_CHECKLIST.md` still say 2.11.0 (incl. `set_min_app_version('2.11.0')`). Decide the real minimum version before running those SQL lines (Stage 6 updates the docs).
3. **Plugin note (no change made).** Root `build.gradle` declares only `com.android.application`, while `:shared` applies `com.android.library`. This works with AGP (same artifact); if Gradle complains, add `id 'com.android.library' version '8.11.1' apply false` to the root plugins block.
4. Admin app is built in code (no ViewBinding, `buildFeatures` has no `viewBinding`), consistent with its sources.

## Not covered (needs your machine)
- Gradle / AGP dependency resolution, R8, lint, generated `R` and Binding classes, real type checking against the SDK, instance (non-static) method calls and generics on own classes, any unit test run (junit is not available here), any device test.
- Known limit of the scripts: `//` inside a string literal can hide the rest of that line from `check_all.py`.

## Run on your machine
```
cd C:\Users\user\bofo-finder\bofo-android
.\gradlew :shared:testDebugUnitTest :app-admin:assembleDebug :app:assembleDebug 2>&1 | Tee-Object build.log
```
Paste the first error from `build.log` here. Re-run the checks any time (Git Bash): `sh tools/verify/run_all.sh`
