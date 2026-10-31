# Stage 8d changelog - v2.0.11 (owner verification)

## What changed
| File | Change |
|---|---|
| `ui/VerificationFragment.java` (new) | The screen: status + form (ID type, front, back, selfie, consent, send) |
| `res/layout/fragment_verification.xml` (new) | Its layout |
| `util/VerificationRules.java` (new) | Pure rules: states, who may send, ID types, JPEG / PNG naming, consent version |
| `service/VerificationService.java` | `submit` names / types uploads from the bytes (JPEG on Android) |
| `ui/ListingsFragment.java`, `fragment_listings.xml` | Verification banner; Add a place checks verification first |
| `res/navigation/nav_graph.xml` | `verificationFragment` destination |
| `res/values/strings.xml` | `verify_*` strings; `form_not_verified` text updated |
| `app/build.gradle`, `README.md`, `CHANGES.md`, `HANDOFF_ANDROID.md` | Version 2.0.11 / 211 |
| `test/.../VerificationRulesTest.java` (new) | 4 tests |

## How to open it
Sign in as an **owner** > **My places** > tap **Verification** in the banner (or **Add a place**).

## How to test
1. Unzip the changes zip over the repo, sync Gradle (no new libraries), run `gradlew.bat testDebugUnitTest` (expect 109 passed).
2. Owner, not verified: banner "You are not verified yet". Open Verification: pick an ID type, **Take photo** for front and back (or **Choose**), **Take selfie**, tick the box, **Send for review**. The card turns to "Waiting for review" and the form disappears.
3. In Supabase run `select * from owner_verifications order by submitted_at desc limit 1;` (status pending) and check the three files under `id-verification/<your uid>/`.
4. Approve as admin (SQL editor, logged in as an admin through the app's RPC, or `update users set is_verified = true, verification_status = 'verified' where id = '<uid>'` using the service role in the dashboard). Reopen My places: banner gone, **Add a place** opens the form.
5. Reject test: mark the row `rejected` with a `rejection_reason`: the card shows the reason and the form is open again.

## Simple fix guide
- Upload says "mime type not supported": the bytes are neither JPEG nor PNG; send me the red text.
- Upload / insert says "row-level security": the account must have role `owner`, and the first folder of the path must be the user id.
- Blurry warning on every photo: raise or lower the number in `VerificationService.looksSharp` (`> 40`).
- Camera does nothing: the device has no camera app; use **Choose** for the ID photos (the selfie needs a camera).
