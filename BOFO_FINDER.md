# BOFO Finder (BOFO – Boarding House Finder)

Android app for students to find boarding houses (BH). **BOFO** is also the AI mascot.
Stack: JavaFX + Maven → Android (GluonFX / Gluon Attach) · Supabase (Auth, Postgres, Storage, Realtime-ready, Edge Functions) · Google sign-in only.

## 1. What it does
| Role | Can do |
|---|---|
| Student | Sign in with Google, pick a college, browse map + New / Hot / Best tabs (nearest first), filter by price and inclusions, save favorites, chat with owners, rate and review a BH |
| Owner | Verify identity (ID front/back + live selfie, manual admin review), then add / edit / unlist / delete listings, reply to reviews, chat, **boost listings with GCash** |
| Admin | Review the verification queue (ID and selfie side by side), approve or reject with a reason, see reports, audit log |

## 2. Launch and auth flow
1. **Splash** (mascot + name, at least ~1.2 s) while the saved session is restored.
2. No session → **Login** ("Continue with Google", the same button for sign-in and log-in).
3. First login → **Terms/Privacy checkbox**, then choose **Student or Owner** (admin can never be self-assigned).
4. Returning user → straight to Student Home / Owner Dashboard / Admin Queue.
5. Session is stored **AES-256-GCM encrypted** in the app sandbox, auto-refreshed; failed refresh or revoked token → back to Login. Log out clears it.
6. Offline launch with a cached profile opens the app with an offline banner and cached listings.

## 3. Project layout
```
pom.xml                          Maven + gluonfx-maven-plugin
src/main/java/com/bofo/finder/
  app/Main                       entry, routing
  config/AppConfig               reads config.properties
  model/                         Profile, BoardingHouse, College, Conversation, Message, Role
  service/                       SupabaseClient, AuthService, SessionStore, BhService,
                                 ChatService, BofoAiService, VerificationService
  ui/                            Splash, Login, Role, Shell (bottom nav), Student/Owner/Admin shells,
                                 Home, Explore, Saved, BhDetail, Chats, Chat, Profile,
                                 Dashboard, Listings, ListingForm, Verification, Queue, Reports,
                                 MapPane, BhCard, BofoPanel (floating mascot), Nav, State, Ui
  util/                          Async, Cache, Capture, Geo, Pkce, Validate
src/main/resources/.../bofo.css  whole theme (named colors)
src/android/AndroidManifest.xml  permissions + bofo://auth-callback
supabase/migrations/001_schema.sql        tables, RLS, RPCs, storage
supabase/migrations/002_performance.sql   indexes, pre-computed aggregates
supabase/migrations/003_boosts.sql        GCash boosts: plans, payments, boosts, featured flag
supabase/functions/bofo-chat/index.ts     BOFO AI Edge Function
supabase/functions/create-boost-checkout  starts a GCash checkout (PayMongo)
supabase/functions/paymongo-webhook       verifies payment, activates the boost
```

## 4. Screens and navigation
- Student bottom nav: **Home · Explore · Saved · Chats · Profile**
- Owner: **Dashboard · Listings · Chats · Verify · Profile**
- Admin: **Queue · Reports · Profile**
- Floating **BOFO** button on every logged-in screen: draggable, snaps to the edge, can be hidden in Profile.

## 5. Theme (50 / 30 / 20)
White 50% (`#FFFFFF`, page `#F8F5FD`) · Purple 30% (`#6C3FC5`, dark `#4A2A8F`, tint `#EDE4FA`) · Amber 20% (`#FFB400`, used sparingly for stars, Hot badge, CTAs) · text `#1F1A2E` / `#6B6580` · success `#2E9E6B` · error `#D64545`.
Change colors only at the top of `bofo.css` (`-bofo-primary`, `-bofo-accent`, …).

## 6. Database summary
`users`, `colleges`, `boarding_houses`, `rooms`, `bh_contacts`, `photos`, `favorites`, `reviews`, `bh_activity`, `conversations`, `messages`, `blocks_reports`, `user_agreements`, `owner_verifications`, `audit_logs`, `ai_usage`.
Views: `bh_cards` (public-safe listing cards), `inbox_v`, `profiles_v`. Every table has RLS; key rules:
- Role, `is_verified` and `verification_status` cannot be changed by the user (trigger + RPC-only changes).
- Only verified owners can insert listings (policy), and only the owner can edit their own.
- Only the two participants can read/write a conversation; conversations are created through `get_or_create_conversation()`; blocked users cannot message.
- ID/selfie images live in the **private** `id-verification` bucket: owner writes to `<uid>/`, only that owner and admins read; admins view via 120 s signed URLs.
- Admin decisions go through `review_verification()` which writes the audit log in the same transaction.

