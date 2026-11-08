# STATE_VERIFY_5: admin accessibility + per-role smoke sheet (v2.12.2 / 21202, both apps)
Labels: RAN = executed here, READ = read only, NOT CHECKED = needs Gradle / phone / TalkBack.

| Item | Label | Result |
|------|-------|--------|
| Icon buttons / images without label | READ + RAN (`check_a11y.py`) | No icon-only buttons exist; the one ImageView has a description. 0 failures |
| Headings, button role on tappable cards, chip selected state | READ | Added in `Ui` (`text`, `asButton`, `chip`) and used on 7 card sites + 4 chip sites. TalkBack output: NOT CHECKED |
| 48 dp touch targets | READ | `Ui.button` min height 48 dp. On a device: NOT CHECKED |
| Contrast (12 pairs used in the admin app) | RAN | Before: success/tint 3.60, error/tint 3.97, error/bg 4.27, success/white 4.13 (fail). After: all 4.5+ (lowest 5.63) |
| Large font | READ | Button rows stack at fontScale 1.3+ (ID review, Approvals); Reports chips scroll. 200% on a device: NOT CHECKED |
| State changes spoken | READ | All use Toast / Snackbar (TalkBack reads both). Not heard on a device |
| Screen change announced | READ | `MainActivity.show` sets a pane title from the first heading |
| Compile | RAN (partial) | Existing scripts: 0 problems (resources, ViewBinding, 1793 calls). `javac_pass.sh` shows 1 SYNTAX-LIKE line at `Ui.java:64` (`super` in `ButtonRole`): it is caused by the missing androidx jar; the same code compiles with stub classes. Gradle: NOT CHECKED |

## Not fixed (backlog)
- Focus does not move to the new screen when a More sub-screen opens (`MoreScreen.sub`).
- 320 dp phone with long names (Team / Appeals): needs a device (sheet A6).
- Admin dialogs built with `Dialog` (ID review) have no window title for TalkBack.

## Needs you
- Run Gradle (command in `HANDOFF_ANDROID.md`), paste the first error if any.
- Phone: follow `docs/SMOKE_TEST_SHEET.md` (A with TalkBack on first), send me the F rows.

## Files
New: `docs/SMOKE_TEST_SHEET.md`, `tools/verify/check_a11y.py`, this file. Changed: `Ui`, `MainActivity`, `IdScreen`, `ReportsScreen`, `ApprovalsScreen`, `AppealsScreen`, `AiChatLogScreen`, `AiUnansweredScreen`, `MoreScreen`, `PaymentsScreen`, `TeamScreen`, `ExportScreen`, admin `colors.xml`, both `build.gradle`, `docs/ADMIN_UI_AUDIT.md`, `tools/verify/run_all.sh`, `VERIFY_PROGRESS.md`, `CHANGES_ANDROID.md`, `HANDOFF_ANDROID.md`.

## First step of Stage 6
Read `.github/workflows/` (and `BOFO_STAGE_1/.github/workflows/android-gradle.yml`), then write the two-app workflow with `permissions: contents: read`.
