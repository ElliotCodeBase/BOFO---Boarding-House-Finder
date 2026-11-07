# STATE after Verify Stage 1 (build-risk sweep)
- Code unchanged (no version bump). Added: `tools/verify/*` (5 scripts + run_all.sh), `COMPILE_RISK_REPORT.md`, this file, `VERIFY_PROMPT.md`.
- Result: 0 defects from checks 1-11 (see report). Gradle NOT run: first real proof is your `gradlew` run.
- Needs you: run the Gradle command in the report; paste the first error (if any); delete the 4 stray folders (finding 1).
- Not done: tests were not run (no junit here); Gradle-only issues unknown.
- Backlog (NICE): instance-call checker for own classes.
- Next stage (2): database verification of migrations 038..045 (MUST: order/safety/app-to-SQL cross-check + `supabase/RUN_ORDER.md`). First step: list `supabase/migrations/03*..04*`, read headers, build the dependency order.
