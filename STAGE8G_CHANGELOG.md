# Stage 8g changelog (v2.0.14) - Boost + Payments

## What changed
| File | Change |
|---|---|
| `ui/BoostFragment.java`, `res/layout/fragment_boost.xml` | NEW. Choose place + plan, Pay with GCash, Custom Tab, status polling (3 s, +2 s per try, max 10 s, 16 tries), Check again |
| `ui/PaymentsFragment.java`, `res/layout/fragment_payments.xml` | NEW. Last 30 payments, details dialog, pull to refresh |
| `util/BoostRules.java` | NEW, pure: status mapping, polling delay, `safeCheckoutUrl` (https only), "function missing" test, plan line, date label |
| `test/.../BoostRulesTest.java` | NEW, 6 tests |
| `ui/ListingsFragment.java`, `res/layout/fragment_listings.xml` | Boost + Payments buttons; "Boost this place" in a place's menu (active places only) |
| `res/navigation/nav_graph.xml` | `boostFragment` (arg `bhId`, optional), `paymentsFragment` |
| `res/values/strings.xml` | `boost_*`, `pay_*`, `listing_boost` |
| `app/build.gradle` | version 2.0.14 / 214, `androidx.browser:browser:1.8.0` |

## What I found in Supabase (read-only, project `zfxldzcoirqolflkkhrh`)
- Tables `boost_plans` (id, name, days, price_php, active, sort_order; 3 plans: starter 3 d P49, weekly 7 d P99, monthly 30 d P299), `payments` (status check: pending / paid / failed / expired / refunded; 0 rows), `boosts` (one per payment).
- RLS: plans readable when active; owners read only their own `payments` and `boosts`; admins read all. No client insert / update policy: payments are written by the server only.
- Functions: `activate_boost(pid)`, `admin_refund_boost(pid, reason)` (both security definer).
- **Edge Functions: only `delete-account` exists.** `create-boost-checkout` (used by `BoostService.checkout`) is missing. `bofo-chat` (Ask BOFO, 8a) is missing too: the assistant uses its FAQ fallback.

## How to open it
My places tab > **Boost a place** or **Payments**; or tap a place > **Boost this place**.

## Simple fix guide
| Symptom | Fix |
|---|---|
| "Boost payments are not available yet" | Deploy the `create-boost-checkout` Edge Function (PayMongo secret, success / cancel URL secrets, webhook calling `activate_boost`). Tell me and I can write it. |
| Gradle cannot find `androidx.browser` | Sync Gradle; if 1.8.0 is refused, use the version Android Studio suggests. |
| Stays on "Waiting for your payment" | The webhook is not updating `payments.status`; check the PayMongo webhook. |
| Nothing to pick in Boost | You have no active place (unlisted / taken-down places are hidden). |
