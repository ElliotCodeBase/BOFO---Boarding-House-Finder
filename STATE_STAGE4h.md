# STATE after Stage 4, part 8 (admin app 2.9.15 / 20915): saved replies

## Done (app-admin + shared only; NO new SQL)
- **ID reject dialog** (`IdScreen.reject`): one multi-line reason box (max 300). Chips above it: 8 built-in replies (`SavedReplyRules.LABELS/TEXTS`) then the reviewer's own ("Mine: ..."). Tap fills the box (still editable); long-press an own chip = delete (asks). "Save this as my reply" keeps the box text (max 10, no duplicates, 5+ chars).
- Reject needs a reason of 5+ characters (before: a pick, with a note required for "Other"). Still asks fingerprint / screen lock, still calls `review_verification`.
- Own replies live in private SharedPreferences `admin_replies` (`RepliesStore`), phone only, never sent anywhere. Cleared on uninstall.
- `shared/SavedReplyRules` (+ `SavedReplyRulesTest`, 7 tests). Version 2.9.15 in both `build.gradle`.

## What was and was NOT run
- Run: `SavedReplyRulesTest` on plain JUnit 4.13.2: 7 of 7 pass.
- NOT run: Gradle, `IdScreen` / `RepliesStore` compile, a phone. Send me the first compile error if any.

## How to test (Owner / Admin / Moderator with id_review)
1. ID requests > open one > Reject: 8 chips; tapping one fills the box; edit it.
2. Type "hello there" > Save this as my reply: a "Mine: ..." chip appears. Close the app, reopen: still there. Long-press it > Delete.
3. Reject with 3 characters: refused. With a good reason: lock prompt > "Rejected."; the owner sees the text as the reason.
4. Save an 11th reply: refused with the "long-press to delete" hint.

## NOT done (backlog)
Strikes ladder, Escalate, queue claiming, unusual-activity alert, session list + revoke, break-glass, appeals (migrations, need OK); push when an approval is decided; AI announcements; then Stage 5.
