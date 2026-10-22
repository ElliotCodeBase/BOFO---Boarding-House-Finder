# BOFO Finder (BOFO – Boarding House Finder)

JavaFX + Maven → Android via GluonFX. Backend: Supabase (Auth, Postgres, Storage, Edge Functions).

## Setup
1. **Supabase**: create a project → SQL editor → run `supabase/migrations/001_schema.sql`.
2. **Auth → Providers → Google**: enable, add your Google OAuth client. In **URL Configuration → Redirect URLs** add
   `bofo://auth-callback` (Android) and `http://localhost:54321/callback` (desktop dev).
3. Edit `src/main/resources/com/bofo/finder/config.properties` (URL + **anon** key only; never the service-role key).
4. Insert your colleges, then make yourself admin (see bottom of the SQL file).
5. `supabase functions deploy bofo-chat` and set secrets `LLM_API_KEY`, `LLM_MODEL`.
6. Run on desktop: `mvn javafx:run`. Android (Linux host): `mvn -Pandroid gluonfx:build gluonfx:package` then `gluonfx:install gluonfx:nativerun`
   (this pom uses `<target>android</target>` in the plugin config; for desktop JVM runs use the javafx plugin).

## Layout
- `app/Main` – splash → session restore → Login | Role | Student / Owner / Admin home
- `service/` – SupabaseClient, AuthService (PKCE Google), SessionStore (AES-GCM), BhService, ChatService, BofoAiService, VerificationService
- `ui/` – all screens (Home, Explore, Saved, Detail, Chats, Chat, Profile, Owner Dashboard/Listings/Form/Verification, Admin Queue/Reports), floating `BofoPanel`
- `resources/.../bofo.css` – the whole theme (50/30/20) via named colors on `.root`
- `supabase/` – schema + RLS + RPCs + storage policies, and the `bofo-chat` Edge Function

## Map decision
- **Gluon Maps (used here)**: native JavaFX control, works in GluonFX native-image, supports custom layers (purple pins, college circle, route line). Uses OSM tiles (keep the attribution). Routing: compute straight-line distance now; for real travel time call a routing API from an Edge Function.
- **Google Maps JS in a WebView**: best map quality, but JavaFX WebView is not reliably available on Android with GluonFX. Spike this first if you want Google tiles.
- Google's terms also forbid showing Directions/Distance Matrix results on a non-Google map, so mixing them with Gluon Maps is not allowed. To use Google routes, use Google's map too.

## Known gaps you must finish / verify (I could not compile or run this offline)
1. **Android OAuth deep link**: the manifest intent-filter is included, but delivering the `bofo://auth-callback?code=…` URI into Java (`AuthService.handleDeepLink`) needs a small native Attach-style bridge. Desktop works through the loopback server.
2. **Session key** is a private-sandbox file; swap for Android Keystore via a native service for hardware-backed protection.
3. **Chat** polls every 3 s. To use Supabase Realtime, subscribe to `postgres_changes` on `messages` over websocket and call the same UI callback.
4. Verify Attach API names on your versions (`PositionService.start()`, `PicturesService.takePhoto/loadImageFromGallery`) and Gluon Maps `centerLat()/centerLon()` (`MapPane.center()`).
5. No push notifications yet (needs FCM + a native service). Policies/Terms URLs are stubs.
6. Run Supabase advisors, test RLS with two users, and add the real mascot PNG (the 🏠 emoji is a placeholder).
