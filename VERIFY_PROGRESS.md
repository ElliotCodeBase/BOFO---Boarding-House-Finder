# BOFO verification progress (read by VERIFY_PROMPT.md, AUTO-RESUME)

| Stage | Name | Status | Zip | Date | Resume point / notes |
|-------|------|--------|-----|------|----------------------|
| 1 | Build-risk sweep | DONE | BOFO_VERIFY_1.zip | 2026-11-07 | Static checks 0 defects; user ran Gradle: debug + release builds of both apps OK, :shared and :app tests pass |
| 2 | Database verification (038..045) | DONE | BOFO_VERIFY_2.zip | 2026-11-07 | Parsed + linted + app cross-check 0 defects; 3 Low findings; SQL never run on Postgres |
| 3 | Open security items, server side | TODO | | | |
| 4 | Open security items, app side + tests | TODO | | | |
| 5 | Admin accessibility + per-role smoke tests | TODO | | | |
| 6 | CI, release setup, final report | TODO | | | |

## Needs me
- Install the release build on a phone once and open every screen (R8 can crash at runtime; Gradle success does not prove it runs).
- Decide the real minimum app version (docs still say 2.11.0, code is 2.12.0 / 21200) before running `set_min_app_version`.

- Take a backup, then run migrations in `supabase/RUN_ORDER.md` order; run `supabase/VERIFY_AFTER.sql` and `supabase/ROLE_TESTS.sql` and report any row that is not OK / PASS.

## My confirmations (only what I reported back)
- 2026-11-07: `gradlew :shared:testDebugUnitTest :app-admin:assembleDebug :app:assembleDebug` BUILD SUCCESSFUL; `:app:testDebugUnitTest :app-admin:testDebugUnitTest :app:assembleRelease :app-admin:assembleRelease` BUILD SUCCESSFUL.
- Stray duplicate folders under bofo-android/ removed and pushed (534aefa).