## 7. Performance improvements (this version)
**Client**
- **Cache with offline fallback**: `Cache` keeps listings 30 s and colleges 1 h; if the network fails it serves the last good copy and the app shows the offline banner. Cache is cleared on sign-out.
- **Stale-request guard**: Home and Explore ignore responses from older requests, so fast filter changes never show outdated results.
- **Debounced radius slider**: one reload 350 ms after the slider stops, not one per tick.
- **Lazy rendering**: cards render in batches of 15 with "Show more"; map caps at 60 pins.
- **Image LRU cache**: 80 decoded images, loaded at 2× the display size in the background.
- **Smaller payloads**: only the columns the cards need (not `select=*`), limit 60.
- **Bounding-box queries**: radius searches add `lat/lng` ranges so Postgres uses the geo index; exact distance is checked client-side.
- **Parallel detail load**: photos, rooms, contacts and the card load at the same time; view counting is fire-and-forget.
- **Bounded thread pool** (6 threads) instead of an unbounded one.
- **Adaptive chat polling**: 3 s when active, backing off to 12 s when quiet, reset on any message.

**Server (`002_performance.sql`)**
- New indexes: reviews, favorites, unread messages, active-listing geo.
- Rating, review count and free beds are **pre-computed** by triggers instead of subqueries on every card.
- RLS policies use `(select auth.uid())` so it is evaluated once per query, not per row.

**Further steps when you grow**: switch chat to Supabase Realtime websockets, add keyset pagination, serve photo thumbnails through Supabase image transforms, and cache the map tiles.

## 8. BOFO AI
Called through the `bofo-chat` Edge Function. The LLM key lives only in function secrets. The function reads listings through the **caller's JWT** (so it only sees what the user can see), rate-limits to 10 requests/min/user, treats user and listing text as untrusted, and never touches IDs, selfies or chats. If the AI is offline the app falls back to a built-in FAQ.

## 8b. GCash Boost (paid visibility for owners)
**How it works**
1. Verified owner opens *My Listings → 🚀 Boost* and picks a plan (Starter 3 d, Weekly 7 d, Monthly 30 d; prices live in the `boost_plans` table, placeholders for now).
2. The app calls the `create-boost-checkout` Edge Function. The server re-checks that the user is a verified owner, the listing is theirs and active, and uses the **database price** (the app can never set a price). It creates a pending `payments` row and a PayMongo Checkout Session limited to **GCash**.
3. The app opens the GCash checkout; the owner approves the payment in GCash.
4. PayMongo calls `paymongo-webhook`. It verifies the HMAC signature (with replay window), matches the amount against the stored payment, then runs `activate_boost()`: idempotent, one boost per payment, boosts **stack** (a second purchase starts when the first ends), audit-logged.
5. The app polls the payment status, shows a confirmation and refreshes lists.

**What students see**: Featured listings are pinned at the top of Home and Explore (at most **3 per list**, nearest first) with a ⭐ Featured badge. Ratings, reviews and the Best tab are not affected. Featured ads are always labelled.

**Safeguards**: only verified owners and active listings can be featured; unverified, unlisted or taken-down listings lose Featured automatically (the `is_featured` flag in `bh_cards` checks it live); clients cannot write `payments` or `boosts` (RLS, service role only); max 5 pending checkouts per owner per hour; admin **Payments** tab with refund bookkeeping (`admin_refund_boost` ends the boost and logs it; return the money in the PayMongo dashboard).

**Setup**: run `003_boosts.sql`; create a PayMongo merchant account (needs business verification, enable GCash); set secrets `PAYMONGO_SECRET_KEY`, `PAYMONGO_WEBHOOK_SECRET`, `BOOST_SUCCESS_URL`, `BOOST_CANCEL_URL` (simple https pages saying "Return to BOFO"); deploy `create-boost-checkout` and `paymongo-webhook --no-verify-jwt`; register the webhook URL in PayMongo for `checkout_session.payment.paid`. Test with `sk_test_` keys first.

