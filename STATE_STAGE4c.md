# STATE after Stage 4, part 3 (admin app 2.9.10 / 20910)

Interpreted "next stage" as the next slice of Stage 4 (its backlog was still open). Stage 5 deletes admin from the main app, so it waits until the admin app has parity.

## Done (app-admin + shared only; NO new SQL, nothing touches the live database)
- **More > Needs attention (AI)** (`ai_answer`: Owner, Admin, Moderator, Support): newest 50 questions BOFO could not answer, filter by reason, shows "(text not stored)" when the AI chat monitor was off. Read-only. Server gate: policy `ai_unanswered_admin_read` (040).
- **More > Export CSV** (`csv_export`, Owner only): Reports or Payments, newest 1000, columns id / date / reason-or-amount / status (no names). Order: fingerprint/screen lock -> read rows -> `admin_log_export` (audit log; if it fails NO file is made) -> write file -> share sheet.
- Formula-injection protection (`= + - @ tab` get a leading apostrophe), UTF-8 BOM for Excel: `shared/CsvRules` (moved from the main app, same tests).
- Export file lives in the app cache `exports/`, deleted before each export and at app start. A `FileProvider` (not exported, only that folder) was added to the admin manifest: `app-admin/src/main/res/xml/file_paths.xml`.
- `shared/AiUnansweredRules` (moved, now checks `ai_answer` through `PermissionRules` instead of the old `Role` enum) + tests.
- Version 2.9.10 / 20910 in `app-admin/build.gradle` and `bofo-android/app/build.gradle`. The main app keeps its own copies of both rules classes until Stage 5.

## What was and was NOT run
- RAN: `javac` + JUnit on `shared` pure-Java classes: CsvRulesTest, AiUnansweredRulesTest, PermissionRulesTest, AutoLockRulesTest = 21 tests, all OK.
- NOT run: Gradle, Android build, the screens on a phone (no SDK here). Expect possible small compile errors in the 3 new screens: send me the first error.

## How to test (Owner unless stated)
1. `.\gradlew :shared:testDebugUnitTest :app-admin:assembleDebug`
2. More > Needs attention: list loads, chips filter. Support role: entry visible, no Export entry.
3. More > Export CSV > Payments: lock prompt, share sheet, open file in Excel: header row, no names. Then More > All admin activity shows a `csv_export` row with the row count.
4. Moderator / Admin: no "Export CSV" entry (server would also refuse `admin_log_export`).

## NOT done (backlog, suggested order)
1. Admin push: `FirebaseMessagingService` in the admin app, `device_tokens` app column, `send-push` targeting by permission, "approval needs you" push (read `send-push` first; needs a migration and your OK).
2. Strikes ladder, Escalate, queue claiming + waiting colours, saved replies, unusual-activity alert, session list + revoke, break-glass, appeals (all need migrations).
3. Refunds screen, AI chat log + AI settings/announcements in the admin app.
4. Two-step enrolment inside the admin app.
5. Stage 5 only after 1-4 (or after you decide to drop some of them).

## First step of the next part
Read `supabase/functions/send-push/index.ts` and migrations 029/033, then write `042_admin_push.sql` and tell me before I apply anything.
