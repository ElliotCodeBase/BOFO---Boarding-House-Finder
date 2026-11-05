# BOFO admin inventory (Stage 1A, no code changed)

Source: repo v2.9.4 zip. "Shared" = also used by student/owner screens, so it stays in the main app or moves to the shared module.

## 1. Admin-only UI (move to BOFO Admin)
| Item | Notes |
|---|---|
| `AdminHomeFragment` + `fragment_admin_home`, `item_admin_home_card/section/week` | Home: grouped cards, "Last 7 days", CSV export entry |
| `QueueFragment` | ID verification queue (waiting colours, `ReviewSpeedRules`) |
| `VerificationReviewFragment` + `fragment_verification_review`, `RejectDialog`, `ZoomImageView` | ID/selfie review, reject reasons, second opinion, FLAG_SECURE, signed URLs 120 s |
| `ReportsFragment` + `ReasonDialog` | Suspend, ban, dismiss, take down, grouped, answered |
| `AdminPaymentsFragment` | Payments check, refund |
| `AiUnansweredFragment` (+layout/item) | Needs attention |
| `AiChatLogFragment`, `AiChatLogAdapter` (+layouts) | AI chat monitor, wipe |
| `BannedUsersFragment` (+ `item_banned_user`) | Banned/suspended list, unban |
| `ActivityLogFragment` | "What I did" |
| `AdminRowAdapter`, `AdminListChrome`, `item_admin_row`, `item_admin_skeleton`, `fragment_admin_list` | shared by the admin lists |
| `AdminLock`, `AdminMfaSheet` (+`sheet_admin_mfa`) | auto-lock, TOTP |
| `CsvShare` | CSV export |
| Parts of `SettingsFragment` / `fragment_settings`: `admin_mfa`, `admin_view_as`, `admin_ai_row`, `admin_lock_row` | admin settings; Settings stays, rows go |
| `ShellFragment` (admin branch), `AuthRouter` (admin branch), `ProfileFragment` (admin parts), `BhDetailFragment` (admin take-down / suspend actions), `PushService`, `PushRules`, `PushNotifier`, `PushRouter` (admin push kinds: verify each in Stage 5) | cut the admin branches in Stage 5 |
| `DebugFragment` | dev only, remove from release |

## 2. Admin-only services and rules
Services: `AdminCountsService`, `AdminWeeklyService`, `AiChatLogService`, `AiUnansweredService`, `BannedUsersService`, admin half of `ReportService`, admin half of `VerificationService`, admin half of `BoostService` (refund).
Rules (all have a test): `AdminRules`, `AdminHomeRules`, `AdminListRules`, `AdminActivityRules`, `AdminErrorRules`, `AiChatRules`, `AiUnansweredRules`, `AutoLockRules`, `RejectReasonRules`, `ReviewSpeedRules`, `CsvRules`.
Shared rules (stay in `shared`): `SuspendRules`, `BanRules`, `VerificationRules`, `PushRules`, `Validate`, `Money`, `ErrorText`, `Http`, `Async`, `Cache`.
Config: `AppConfig.devEmail/devPassword/hasDevAccounts` (drop in the admin app).

## 3. Navigation and menus
- Menu `bottom_nav_admin.xml`: `tab_admin_home`, `tab_queue`, `tab_reports`, `tab_admin_payments`, `tab_profile`.
- `nav_graph.xml` destinations: `verificationReviewFragment`, `aiChatLogFragment`, `aiUnansweredFragment`, `activityLogFragment`, `bannedUsersFragment` (admin only). **Not admin**: `bannedFragment`, `suspendedFragment` (what a banned/suspended user sees), `paymentsFragment`/`PaymentsFragment` (owner's own boost payments).

## 4. Strings and drawables
`strings_admin_actions/clarity/home/insights/lists/lock/mfa/push/speed/view.xml`, `strings_ai_chats.xml`, `strings_ai_switch.xml`, `strings_ai_unanswered.xml`, `strings_review_admin.xml`, `strings_ban.xml` (admin half; user ban text stays). Drawables `ic_adm_*` (11), `ic_dock_queue/reports/payments`, `bg_adm_icon_tile`, `bg_admin_role_tag`, `bg_week_stat`, `bg_report_pill`, `divider_admin_row`.

## 5. Supabase
**Tables** (admin reads/writes): `reports`, `owner_verifications` (incl. `second_opinion_*`, `id_hash`), `banned_identities`, `audit_logs`, `payments`/`boosts`, `ai_chat_log`, `ai_unanswered`, `ai_feedback`, `app_flags`, `app_settings`, `app_secrets` (pepper, private).
**Views**: `reports_admin_v`, `bh_cards` (admin sees all statuses), `test_users_v`.
**Admin RPCs** (all `is_admin()`-gated): `admin_approve_verification`, `review_verification`, `admin_flag_second_opinion`, `admin_review_report`, `admin_report_takedown`, `admin_takedown`, `admin_unban`, `admin_banned_list`, `admin_refund_boost`, `ai_chat_log_wipe`, `set_ai_enabled`, `send_test_push`, `push_test_result`.
**Owner/SQL-only (no grant to the app)**: `set_admin_mfa_required`, `reset_admin_mfa`, `set_min_app_version`, `set_min_admin_app_version`, `push_stale_check`, `ai_chat_log_purge`, `activate_boost` (service role).
**Policies using `is_admin()`**: read on users, boarding_houses, rooms, contacts, photos, reports, blocks_reports, user_agreements, owner_verifications, audit_logs, banned_identities, ai_unanswered, ai_chat_log; write on colleges, reviews delete, bh delete; storage `idv_read` (ID photos).
**Gate helpers**: `is_admin()` (role + MFA flag + `app_version_ok()`), `app_version_ok`, `enforce_min_app_version`.
**Triggers**: `push_report`, `push_idreview`, `push_dm`, `push_group`.
**Edge functions**: `send-push` (reports/idreview/stale/test go to admins), `paymongo-webhook` (no JWT), `bofo-chat` (writes `ai_chat_log`), `bofo-announce` (owner tool; admin exempt from AI switch), `create-boost-checkout`, `delete-account`.
**Push kinds for admins**: `report`, `idreview`, `stale`, `test`; channel and tap routing in `PushNotifier`/`PushRouter` (to confirm in Stage 3). No FCM topics exist.

## 6. Owner-only list (Stage 2) mapped to what exists today
| Owner-only item | Today |
|---|---|
| 1 sub-admins | does not exist (single `users.role='admin'`) |
| 2 payments | `AdminPaymentsFragment`, `admin_refund_boost` |
| 3 CSV | `CsvShare` (client side only: no server permission) |
| 4/5 ban, unban | `admin_review_report(ban)`, `admin_unban` |
| 6 take-down | `admin_takedown`, `admin_report_takedown` |
| 7 others' activity log | `audit_logs` readable by every admin |
| 8 AI/announce/broadcast | `set_ai_enabled`; announcements are owner features |
| 9 MFA reset | `reset_admin_mfa` (SQL editor only) |
| 10 app config | `set_min_*_version`, flags (SQL editor only) |
| 11 delete user data | `delete-account` (self only) |
| 12 raw ID images | `idv_read` + `signed()`: every admin, any time |
