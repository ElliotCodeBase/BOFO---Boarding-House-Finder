# v2.0.29 - Android port, Step 7: chips, section header, rent card, list states (JavaFX app stays v1.20.0)
- **Chips:** New / Hot / Best rated as pills (selected = ink fill, white text) instead of the tab strip.
- **Section header:** bold "Nearby boarding houses" + subtitle: "Closest to you first - 7 places" (Near me), "Within 3.5 km of <campus> - 4 places" (follows the radius pill), "Choose a campus to see what is close" (nothing chosen).
- **Rent card:** white, 18 dp corners, #ECE8FA border. With a campus / Near me: Lowest / Middle (Best rated on that tab) / Highest, bold value + small "/mo", a thin line with one marker per price at its real position. Tap a value or a marker = that house's circle glows on the map and the value turns red; tap again = off. No campus: "Choose a campus to see rent near it" + purple bold "Choose" (opens the dropdown). Reuses `PriceRange`.
- **List:** cards unchanged (`item_bh_card`, 2 columns); batches of 15 with a full-width "Show more (n)" button; skeleton while loading; empty state with the mascot and "Nothing nearby yet".
- New: `RentCardView`, `bg_chip_on`, `bg_chip_off`, `item_show_more.xml`, `strings_home7.xml`. Changed: `HomeFragment`, `HomeSearchBar` (openDropdown), `ListArea` (footer), `fragment_home.xml` (tabs + price_range removed), `strings.xml` (home_empty text), `build.gradle` (2.0.29 / 229).
- Not built or run by Claude (parse-checked only). Profile > About shows 2.0.29.

# v2.0.28 - Android port, Step 6: pins, clusters, campus dots, circle label, preview card (JavaFX app stays v1.20.0)
- **Pins:** zoomed out (below 12.5) small purple dots; dots closer than 30 dp merge into numbered bubbles (tap = glide to a view of the whole group); zoomed in, full pins with the house name under them, labels that would collide are hidden (the selected one always shows). The selected pin grows and turns dark purple. Merging/unmerging is recomputed when the camera stops moving.
- **Campus dots:** every campus is a small orange dot (names from zoom 11); tap = choose that campus. The chosen campus keeps its bigger dot.
- **Radius circle:** "500 m" / "4.5 km" chip on its east edge.
- **Preview card:** house-icon square, name, bold purple "P1,550/mo", star + rating (+ distance), X closes, > opens the place. Tapping a pin glides the camera to it.
- **Price glow:** `MapCardView.setGlow(id)` lights one house's circle (pink #F43F5E); Step 7's rent card will call it.
- The map style has no fonts, so all map text (names, bubble numbers, edge label) is drawn as bitmaps and cached per text.
- Selecting a pin does NOT draw the route yet (Step 8). Red destination pin: Step 8.
- New: `bg_peek_icon`, `strings_pins.xml`. Changed: `MapCardView`, `HomeFragment` (campusTapped, setColleges), `view_map_card.xml`, `build.gradle` (2.0.28 / 228). Not built or run by Claude (parse-checked only). Profile > About shows 2.0.28.