**Alternative without a payment provider**: owner sends GCash to your number and enters the reference no.; an admin approves it in the Payments tab. Simpler and no fees, but manual and easy to abuse.

**Legal/finance**: add a Payments & Boost clause (price, duration, no refund once active, takedown rules) to the Owner Agreement and Terms; PayMongo fees and taxes apply, so check BIR/DTI requirements for collecting payments. Verify the PayMongo event and signature fields against their current docs before going live.

## 8c. BOFO mascot
Built only from basic shapes on a 200×200 grid, with no arms (one clean silhouette): a **rounded-rectangle body** (purple), a white rounded-rectangle face screen with a soft tint highlight, black pill-shaped eyes and mouth, a small amber status light, rounded-rectangle feet, and a house-roof hat (amber triangle roof, chimney, rounded-rectangle brim in darker amber).
- **Expressions**: happy (default, floating button, login), thinking (while the AI answers, empty states; glancing eyes, flat mouth, thought bubble), waving (splash screen; cheerful black ^ ^ eyes, open smile and a small greeting tilt).
- **In the app**: `ui/Mascot.java` draws it with JavaFX shapes (no image files), so it is sharp at any size; `Mascot.bob()` gives the idle bounce.
- **Loading animation**: `bofo-jump-spritesheet.png` is a 16-frame jump loop (256×256 per frame, anticipation squash, launch stretch, apex with cheerful eyes, landing squash, ground shadow that shrinks as BOFO rises). `ui/SpriteView.java` plays it at 16 fps and `SplashView` uses it. `design/mascot/sprite.py` regenerates the sheet and `bofo-jump-preview.gif`.
- **Design files** (`design/mascot/`): `bofo-happy|thinking|waving.svg` and `.png`, `bofo-app-icon.svg/png`, `bofo-play-store-512.png`, and `build.py` which regenerates them.
- **Android launcher icons**: `src/android/res/mipmap-*/ic_launcher.png` (verify GluonFX picks them up).
- Colors: purple `#6C3FC5` / dark `#4A2A8F`, ink `#1F1A2E`, white, amber `#FFB400` / darker `#E39B00`.

## 9. Setup
1. Create a Supabase project and run `001_schema.sql`, `002_performance.sql`, then `003_boosts.sql`.
2. Auth → Google provider; add redirect URLs `bofo://auth-callback` and `http://localhost:54321/callback`.
3. Put the project URL and **anon** key in `config.properties` (never the service-role key).
4. Insert colleges and make yourself admin (bottom of `001_schema.sql`).
5. `supabase functions deploy bofo-chat`; set secrets `LLM_API_KEY`, `LLM_MODEL`.
6. Desktop test: `mvn javafx:run`. Android (Linux/WSL2): `mvn gluonfx:build gluonfx:package`.

## 10. Known gaps
- Android OAuth deep link needs a small native bridge into `AuthService.handleDeepLink`.
- Session key is a sandbox file; move to Android Keystore for hardware-backed protection.
- Chat uses polling (Realtime upgrade path in `ChatService`); no push notifications yet.
- Gluon Maps (OpenStreetMap) is used instead of Google Maps because WebView is unreliable on Android with GluonFX; Google's terms forbid mixing its routes with other maps.
- The code was not compiled in this environment: expect a round of fixes on the first build and verify Attach API names.
- Legal documents are stubs: have a lawyer or school adviser review them (RA 10173, Google Play policies).

## 11. Security audit checklist (before launch and after each major release)
- [ ] RLS enabled on every table; two-user test for each policy; Supabase advisors clean
- [ ] `id-verification` bucket private; no service-role key in the app or repo
- [ ] Google OAuth redirect URIs locked down; token refresh/revoke tested
- [ ] Role changes cannot be self-assigned (test via the REST API)
- [ ] Maps/LLM keys restricted or server-side only; nothing hardcoded
- [ ] Upload limits (5 MB, PNG/JPEG) and input validation tested
- [ ] Chat/BOFO rate limits, prompt-injection and data-leak tests
- [ ] Audit log covers every approve/reject and takedown
- [ ] Retention job for ID images running; deletion flow tested
- [ ] Dependency scan; OWASP Mobile Top 10 pass; incident response plan written
Record each finding with date, severity, fix and re-test result.
