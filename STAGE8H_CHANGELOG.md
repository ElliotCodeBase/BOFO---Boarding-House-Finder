# Stage 8h changelog (v2.0.15) - Admin shell

## What changed
| File | Change |
|---|---|
| `ui/ShellFragment.java`, `res/menu/bottom_nav_admin.xml` | Admin role gets Queue / Reports / Payments / Profile; first tab Queue; mascot button hidden |
| `ui/QueueFragment.java`, `ui/VerificationReviewFragment.java`, `res/layout/fragment_verification_review.xml` | NEW. Pending list, review screen with 3 photos, Approve / Reject. Glide with no disk / memory cache, `FLAG_SECURE` while open |
| `ui/ReportsFragment.java` | NEW. `blocks_reports` list, details, take down a place of the reported user |
| `ui/AdminPaymentsFragment.java` | NEW. All payments, details, refund |
| `ui/ReasonDialog.java` | NEW. "Type a reason" dialog (cleaned, max 300) |
| `res/layout/fragment_admin_list.xml` | NEW. Shared list layout of the three admin tabs |
| `util/AdminRules.java`, `test/.../AdminRulesTest.java` | NEW, pure + 4 tests (reason rules, who can be refunded / taken down, UUID-only `in.(...)` lists) |
| `res/navigation/nav_graph.xml`, `res/values/strings.xml`, `app/build.gradle` | `verificationReviewFragment` (args vid, ownerName, idType, submitted, front, back, selfie); strings; version 2.0.15 / 215 |

## What I found in Supabase (read-only, project `zfxldzcoirqolflkkhrh`)
- `owner_verifications` (status pending / verified / rejected), `blocks_reports` (reporter_id, reported_user_id, reason max 300, is_block, created_at); both readable by their owner and by `is_admin()`. Currently 0 pending, 0 reports. One admin user exists.
- `review_verification(vid, approve, reason)`: admin only, must be pending, rejection needs a reason, updates `users.is_verified` / `verification_status`, writes `audit_logs`.
- `admin_takedown(bh, reason)` sets the place to `taken_down` + audit log. `admin_refund_boost(pid, reason)` sets the payment to `refunded`, ends the boost now + audit log. All are `SECURITY DEFINER` and check `is_admin()` themselves.
- Reports are about USERS, not places, so a takedown goes through the reported user's places (`bh_cards`: id, owner_id, name, status).

## How to open it
Sign in with the admin account: the bottom bar shows Queue / Reports / Payments / Profile.

## Simple fix guide
| Symptom | Fix |
|---|---|
| Admin sees the student tabs | `users.role` of that account must be `admin` (set it in Supabase). |
| Photos stay grey | The signed link lasts 2 minutes: leave the screen and open the request again. Check the `id-verification` bucket policy `idv_read` (own or admin). |
| "Could not save the decision" | The request was already decided (not pending any more). Pull down in Queue. |
| Cannot take a place down | The reported user may have no place left that is not already taken down. |
| Screenshots of the review screen are black | On purpose (`FLAG_SECURE`): ID photos are private. |