# v2.0.27 - Android port, Step 5: radar dot, location states, overview (JavaFX app stays v1.20.0)
- **Radar dot (LiveDot):** blue (#2F6FED) core in a white ring with a soft shadow; TWO staggered ripple rings grow 0.5 -> 2.7x and fade linearly, looping (2.4 s, ~30 fps, paused when the map is off screen, in the background or with animations off); a soft view cone points the way you are moving (shown after the phone has moved ~8 m); the first fix grows in from 82 %; Locate landing = one bigger "found you" ring + a small pop.
- **Glass pill (top-left):** "Finding your location...", "Your location", "Approximate location" (only coarse permission), "Location is off". Hidden while the radius pill is shown (campus or Near me chosen). The Locate icon pulses while finding; extra taps are ignored.
- **Overview** (no campus / Near me and the search is empty): the map zooms out to fit every pin and you in ~90 % of the card; the Locate button is purple while it is on. Tap = fly to me (overview off); tap again = back to the overview. Clearing the search or the campus brings it back; your first fix joins the overview once.
- New: `bg_glass_pill`, `bg_map_button_on`, `strings_radar.xml`. Changed: `MapCardView`, `HomeFragment`, `HomeSearchBar` (searchChanged hook, isEmpty), `LocationHelper.precise()`, `view_map_card.xml`, `build.gradle` (2.0.27 / 227).
- Not built or run by Claude (parse-checked only). Profile > About shows 2.0.27.

# v2.0.26 - Android port, "Turn on location?" popup (JavaFX app stays v1.20.0)
- Tapping Locate or Near me while the phone's Location switch is off now shows Google's system popup ("For a better experience, turn on device location"); Yes switches it on and the position is read straight away, No shows the "location is turned off" message. The live dot never asks by itself.
- Needs Google Play services (phones and Play emulator images); without them the old message shows. Not built or run by Claude. Profile > About shows 2.0.26.

# v2.0.25 - Android port, current location fix (JavaFX app stays v1.20.0)
- If Google's fused location returns nothing (no Play services, emulator with no mock position, first fix slow), the app now falls back to Android's own LocationManager: last known fix from any provider, else one fresh fix (12 s). Failures are logged with tag `BOFO`.
- New toast when the phone's Location switch is off: "Location is turned off on your phone..." instead of the generic failure message.
- Not built or run by Claude. Profile > About shows 2.0.25.

# v2.0.24 - Android port, map speed: do what the JavaFX map does (JavaFX app stays v1.20.0)
- Why JavaFX is smooth: it paints each OpenFreeMap tile ONCE into a 512 px picture (only land, parks, water, buildings, roads; no labels, no blending), caches it, then pans/zooms just move pictures; zoomed out the pins are small dots, zoom 15+ reuses the zoom 14 tile, and camera moves avoid drawing every zoom level on the way. The Android map re-draws vector geometry every frame at full screen density.
- Now: map capped at 2x pixel density (3x phones drew 2.25x the pixels); style trimmed (no opacity blending, no minor-road outlines, no fade transition); zoom limited to 6-17 like JavaFX; tile prefetch delta 4 -> 1; zoomed out (below 12.5) pins are small dots, full pins only when zoomed in; pins/selection redraw only the pins layer; the map starts 300 ms after the page, not in the same frame as the list.
- Not built or run by Claude. Profile > About shows 2.0.24.

# v2.0.23 - Android port, map lag fix (JavaFX app stays v1.20.0)
- The Home map was slow: texture mode (added in v2.0.22 for the rounded corners) is back to the default fast surface; the rounded corners are drawn by a new `CornerMaskView` on top. Dragging the radius pill now only redraws the circle (not every layer) and moves the camera at most ~6 times a second, with one final move when you let go. Live location and campus dot also update only their own layer.
- Not built or run by Claude. Profile > About shows 2.0.23.

# v2.0.22 - Android port, revert Step 4: inline map card (JavaFX app stays v1.20.0)
- Home is now ONE scrolling page: offline strip, greeting, search, rounded map card, New / Hot / Best rated, rent line, title and the card grid all scroll together (CoordinatorLayout + AppBarLayout; the list recycles as before; pull-to-refresh only at the very top).
- Removed from Home: the List / Map toggle, the full-screen map button and the old radius slider row.
- Map card: 28 dp corners, thin outline, height 42% of the screen (200-440 dp), round white locate button bottom-right, attribution as a small pill bottom-left, radius pill top-centre (Radius, purple slider, chip like "1.0 km"; 0.5 to 5 km, step 0.5). Dragging the pill moves the circle and eases the zoom live; the list reloads 350 ms after it settles.
- The map no longer re-zooms when only the radius changes; it fits when the centre changes (first load, campus picked, Near me).
- MapView now uses texture mode so it clips to the rounded card and the search dropdown floats above it.
- Not built or run by Claude. Profile > About shows 2.0.22.

# v2.0.18 - Android port, login errors are now visible (JavaFX app stays v1.20.0)
- Google sign-in on a real phone returned to the login screen with no message: the error text sits below the buttons, hidden behind the keyboard. Now any login message closes the keyboard and scrolls to it, and failures are also written to Logcat (tag `BOFO`).
- No Supabase, library or other code change. Profile > About and Settings show 2.0.18.

# v2.0.17 - Android port, first successful build (JavaFX app stays v1.20.0)
- First time the Android code was compiled in Android Studio / Gradle: `testDebugUnitTest` passes (BUILD SUCCESSFUL).
- Fixed 3 build errors: duplicate string `cd_mascot` (second one renamed `cd_mascot_ask`, used by `MascotView`); style `Widget.Form.Input` had no parent and Android looked for `Widget.Form` (now `parent=""`); `LoginFragment` caught `GoogleIdTokenParsingException`, which the current Google ID library no longer throws (now `catch (Exception e)`).
- No feature, Supabase or library change. Profile > About and Settings show 2.0.17.

# v2.0.16 - Android port, Stage 9 (release prep; JavaFX app stays v1.20.0)
- Release builds now use R8 (code shrinking + obfuscation) and resource shrinking, with rules in `proguard-rules.pro` (readable crash lines, mascot animation, Fragments kept).
- Release signing from `bofo-android/keystore.properties` (git-ignored; template included) or from environment variables; without a key the release APK is built unsigned.
- New optional GitHub workflow `android-gradle.yml` (tests + debug APK on push; manual run builds the signed release APK + AAB). The old GluonFX workflow is untouched.
- New `bofo-android/docs/RELEASE.md`: how to make a key, build, add the release SHA-1 to Google sign-in, fix R8 problems, and what is still missing before a public release (Boost checkout function, `bofo-chat`, legal text review).
- No app code change, no Supabase change, no new library or test. Profile > About and Settings show 2.0.16. Not built in Android Studio / Gradle by Claude; the R8 rules and the workflow were never run.

# v2.0.15 - Android port, Stage 8h (admin shell; JavaFX app stays v1.20.0)
- Admins now get their own bottom bar: **Queue**, **Reports**, **Payments**, **Profile** (no Ask BOFO button). The role is read when you sign in.
- **Queue:** owner verification requests waiting for review, oldest first. Tap one: ID front, ID back and selfie (private bucket, links valid 2 minutes), then **Approve** (asks first) or **Reject** (a reason is required). The decision uses the existing admin-only `review_verification`. The photos are never kept in the app's image cache and the screen blocks screenshots while it is open.
- **Reports:** the latest 100 reports and blocks (who, about whom, reason, date). Tap one: details and **Take down a place** of the reported user (pick the place, type a reason; uses `admin_takedown`, which also writes the audit log). The database has no "resolved" state for reports, so none is shown.
- **Payments:** the latest 100 boost payments of all owners. Tap one for details; a **Paid** payment can be **Refunded** (reason required; `admin_refund_boost` marks it refunded and ends the boost). **The refund only changes your records: return the money in the PayMongo dashboard.**
- No Supabase change (read-only checks of `owner_verifications`, `blocks_reports`, `users`, `bh_cards`, the three admin functions and their RLS). 4 new unit tests (`AdminRulesTest`, total 122). No new libraries. Profile > About and Settings show 2.0.15. Not built in Android Studio / Gradle by Claude.

# v2.0.14 - Android port, Stage 8g (Boost + Payments; JavaFX app stays v1.20.0)
- **My places > Boost a place** (and **Boost this place** in a place's menu): choose one of your active places and a plan (Starter / Weekly / Monthly, read from `boost_plans`), tap **Pay with GCash**. The payment page opens in a Custom Tab; when you come back the app checks the payment until it says Paid / Failed / Expired (about 2 minutes, then **Check again**).
- **My places > Payments**: your last 30 boost payments (amount, status, place, date); tap one for the reference number.
- **Important - backend not ready:** the live Supabase project has NO `create-boost-checkout` Edge Function (only `delete-account`), so **Pay with GCash will say "Boost payments are not available yet" until that function is deployed** (with the PayMongo secret and a webhook that calls `activate_boost`). Nothing is charged in that case. Claude did not deploy it: it needs your PayMongo keys and cannot be tested from here.
- Safety: the payment link is opened only when it is a plain `https://` link with a real host. Prices come from the server (`boost_plans`), the app never sends an amount.
- New library: `androidx.browser:browser:1.8.0` (Custom Tabs). 6 new unit tests (`BoostRulesTest`, total 118). No Supabase change (read-only checks only). Profile > About and Settings show 2.0.14. Not built in Android Studio / Gradle by Claude.

# v2.0.13 - Android port, Stage 8f (owner dashboard; JavaFX app stays v1.20.0)
- **My places > Dashboard** button: totals over all your places (views, saves, chats, average rating, reviews, free beds) and one card per place, most viewed first, with a star on boosted places. Pull down to refresh.
- The numbers come from the `bh_activity` table (one row per place, kept by your database); boosted places from `boosts` (if that cannot be read the stars are just missing). The desktop `DashboardView` was not available, so the screen follows the database, not the desktop layout.
- 3 new unit tests (`DashboardRulesTest`, total 112). No new libraries. No Supabase change (read-only check of `bh_activity` columns and its read policy).
- Profile > About and Settings show 2.0.13. Not built in Android Studio / Gradle by Claude.

# v2.0.12 - Android port, Stage 8e (edit a place; JavaFX app stays v1.20.0)
- **My places > tap a place > Edit**: the Add-a-place form opens filled with that place (name, address, payment and mode, free water / electricity / Wi-Fi, rooms, contact, location). **Save changes** updates the listing, replaces the rooms and the contact, and adds any NEW photos after the existing ones (8 photos in total at most).
- Not in this version: removing or re-ordering photos, amenities (your Supabase project has no `amenities` / `bh_amenities` tables, so the amenity chips are hidden when editing; Add a place shows none either), the owner Dashboard (stats), map point picker.
- Saving needs a verified owner (database rule). If a save stops halfway the message says which step failed; press Save again (rooms are replaced, so run it to the end).
- No new libraries. No Supabase change (read-only check of RLS on `boarding_houses`, `rooms`, `bh_contacts`, `photos`: the owner may write them). No new unit tests. Profile > About and Settings show 2.0.12. Not built in Android Studio / Gradle by Claude.

# v2.0.11 - Android port, Stage 8d (owner verification; JavaFX app stays v1.20.0)
- **Verification screen** (My places > the banner's **Verification** button, or **Add a place** while not verified): shows where you stand (Not verified / Waiting for review / Verified / Not approved, with the admin's reason) and, when you may send a request, the form: type of ID, ID front, ID back (camera or gallery), a live selfie (camera only), a tick that you agree, **Send for review**.
- Photos are shrunk to 1600 px JPEG on the phone and sent to the private `id-verification` bucket (JPEG, 5 MB; checked in your Supabase). A quick blur check only warns "looks blurry"; it never blocks. After sending, the photos are wiped from memory and the camera copies from the cache.
- **My places** now shows a banner while you are not verified; **Add a place** sends an unverified owner to the Verification screen (a waiting request just says to wait). If the status cannot be read (offline), Add a place still opens and the database decides.
- `VerificationService.submit` now names and types each upload from its bytes (`.jpg` / `image/jpeg`, PNG still works). New `util/VerificationRules` (pure).
- 4 new unit tests (`VerificationRulesTest`, total 109). No new libraries. No Supabase change (read-only check: bucket, RLS policies, `owner_verifications`, `review_verification`; the admin side comes with the admin shell).
- Profile > About and Settings show 2.0.11. Not built in Android Studio / Gradle by Claude.

# v2.0.10 - Android port, Stage 8c (add a place; JavaFX app stays v1.20.0)
- **My places > Add a place**: form with name, address, monthly payment (per bed or per room), rooms (beds and taken), free water / electricity / Wi-Fi, amenities, contact (phone, Facebook, e-mail), location and up to 8 photos. **Publish** checks everything first and lists what is missing; if saving fails halfway the half-made place is removed.
- Location: type latitude / longitude or tap **Use my location** while standing at the place (a map picker comes later). Photos are shrunk to 1600 px JPEG on the phone and uploaded as JPEG (the bucket allows PNG and JPEG, 5 MB each; checked in your Supabase).
- Publishing is refused by the database for owners who are not verified (Verification screen comes later), the form shows the reason.
- 3 new unit tests (`ListingRulesTest`, total 105). No new libraries. No Supabase change. Editing an existing place is not ported yet.
- Profile > About and Settings show 2.0.10. Not built in Android Studio / Gradle by Claude.

# v2.0.9 - Android port, Stage 8b (owner tabs + My places; JavaFX app stays v1.20.0)
- Owners now get their own bottom bar: **My places**, **Chats**, **Profile** (the student tabs are only for students). The role is read when you sign in.
- **My places**: your listings with their status (Active / Unlisted / Taken down). Tap a place: View, Unlist (hide from students) / List again, Delete (asks first). A place taken down by an admin cannot be re-listed by the owner.
- Not yet: creating / editing a place, dashboard, boost, payments, verification, admin screens (8c and later).
- Checked in your Supabase project (read-only): `boarding_houses.status` allows `active`, `unlisted`, `taken_down`; `inbox_v` columns match the chat code. No Supabase change, no new library, no new unit tests.
- Profile > About and Settings show 2.0.9. Not built in Android Studio / Gradle by Claude.

# v2.0.8 - Android port, Stage 8a (BOFO assistant + mascot; JavaFX app stays v1.20.0)
- A small BOFO mascot button floats above the bottom bar on every tab. Tap it: **Ask BOFO** sheet. Your question goes to the `bofo-chat` Edge Function; if you are offline or it refuses, the built-in FAQ answers. The mascot tilts its head while thinking. The talk is not saved.
- `MascotView` (moods: sitting, thinking, running in circles, sleeping; only thinking / sitting are used so far).
- Not done yet: the search filters the assistant can return (`Answer.filters`) are ignored; owner / admin shells are still next (8b).
- No new libraries. No Supabase change. Profile > About and Settings show 2.0.8. Not built in Android Studio / Gradle by Claude; no new unit tests (UI only).

# v2.0.7 - Android port, Stage 7 (chat; JavaFX app stays v1.20.0)
- **Chats** tab (was a placeholder): your conversations, newest first, with the other person's name, the place, the last message, the time and an unread badge. Pull down to refresh, tap to open, long-press to delete a chat from your list.
- **Chat screen**: bubbles (yours on the right), one tick = sent, two blue ticks = read, 30 messages at a time (older ones load when you scroll to the top), long-press a message to copy it or delete it (for me, or for everyone if it is yours). New messages arrive by polling: every 3 s, slower when quiet (never more than 12 s), and only while the chat is on screen.
- **Offline-safe sending**: every message is saved on the phone first (Outbox) and shows a clock; it is sent by itself when the connection returns, even if you left the chat or closed the app. A message the server refuses stays on screen in red: tap it to send again.
- Place screen: **Chat with owner** now works (opens or creates the one conversation for you + this place). On your own place the button is off.
- 9 new unit tests (102 in total). No new libraries. No Supabase change.
- Profile > About and Settings show 2.0.7. Not opened or built in Android Studio / Gradle by Claude.

# v2.0.6 - Android port, Stage 6 (directions + trip planning; JavaFX app stays v1.20.0)
- Place screen: new **Directions** card. Tabs Me -> BH, BH -> Campus, Me -> Campus; a small map with the route line (dashed when it is only an estimate); the ways to go (walk, tricycle, jeepney lines, bus or van when far) with time and fare, the best one marked; More info; Open in OpenStreetMap.
- Trip planner ported from the desktop app (same fares: LTFRB order of 19 March 2026, same limits): roads from OSRM then Valhalla, jeepney lines from OpenStreetMap (Overpass, saved on the phone for 14 days, an old copy is used offline), hand-made lines from `assets/local-transit.json` (empty for now). Never waits more than 12 seconds: then it shows an estimate.
- Location is never asked when the screen opens: if it was already allowed the card follows you (plans again after about 80 m), otherwise the **Use my location** button asks.
- Fixed a Stage 5 compile error in `MapCardView` (view binding inflate call).
- 23 new unit tests (93 in total), including the desktop `TripLogicCheck` as JUnit. No new libraries. No Supabase change.
- Profile > About and Settings show 2.0.6. Not opened or built in Android Studio / Gradle by Claude.

# v2.0.5 - Android port, Stage 5 (map + location; JavaFX app stays v1.20.0)
- Map (MapLibre Native, OpenFreeMap vector tiles, BOFO colours, no labels / POI icons): purple pins (amber star for featured places), dashed radius ring, campus dot, live "you are here" dot, tap a pin for a peek card (price, rating, distance) and View.
- Home: List / Map toggle. The map shows the same places as the list (New / Hot / Best tabs, campus, radius) and has a locate button and an expand button.
- Full-screen map (`MapFragment`): campus picker, radius slider, locate button. Opened from Home (expand) and from a place's detail screen (**Show on map**: centres and selects that place).
- Location (`LocationHelper`, Play Services): permission asked only when you tap Near me or the locate button; honours Settings > Use my location (off = nothing is requested). New **Near me** chip on Home measures distance and radius from your phone instead of a campus.
- Map attribution always visible; MapLibre tile cache capped at 50 MB.
- New libraries: `org.maplibre.gl:android-sdk` 13.0.0, `play-services-location` 21.3.0. 7 new unit tests (`MapGeoTest`). No Supabase change.
- Profile > About and Settings show 2.0.5. Not opened or built in Android Studio / Gradle by Claude.

# v2.0.4 - Android port, Stage 4 (Home, Explore, Saved, place detail; JavaFX app stays v1.20.0)
- Home: campus button + nearest-college chips, New / Hot / Best rated tabs, Lowest / Middle / Highest rent row, radius slider around the chosen campus, card grid with skeleton loading, pull to refresh and an offline banner.
- Explore: search by name or area, Filters sheet (monthly price range + amenities with typo-tolerant search), sort tabs. Saved: the hearted places (works offline from the last copy). Heart on every card.
- Place detail: photo pager, price, rating, beds, free utilities, amenities, call / Facebook / e-mail buttons, reviews. "Chat with owner" is visible but disabled until Stage 7.
- Campus picker sheet saves the choice in `users.default_college_id`. Back from a place returns to the same tab.
- New libraries: recyclerview, swiperefreshlayout, viewpager2. 13 new unit tests (rent range, peso text, price filter, campus order, card texts). No Supabase change.
- Profile > About and Settings show 2.0.4. Not opened or built in Android Studio / Gradle by Claude.

# v2.0.3 - Android port, Stage 3 (login, roles, profile; JavaFX app stays v1.20.0)
- Sign-in: Google (native Credential Manager sheet, browser fallback through `bofo://auth-callback` with PKCE), e-mail code, e-mail + password. Session is restored on launch (splash decides login / role / profile setup / app) and refreshed automatically.
- Role chooser (student / owner) with agreement tick (saved in `user_agreements`), profile form with camera / gallery photo, Profile tab account card, Settings (edit profile, switch student / owner, location switch, terms / privacy / owner agreement, sign out, delete account).
- 9 new unit tests (sign-in link parsing, username / age / phone / e-mail rules). New libraries: Credential Manager + googleid, exifinterface, Glide. No Supabase change was needed.
- Profile > About and Settings show 2.0.3. Not opened or built in Android Studio / Gradle by Claude.

# v2.0.2 - Android port, Stage 2 (data layer; JavaFX app stays v1.20.0)
- Ported the models, the Supabase client (now OkHttp), session store (Android Keystore), offline cache + outbox, error text, geo / fuzzy / validate / PKCE helpers and the boarding-house, chat, boost, AI and verification services. Same class and method names as the desktop app.
- 41 unit tests (run on the JVM). Debug-only screen "Developer: data layer check" under Profile signs in and fetches boarding houses / colleges.
- Profile > About shows 2.0.2. Not opened or built in Android Studio / Gradle by Claude.

# v2.0.1 - Android port, Stage 1 (project skeleton; JavaFX app stays v1.20.0)
- New `bofo-android/` Gradle project (Java 17, Material 3): opens in Android Studio, splash with the mascot, bottom-nav shell (Home, Explore, Saved, Chats, Profile) with placeholder tabs, Profile > About shows the version from `BuildConfig.VERSION_NAME` (2.0.1, versionCode 201).
- BOFO purple theme, button and card styles ported from `bofo.css`; adaptive app icon from the mascot; edge-to-edge screens that move above the keyboard.
- Manifest keeps the `bofo://auth-callback` deep link; CAMERA / storage permissions are intentionally not declared (system camera + photo picker need none).
- Not opened or built in Android Studio by Claude (no Android SDK in the sandbox).

# v2.0.0 - Android port, Stage 0 (audit and plan; JavaFX app stays v1.20.0)
- New Android line (v2.0.N per stage). Added `ANDROID_PORT_PLAN.md` (class map JavaFX -> Android, risks, decisions), the `bofo-android/` folder layout (no Gradle yet), `HANDOFF_ANDROID.md`.
- The release rule for the Android line is now in `MAP_FEATURE_CHANGES.md` and `CLAUDE.md` (section 5). No app code, nothing built or run.

# v1.20.0 - Login inside the app + phone keyboard
- New sign-in screen that never opens a browser tab: type your email, get a 6-digit code, type it in the app (like the Claude app). Also email + password (sign in / create account) and Google.
- Friendly error messages (wrong password, expired code, too many tries, no internet). Resend code with a 45 s timer.
- Phones: the app lifts above the on-screen keyboard and scrolls the field you tap into view (all forms, chat input).
- Test-account buttons on the login screen only appear when `dev.*` accounts are in `config.local.properties`.
- Needs Supabase email templates with the code: `docs/EMAIL_LOGIN_SETUP.md`. App version 1.20.0 (versionCode 80).

# v1.19.15 - Android build: Gluon license step
- v1.19.14 got past the native compile, then `gluonfx:package` stopped asking for a Gluon license (https://gluonhq.com/activate). The workflow now runs `gluonhq/gluon-build-license@v1` with the repo secret `GLUON_LICENSE` before the build. Needs the secret (see HANDOFF.md). App version 1.19.15 (versionCode 79).

# v1.19.14 - Android build fix (APK now builds for real)
- Fix: the GitHub "Build Android APK" run showed green but built nothing. The native build stopped with `Error parsing resource configuration ... Invalid escape character` because the `resourcesList` patterns in `pom.xml` used `\.`. They now use `[.]`.
- Workflow: the build step now fails (red) when Maven fails (`set -o pipefail`), the build log is uploaded as its own artifact `BOFO-build-log`, and `BOFO-android` holds only the `.apk` (the run fails if none is made). Actions bumped to checkout@v5 / upload-artifact@v5 (Node 20 warning).
- `pom.xml`: `packageType` is now `apk` (an `.aab` cannot be installed on a phone directly). App version 1.19.14 (versionCode 78).

# v1.19.13 - Calmer Directions card
- Directions now shows the result, one short line and two small links. The long notes, the data credit and the extra explanations are behind a "More info" link. "Show BH -> Campus" and "Open in OpenStreetMap" are text links instead of a big button.

# v1.19.0 - Home: lowest / middle / highest rent in range
- Under "Nearby boarding houses" Home now shows **Lowest, Middle, Highest** monthly rent of the places in the radius (e.g. `Lowest P1,300/mo   Middle P1,550/mo   Highest P2,050/mo`). Plain text, no tiles.
- Follows the campus and radius slider (and "closest to you" when no campus is chosen). Middle = median; with an even count it is halfway between the two middle prices.
- 1 place, or all the same price: one "Rent" value. 2 places: Lowest + Highest only. 0 places or a load error: the row is hidden (never a dash).
- Same numbers on New, Hot and Best rated: "Best rated" only lists places with 3+ reviews, so that tab reads the range from the unfiltered list (one extra request).
- New `ui/PriceRange` (pure maths), `HomeView.showPriceRange / paintPriceRange / priceCell`. No CSS or Supabase change. Not compiled/run by Claude (no javac/Maven in the sandbox; brackets checked only).

# v1.18.0 - New live location marker + Home / map redesign
- **Blue dot redesigned** (new `ui/LiveDot`): glossy blue-indigo core in a white ring with a soft shadow, two staggered ripple rings, and a fading **view cone** that turns the way you move (replaces the tiny arrow). First fix settles in from 82% size; pressing the locate button ends with a "found you" ring + pop on the dot.
- **Home / map**: softer framed map (28 px corners, white frame), glass **Live / Finding you... / Approximate / Location off** chip on the map (hidden while a campus is chosen, the radius bar uses that spot), 46 px locate button with a clearer "on" state, lighter map colours (land, greens, water, buildings), press feedback on buttons, chips and cards, and a short staggered entrance on the first Home of a login only.
- Motion follows the ui-animation skill: only transform + opacity animate, enter curve `cubic-bezier(0.22,1,0.36,1)`, never from scale 0, interruptible, ripples pause when the map is off screen.

# v1.17.3 - PC: an old pinned spot can no longer hide your real location
- Fix: the spot you pinned by pressing and holding the map was saved on the PC and used on every start instead of your real location (easy to trigger by accident). It is now kept for the current session only, and turning "Use my location" off and on again clears it.
- The console now prints where each location came from, e.g. `[location] wifi service -> 9.37, 122.80` (sources: phone GPS, wifi service, windows, browser, pinned spot, dev.location).

# v1.17.2 - PC location from nearby Wi-Fi (like Google Maps)
- On a PC the app now scans the Wi-Fi networks around you (`netsh wlan`) and asks a Wi-Fi location service where those routers are, the way Chrome / Google Maps do. Usually 20-200 m instead of Windows' internet-address guess. The dot then keeps following you (checked every 15 s, moves under 25 m ignored).
- Service: free beacondb.net by default; for Google's own database set `geolocation.key=<Google Geolocation API key>` in `config.local.properties`.
- If the Wi-Fi service cannot place you (fewer than 2 networks, no coverage), the app falls back to Windows' location as before. Press and hold the map still pins an exact spot.

# v1.17.1 - PC: blue dot follows you live
- On a PC the blue dot used to be set once. Now, after the first Windows location fix, the app keeps Windows' location watcher open and moves the dot whenever the position changes by more than 10 m (checked every 3 s). It stops when "Use my location" is turned off or when you press and hold the map to pin your spot (a pinned spot is never moved).
- A PC has no GPS, so the dot is only as exact as Windows can make it (Wi-Fi). Turn on Windows Settings > Privacy & security > Location. Phones are unchanged (real GPS).

# v1.17.0 - Profile setup on first sign-up + region the map opens on
- New accounts get a "Set up your profile" screen right after choosing Student / Owner: profile picture (camera or gallery), username, mobile number, age, sex and region. Everything is optional ("Skip for now").
- All of it can be edited later in Profile > Personal information (age and sex now load back into the form; before they were saved but shown empty).
- The map (student Home, Explore, owner Dashboard) opens on the saved region every time; the round locate button still flies to you.
- Profile picture shows in the Profile header (Google photo until you change it). Usernames are unique (case-insensitive).
- Supabase: run `supabase/migrations/009_profile_setup.sql` (new columns, `username_available`, public `avatars` bucket). Redeploy the `delete-account` Edge Function (it now also clears avatars).

# v1.16.13 - Owner locate button works like the student one
- Fix: on the owner Dashboard map and the New/Edit listing map the app never asked for your location, so the blue dot was missing and the round locate button had to wait (or did nothing). They now ask once (same "Use your location" question as the student Home) and start tracking, so the button flies to you right away.

# v1.16.12 - Locate button no longer "sometimes does nothing"
- Fix: on a phone, a tap with a tiny finger wobble was cancelled by the button. It now fires on release with some slack, and dragging that starts on the button no longer switches off "follow me".
- Fix: the first time (Android permission dialog) the 12 s timer could run out and throw your tap away; if the position arrives late the map now still flies to you.
- Fix: an old timer could mark a newer location request as failed; only the newest tap waits (no duplicate flights); the pulsing icon stops after ~15 s at most.

# v1.16.11 - Locate button works smoothly
- Fix: tapping the round location button while you were far from the map no longer stutters: the camera flight and the blue dot's glide were both moving the map at once. The dot now waits until the flight is done.
- The button gives feedback: it shrinks slightly when pressed, and its icon pulses while the phone is still finding you.
- Tapping it again while it is still finding you is ignored (before, every tap added another flight and another "Finding your location..." message).

# v1.16.10 - Dashboard loading skeleton
- The owner Dashboard now shows a placeholder for the whole page while it loads: the three Overview tiles, the map and two listing cards pulse in grey, then are replaced by the real content (before, only the tiles had a placeholder and the map and listings popped in later, pushing the page down).

# v1.16.9 - Narrow-screen UI fixes (Profile, My listings)
- Fix: Profile rows no longer break words ("Verificatio / n") - the value (phone, Verified, Pending) is now a grey line under the title, and rows are tighter. "Verified" is green.
- Fix: My listings header showed "My ..." and "+ Add lis..." - the title now gets all free width and "Add listing" is a compact round "+" button.
- Fix: Boost / Unlist / Delete buttons were cut to "Bo... / Un... / Del..." - compact buttons that never clip their label, grouped with their own card. No favourite heart on your own listings.
- Profile: extra bottom space so Log out / Delete can scroll clear of the tab bar and the mascot.

# v1.16.8 - Owner map: dots + number bubbles again; locate button works
- Fix: on the owner Dashboard the pins turned into big full pins after the page was scrolled/pinched over the map (no dots, no number bubble). The map now always uses its real zoom.
- Fix: the round locate button did nothing when your position was already known but the GPS status was not "on". It now flies to your blue dot right away.

# v1.16.7 - Dashboard shows your listings; map names only when zoomed in
- Owner Dashboard now lists every listing you created (card list under the map).
- Campus names on the map only appear when zoomed in (zoom 14.5+), and never overlap.

# v1.16.6 - Preview 4 screen sizes at once (developers)
- New `dev/preview-sizes.ps1` opens the app in a small phone, a normal phone, a big phone and a tablet window side by side. Run: `powershell -ExecutionPolicy Bypass -File dev\preview-sizes.ps1`.
- `--size=WxH@X,Y` can now also set where the window opens.

# v1.16.5 - Map: number bubble really zooms in with one tap
- Fix: tapping a numbered bubble now zooms until its houses are apart (no new smaller bubbles), so one tap shows the pins.
- Houses packed very close together still take two taps: the first goes part way, the second finishes.

# v1.16.4 - Map: number bubble zooms in with one tap
- Tapping a numbered bubble now zooms straight to a view that shows all the boarding houses inside it (before it took up to 3 taps).
- If the houses are packed close together in a small area, it takes two taps: the first goes half way, the second finishes.

# v1.16.3 - Chat header follows the chat
- Fix: the chat header said "Boarding house owner" or "Student" from your account's current role, so it could be wrong (for example after Switch mode). It now follows who is in that chat. Quick replies and the empty-chat hint follow too.
- Testing on PC: a second window can use its own login. In PowerShell: `$env:BOFO_PROFILE="owner"; mvn javafx:run` (any name). Without it the app behaves as before.
- Includes v1.16.1 and v1.16.2 (pop-ups, action sheets, chat delete).

# v1.16.2 - Menus match the pop-ups
- Press-and-hold / right-click on a message, the three dots in a chat, and a chat card in Chats now open a rounded bottom sheet (Copy, Delete, Report / block) instead of the grey Windows-style menu. Tap outside or Esc closes it.
- Includes everything from v1.16.1 (chat delete fix and the new pop-ups).

# v1.16.1 - Chat delete works + new pop-ups
- Fix: "Could not delete the chat". The database part of chat delete had not been installed on Supabase; it is now. Deleting a chat or a message works.
- New look: every question / warning / error box in the app (delete chat, delete message, delete listing, delete account, report, refund, reject, location permission) is now one rounded in-app pop-up with an icon, soft text and pill buttons instead of the grey Windows-style window. Tap outside or press Esc to cancel.

# v1.16.0 - Delete messages and chats
- Press and hold (phone) or right-click (PC) a message: Copy, Delete for me, and for your own messages Delete for everyone. The other person's screen drops a message deleted for everyone within a few seconds.
- Press and hold / right-click a chat card in Chats: Delete chat. It leaves your list and its old messages are hidden for you; the other person keeps their copy. If they write again the chat comes back with only the new messages. The three dots inside a chat also have Delete chat.
- Works for students and owners. **Run `supabase/migrations/008_delete_chat.sql` once in Supabase before using it.**

# v1.15.5 - My listings: card with a photo no longer wider than the screen
- Cause: in a one-column list a card was sized with its own minimum width, which a loaded photo makes large, so it ran past the right edge. `CardGrid` now gives a card exactly the column width.

# v1.15.4 - My listings fits the screen
- Fix: the My listings cards were wider than the screen (photo cut off on the right) and the Boost / Unlist / Delete buttons covered the card text. Now the list is one column sized by the screen width, and the three buttons share the width equally.

# v1.15.3 - Cleanup + faster loading
- Removed code nobody called: `SpriteView`, 6 unused mascot PNGs bundled in the app (about 320 KB), `Geo.pathKm`, `Ui.menuRow`, `FilterSheet.isOpen`, `MapPane.onPinTap/currentCenter`, `Icons.forLabel`, `Switch.setOn`, `AppConfig.devBypass/devRole`, `AuthService.devForce/signedIn`, and the dead `dev.bypassLogin` / `dev.role` settings.
- Network: server answers are gzip-compressed (much less data on slow connections); reads give up after 15 s instead of 30 s; the main list and the featured list now load at the same time.
- Publishing a listing: rooms, amenities and photo rows are saved in one request each, and photos upload side by side. Photos are encoded smaller (1024 px wide, PNG Sub filter).
- Start-up: splash holds 1.2 s (was 2.2 s) and no longer starts its own thread.

# v1.15.2 - Camera and Gallery fix
- PC: Camera and Gallery open a file picker. Phone: errors are caught; storage/media permissions added to the manifest.

# v1.15.1 - New listing polish
- Blue map pin, mascot hidden on the form, slimmer bottom bar, soft card shadows.

# v1.15.0 - Blue Create button + step-by-step New listing
- Create in the bottom bar is a raised blue square.
- New listing is a 4-step form with progress, per-step checks and a review page.

# v1.14.2 - Cleaner two-column card

- The compact card now shows only: photo with heart, name, price and one short line ("★ 4.5 · 1.2 km" or just the distance).
- No tags, no Verified/Hot badges and no "beds left" pill on it (a "Full" pill still shows when there are no free beds). The full card (single column) is unchanged.

# v1.14.1 - Fix: cards in the 2-column list lost their styling

- Cause (most likely): `CardGrid` switched every card between normal and compact (style class, padding, photo height) while measuring and laying out, so the CSS never settled and the cards showed unstyled.
- Now compact is fixed when the card is created (`new BhCard(..., true)` for Home, Explore and Saved). `CardGrid` no longer touches the cards.

# v1.14.0 - Two-column list + ad slots

- Home, Explore and Saved show listings in 2 columns (`CardGrid(10, 150, 2)`). Cards in a row share the same height.
- Compact card in narrow columns: shorter photo, smaller text, "/mo", first two tags only (`BhCard.setCompact`).
- New `AdSlot`: a grid cell that takes the place of one card (or a full row). Off by default (`AdSlot.ENABLED = false`); Home and Explore already insert it at random positions (`AdSlot.spots`) once it is switched on.

# v1.13.2 - Home cleanup

- One location button (the one on the map); the duplicate next to the search bar is gone, so the search bar is full width and its text is no longer cut off.
- Map is shorter (230 px instead of 340) so the first listings show without scrolling.
- "6 places" moved into the list subtitle (the mascot covered the pill).
- Map credit is one small line instead of a two-line box.

# v1.13.1 - Top section: light and clean instead of the purple band

- Removed the gradient band. Every main tab now opens with a big dark title, a quiet subtitle, white round buttons and a white search bar with a soft shadow.

# v1.13.0 - Branded top section on every main tab

- New `Ui.pageHeader`: indigo gradient band with rounded bottom (same family as Profile), white title + subtitle, round translucent buttons.
- Home: avatar, greeting, bell and the single search bar + near-me button now sit together in the band.
- Explore: title, subtitle and search + filter button in the band. Saved and Chats use the band too; owner/admin tabs (Dashboard, My listings, Payments, Verification queue, Reports) too.

# v1.12.9 - Listing card redesign

- Price is now its own big indigo line under the name (no more squeezing the name next to the price).
- Rating and distance share one wrapping row ("No reviews yet" became "New listing").
- Verified / Featured / Hot badges come first, then at most 3 amenity tags.
- Card border tints on hover; slightly more spacing.

# v1.12.8 - Home screen polish

- Greeting follows the time of day (Good morning / afternoon / evening).
- The list title now has a subtitle ("Within 2.0 km of <campus>", "Closest to you first" or a hint to choose a campus) and a "12 places" count pill.
- Thin rounded outline around the map, slightly more even spacing between the map, the New / Hot / Best chips and the list.

# v1.12.7 - Thinking pose cleaned up

- The raised arm no longer sticks out of the body like a lump: it is now a short straight arm with the hand under the chin (still taps).

# v1.12.6 - Better sitting and sleeping poses

- Sitting and sleeping no longer squash the body into a flat slab. BOFO keeps its normal body and face, sits a little lower with the feet stretched out in front, and the arms rest at the sides. Sleeping: eyes shut, small breathing mouth, head slowly drooping to the side with the hat tipping, z z z floating up.

# v1.12.5 - Poses change with a hop instead of a fade

- Switching idle poses (or going back to normal after a tap) is now an animation: BOFO crouches, hops up, changes pose in the air, lands with a squash and settles (about 0.6 s). Replaces the fade of v1.12.4.

# v1.12.4 - Smooth change between idle poses

- Switching from one idle pose to the next (or back to normal after a tap) no longer snaps: the old pose keeps moving while it fades out (0.13 s), then the new pose fades in (0.2 s). A tap or question during a fade still wins at once.

# v1.12.3 - Fix: effects of one idle pose stayed on screen in the next

- The z z z (sleeping) and speed lines (running) were left on the mascot when the pose ended and showed up in the other poses. They are now cleared whenever a pose ends or a new one starts.

# v1.12.2 - Idle poses redrawn; the floating head no longer disturbs the UI

- **Real poses, not squashed copies:** Thinking = one hand on the chin (tapping) and a foot tapping. Sitting = plops down, body low, feet out in front swinging, arms resting, blinks and glances. Running = leans forward, arms pump, feet stride, speed lines. Sleeping = slumped sit, eyes shut, mouth breathing, head nods with the hat drooping, z z z.
- **Fixed:** the head used to resize and drift while animating (floating z's, the running circle, tilt changed the button size and bumped things around). The mascot now sits in a fixed 46 px box inside a fixed 56 px button, and running happens in place.

# v1.12.1 - Idle moods start sooner and are easier to see

- The chat head now starts a mood after 4-10 s of quiet (was 8-18 s) and rests 4-10 s between moods.
- Bigger movement on the small 46 px head: wider head tilt and eye movement when thinking, deeper sit with feet spread, bigger running circle, deeper sleep and larger z's.

# v1.12.0 - The floating BOFO head has 4 idle moods

- After 8-18 seconds of quiet the chat head plays one mood, then rests and picks another (never the same one twice in a row): **Thinking** (head tilts, eyes look around, thought bubble floats), **Sitting down** (sinks and spreads its feet, breathes, blinks), **Running in a circle** (happy face, runs a lap every 1.8 s, leans into the turn, feet take turns), **Sleeping** (eyes closed, head nodding, z z z floating up).
- A tap, a drag, opening the chat or asking BOFO something ends the mood at once. Nothing plays while the chat is open, while BOFO is answering, or when the mascot is hidden in Settings.

# v1.11.0 - Privacy Policy, Terms of Service and Agreements open inside the app

- Profile > Security and privacy: **Privacy Policy**, **Terms of Service** and **Agreements and Guidelines** now slide up as a reader over the app (no browser, works offline). Close with the X, the Close button, a tap outside, or Esc.
- The two links on the login screen (Terms of Service, Privacy Policy) open the same reader.
- The text is a draft written for how BOFO really works (Google sign-in, location, owner ID + selfie, chats, PayMongo boosts, Data Privacy Act 2012). Have a Philippine lawyer review it before public release.

# v1.9.13 - Chats offline: read every chat, write messages that send later

- **Read offline:** the newest messages of your 20 latest chats are now saved on the device automatically whenever the chat list loads (before, only chats you had opened). A chat with nothing saved yet no longer shows a dead "No internet" screen: you see a short note and the message box still works.
- **Write offline:** every message is saved in a queue on the device first. With no internet it shows as a faded bubble "Waiting for internet - sends automatically" and is sent in order by itself when the connection returns, even if you closed the app in between. Messages sent online show "Sending..." and turn normal as before.
- If the server refuses a queued message (for example you are blocked) the bubble says "Not sent (tap to remove)" instead of retrying forever.
- The saved copy of an open chat is refreshed after new or sent messages, so reopening it offline shows the latest.

# v1.9.12 - Always open the app when signed in, even with no internet

- Fixed: a signed-in user who started the app with no internet (and nothing saved yet) still got a dead "No internet connection" screen. Now the app always opens after a 4 s wait: with the saved profile if there is one, otherwise with a basic profile from the Google account (student view until the real role can be loaded). The offline banner shows and every section says "No internet connection" until the network is back.
- Only a user who never signed in on this device still needs internet (to sign in).

# v1.9.11 - Works without internet (saved chats, saved places, listings) + fewer false "no internet" screens

- **Opens offline:** the signed-in profile is now kept on the device. Starting the app with no internet (or a server that answers slower than 4 s) opens it straight into Home / Dashboard with the "No internet connection - showing saved data" banner instead of a dead "No internet" screen. Only the very first sign-in on a device needs internet.
- **Saved data on the device:** chat list, the newest messages of every chat you opened, the Saved tab, listing lists, each place you opened, and the campus list are stored and shown when offline. Older messages, sending, saving or removing a place, reviews and sign-in still say "No internet connection" when you try them.
- **Back online:** screens that showed saved data reload by themselves when the connection returns.
- **False "no internet":** a read request now retries once before the app flips to offline (one dropped connection no longer kills the whole app); connect timeout 8 s -> 12 s.
- **Real reason on error screens:** a small grey line under "No internet connection" / "Something went wrong" names the actual error (for example `UnknownHostException: xxx.supabase.co`), so a failure while you ARE online can be told apart (wrong URL, paused Supabase project, DNS, firewall).
- Sign-out / session expiry wipes the saved copies.

# v1.9.10 - Loading screen: hat pops off and pops back on

- The hat now has a proper toss: it sits on the head while the body launches, **pops off** just after the top of the jump (stretches upward, tilts 9 degrees, a white ring and five sparks burst from the head), hovers, falls back stretched, then **pops on** at the landing (squashes onto the head, rebounds, second burst) and settles. The second jump tilts the hat the other way.
- Hat tilt now pivots on the brim centre and the squash pivots on the underside of the brim, so it presses onto the head instead of wobbling around its own middle.
- The loop is 2.4 s now (two jumps + a short rest); the mascot recovers from the landing squash and crouches again so the restart is smooth.
- New `Mascot.fx()` overlay group (art-grid units) used for the sparks.

# v1.9.9 - Loading screen: hat no longer looks like a flying plate, visible shadow, livelier dots

- Fixed: when the hat came off it lifted far above the head and tilted 14 degrees, so the wide brim read as a flat tilted plate. It now lifts a short way (20 / 26 units) and tilts only 5 degrees (the second jump tilts the other way), so it still reads as a hat.
- Fixed: the ground shadow was black on the dark background and could not be seen. It is now soft light purple, a little wider, so the mascot looks grounded.
- The mascot is a bit bigger (172 px) with more room above it for the jump; the title, subtitle and status text are larger and brighter.
- The three loading dots now rise and fade in a wave (bigger, 10 px apart) and stop when the screen is removed (before they kept running).

# v1.9.8 - Loading screen: two jumps per cycle

- The mascot now does two jumps in a row before the loop restarts (about 2.2 s). The second jump is a bit higher and the hat is tossed higher; in both, the hat comes off at the top and lands back on with the feet. The shadow still touches the feet on landing.

# v1.9.7 - Loading screen: shadow under the feet, hat drops off at the top of the jump

- Fixed: the shadow did not touch the feet when the mascot landed. The feet now stand on a ground line with the shadow centred under them, and the squash / stretch pivots on the soles so the feet stay on the ground.
- New: at the top of the jump the hat comes off, floats and tilts, then drops back onto the head exactly as the feet land (with a small press).

# v1.9.6 - Owner chat shows the student's name

- Owner side: the chat header now shows the student's name instead of the boarding house name. Students still see the boarding house name. The Chats list is unchanged.

# v1.9.5 - New listing form redesign

- The form is now seven numbered cards (Basics, Pin location, Monthly payment, Rooms, Amenities, Photos, Contact), each with a short hint.
- Rooms: beds and "Occupied now" use clean - / + steppers; occupants can never be higher than beds; extra rooms can be removed.
- Price: "Per bed / Per room" is now a switch under the price field.
- Pin: the status under the map turns green once the pin is set (with distance from the nearest campus).
- Photos: added photos show as small previews with an x to remove them; the label says "No photos yet", "1 photo", "2 photos".
- The Publish button and a "x of 6 required details done" line stay at the bottom while you scroll; errors show in a red box above it.
- Removed the emoji from the buttons (they showed as black glyphs on Windows).

# v1.9.4 - Neater map credit

- The credit under the map ("OpenFreeMap (c) OpenMapTiles, data from OpenStreetMap") is now a small rounded pill with a soft border and shadow, in the app's purple-grey text colour, instead of a plain grey strip.
- It is wrapped so it never runs under the "find me" button on narrow phones, and sits slightly inside the map corner.
- The credit text itself is unchanged and still visible (required by the OpenStreetMap and OpenMapTiles licences). Edit its look in `bofo.css` (`.map-attr`).

# v1.9.3 - Home map overview + clearer map

- Home opens zoomed out showing every boarding house and your location; the location button switches it off (go to me) and back on, and is highlighted while it is on. Searching or choosing a campus turns it off.
- Clearer markers: purple dots, white numbered bubbles for groups, a red pin for the selected boarding house, orange campus dots.
- Faster map: tiles are drawn one at a time, no buildings or alleys, fewer outlines; smoother zoom when tapping a boarding house.

# v1.9.2 - Directions: BH â†’ Campus fix and new look

- Fixed: tapping "BH â†’ Campus" (or any tab) left the previous tab's line on the map while it said "Finding the best wayâ€¦". The old line is cleared at once and the new trip is drawn straight away (a guide line), then replaced by the real route.
- New look: From/To card with dots, a big summary (time, distance, fare), tidy notes with small symbols, softer buttons, and loading placeholders instead of plain text.

# v1.9.1 - Directions tabs

- The three tabs now read Me â†’ BH, Me â†’ Campus and BH â†’ Campus (current location to the boarding house, current location to the campus, boarding house to the campus). The labels fit on one line.

# v1.9.0 - Directions work offline, local routes, options on the map card

- Jeepney lines are saved on your phone after the first time, so directions still show them without internet (the card says when they come from an older copy).
- Local lines: the team can add jeepney / multicab routes by hand (with a fare someone checked) for places OpenStreetMap does not cover. See docs/LOCAL_TRANSIT.md.
- Tapping a pin on the Home or Explore map now shows the best way to get there (walk, tricycle, jeepney or bus/van) with time and fare, not just the walk.
- Directions with no internet are ready in about 3 seconds instead of 9.

# v1.8.0 - Better directions

- The Directions card now fits the screen (the tabs and buttons no longer run off the right edge).
- Shows the real ways to get there with time and fare: walk, tricycle, jeepney (from public jeepney route data) or bus/van when it is far.
- No more silly times like "1266 min walk": long times read "21 h 6 min", and a place far away is a bus/van trip, not a walk.
- Tells you when your location comes from a computer (it can be off by several km) and can show the route from the campus instead.

# v1.7.0 - New boarding house page

- Redesigned page: photo hero, price and status at the top, quick facts, and separate cards for amenities, rooms, location and reviews.
- Removed the Call button; only "Chat with owner" is left.

# v1.6.3 - Boarding house page opens again

- A place now opens even if its amenities cannot be loaded.

# v1.6.2 - Smarter amenity search, fixed search dropdown and List/Map switch

- Typing in the amenity filter suggests matching amenities (also similar words and typos), only ones a boarding house really has; clear message when none does.
- Campus dropdown on Explore/Home floats under the search bar and no longer gets cut off.
- List | Map switch no longer turns into "..." buttons.

# v1.6.0 - Amenities, one search bar on Home, new Explore filter

- New shared amenity catalog (20 starters + anything owners add). Owners pick or create amenities when adding a listing; students filter by them in Explore (popular chips + search any amenity). Cards, map card and detail page show them.
- Home has a single search bar (campus / name / area) instead of two.
- Explore: nicer search bar, quick-filter row, bottom-sheet filter (price, sort, amenities). Search matches name or address.
- Database: run `supabase/migrations/005_amenities.sql`.

# v1.2.1 - Sharp vector map (OpenFreeMap), own style (not run on screen yet)

- The map is now drawn inside the app from OpenFreeMap vector tiles (free, no key, commercial use allowed): `util/VectorTileRetriever` + `util/Mvt`. Lavender land, soft green parks, light-blue water, light buildings, white roads with a thin outline (main roads tinted brand purple). No place names, no shop icons. Rendered at 512 px per tile, so lines and edges stay crisp on phones.
- `map.renderer=vector|raster` in `config.properties` switches back to the old picture tiles; a tile that cannot be loaded falls back to the old tiles by itself. Works offline from the 30-day tile cache.
- Attribution under the map: "OpenFreeMap (c) OpenMapTiles, data from OpenStreetMap" (follows the renderer).
- No map API key is needed any more for the default map.

# v1.2 - Map style, "find me" location, in-app Google sign-in (not compiled: no JavaFX/Maven/Android SDK in the editing environment)

- **Plain map**: `util/MinimalTileRetriever.java` (registered in `META-INF/services/com.gluonhq.maps.tile.TileRetriever`) replaces the default OpenStreetMap tiles with CARTO "light, no labels": roads, water, parks and building shapes only; no place names, no shop / bus-stop icons. Tile URL and key are in `config.properties` (`map.tiles.url`, `map.tiles.key`). Tiles are cached on disk for 30 days and still used offline. Without a key (or if the server refuses it) it falls back to plain OpenStreetMap tiles. Free CARTO keys are for non-commercial use: switch `map.tiles.url` to a plan you may use commercially before selling Boost plans.
- **Location**: `util/Locator.java` asks for location permission when Home opens and keeps the newest fix in `State.gps`. Maps show a blue "you are here" dot and a round find-me button (bottom right). Home has a "Near me" chip (sorts by where you are instead of a college) and a hint with "Try again" when location is off or blocked. PC testing: `dev.location=10.3157,123.8854` in `config.local.properties`.
- **Google sign-in**: Android shows Google's account sheet inside the app (`service/NativeGoogle.java`, `src/android/java/.../GoogleSignInActivity.java`, `AuthService.exchangeIdToken`). Needs the setup in `docs/NATIVE_GOOGLE_SIGNIN.md`; until `google.webClientId` is set nothing changes. The PC still uses the browser (Google does not allow sign-in inside an app window), now with a Cancel button, a nicer "you're signed in" page and the app window returns to the front.

# Redesign v4 - flat, professional UI (not compiled: no JavaFX/Maven in the editing environment)

- `bofo.css` rewritten as one design system: radius scale (chips 999, buttons/inputs 12, cards 14), 1px lines instead of drop shadows, neutral grey-white canvas with purple as the single brand colour, 12/14/16/20/26 type scale.
- Native controls now styled: CheckBox, Slider, ComboBox (+ popup), ScrollBar, Tooltip, dialogs (`Ui.style(dialog)`).
- `Shell.java`: floating pill nav replaced by a full-width bottom tab bar (icon + label, tinted active tab). Content no longer sits under the bar.
- `BhCard.java`: sentence-case badges, "No reviews yet" instead of empty stars, house icon on photo placeholders.
- Back/more buttons share the `.icon-btn` class (no inline styles). Bottom paddings reduced to match the new tab bar.

# BOFO Finder â€” Redesign Changelog (purple / professional)

> Note: these changes were written without compiling (no Java/Maven in the editing environment). Run the app once and use the troubleshooting guide below if anything misbehaves.

## What I did

**1. Theme: new professional purple (`bofo.css`, fully rewritten)**
- New palette: primary `#5B34B8`, dark `#3F2285`, tint `#F0EBFA`, background `#FAF9FC`, border `#E7E2F1`.
- Removed the amber accent. Purple is now the only brand color; gold `-bofo-gold` is used for rating stars only.
- Flatter look: cards use a 1px border instead of drop shadows; radii tightened (buttons/inputs 10, cards 12).
- Tags/badges are small rounded labels; hover states added on buttons, cards and the Google button.
- Floating mascot button is now white with a thin border and a soft purple shadow.
- All old variable names are kept (`-bofo-accent` now equals primary), so no screen breaks.

**2. Loading screen: white animated mascot (`SplashView.java`, rewritten; `Mascot.java` extended)**
- Flat deep-purple background with a WHITE BOFO mascot that jumps in a loop (squash, stretch, airtime, landing) with a ground shadow that shrinks and fades.
- "BOFO" wordmark, subtitle, three pulsing dots and a status line.
- Built from JavaFX shapes and a `Timeline` (no image files). Stops animating when the screen is removed.
- `Mascot` has a new constructor: `new Mascot(Expression.HAPPY, 150, true)` gives the white version (white body, deep-purple face). The old 2-argument constructor still gives the purple one.
- Splash minimum display time raised from 1.2 s to 2.2 s (`Main.java`) so the jump is actually seen.

**3. Smaller polish**
- `Nav.java`: every real screen fades in (260 ms); toast styled via CSS class.
- `Shell.java`: offline banner restyled (lavender, no amber).
- `BhCard.java`: emojis replaced with clean text tags (Water, Power, Wi-Fi, FEATURED, HOT, VERIFIED OWNER).
- `BofoPanel.java`: emojis removed from chat greeting text.
- `MapPane.java` and `VerificationView.java`: purple colors updated to the new palette; route line is dark purple.
- Status bar color (`Main.java`) set to `#3F2285` to match the splash.

## How to access
- Desktop: `mvn javafx:run` â€” the splash appears first, then login (or home if a session exists).
- Android: `mvn -Pandroid gluonfx:build gluonfx:package` then `gluonfx:install gluonfx:nativerun`.
- Edit colors in one place: `src/main/resources/com/bofo/finder/bofo.css` (top `.root` block).
- Preview the splash alone: in `Main.start`, temporarily skip the `Async.run(...)` call and just keep `Nav.show(splash, false)`.

## If a problem occurs (quick fixes)
| Problem | Fix |
|---|---|
| Build error in `Mascot.java` ("variable might not have been initialized") | Make sure both branches of the `if (white)` block in the constructor assign all 7 colors. |
| `Mascot.circle(...)` error | The method now takes 4 arguments (`cx, cy, r, color`). Update any other call you added. |
| Splash is not purple / unstyled | Confirm `bofo.css` is on the classpath (`Main` adds it) and the root node has style class `splash`. Run `mvn clean javafx:run`. |
| Mascot does not jump | The animation starts when the splash joins a Scene. Check `Nav.show(splash, false)` is called after `stage.show()`. |
| Old amber / old colors still visible | Run `mvn clean` (stale resources), then search for hard-coded colors: `grep -rn "#FFB400\|#6C3FC5" src`. |
| Emojis/tags look cramped on a card | Adjust `.tag` padding or font size in `bofo.css`. |
| Splash feels too long or short | Change `2200` in `Main.java` (milliseconds). |
| Want the old look back | Restore `bofo.css`, `SplashView.java` from your git history (`git checkout -- <file>`). |

## Update: developer force-login
- `LoginView.java`: added "Developer: force login" with Student / Owner / Admin buttons. They skip Google sign-in and open the app with a fake profile.
- `AuthService.java`: added `devForce(Role)` which sets a local fake profile (no real session; Supabase calls use the anon key, so data protected by RLS may appear empty).
- Remove before release: delete the `devLbl`/`devRow` lines in `LoginView` and the `devForce` method in `AuthService`.

## Update: inside screens redesigned (user-friendly)
**Shared building blocks (`Ui.java`, `bofo.css`)**: friendly empty states (mascot + one sentence), section titles, card helper, stat cards, round initials avatars, on/off filter chips, sticky action bar, notice banners (ok / warning / problem). Bottom navigation now shows a purple indicator on the active tab.

**Screens**
- `HomeView`: purple greeting header with a one-tap college selector (replaces "Change college" button and the bell), map as a rounded block, radius slider with a clear label, tabs renamed New / Hot / Best rated, "Nearby boarding houses" heading, friendly empty state.
- `ExploreView`: search + tappable filter chips (Free water / electricity / Wi-Fi) instead of checkboxes, max price and sort on one row, empty state "No matches".
- `SavedView`, `ChatsView`: empty states; chat rows now have an avatar, owner name, listing name, last message, time and a purple unread count.
- `BhDetailView`: tags for inclusions and verified owner, "Rooms" and "Reviews" sections, and a sticky bottom bar with Call and Chat with owner always visible.
- `DashboardView`: three stat cards (Listings, Beds available, Avg rating) and a colored status banner.
- `ProfileView`: header with avatar, name and role; settings grouped into cards (Edit profile, Preferences, Account, Legal, Danger zone); Log out as its own button.
- All remaining emojis in these screens removed.

**If a problem occurs**
| Problem | Fix |
|---|---|
| Build error mentioning `Ui.Chip`, `Ui.emptyState`, `Ui.avatar`, `Ui.card` or `Ui.section` | `Ui.java` was not copied. Copy the new `Ui.java` and rebuild. |
| Layout looks unstyled | Copy the new `bofo.css` and press F5 in the app (or restart). |
| Want one old screen back | Copy it from the `_backup_before_inside` folder the update script creates. |

## Update: soft rounded redesign (inspired by the reference screens)
- **Floating icon navigation**: pill-shaped bar with vector icons; active tab is a filled purple circle; unread badge on Chats. New `Icons.java` (SVG icons, no images).
- **Login**: soft circle logo, "Welcome to BOFO", full-width purple pill Google button, "Developer: force login" row, terms links.
- **Home**: avatar + "Welcome back" + bell, rounded search bar (opens Explore), college chip, rounded map, radius card, big photo listing cards.
- **Listing cards (`BhCard`)**: large rounded photo, heart button on the photo, "N beds left" pill, name + price, distance/walk time, rating, tags.
- **Explore**: pill search with search button, filter chips (price, water, electricity, Wi-Fi), result count and sort.
- **Profile**: identity card plus grouped cards with icon rows and chevrons (Switch mode, Switch Google account, Terms, Privacy, Agreements, Log out).
- **Global style**: light top bars with round back buttons, 22px card radius, pill buttons and chips, very soft shadows. Emojis removed from remaining screens.

| Problem | Fix |
|---|---|
| Build error about `Icons` | `Icons.java` is new: make sure it was copied to `src/main/java/com/bofo/finder/ui/`. |
| Nav icons missing or squashed | Check `Icons.java` copied and run `mvn clean javafx:run`. |
| Listing photo not filling the card | Photos load online; blank until the image arrives. A lavender placeholder shows meanwhile. |
| Map corners not rounded | The map is clipped in `HomeView` (`mapWrap`). Remove the `setClip` line if the map renders oddly. |

## Bottom dock redesign (matches the reference picture)
- `Shell.java`: the dock is now one dark shape - pill + curved neck + round button (drawn as an SVG path, so it stays crisp at any size). Admin/Student have the pill only.
- Icons are outline when idle; the active icon is filled purple with a dot under it (`DockIcon.java`, outline paths in `Icons.java`).
- Pressing a tab: the dot glides over (and stretches slightly) and a circle pulse grows out of that tab. The round button gives the same pulse.
- Student: removed the round "+" (it only jumped to Explore, which has its own tab). Owner keeps it (add listing). To bring it back: `setAction(() -> select(1));` in `StudentShell`.

## Theme v5 "Dock" (app theme redesigned around the new navigation)
- `bofo.css`: palette taken from the dock - indigo `#5B3BF0` for actions/active state, ink `#17142A` (the dock colour) for selected chips, strong buttons, tooltips, toast and splash. Page background is a cool near-white `#F5F6FC`.
- Same shape language as the dock: buttons, chips, search and tooltips are pills; cards are 20px, inputs 16px, notices 16px. Top bar has no strip or line any more.
- Cards stay flat on purpose (smooth scrolling on phones); shadows only on floating things (dock, mascot, toast, primary button, popups).
- Hard-coded colours in `Ui`, `HomeView`, `MapPane`, `VerificationView`, `LoginView`, `BhCard`, `Mascot`, `Main` (status bar) updated to match. All colours still live in the `.root` block of the CSS.

## Responsive layout (phones, tablets, landscape)
- `Nav`: the app column is at most 840 px wide and centred; wider screens keep the page colour on the sides. Splash stays full-bleed.
- `Shell`: the dock re-fits to the screen width (tabs shrink on small phones, never overflow; stay compact on tablets). Tab buttons are sized in code now.
- `CardGrid` (new): listing lists show 1 column on phones, 2 on tablets/landscape (Home, Explore, Saved).
- `BhCard` / `Ui.coverPhoto`: photos crop to fill instead of stretching; taller on wide cards.
- Forms (Profile, Add listing, Verification, Boost) are at most 560 px wide on tablets. Login scrolls on short screens and is at most 440 px wide.
- Fixed pixel widths removed from Explore filters, Verification/Queue photos, chat bubbles and the BOFO mascot panel (now relative to the screen).
- Desktop window is resizable (min 320x480). Preview any device: `mvn javafx:run -Djavafx.args="--size=820x1180"`
  (phone small 320x640, phone 390x844, tablet portrait 820x1180, tablet landscape 1280x800, phone landscape 800x360).

## Loading skeletons + "No internet connection"
- `Skeleton.java` (new): placeholders shaped like the real content (listing cards, chat/list rows, stat tiles, detail page, chat bubbles). They pulse while loading and stop when off screen.
- Used on: Home, Explore, Saved, Chats, Owner dashboard + listings, Admin queue/payments/reports, listing detail, owner verification, boost plans, chat history.
- `Ui.load(...)` is the standard flow (skeleton -> load -> render, or error state with retry; stale answers ignored). `Ui.errorState(...)` shows "No internet connection" (or "Something went wrong") with Try again, and reloads by itself when the connection returns.
- `Connectivity.java` (new): the HTTP layer (`SupabaseClient.send`) reports success/failure, so the banner "No internet connection - showing saved data" and the error screens switch on from any screen. While offline a tiny probe (every 4 s) detects when the network is back. `State.offline` now points at it.
- Launch with no internet and no saved account: full-screen "No internet connection" (Try again / auto-continue) instead of splash text.
- Connect timeout 15 s -> 8 s so a dead network is detected sooner.

## Photo loading, chat send failures, favorite failures
- Photos (listing cards, detail photo strip): grey pulsing backdrop while the picture downloads; plain tint + icon if there is no picture or it failed. A failed picture reloads by itself when the internet returns (`Ui.trackLoading`, `Ui.photoLoading`, auto-retry in `Ui.photo` / `Ui.coverPhoto`; broken cached images are no longer reused).
- Chat: a sent message appears at once as "Sending...", then becomes a normal bubble. If it fails it stays in the chat with a red "No internet - tap to retry" / "Not sent - tap to retry" (blocked users get a clearer line). Network failures resend by themselves when the connection is back. Message ids are tracked so nothing shows twice.
- Favorites: the heart flips instantly and flips back if saving fails, with a toast saying why (`BhCard.saveFavorite`). `BhCard` now takes `(on, undo)` instead of `on`.
- Other failed actions (review, start chat, report, profile save/delete, unlist/delete, refund, approve/reject) use `Ui.failToast`: "No internet connection..." when offline, the old message otherwise.

## UI rework: tab bar (video 2), Explore, Settings (GCash-style)
- Tab bar (`Shell`, `DockIcon`, CSS): replaced the dark pill + round button with a white rounded bar. Each tab = outline icon + label. A lavender highlight sits behind the active tab and stretches to the pressed tab (leading edge first, trailing edge follows) while icon and label fade from pale to strong. Tab width adapts to the screen (54-84 px).
- Owner tabs are now Dashboard | Listings | Create | Chats | Profile. "Create" opens the add-listing form (the old round "+"). Verification moved into Profile > Verification (with a back arrow).
- Explore (`ExploreView`): one search bar with a search icon (searches as you type, clear button), a filter button with a count badge, quick amenity chips, a collapsible filter panel (max price + sort, Reset), result count and a List | Map switch. The map only loads when opened, so the list gets the whole screen.
- Profile / Settings (`ProfileView`, `PersonalInfoView`): gradient header (avatar, name, role / verification chips), then grouped white cards of icon rows with chevrons: Account (Personal information, Verification for owners, Switch mode), Preferences (switches), Security and privacy, About; outlined Log out button and a quiet Delete account link. Personal information is its own sub-screen.
- New: `Glyphs` (outline icon paths), `Ui.settingsRow / settingsToggle / settingsGroup / switchControl / segmented / backBar`, `MapPane.fillAvailable`, `Shell.pendingTab` (return to the Profile tab after a sub-screen).
