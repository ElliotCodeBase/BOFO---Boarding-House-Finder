# STATE after Stage 4, part 9 (admin app 2.12.0 / 21200): safety features (Stage 4i, rebuilt)

## Why "rebuilt"
Docs and versions said 2.10.0 (Stage 4i) was done, but this repo had no migration 044, no `QueueRules`, no Appeals / Unusual / break-glass code, no `AppealHelper`. Rebuilt from `docs/ADMIN_GUIDE.md` ("New in v2.10.0"), `SECURITY_AUDIT_FINAL.md` section 5 and `045_drop_legacy_admin.sql` (it expects `break_glass_keys()` and `admin_break_glass`). If you have the real 2.10.0 files, compare before merging.

## Done
- **044_admin_stage4_safety.sql** (NOT applied): tables `admin_item_marks`, `user_strikes`, `appeals`, `admin_break_glass` (RLS on, all grants revoked); `has_permission` = 039 body + break-glass branch (6 lendable keys); RPCs claim / release / marks list, escalate / clear, user_strikes / issue_strike, appeals list / decide, submit_appeal / my_appeal, break-glass grant / end / list, member_sessions, unusual_activity; triggers `push_unusual` (10 risky actions in 1 h, non-owner) and `push_decided`. N-1 (stale appeal) and N-4 (place-only strike) fixes included.
- **send-push**: kinds `escalated`, `decided`, `unusual` (admin-app phones, type only, no names). Redeploy.
- **shared**: `QueueRules` (+ `QueueRulesTest`), `PushRules` (+3 kinds, test extended).
- **Admin app**: Reports (waiting colour, "Being handled by", Escalate / Clear, Escalated chip, "Strike: next step"), IDs (waiting colour, claim), More > Appeals, More > Unusual activity, Team > Recent sign-ins / Lend a permission for 1 hour / Lent right now. `Marks` helper, `Ui.waitPill`.
- **User app**: `AppealHelper`, button + status on banned and suspended screens, `strings_appeal.xml`.
- Versions 2.12.0 / 21200 in both `build.gradle`.

## What was and was NOT run
- Run: `QueueRulesTest` + `PushRulesTest` (15 of 15) on a stand-in JUnit; javac parse pass on every changed Java file (only missing-Android-symbol errors, no syntax error); layout / strings XML parse.
- NOT run: Gradle, any Android compile, a phone, the SQL (no Postgres here), Deno / send-push. Expect possible small compile errors: send me the first one.

## Decisions to check
1. A claim is a 30-minute heads-up, not a lock. Closing the item releases it; the app has no separate Release button.
2. A warning strike is a staff record only (the user is not messaged). Strikes never expire.
3. Strike on a group of waiting reports about one person: the strike is applied to the oldest one, the others are marked "reviewed" ("Covered by a strike").
4. Accepting a ban appeal needs `unban` (Owner, or lent). If the Owner made the ban nobody else can decide unless the Owner lends `unban` (N-2, documented).
5. Break-glass beats a per-person deny (N-4, intended).

## How to test
Apply 042, 043, 044 (SQL editor, backup first), deploy `send-push`, then follow `RELEASE_CHECKLIST.md` sections 8 and 9 (per role). Quick check: two staff phones open the same report: the second sees "Being handled by ..."; a banned test user taps Appeal once, a second try is refused, the Owner sees it under More > Appeals.

## NOT done
`045` (only after old user-app versions are blocked), `SECURITY_AUDIT_FINAL.md` N-3 helper grants, S-07 nonce, TalkBack labels in the Admin app, bulk approve.
