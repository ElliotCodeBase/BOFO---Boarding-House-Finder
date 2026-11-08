# BOFO verification progress (read by VERIFY_PROMPT.md, AUTO-RESUME)

| Stage | Name | Status | Zip | Date | Resume point / notes |
|-------|------|--------|-----|------|----------------------|
| 1 | Build-risk sweep | DONE | BOFO_VERIFY_1.zip | 2026-11-07 | Static checks 0 defects; user ran Gradle: debug + release builds of both apps OK, :shared and :app tests pass |
| 2 | Database verification (038..045) | DONE | BOFO_VERIFY_2.zip | 2026-11-07 | Parsed + linted + app cross-check 0 defects; 3 Low findings; SQL never run on Postgres |
| 3 | Open security items, server side | DONE | BOFO_VERIFY_3.zip | 2026-11-07 | N-1 closed (in 044), N-3 = 046 written, not run; S-01/S-03/S-04 are steps for you |
| 4 | Open security items, app side + tests | DONE | BOFO_VERIFY_4.zip | 2026-11-07 | v2.12.1: nonce, re-auth before delete, dev-login gate; logic RAN as plain Java, Gradle not run |
| 5 | Admin accessibility + per-role smoke tests | DONE | BOFO_VERIFY_5.zip | 2026-11-07 | v2.12.2: labels, 48 dp, contrast, large font fixed in code; TalkBack / device NOT run; sheet = docs/SMOKE_TEST_SHEET.md |
| 6 | CI, release setup, final report | TODO | | | |

## Needs me
- Install the release build on a phone once and open every screen (R8 can crash at runtime; Gradle success does not prove it runs).
- Decide the real minimum app version (docs still say 2.11.0, code is 2.12.0 / 21200) before running `set_min_app_version`.

- Take a backup, then run migrations in `supabase/RUN_ORDER.md` order; run `supabase/VERIFY_AFTER.sql` and `supabase/ROLE_TESTS.sql` and report any row that is not OK / PASS.

- Run `046`, then `supabase/SECURITY_CHECKS.sql` (N-1, N-3 rows must be PASS); follow the S-03/S-04 order in `SECURITY_AUDIT_FINAL.md` section 7; run the S-01 commands in `docs/HISTORY_CHECK.md` and tell me the hit counts per row.

- Gradle build + tests for 2.12.2 (Stage 4 + 5 changes), phone test of Google nonce sign-in and account deletion (Google + e-mail code), Supabase Google provider "Skip nonce checks" OFF.

- Stage 5: run `docs/SMOKE_TEST_SHEET.md` on a phone (section A with TalkBack on, 200% font, 320 dp if you have a small phone) and send me the rows marked FAIL.

## My confirmations (only what I reported back)
- 2026-11-07: `gradlew :shared:testDebugUnitTest :app-admin:assembleDebug :app:assembleDebug` BUILD SUCCESSFUL; `:app:testDebugUnitTest :app-admin:testDebugUnitTest :app:assembleRelease :app-admin:assembleRelease` BUILD SUCCESSFUL.
- Stray duplicate folders under bofo-android/ removed and pushed (534aefa).
