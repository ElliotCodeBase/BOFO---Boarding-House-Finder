# Handoff (Android) - v2.0.42 force-login buttons (2026-10-31)
- **PR title:** `v2.0.42 - Android: Student / Owner / Admin test-login buttons on the login screen (debug builds)`
- Sync Gradle, then run a DEBUG build from Android Studio. Buttons need `dev.student|owner|admin.email` / `.password` in `C:\Users\user\bofo-finder\src\main\resources\com\bofo\finder\config.local.properties` (git-ignored, the JavaFX file); `bofo-android/local.properties` can override them. No keys = no buttons.
- `AuthService.devSignIn(Role)` = `signInPassword` with the role's test account; `AppConfig.devEmail/devPassword/hasDevAccounts` read the debug-only `BuildConfig.DEV_*` fields. Release builds have them empty (never ship a test password).
- If a button shows an error: "Wrong email or password" = the test user changed in Supabase (Auth > Users); "No dev.<role>..." = key missing or Gradle not synced. Test accounts were created 2026-10-23 and not re-checked.

---

# Handoff (Android) - v2.0.41 OpenGL ES engine (2026-10-31)
- **PR title:** `v2.0.41 - Android: MapLibre OpenGL ES build instead of Vulkan`
- Sync Gradle after pulling. If STILL choppy: the user must report `map.mode=off` and `map.mode=raster` results (assets/config.properties) before more changes.

---

# Handoff (Android) - v2.0.40 lag test switch (2026-10-31)
- **PR title:** `v2.0.40 - Android: map.mode test switch (vector / raster / off)`
- Why: JavaFX paints each OpenFreeMap vector tile once into a 512 px bitmap (`VectorTileRetriever`) and then only slides bitmaps; Android MapLibre redraws the vector layers on the GPU every frame. Bisect on the realme 14 Pro+: `off` still laggy = not the map (look at Home scroll / SurfaceView in AppBar / list images); `raster` smooth but `vector` laggy = live vector drawing; both laggy = something else on the main thread.

---

# Handoff (Android) - v2.0.39 lag fix 5 (2026-10-31)
- **PR title:** `v2.0.39 - Android: cap map at 60 fps, ripples at 30 fps (120 Hz phones)`
- User phone: realme 14 Pro+ (Snapdragon 7s Gen 3, Adreno 710, 1272x2800, 120 Hz). Lag "everywhere, all the time" even after v2.0.37/38. If still laggy: measure (Developer options > Profile HWUI rendering > On screen as bars; or `adb shell dumpsys gfxinfo com.bofo.finder framestats`), A/B `android-sdk-opengl`, release vs debug build.

---

# Handoff (Android) - v2.0.38 lag fix 4 (2026-10-31)
- **PR title:** `v2.0.38 - Android: cheaper rounded-corner mask, smaller ripple repaint area`
- User: real mid-range+ phone, lag everywhere all the time (v2.0.37 did not fix it). Still open: A/B renderer (`android-sdk-opengl`, commented in `build.gradle`), debug vs release build, SurfaceView inside the scrolling AppBar page (texture mode was slower before), pixelRatio 1.5.

---

# Handoff (Android) - v2.0.37 lag fix 3 (2026-10-31)
- **PR title:** `v2.0.37 - Android: map idle between gestures (ripples on a Canvas overlay, no rebuild on pan)`
- `RadarOverlayView` (new) draws ripples + found ring; `MapCardView.refreshRadar()` places it (on camera idle, on a fix, on resize); `rebuildPlaces(force)` / `rebuildColleges(force)` skip when only the position changed; `paintRoute()` split out of `render()`.
- If STILL laggy: (1) ask phone model / emulator; (2) A/B the renderer: swap to `org.maplibre.gl:android-sdk-opengl:13.0.0` (commented line in `build.gradle`); (3) lower `pixelRatio` to 1.5 (scale the circle radii and `RadarOverlayView.unit`); (4) cap FPS.

---

# Handoff (Android) - v2.0.35 lag fix 2 (2026-10-31)
- **PR title:** `v2.0.35 - Android: lighter map drawing and debounced pin rebuild`
- `MapCardView.idleRebuild` (160 ms), `imgSize` cache, pixelRatio 1.75, max zoom 16. Style: building minzoom 15, road-minor 14. If STILL laggy: ask the phone model; next steps = cap FPS, replace the ripple GL layers with a View overlay, or lower pixelRatio to 1.5 (and scale the circle radii).

---

# Handoff (Android) - v2.0.34 lag fix (2026-10-31)
- **PR title:** `v2.0.34 - Android: smoother Home (ripples at 12 fps, paused while moving or scrolling)`
- `MapCardView`: `paintRipples()`, `camMoving` (set by camera move-started / idle), `RIPPLE_FRAME_MS = 80`, scroll detection via `getGlobalVisibleRect` top change. If still laggy: ask if it is an emulator (software GL: try Graphics = Hardware in AVD settings) and which action lags; next suspects are `rebuildPlaces` bitmap text and the CornerMaskView overlay.

---

# Handoff (Android) - v2.0.33 Step 11 final pass (2026-10-31)
- **PR title:** `v2.0.33 - Android: remove unused Ask-BOFO sheet and old strings (final pass)`
- Scan method: grep for `R.<type>.<name>` / `@<type>/<name>` / ViewBinding class in java + res. The `offline_banner` string is unused but kept (the id of the same name is used).
- Revert plan Steps 1-11 complete. Remaining work: build and run on a phone, compare with the original recordings, fix differences.

---

# Handoff (Android) - v2.0.32 Step 10 Detail, chat, chats (2026-10-31)
- **PR title:** `v2.0.32 - Android: Detail, Chat and Chats screens restyled (skeleton rows, quick replies, day chip, pinned chat button)`
- `ChatsFragment.showSkeleton` (900 ms pulse) replaces the ProgressBar (`loading` id is gone). `ChatFragment`: `quick1/quick2` call `send()`; quick bar shows only when the chat is empty; `Adapter.Holder` has a `day` chip computed from the previous item (`ChatRules.dayLabel`; queued messages have no date and never get a chip). `BhDetailFragment`: `info_chips`, `tile_*_value`, `no_photos`, `title_bar` + `chat_bar` are siblings of the ScrollView (back and heart moved there, same ids). Distance chip needs `bh.distanceKm() > 0` (the detail query may not set it).
- Known small gap: when older messages load above, the first old item's day chip is not recomputed until the next render.
- Unused now (delete in Step 11): strings `detail_stats`, `chat_hint`, `chat_empty`, `chats_empty`, `detail_verified` is still used.
- Next: Step 11 (final pass, list unused layouts/strings as DELETE).

---

# Handoff (Android) - v2.0.31 Step 9 Explore + Filters sheet (2026-10-31)
- **PR title:** `v2.0.31 - Android: Explore restyle (search pill, filter button, List | Map) and new Filters sheet`
- `BrowseState.sort` (0 Nearest, 1 Lowest price, 2 Most beds) and `amenityNames` (fallback picks) join `minPrice/maxPrice/amenityIds`; `activeFilters()` counts price, sort and every amenity. `ExploreRules` = sort + `withAll(names)` + `parseMax`. `ExploreFragment` always loads `Tab.NEW` then sorts client-side (featured pinning is kept only for Nearest). `FilterSheet`: sheet is 90% of the screen, expanded, adjustResize.
- Amenity error: no migration needed. If the chips are the built-in list on a phone that is online, run `supabase/migrations/005_amenities.sql` and check `amenities_v` + `get_or_create_amenity`; the fallback filters by name on the card data (needs `bh_cards.amenity_names`, same migration) plus the free water / electricity / Wi-Fi flags.
- Unused now (delete in Step 11): strings `filter_title`, `filter_price*`, `filter_amenities`, `filter_amenity_hint`, `filter_amenities_failed`, `filter_reset`, `filter_apply`, `filters_btn*`, `cd_price_range`; `home_tab_*` still used by Home/Saved.
- Next: Step 10 (Detail, chat, chats layouts; fix the blank Chats screen).

---

# Handoff (Android) - v2.0.30 Step 8 route on pin select (2026-10-31)
- **PR title:** `v2.0.30 - Android: route, red end pin and distance/time chip when a Home pin is selected`
- `MapCardView.Listener.placeSelected(id)` fires only for user taps (`userSelect`); `setRouteLabel`, `fitRouteBounds` (bottom padding = preview card height). `HomeFragment.showRoute/fetchRoute/drawRoute`, `routeToken` cancels stale answers, `me` = last phone fix.
- Open for Step 8 follow-up: compare route times with JavaFX on 2 samples (not done: no way to run both here); `DirectionsCardView` unchanged on purpose.
- Next: Step 9 (Explore restyle + filter sheet with price chips, Sort by, Amenities, built-in amenity fallback list; check `supabase/migrations/005_amenities.sql` and `BhService` amenity code).

---

# Handoff (Android) - v2.0.29 Step 7 chips, section header, rent card, list states (2026-10-31)
- **PR title:** `v2.0.29 - Android: Home chips, section subtitle, rent card with map glow, Show more`
- `RentCardView` (canvas): `setData(basis, best)` / `setNoCampus()`, `Listener.glow(id)` -> `MapCardView.setGlow`, `choose()` -> `HomeSearchBar.openDropdown()`. `HomeFragment`: `Result.basis`, `paintRent`, `paintSub`, `paintChips`, `renderList` (BATCH 15). `ListArea.setMore(text, action)` = ConcatAdapter footer (shared; Explore/Saved just never set it).
- Glow only works for houses in the displayed list (on Best rated the basis is the unfiltered list). `home_tab_*` strings are still used by Explore; `TabLayout` code in `ListArea.onTab` is still used there.
- Next: Step 8 (route + directions card; draw the route on pin select, red destination pin).

---

# Handoff (Android) - v2.0.28 Step 6 pins, clusters, campus dots, preview card (2026-10-31)
- **PR title:** `v2.0.28 - Android: pin clusters, labels, campus dots, radius label and preview card on the Home map`
- `MapCardView.rebuildPlaces()` (own clustering in screen px, per zoom, on camera idle) writes `places-src` with `kind` = dot | cluster | pin; labels in `labels-src`; campuses in `colleges-src` / `college-labels-src`; edge label `ringlabel-src`; glow `glow-src`. Text = bitmaps added with `imgs` cache (style has no glyphs). `Listener.campusTapped`. Layout: new `peek` card (`peek_price`, `peek_close`, `peek_open` is now an ImageButton).
- If it looks off: check cluster merge threshold (30 dp) and PIN_ZOOM (12.5); if labels lag on slow phones, limit rebuild to idle only (already) or cap names. Next: Step 7 (chips, section header, rent card, list states; call `setGlow`).

---

# Handoff (Android) - v2.0.27 Step 5 radar + location states + overview (2026-10-31)
- **PR title:** `v2.0.27 - Android: radar dot, location pill and overview mode on the Home map`
- `MapCardView`: user layers are now ripple1/ripple2/found/cone/shadow/ring/dot (all paint-only updates from `paintUser()`, ripples ~30 fps, skipped when `getGlobalVisibleRect` is empty); `setUser` computes heading; `setLocState(Loc)`, `setLocateActive`, `foundYou()`, `overview()`. `HomeFragment`: `ovOn/ovFit/overviewActive()`, `onLocateTap()`, `locateMe()` with FINDING/EXACT/APPROX/OFF. `HomeSearchBar.Host.searchChanged()`.
- Defaults I chose: status pill hidden while the radius pill shows; "approximate" = only ACCESS_COARSE_LOCATION granted; cone appears after ~8 m of movement.
- Check on the emulator if anything looks off: dot position vs. camera, pill text, ripple smoothness (if laggy, raise the 33 ms throttle in `startRipples`). Next: Step 6 (pins, clusters, campus dots, circle edge label, preview card).

---

# Handoff (Android) - v2.0.26 location-on popup (2026-10-31)
- **PR title:** `v2.0.26 - Android: "Turn on location?" popup when Location is off`
- `LocationHelper.ensureSettings()` (SettingsClient.checkLocationSettings + ResolvableApiException via a StartIntentSenderForResult launcher registered in the constructor). Used for requestOnce only.

---

# Handoff (Android) - v2.0.25 current location (2026-10-31)
- **PR title:** `v2.0.25 - Android: current location fallback (LocationManager) + clear "location is off" message`
- `LocationHelper.platform()` fallback, `Callback.servicesOff()` (default = failed()), Home overrides it. Emulator: set a position in Extended controls > Location. Real cause not confirmed: check which toast shows and `adb logcat -s BOFO`.

---

# Handoff (Android) - v2.0.24 map speed (2026-10-31)
- **PR title:** `v2.0.24 - Android: faster Home map (2x pixel cap, lighter style, dots when zoomed out, delayed start)`
- `MapCardView`: pixelRatio cap, zoom 6-17, prefetch delta 1, `places-dots` layer (maxzoom 12.5) + `places` symbols (minzoom 12.5), per-source updates. `bofo-map-style.json` trimmed. `HomeFragment` activates the map after 300 ms. If it is STILL laggy: ask whether it is an emulator (software GL) or a phone, and whether the lag is panning the map, dragging the radius pill or scrolling the page.

---

# Handoff (Android) - v2.0.23 map lag fix (2026-10-31)
- **PR title:** `v2.0.23 - Android: fix laggy Home map (no texture mode, per-layer updates, throttled radius glide)`
- `MapCardView`: default MapView again; `setRing/setCampusDot/setUser` update one source; `glideToRing(force)` throttled 160 ms. New `CornerMaskView` in `fragment_home.xml`. Step 5 is still next.

---

# Handoff (Android) - v2.0.22 revert Step 4, inline map card (2026-10-31)
- **PR title:** `v2.0.22 - Android: Home is one scrolling page with an inline map card and radius pill`

## Done
- `fragment_home.xml`: CoordinatorLayout; `AppBarLayout` (id app_bar, scroll flag) holds the whole header, the list include sits behind it with the scrolling-view behaviour. Map is in `map_frame` (MaterialCardView 28 dp, height set in HomeFragment).
- `MapCardView`: `setRadiusPill`, `glideToRing`, `Listener.radiusChanged`, texture-mode MapView, padded ring fit. `view_map_card.xml`: radius pill, locate bottom-right, attribution pill, peek card leaves room for the locate button.
- `HomeFragment`: no more map mode / toggle; radius debounce 350 ms; fits only when the centre changes. `HomeSearchBar.closeOnScroll()`.

## Next
Step 5 (radar dot, glass pill, overview/locate logic). See `REVERT_PROMPT.md`. Unused now: `home_view_list` / `home_view_map` strings (delete in Step 11).

---

# Handoff (Android) - v2.0.18 login errors visible (2026-10-31)
- **PR title:** `v2.0.18 - Android port: show login errors (scroll into view, hide keyboard, Logcat)`

## Done
- `LoginFragment.message()` now hides the keyboard and scrolls to the message; `fail()` and the Credential Manager `onError` log with tag `BOFO`.
- Found on the user's Realme phone (Android 16, API 36): Continue with Google shows the account chooser, then returns to the login screen with no visible error. Real cause not yet known: read the message now shown on screen or `adb logcat -d -s BOFO`.

## Next
Get the Google sign-in error text. Likely suspects: Supabase Google provider "Authorized Client IDs" does not include the web client ID, or the token request is rejected. Then `assembleDebug` / release as before.

---

# Handoff (Android) - v2.0.17 first successful build (2026-10-31)
- **PR title:** `v2.0.17 - Android port: first successful Gradle build (fix duplicate string, form style parent, Google sign-in catch)`

## Done
- Project opened in Android Studio, Gradle synced, `gradlew.bat testDebugUnitTest` = BUILD SUCCESSFUL. Fixes: `strings.xml` duplicate `cd_mascot` -> `cd_mascot_ask` (+ `MascotView.java`); `styles.xml` `Widget.Form.Input parent=""`; `LoginFragment.java` `catch (Exception e)`.
- Folder layout on the user's PC: repo `C:\Users\user\bofo-finder`, Android project `C:\Users\user\bofo-finder\bofo-android`. Claude's notes there are `CLAUDE_ANDROID.md` / `CHANGES_ANDROID.md` (the JavaFX `CLAUDE.md` / `CHANGES.md` stay separate).
- Network note: the user's Wi-Fi needed public DNS (1.1.1.1 / 8.8.8.8) for the first sync. Never commit the temporary local edit of `gradle-wrapper.properties`.

## Next
Run `gradlew.bat assembleDebug`, then run the app on an emulator / phone and send the first runtime problem. Then the open items in the v2.0.16 entry below.

## Not verified
- `assembleDebug`, running the app and the release build were not confirmed yet; Claude cannot build (no Android SDK).

---

# Handoff (Android) - v2.0.16 Stage 9: release prep (2026-10-30)
- **PR title:** `v2.0.16 - Android port: R8 release build, signing config, Gradle workflow, release guide (Stage 9)`

## Done
- `app/build.gradle`: release `minifyEnabled true` + `shrinkResources true`; `signingConfigs.release` only when a key is found (`bofo-android/keystore.properties` or env `BOFO_KEYSTORE_FILE`, `BOFO_KEYSTORE_PASSWORD`, `BOFO_KEY_ALIAS`, `BOFO_KEY_PASSWORD`); no key = unsigned release APK, no build failure. Version 2.0.16 / 216 (the stage-N = v2.0.N rule ended at 8h: versions are patch bumps now).
- `proguard-rules.pro`: keep `SourceFile` + `LineNumberTable`, `MascotView.setMascotAngle` (the only by-name reflection found: `ObjectAnimator.ofFloat(this, "mascotAngle", ...)`), Fragments / Sheets (safety net), `-dontwarn` for okhttp platform / conscrypt / bouncycastle / openjsse / javax.annotation / maplibre. Searched `java/` for `Class.forName`, `getMethod`, `getIdentifier`, `Serializable`, `Parcelable`, `@GlideModule`: none (models use `org.json` by hand).
- `keystore.properties.example`, `.gitignore` (+ `keystore.properties`), `.github/workflows/android-gradle.yml` (tests + debug APK on push / PR; manual run with the 4 secrets also builds signed APK + AAB + `mapping.txt`; checkout@v5, setup-java@v4 temurin 17, gradle/actions/setup-gradle@v4, upload-artifact@v5), `bofo-android/docs/RELEASE.md`, `STAGE9_CHANGELOG.md`.
- No app code, Supabase, library or test change (122 tests as before).

## Next
**Before anything else, BUILD** (stages 5 to 9 were all written without the Android SDK): open `bofo-android`, sync Gradle, `gradlew.bat testDebugUnitTest`, `gradlew.bat assembleDebug`, then `assembleRelease` and test the release APK on a phone. Send the first error. Then the open items: deploy `create-boost-checkout` (Boost payments cannot work without it; needs PayMongo secret names, I can write it) and `bofo-chat`; amenities tables (8e) or drop amenities; remove / reorder photos in Edit; map point picker; `Answer.filters`; unread badge; role switch in Profile; Verification entry in the owner Profile tab; admin ban / unban + a "resolved" state for reports (needs a migration); lawyer review of Privacy / Terms; Play Console listing and data-safety form.

## Not verified
- Nothing was built or run. R8 rules are written from knowledge of the libraries' consumer rules, not tested: a release-only crash is possible (fix table in `docs/RELEASE.md` section 5; escape = `minifyEnabled false`). MapLibre 13.0.0 (JNI) and Credential Manager under R8 are the likeliest suspects.
- The workflow YAML was only read, never run on GitHub. `gradlew` must be executable in the repo (`git update-index --chmod=+x bofo-android/gradlew` if the runner says permission denied).
- Google sign-in in the release build needs the release key's SHA-1 in the Google Cloud Android OAuth client (see `docs/RELEASE.md` section 4).

The resume prompt in the Stage 7 entry below still applies, with "Current stage: fixes from the first build, then the open items above; last completed 9 (v2.0.16)".

---

# Handoff (Android) - v2.0.15 Stage 8h: admin shell (2026-10-30)
- **PR title:** `v2.0.15 - Android port: admin shell (verification queue, reports with takedown, all payments with refund) (Stage 8h)`

## Done
- `ShellFragment`: `AuthRouter.role == ADMIN` swaps the bottom menu to `menu/bottom_nav_admin` (Queue / Reports / Payments / Profile), opens Queue first, hides the mascot button. No separate AdminFragment (same pattern as the owner tabs).
- `ui/QueueFragment` (`VerificationService.pending` + names from `users`) -> `ui/VerificationReviewFragment` (nav `verificationReviewFragment`; signed URLs `VerificationService.signed`, Glide with `DiskCacheStrategy.NONE` + `skipMemoryCache`, `FLAG_SECURE` in onStart / cleared in onStop; Approve with confirm, Reject through `ui/ReasonDialog`, `VerificationService.decide`). `ui/ReportsFragment` (`blocks_reports`, tap -> details -> Take down a place of the reported user from `bh_cards` -> `admin_takedown`). `ui/AdminPaymentsFragment` (`BoostService.allPayments`, refund of PAID only -> `admin_refund_boost`). Shared layout `fragment_admin_list.xml`. `util/AdminRules` (pure).
- **Supabase (read-only):** RLS lets `is_admin()` read `owner_verifications`, `blocks_reports`, `payments`, `users`; the three admin RPCs check `is_admin()` themselves; no migration. 0 pending verifications, 0 reports, 1 admin user right now. Reports are about users (no place column, no "resolved" flag).
- Version 2.0.15 / 215. Tests: 4 new (`AdminRulesTest`; with `BoostRulesTest` and `DashboardRulesTest` run on the JVM with stand-ins for `org.junit`: 13 passed, 0 failed); total 122 (older ones not re-run). Also written: `STAGE8H_CHANGELOG.md`.

## Next
**Stage 8 is now complete in code.** Before more features, build it: open `bofo-android`, sync Gradle, run `gradlew.bat testDebugUnitTest`, fix compile errors (stages 5 to 8h were written without the Android SDK). Then **Stage 9 - release prep** (plan table: R8 + rules, signing config, optional Gradle workflow) and the open items: the missing Edge Functions `create-boost-checkout` (boost payments cannot work without it) and `bofo-chat`, amenities tables (8e), remove / reorder photos in Edit, map point picker, `Answer.filters`, unread badge, role switch in Profile, a Verification entry in the owner Profile tab, admin: ban / unban users and a "resolved" state for reports (needs a migration).

## Not verified
- Not built / run. Unchecked: the admin bar after sign-in (role read from `profiles`/`users`), the review screen with real photos (signed URL format, Glide with `NONE` cache, `FLAG_SECURE` on the Activity window), `AlertDialog` with an `EditText` in `ReasonDialog` (the positive button is wired in `setOnShowListener` so an empty reason keeps the dialog open), `bh_cards` read as admin for another owner's places, the admin Profile tab (the student profile screen is reused unchanged; role switch there is not meant for admins).
- A refund only changes the database; the money is returned in PayMongo by hand. Reports cannot be marked as handled.

The resume prompt in the Stage 7 entry below still applies, with "Current stage: 9 (or fixes from the first build), last completed 8h (v2.0.15)".

---

# Handoff (Android) - v2.0.14 Stage 8g: Boost + Payments (2026-10-30)
- **PR title:** `v2.0.14 - Android port: Boost (GCash checkout in Custom Tabs) and Payments list (Stage 8g)`

## Done
- `ui/BoostFragment` (+ `fragment_boost.xml`, nav `boostFragment`, arg `bhId` optional): active places of the owner (`BhService.myListings` + `boarding_houses.status = active`) and `BoostService.plans`; **Pay with GCash** -> `BoostService.checkout` -> link opened with `CustomTabsIntent` only if `BoostRules.safeCheckoutUrl` accepts it (https + host); then `BoostService.status(paymentId)` polled (3 s, +2 s per try, max 10 s, 16 tries) on `Handler.postDelayed`, restarted in `onResume` (return from the payment page), cut in `onPause` / `onDestroyView`; **Check again**. State (place, plan, paymentId, reference) survives rotation.
- `ui/PaymentsFragment` (+ `fragment_payments.xml`, nav `paymentsFragment`): `BoostService.myPayments`, place names from `myListings`, tap = details dialog.
- `ListingsFragment`: **Boost a place** + **Payments** buttons, **Boost this place** in a place's menu (active only). `util/BoostRules` (pure). Library `androidx.browser:browser:1.8.0` added (version from memory, not looked up). Version 2.0.14 / 214. Also written: `STAGE8G_CHANGELOG.md`.
- **Supabase (read-only):** `boost_plans`, `payments` (status check pending / paid / failed / expired / refunded), `boosts`; RLS: owner reads own rows, admin all; no client write policy. Dashboard's `boosts` read for an owner is therefore fine (`boost_read`). **Finding: the live project has NO `create-boost-checkout` Edge Function (only `delete-account`), and no `bofo-chat`.** Checkout shows "not available yet" (HTTP 404) until it is deployed; nothing is charged. I did not deploy it (needs PayMongo keys, cannot be tested here). No migration.
- Tests: 6 new (`BoostRulesTest`, run on the JVM with stand-ins for `org.junit`: 6 passed, 0 failed; `DashboardRulesTest` re-run: 3 passed); total 118 (older ones not re-run).

## Next
**8h - Admin shell:** `AdminFragment` bar, **Queue** (`VerificationService.pending` / `signed` / `decide` -> RPC `review_verification(vid, approve, reason)`), **Reports** (`blocks_reports` table; `admin_takedown(bh, reason)`), payments (`BoostService.allPayments` / `refund` -> `admin_refund_boost(pid, reason)`). Read the tables / RLS read-only first. Before that, the user must decide about the missing `create-boost-checkout` function (I can write it for PayMongo if they give the secret names) and about amenities (see 8e). Open items: remove / reorder photos in Edit, map point picker, `Answer.filters`, unread badge, role switch in Profile, a Verification entry in the owner Profile tab.

## Not verified
- Not built / run. Unchecked: `CustomTabsIntent` + the 1.8.0 dependency, `RadioGroup` built in code with generated ids, the return from the payment page (success URL is a server secret: the app only re-reads the status on resume), what the function really returns (`checkout_url`, `payment_id`, `reference` were read from `BoostService`), the real status flow (no webhook exists to test), the look on a small phone.
- A pending payment is forgotten when the screen is left (the row stays in Payments).

The resume prompt in the Stage 7 entry below still applies, with "Current stage: 8h, last completed 8g (v2.0.14)".

---

# Handoff (Android) - v2.0.13 Stage 8f: owner dashboard (2026-10-30)
- **PR title:** `v2.0.13 - Android port: owner dashboard (views, saves, chats, rating, free beds) (Stage 8f)`

## Done
- `ui/DashboardFragment` (+ `fragment_dashboard.xml`, nav `dashboardFragment`, strings `dash_*`, plural `dash_places`), opened by the **Dashboard** button in `ListingsFragment`. Reads `BhService.myListings`, then `bh_activity` (`boarding_house_id=in.(...)`: views, favorites_count, chats_count, review_count, avg_rating, available_beds) and, in a try / catch, active `boosts` (`ends_at > now`). Built in code (stat cells + one card per place). `util/DashboardRules` (pure: `totals`, weighted rating, `rating` text).
- **Supabase (read-only):** `bh_activity` has those columns and a read policy `true`; no migration. The JavaFX `DashboardView` was not in the zips, so nothing is compared with the desktop screen.
- Version 2.0.13 / 213. Tests: 3 new (`DashboardRulesTest`, run on the JVM with stand-ins for `org.junit`: 3 passed, 0 failed); total 112 (older ones not re-run).

## Next
**8g - Boost** (`BoostFragment`, Custom Tabs: `BoostService.plans`, `checkout` returns a payment link, `status(paymentId)` to poll) and **Payments** (`myPayments`), then the admin shell (**Queue** = `VerificationService.pending` / `signed` / `decide`; **Reports**; `allPayments` / `refund`). Check the Edge Function / RPC behind `BoostService.checkout` and the `payments`, `boost_plans` tables read-only first, and add the Custom Tabs library (`androidx.browser:browser`) deliberately. Open items: amenities tables are missing in the live project (see 8e), remove / reorder photos in Edit, map point picker, `Answer.filters`, unread badge, role switch in Profile, a Verification entry in the owner Profile tab.

## Not verified
- Not built / run. Unchecked: whether `bh_activity` rows exist for every place (a missing row shows zeros), the `in.(uuid,uuid)` filter, the `boosts` read for an owner (RLS not inspected), the look of the stat cells on a small phone.

The resume prompt in the Stage 7 entry below still applies, with "Current stage: 8g, last completed 8f (v2.0.13)".

---

# Handoff (Android) - v2.0.12 Stage 8e: edit a place (2026-10-30)
- **PR title:** `v2.0.12 - Android port: edit a place from My places (Stage 8e)`

## Done
- `ListingFormFragment` has an edit mode (nav argument `editId`, default null): `startEdit` fills the form from `BhService.detail`, rooms / contact / location included, Save runs `BhService.updateListing` (new): updates `boarding_houses`, deletes + inserts `rooms`, updates (or inserts) the `bh_contacts` row, uploads NEW photos as `{owner}/{id}/{timestamp}-{n}.jpg` and inserts `photos` rows after the existing ones. `Draft.existingPhotos` lets `validate` accept an edit with no new photo. `ListingsFragment` options list got **Edit** (not for taken-down places).
- **Supabase (read-only, project BOFO `zfxldzcoirqolflkkhrh`):** RLS lets the owner write `rooms`, `bh_contacts`, `photos`; `bh_update` needs `is_verified_owner`. **Finding: the live project has NO `amenities` and NO `bh_amenities` table** (tables: ai_usage, audit_logs, bh_activity, bh_cards, bh_contacts, blocks_reports, boarding_houses, boost_plans, boosts, colleges, conversations, favorites, inbox_v, messages, owner_verifications, payments, photos, profiles_v, reviews, reviews_v, rooms, user_agreements, users). So `BhService.amenities()` fails (swallowed: no chips on Add a place), `publish` would fail only if an amenity were ticked, and edit hides the chips. Decide: add the tables with a migration, or drop amenities from the Android port.
- Version 2.0.12 / 212. No tests added (UI + network only).

## Next
**8f - Dashboard** (owner stats; the JavaFX `DashboardView` was not in the zips, so look at the desktop app or the `bh_activity` table first and tell the user what is shown), then Boost (Custom Tabs), Payments, then the admin shell (Queue = `VerificationService.pending` / `signed` / `decide`, Reports). Also: remove / reorder photos in Edit, the amenities decision above, map point picker, `Answer.filters`, unread badge, role switch in Profile, a Verification entry in the owner Profile tab.

## Not verified
- Not built / run. Unchecked: the prefill from `detail()` (keys of the `bh_cards` view: `price_mode`, `free_*`, `lat`, `lng`; the `rooms` and `contact` arrays), the Edit action in the dialog, saving against the real RLS, `detail()` also counts a view (`bump_view`) when the owner opens Edit.
- Rooms are replaced (delete, then insert): a failure in between leaves the place without rooms until Save is pressed again.

The resume prompt in the Stage 7 entry below still applies, with "Current stage: 8f, last completed 8e (v2.0.12)".

---

# Handoff (Android) - v2.0.11 Stage 8d: owner verification (2026-10-30)
- **PR title:** `v2.0.11 - Android port: owner verification (ID front / back + live selfie, status) (Stage 8d)`

## Done
- `ui/VerificationFragment` (+ `fragment_verification.xml`, nav `verificationFragment`, strings `verify_*`): status card (unverified / pending / verified / rejected + reason, read with `ProfileService.fetchMe` + `VerificationService.myLatest`), and for unverified / rejected owners the form: ID type (dialog list), ID front + back (camera `TakePicture` through the existing FileProvider, or gallery `PickVisualMedia`), selfie (camera only), consent tick, **Send for review** -> `VerificationService.submit`. Photos: `Images.listingJpeg` (1600 px, JPEG 82) + `VerificationService.looksSharp` (warns only). Photos are kept in a `ViewModel` (survive rotation), wiped after sending and when the screen is left; camera cache copies are deleted in `onDestroy`.
- `ui/ListingsFragment` (+ `fragment_listings.xml`): banner with a **Verification** button while not verified; **Add a place** opens the verification screen instead of the form when the state is known and not verified (unknown state = old behaviour, the database decides).
- `service/VerificationService.submit`: upload names / content types follow the bytes (`.jpg` / `image/jpeg`; PNG still works). `util/VerificationRules` (pure: states, `canSubmit`, `effective`, first missing slot, ext / mime, ID types, consent version from `policy.verification.version`, default 1.0).
- **Supabase (read-only, project BOFO `zfxldzcoirqolflkkhrh`):** bucket `id-verification` is private, 5 MB, allows png + jpeg; storage policies `idv_insert` (first folder = own uid) and `idv_read` (own or admin); `owner_verifications` RLS `ov_insert` (own, status pending, role owner) and `ov_read`; `user_agreements.document_type` allows `verification_consent`; `review_verification(vid, approve, reason)` is admin only and sets `users.is_verified` / `verification_status`. No trigger on `owner_verifications`, so `users.verification_status` stays `unverified` while a request waits: the app reads the latest `owner_verifications` row. No migration.
- Version 2.0.11 / 211. Tests: 4 new (`VerificationRulesTest`, run on the JVM with stand-ins for `org.junit`: 4 passed, 0 failed); total 109 (the older 105 not re-run). Also written: `STAGE8D_CHANGELOG.md`.

## Next
**8e - Dashboard + Edit place** (owner), then Boost (Custom Tabs), Payments, then the admin shell (**Queue** = `VerificationService.pending` / `signed` / `decide`, Reports). Also: map point picker for the form, `Answer.filters` from Ask BOFO, unread badge on Chats, role switch inside Profile (the shell keeps the old tabs until sign-in again), a Verification entry in the owner Profile tab, front-camera selfie.

## Not verified
- Not built / run. Unchecked: `TakePicture` with the FileProvider on the owner screen (same pattern as the profile photo), `MaterialAlertDialogBuilder.setItems` with a `String[]`, `ViewModelProvider(this).get(Draft.class)` for a nested static class, the screen with the keyboard / on small phones, a real submit (needs an owner account; the admin side is not ported, so approve in Supabase > `review_verification` or by SQL as admin).
- The blur threshold in `looksSharp` (> 40) is a guess ("tune on real devices"); it only warns. The selfie opens the normal camera app (back camera by default): a front-camera shortcut is not possible with `TakePicture`.
- The 3 uploads run one after another, then 2 inserts: a failure in between leaves files in the private bucket (the next try uses new names). A second request can be sent while one is pending only through the API (the screen blocks it).
- `policy.verification.version` is not in `assets/config.properties` (default 1.0 is recorded in `user_agreements`).

The resume prompt in the Stage 7 entry below still applies, with "Current stage: 8e, last completed 8d (v2.0.11)".

---

# Handoff (Android) - v2.0.10 Stage 8c: add a place (2026-10-30)
- **PR title:** `v2.0.10 - Android port: owner listing form (add a place with photos) (Stage 8c)`

## Done
- `ui/ListingFormFragment` (+ `fragment_listing_form.xml`, styles `Text.Form.Label`, `Widget.Form.Input`, nav `listingFormFragment`, strings `form_*`), opened by the **Add a place** button in `ListingsFragment`. Uses the ported `BhService.Draft` / `validate` / `publish`. Photo picker `PickMultipleVisualMedia(8)`, `Images.listingJpeg` (1600 px, EXIF, JPEG 82), `util/ListingRules` (money, coords, ints, photo limit).
- `BhService.publish` now uploads JPEG as `.jpg` / `image/jpeg` (detected from the bytes); PNG still works. Supabase checked read-only: bucket `bh-photos` is public, 5 MB, allows png + jpeg. No migration.
- Version 2.0.10 / 210. Tests: 3 new (`ListingRulesTest`, run on the JVM with stand-ins: 3 passed, 0 failed); total 105 (the older 102 not re-run).

## Next
**8d - Verification** (owner ID photos + selfie, `VerificationService` ported; without it publishing is refused by RLS), then Dashboard, Edit place, Boost (Custom Tabs), Payments, then the admin shell (Queue, Reports). Also: map point picker for the form, `Answer.filters` from Ask BOFO, unread badge on Chats, role switch inside Profile.

## Not verified
- Not built / run. Unchecked: `PickMultipleVisualMedia` with limit 8 (the system may cap it lower), the room rows built in code, the `Button(ctx, null, 0, style)` constructor with the Tint style, the form with the keyboard open, a real publish (needs a verified owner).
- Photos are compressed on a pool thread one after another (fine for 8).

The resume prompt in the Stage 7 entry below still applies, with "Current stage: 8d, last completed 8c (v2.0.10)".

---

# Handoff (Android) - v2.0.9 Stage 8b: owner tabs + My places (2026-10-30)
- **PR title:** `v2.0.9 - Android port: owner bottom bar and My places (list, unlist, delete) (Stage 8b)`

## Done
- `ui/AuthRouter.role` (set in `route`), `ShellFragment` swaps the bottom menu to `menu/bottom_nav_owner` (My places / Chats / Profile) for owners and opens My places first. `ui/ListingsFragment` (+ `fragment_listings.xml`): `BhService.myListings` + a status read, tap = View / Unlist or List again / Delete (`setStatus`, `deleteListing`).
- **Supabase (read-only, project BOFO `zfxldzcoirqolflkkhrh`):** `boarding_houses.status` check = `active` | `unlisted` | `taken_down`; `inbox_v` = id, student_id, owner_id, boarding_house_id, last_message_at, bh_name, other_name, last_message, unread (matches `Conversation.from`, so the Stage 7 "unverified columns" worry is gone). No migration.
- Version 2.0.9 / 209. No tests added (UI only).

## Next
**8c - Listing form** (`ListingFormFragment`: `BhService.Draft`, `validate`, `publish` already ported; photos via `PickVisualMedia`, map point picker), then Dashboard, Boost (Custom Tabs), Payments, Verification, then the admin shell (Queue, Reports). The owner's Profile tab is the student one (role switch / delete account are in it already).

## Not verified
- Not built / run. Unchecked: the two-line `ListView` rows, role being read before the shell opens (it is set in `AuthRouter.route`; after `switch_role` inside Profile the shell keeps the old tabs until sign-in again, to fix), the owner's Chats tab and the mascot button on the owner bar (both reused unchanged).
- Checked in Supabase: the `bh_cards` view shows a place when it is active OR it is the signed-in owner's OR the user is admin, so unlisted places do appear in My places.

The resume prompt in the Stage 7 entry below still applies, with "Current stage: 8c, last completed 8b (v2.0.9)".

---

# Handoff (Android) - v2.0.8 Stage 8a: BOFO assistant + mascot (2026-10-30)
- **PR title:** `v2.0.8 - Android port: Ask BOFO assistant sheet and mascot (Stage 8a)`

## Done
- `ui/MascotView` (ImageView + property animators, moods SITTING / THINKING / RUNNING / SLEEPING), `ui/BofoPanelSheet` (+ `sheet_bofo.xml`; strings `bofo_*`, `cd_mascot`): bottom sheet, your questions as purple bubbles, answers from `BofoAiService.ask` (Edge Function `bofo-chat`, `BrowseState.collegeId` sent), FAQ fallback on any failure or empty reply.
- `fragment_shell.xml` is now a FrameLayout with a floating `MascotView` (`bofo_fab`); `ShellFragment` opens the sheet on tap. Version 2.0.8 / 208. No tests added (UI only). No Supabase change, no new library.

## Next
**8b - Owner / admin shells** (plan section 3): `OwnerFragment` / `AdminFragment` + bars, `DashboardFragment`, `ListingsFragment`, `ListingFormFragment`, `BoostFragment`, `PaymentsFragment`, `VerificationFragment`, `QueueFragment`, `ReportsFragment`. Split further. Also: apply `Answer.filters` from the assistant to `BrowseState`; the mascot button is student-shell only for now; JavaFX `BofoPanel` / `Mascot` were not available, so look and wording are mine.

## Not verified
- Not built / run. Unchecked: the sheet height with the keyboard open, the RUNNING animator (`mascotAngle` property by reflection), the real `bofo-chat` reply format (`reply`, `filters`, read from `BofoAiService`).
- The mascot button sits 84 dp above the bottom; it may cover the last card corner on small phones.

The Stage 7 entry below is still valid; the resume prompt in it applies, with "Current stage: 8b, last completed 8a (v2.0.8)".

---

# Handoff (Android) - v2.0.7 Stage 7: Chat (2026-10-30)
- **PR title:** `v2.0.7 - Android port: chats list, chat screen with polling and offline outbox, Chat with owner (Stage 7)`

## Done
- `ui/ChatsFragment` (+ `fragment_chats.xml`, `item_conversation.xml`): the Chats tab, `ChatService.inbox()` (also kept on the device), unread badge, time label, pull to refresh, reload on every resume, long-press = delete chat from my list. Replaces the placeholder in `ShellFragment`.
- `ui/ChatFragment` (+ `fragment_chat.xml`, `item_msg_mine.xml`, `item_msg_theirs.xml`, nav destination `chatFragment`, args `convId`, `title`, `place`): newest 30 messages, older pages on scroll to top (keeps your place), bubbles with time + ticks (1 sent, 2 read), offline banner, send box (max 1000 characters), long-press = copy / delete for me / delete for everyone (own messages), tap a red failed bubble = send again, marks messages read.
- **Polling:** `Handler.postDelayed` chain, 3 s then +1.5 s per quiet tick up to 12 s, started in `onResume`, cut in `onPause` / `onDestroyView`. Each tick: `Outbox.flushAsync`, `fetchNewer`, and `fetchMyRead` only while I have a one-tick message.
- **Outbox:** every message goes `Outbox.add` first (clock bubble), then `flushAsync`. `BofoApp` flushes whenever `Connectivity.OFFLINE` turns false. Refused messages stay as red bubbles (in memory only).
- `BhDetailFragment`: **Chat with owner** enabled (`ChatService.openConversation`, then `ChatFragment.open`); disabled with "This is your own place" for the owner of the place.
- `util/ChatRules` (pure): merge / dedupe / order, poll delay, cursors, ticks, time labels, preview, badge, title, outgoing text.
- Version 2.0.7 / versionCode 207. No new libraries. **Supabase:** nothing changed, no migration. **Google / Supabase steps:** none new.
- **Tests:** 9 new JUnit tests in `ChatRulesTest` (102 in total). Run on the JVM through a tiny reflection runner with stand-ins for `org.junit` and `org.json`: **9 passed, 0 failed**. The 93 older tests were not re-run.
- Also written: `STAGE7_CHANGELOG.md`.

## Next stage
**Stage 8 - Owner and admin shells** (v2.0.8; split 8a / 8b / ...). **8a first: `BofoPanelSheet` + `MascotView`** (plan table row for stage 7, not done this time: `ui/BofoPanel`, `Mascot` from the JavaFX source; `BofoAiService` is already ported). Then `OwnerFragment` / `AdminFragment` with their own bottom bars and `DashboardFragment`, `ListingsFragment`, `ListingFormFragment`, `BoostFragment` (Custom Tabs), `PaymentsFragment`, `VerificationFragment`, `QueueFragment`, `ReportsFragment` (plan section 3). Today every role opens the student shell, so an owner sees the student tabs (the Chats tab should work for an owner too, since `inbox_v` is per user, but this was not checked).

## Decisions made
- The JavaFX `ChatsView` / `ChatView` were not in the zip, so the screens follow `ChatService` and the plan, not the desktop layout. Compare with the desktop if something looks different.
- Polling only while the chat is on screen (no background polling, no push notifications). New messages while the app is closed are seen on the next open (unread badge in the list).
- Failed messages are kept in memory only: leaving the chat forgets them (the Outbox already removed them because the server said no).
- The Chats tab has no unread badge on the bottom bar yet (needs a shared state; `ChatService.totalUnread` exists).
- Message time shows today as HH:mm, yesterday as "Yesterday", else "Oct 28"; no day separators.

## File map (Stage 7)
| JavaFX | Android |
|---|---|
| `ui/ChatsView` | `ui/ChatsFragment` + `fragment_chats.xml`, `item_conversation.xml` |
| `ui/ChatView` | `ui/ChatFragment` + `fragment_chat.xml`, `item_msg_mine.xml`, `item_msg_theirs.xml` |
| `service/ChatService`, `util/Outbox` | unchanged (Stage 2) |
| (new) | `util/ChatRules`, `drawable/ic_send`, `bg_bubble_mine`, `bg_bubble_theirs`, `bg_unread_badge`, `bg_chat_input`, `bg_send_button` |

## Known issues / not verified
- **Not opened or built in Android Studio / Gradle.** Checked: all new / changed XML is well-formed; every `R.string` / `R.color` used in the new Java exists; every view id used through view binding exists in its layout; the Android Java files pass a `javac` parse (only missing-symbol errors, which need the SDK); `ChatRules` and its 9 tests run on the JVM.
- **Not checked (needs Gradle / a device):** RecyclerView with `stackFromEnd` and the scroll-position keeping when older pages load; the keyboard pushing the send box up (relies on `adjustResize` + the Activity insets); `MaterialAlertDialogBuilder.setItems`; the real Supabase answers (`inbox_v` columns `other_name`, `bh_name`, `last_message`, `unread` were read from `Conversation.from`, not from the live project); RPCs `get_or_create_conversation`, `mark_read`, `delete_message`, `delete_conversation` were not called.
- If the chat list shows no names, send me one row of `inbox_v` (Supabase > Table editor).
- `Outbox.add` stores the message before the chat is shown as sent: if you kill the app between add and send, the message is still sent at the next flush (reconnect or opening a chat).
- Two taps on Send in a row create two messages (as on the desktop).
- The inbox is reloaded on every resume of the tab: fine for a small inbox, revisit with many chats.

## How I test this stage
1. Unzip the changes zip over the repo, open `bofo-android`, sync Gradle (no new libraries). No Supabase or Google steps are new.
2. **Unit tests:** `gradlew.bat testDebugUnitTest` in `bofo-android`. Expect 102 tests passed.
3. Sign in as a student, open any place, tap **Chat with owner**: the chat opens (empty: "Say hello!"). Type a message, tap send: a bubble with "Sending..." then the time and one tick.
4. Sign in as the owner of that place on a second phone / emulator: **Chats** tab shows the conversation with a badge. Open it, answer. On the first phone the answer appears within about 3 seconds; the ticks turn into two blue ticks after the owner opened the chat.
5. Leave the chat (Back): the Chats tab shows the last message, the time, and no badge.
6. Long-press your message: Copy / Delete for me / Delete for everyone. Long-press theirs: Copy / Delete for me.
7. Airplane mode, send a message: the orange "You are offline" bar and a clock on the bubble. Turn airplane mode off: the message goes by itself (also if you left the chat first).
8. Scroll up in a chat with more than 30 messages: older messages load and the view does not jump.
9. Your own place: **Chat with owner** is greyed ("This is your own place"). Long-press a row in Chats: **Delete this chat?**. Send me the red text of anything that fails, or the first Gradle error.

## Paste-this prompt for the next AI
You are taking over a native Android (Java only, XML + Fragments, Material 3, Gradle Groovy) port of the app BOFO - Boarding House Finder. The attached zip is the latest checkpoint; the old JavaFX source is still in the repo as reference (attach the original repo zip too if `src/` is missing), the new project is in `bofo-android/`.

1. Read `CLAUDE.md`, then the top entry of `HANDOFF_ANDROID.md`, then `ANDROID_PORT_PLAN.md` (sections 3, 8, 11 and 12). Do not read the rest of the repo until a task needs it (use `grep -n`, then read only the needed lines).
2. Follow every rule in `HANDOFF_ANDROID.md` and `CLAUDE.md`: Java only, Supabase backend unchanged unless a feature needs it (the user allowed changes, add a migration file and say what changed), `applicationId com.bofo.finder`, no secrets committed, version bump on every stage (`BuildConfig.VERSION_NAME` shown in Profile > About and Settings, PR title, `CHANGES.md`, `HANDOFF_ANDROID.md`). Every change is delivered as a zip with ONLY the changed files. Remember: Android `org.json` throws a CHECKED `JSONException` and has no `JSONArray.isEmpty()`; the profile table is `public.users`; no Java 9+ library calls (minSdk 26, for example no `InputStream.readAllBytes`).
3. Current stage: **Stage 8 - Owner and admin shells (v2.0.8)**, starting with 8a = `BofoPanelSheet` + `MascotView` (left over from Stage 7). Last completed: **Stage 7 (v2.0.7)**. Known issues: see the handoff.
4. Do only the current stage. If it is too big, split it (8a, 8b...). Never leave half-written files.
5. At the end of the stage, and at about 70% of your budget, deliver `BOFO-android-stageN-FULL.zip`, `BOFO-android-v2.0.N-changes.zip` (with `CHANGED_FILES.txt`) and an updated `HANDOFF_ANDROID.md` that ends with this same resume prompt filled in for the next stage. Also write a changelog md (every change, how to open it, a simple fix guide) as the user asked. Put everything in zips.
6. You cannot run Android Studio: never claim it builds or runs; say what was and was not checked (a JDK is available in the sandbox: `javac` may be missing from PATH, then run it with `java -m jdk.compiler/com.sun.tools.javac.Main` (without `--release`); there may be no JUnit jar, so compile the pure-Java classes with small `org.json` stubs and run a plain `main` check; Maven / Google repositories are NOT reachable, so Android and AndroidX classes cannot be compiled).

Start by telling me in 3 lines what the handoff says is done and what you will do now, then do it.

---

# Handoff (Android) - v2.0.6 Stage 6: Directions + trip planning (2026-10-30)
- **PR title:** `v2.0.6 - Android port: directions card, trip planner (walk / tricycle / jeepney / bus), route line on the map (Stage 6)`

## Done
- **6a, logic (pure Java, no UI):** `util/Router` (OSRM then Valhalla, straight-line fallback, cache, one call for two callers), `util/Trip` (fares, times, options, "best"), `util/TripPlanner` (`plan`, `planWithin` = never waits more than 12 s, `quick` = offline), `util/TransitRoutes` (Overpass jeepney lines, session + on-device cache), `util/TransitMatch` (copied unchanged), `util/LocalTransit` (hand-made lines from `assets/local-transit.json`, optional `local-transit.json` in the app's private files folder), `util/HttpText` (the only new OkHttp wrapper: `get`, `postForm`, `head`), `util/DirectionsRules` (NEW, pure: tab meanings, what is missing, first tab, nearest campus, "moved enough to re-plan", hero texts, OpenStreetMap link). `MapGeo` gained `lineJson` and `pathBounds`.
- **6b, UI:** `ui/DirectionsCardView` (+ `view_directions_card.xml`, `item_dir_option.xml`, `item_dir_note.xml`, 8 small `bg_dir_*` drawables) shown on the place screen between the contact block and the reviews. Three tabs that share the width (Me -> BH, BH -> Campus, Me -> Campus), a small map, From / To rows, a big summary of the chosen way, the list of ways (best one marked), More info, Open in OpenStreetMap. `MapCardView` gained `setRoute`, `clearRoute`, `fitRoute`, `setTapsEnabled`, `showLocate`, `setParentScrollBlocked` and the route layers (`route-casing`, `route-line`, `route-dash` for estimates, `route-start`, `route-end`).
- `BhDetailFragment` creates a `LocationHelper` in `onCreate` and binds the card after the place loads. `BofoApp` calls `LocalTransit.init`.
- **A Stage 5 compile error was fixed on the way:** `MapCardView` called `ViewMapCardBinding.inflate(inflater, this)`, which exists only for `<merge>` layouts (`view_map_card.xml` has a FrameLayout root). It now uses `inflate(inflater, this, true)`.
- Version 2.0.6 / versionCode 206. No new libraries (OkHttp, MapLibre and Play Services were already there). **Supabase:** nothing changed. **Google / Supabase steps:** none new.
- **Tests:** 23 new JUnit tests: `TripLogicTest` (7, the desktop `dev/TripLogicCheck` as JUnit) and `Stage6LogicTest` (16: polyline decoder, thinning, estimate, cache reverse lookup, local-transit parsing, Overpass parsing + device cache, `TripPlanner.quick`, `DirectionsRules`, route GeoJSON). Total 93. The real test files were run on the JVM through a tiny reflection runner with stand-ins for `org.junit`, `org.json` and `HttpText` (no network): **23 passed, 0 failed**. The 70 older tests were not re-run.
- Also run (not shipped): `TripPlanner.plan` end to end with the network down and a hand-made line: the line is matched and shows the checked fare; `planWithin` and the far-trip case answer with estimates.
- Also written: `STAGE6_CHANGELOG.md`.

## Next stage
**Stage 7 - Chat** (v2.0.7): `ChatsFragment` (list) and `ChatFragment` (RecyclerView bubbles, polling loop 3 s backing off to 12 s as a `Handler.postDelayed` tied to the Fragment lifecycle, stop in `onPause`, outbox for unsent messages), enable the "Chat with owner" button on the place screen. Port from `ui/ChatsView`, `ui/ChatView`; `service/ChatService` is already ported (Stage 2). Read the plan, section 3 row for chat, and `ChatService` before writing any UI.

## Decisions made
- The planner is the desktop planner: same fares (LTFRB, 19 March 2026), same time formulas, same limits (walk up to 3.5 km, tricycle up to 6 km, "bus or van" beyond 25 km straight, no local-line lookup beyond 15 km). Only the HTTP layer changed.
- Location is never asked when the place screen opens. The card follows you only if the permission is already granted and Settings > Use my location is on; otherwise a **Use my location** button asks. Re-plans when you moved more than 80 m (only for trips that start at you).
- First tab: BH -> Campus when a campus is known and your position is not (it needs no location), else Me -> BH. A campus found later switches an untouched card to BH -> Campus.
- Campus for the card: the chosen campus (`BrowseState`), else the nearest one within 15 km of the place.
- The map in the card ignores pin taps and does not hand its drags to the page scroll (so the page cannot be scrolled by dragging on the map; scroll with the area around it).
- No distance / time badge in the middle of the route (the desktop had one): the Android map style has no fonts, so text on the map is not possible. The numbers are in the summary above the list.
- Fares, trip times and jeepney lines are estimates from public data; `assets/local-transit.json` ships EMPTY on purpose (as on the desktop) until someone checks a real route and fare.

## File map (Stage 6)
| JavaFX | Android |
|---|---|
| `util/Router` | `util/Router` (+ `util/HttpText`) |
| `util/Trip`, `TripPlanner`, `TransitMatch` | same names (Java 8-safe edits only) |
| `util/TransitRoutes`, `LocalTransit` | same names (OkHttp; assets instead of a classpath resource) |
| `ui/DirectionsCard` | `ui/DirectionsCardView` + `view_directions_card.xml` + `util/DirectionsRules` |
| `ui/MapPane.showPath` | `MapCardView.setRoute` / `fitRoute` |
| `dev/TripLogicCheck` | `TripLogicTest` |
| `resources/.../local-transit.json` | `assets/local-transit.json` |

## Known issues / not verified
- **Not opened or built in Android Studio / Gradle.** Checked: all XML is well-formed; every `@string / @drawable / @color / @style` in the new layouts and every `R.string / R.drawable / R.id` and binding field in `DirectionsCardView` resolves; the Android Java files pass a `javac` parse (only missing-symbol errors, which need the SDK); the pure classes and the 23 tests run on the JVM.
- **Not checked (needs Gradle / a device):** the new MapLibre calls (`LineLayer.withFilter(Expression)`, `PropertyFactory.lineCap / lineJoin / lineDasharray`, `Property.LINE_CAP_ROUND`, `Expression.eq(Expression.get(..), Expression.literal(0))`) written from memory of 11.x, not checked against 13.0.0; `MaterialButtonToggleGroup` with three `Button`s (relies on the Material inflater turning them into `MaterialButton`); the OkHttp wrapper against the real servers (OSRM / Valhalla / Overpass were NOT called from the sandbox: no network); how the card looks on a phone (sizes by eye: map 240 dp).
- The first route after opening the screen can take several seconds (free public servers; `planWithin` shows an estimate after 12 s and fills the cache for the next try). Tapping the tab you are already on plans again.
- Wi-Fi or mobile data off: you get estimates and the "could not reach the route server" note, never a crash.
- The `route.*` / `transit.*` keys in `assets/config.properties` are still only a comment (defaults apply): add a key there to point at another server.
- A line typed into `assets/local-transit.json` must have DENSE points (`path`, a point every 100 m or so) or use `stops` (the app fills in the points). See `docs/LOCAL_TRANSIT.md` in the repo.
- Android never rotates the map to the route direction and has no turn-by-turn steps (the desktop did not either).

## How I test this stage
1. Unzip the changes zip over the repo, open `bofo-android`, sync Gradle (no new libraries). No Supabase or Google steps are new.
2. **Unit tests:** `gradlew.bat testDebugUnitTest` in `bofo-android`. Expect 93 tests passed.
3. Sign in, open any place. Scroll below the contact buttons: the **Directions** card. With a campus chosen (or one within 15 km) it opens on **BH -> Campus**: a line appears on the map at once (dashed = estimate), then after a few seconds the summary ("12 min walk", "3.4 km") and a **Ways to go** list (Walk, Tricycle, Jeepney estimate), the best one marked.
4. Tap another way: the summary and the line change. Tap **More info**: sources and notes. Tap **Open in OpenStreetMap**: the browser opens the same trip.
5. Tap **Me -> BH**: if location is not allowed yet, a message and **Use my location**. Tap it, allow: the blue dot appears and the trip is planned. On an emulator set a location first (Extended controls > Location).
6. Tap **Me -> Campus**. With no campus chosen and none near: "Choose a campus on the Home screen".
7. Put the emulator location far away (more than 25 km): "too far to walk" and a single Bus or van option; **Show BH -> Campus** offers the trip that makes sense.
8. Airplane mode, open a place: the card shows dashed estimates and a note, no crash. Settings > Use my location off: **Use my location** says it is off.
9. Drag and pinch the map inside the card: it moves, the page does not scroll under your finger. Send me the red text of anything that fails, or the first Gradle error.

## Paste-this prompt for the next AI
You are taking over a native Android (Java only, XML + Fragments, Material 3, Gradle Groovy) port of the app BOFO - Boarding House Finder. The attached zip is the latest checkpoint; the old JavaFX source is still in the repo as reference (attach the original repo zip too if `src/` is missing), the new project is in `bofo-android/`.

1. Read `CLAUDE.md`, then the top entry of `HANDOFF_ANDROID.md`, then `ANDROID_PORT_PLAN.md` (sections 3, 7, 10 and 11). Do not read the rest of the repo until a task needs it (use `grep -n`, then read only the needed lines).
2. Follow every rule in `HANDOFF_ANDROID.md` and `CLAUDE.md`: Java only, Supabase backend unchanged unless a feature needs it (the user allowed changes, add a migration file and say what changed), `applicationId com.bofo.finder`, no secrets committed, version bump on every stage (`BuildConfig.VERSION_NAME` shown in Profile > About and Settings, PR title, `CHANGES.md`, `HANDOFF_ANDROID.md`). Every change is delivered as a zip with ONLY the changed files. Remember: Android `org.json` throws a CHECKED `JSONException` and has no `JSONArray.isEmpty()`; the profile table is `public.users`; no Java 9+ library calls (minSdk 26, for example no `InputStream.readAllBytes`).
3. Current stage: **Stage 7 - Chat (v2.0.7)**. Last completed: **Stage 6 (v2.0.6)**. Known issues: see the handoff.
4. Do only the current stage. If it is too big, split it (7a, 7b...). Never leave half-written files.
5. At the end of the stage, and at about 70% of your budget, deliver `BOFO-android-stageN-FULL.zip`, `BOFO-android-v2.0.N-changes.zip` (with `CHANGED_FILES.txt`) and an updated `HANDOFF_ANDROID.md` that ends with this same resume prompt filled in for the next stage. Also write a changelog md (every change, how to open it, a simple fix guide) as the user asked. Put everything in zips.
6. You cannot run Android Studio: never claim it builds or runs; say what was and was not checked (a JDK is available in the sandbox: `javac` may be missing from PATH, then run it with `java -m jdk.compiler/com.sun.tools.javac.Main` (without `--release`); there may be no JUnit jar, so compile the pure-Java classes with small `org.json` stubs and run a plain `main` check; Maven / Google repositories are NOT reachable, so Android and AndroidX classes cannot be compiled).

Start by telling me in 3 lines what the handoff says is done and what you will do now, then do it.

---

# Handoff (Android) - v2.0.5 Stage 5: Map (MapLibre) + location (2026-10-30)
- **PR title:** `v2.0.5 - Android port: map (MapLibre), location, Near me, show on map (Stage 5)`

## Done
- `assets/bofo-map-style.json`: OpenFreeMap vector source, BOFO colours (lilac land use, green parks, light-blue water, white roads), no labels / POI icons.
- `ui/MapCardView` (+ `view_map_card.xml`): lazy MapView on `org.maplibre.gl:android-sdk`; pins as a GeoJSON SymbolLayer (purple, amber star for featured, bigger when selected), dashed radius ring, campus dot, live dot, peek card for the tapped pin, locate button, optional expand button, visible attribution text; MapLibre tile cache capped at 50 MB.
- `ui/MapFragment` (+ `fragment_map.xml`, nav destination `mapFragment`): full-screen map with campus picker (reuses `CampusPickerSheet`), radius slider, locate button. `MapFragment.open(fragment)` or `MapFragment.open(fragment, id, lat, lng)` (centres and selects that place).
- `ui/HomeFragment`: List / Map toggle (map created on first use, same data as the list), **Near me** chip (first chip), locate + expand buttons on the map, live dot while the map is shown.
- `ui/BhDetailFragment`: **Show on map** button under the address.
- `util/LocationHelper` (FusedLocationProviderClient, runtime permission launcher): `requestOnce` (Near me, locate), `startWatching` (live dot, only when the permission is already granted), honours Settings > Use my location.
- `util/MapGeo` (pure Java): GeoJSON text for pins / ring / dots, destination point, bounds. `util/BrowseState`: `nearMe`, `userLat/Lng`, `refLat()/refLng()/hasRef()`, `setNearMe`, `clearNearMe`.
- Libraries added: `org.maplibre.gl:android-sdk` **13.0.0** (from the MapLibre newsletter of March 2026; Vulkan by default), `com.google.android.gms:play-services-location` **21.3.0** (from memory, not looked up). Version 2.0.5 / versionCode 205; README, CHANGES, plan (section 10) updated. Permissions were already in the manifest.
- **Supabase:** nothing changed, no migration. **Google Cloud / Supabase steps:** none new.
- **Tests:** 7 new JUnit tests in `MapGeoTest` (70 in total). The JUnit file itself was NOT run (no JUnit jar in the sandbox); the same assertions (18 checks) were run on the JVM through a small main class against the real `MapGeo`, `Geo`, `BoardingHouse` sources (with `org.json` stubs): all passed. The 63 older tests were not re-run.
- Also written: `STAGE5_CHANGELOG.md` (every change, how to open it, a simple fix guide).

## Next stage
**Stage 6 - Directions + trip planning** (v2.0.6): `util/Router`, `TripPlanner`, `TransitRoutes`, `TransitMatch`, `LocalTransit`, `Trip` copied from the JavaFX source (HTTP through OkHttp, `Http`), JUnit tests from `dev/TripLogicCheck.java`, `ui/DirectionsCardView` (segmented walk / ride / transit control that fits the width) shown on the place detail screen, route line on the map (new `route-src` + LineLayer in `MapCardView.addLayers`, plus a `setRoute(...)` method). Reuse `LocationHelper` for the start point and `MapCardView` for drawing. Read `route.*` / `transit.*` keys in `assets/config.properties` first. Split 6a (logic + tests) / 6b (UI) if needed.

## Decisions made
- The map is on Home (toggle) and full screen (`MapFragment`); there is no separate Map tab (the bottom bar keeps its 5 tabs).
- Pins are one SymbolLayer with two bitmaps drawn in code (no image files). Tap = peek card, "View" opens the detail screen.
- Location is never asked on screen open: only when the user taps **Near me** or the locate button. The live dot starts only if the permission is already granted.
- Near me is memory only (like the radius); choosing a campus turns it off, choosing Near me forgets the campus for the session (the one saved on the profile stays). Explore still uses only the campus.
- Default map area when there is nothing to show: Dumaguete (9.3068, 123.3054).

## File map (Stage 5)
| JavaFX | Android |
|---|---|
| `ui/MapPane`, `MapCard` | `ui/MapCardView` + `view_map_card.xml`, `ui/MapFragment` + `fragment_map.xml` |
| `util/VectorTileRetriever`, `Mvt`, `MinimalTileRetriever` | not ported: `assets/bofo-map-style.json` |
| `util/Locator`, `WifiLocator` | `util/LocationHelper` |
| (new) | `util/MapGeo`, `drawable/ic_my_location`, `ic_expand`, `bg_map_button` |

## Known issues / not verified
- **Not opened or built in Android Studio / Gradle.** What WAS checked: all XML is well-formed, the style JSON parses, `MapGeo` runs on the JVM (18 checks), every Java file passes a `javac` parse (the only errors left are missing Android / AndroidX / MapLibre / Play Services / generated-binding symbols and the `@Override` errors that follow from them).
- **Not checked at all (needs Gradle / a device):** every MapLibre call (`MapLibre.getInstance`, `Style.Builder().fromJson`, `GeoJsonSource.setGeoJson`, `Expression.switchCase`, `queryRenderedFeatures(RectF, ...)`, `LatLngBounds.from`, `OfflineManager.setMaximumAmbientCacheSize` + `FileSourceCallback`) written from the 11.x docs and checked against nothing for 13.0.0; the Play Services location calls; the layer names in the style against the live OpenFreeMap tiles; the Vulkan renderer on your emulator; how the pins, peek card and buttons look on a real phone (sizes by eye).
- If a MapLibre class or method does not resolve in Android Studio, send me the exact error (13.x may have renamed something). If the map is blank on an emulator, switch the dependency to `org.maplibre.gl:android-sdk-opengl:13.0.0`.
- `org.maplibre.geojson.Feature` is assumed to come with the SDK (it is an `api` dependency in 11.x).
- The top bar of the full map (back + campus button) and the radius card are laid over the map with a 64 dp right margin for the locate / expand buttons: on a very narrow screen the campus name is cut with "...".
- Near me radius: the ring is drawn around the position you had when you tapped the chip (it does not follow the live dot).

## How I test this stage
1. Unzip the changes zip over the repo, open `bofo-android`, sync Gradle (new libs: MapLibre 13.0.0, play-services-location). No Supabase or Google steps are new.
2. **Unit tests:** `gradlew.bat testDebugUnitTest` in `bofo-android`. Expect 70 tests passed.
3. Sign in, **Home**. Tap **Map** in the List / Map toggle (top right of the list heading): a light map with purple pins appears; attribution text bottom-left. Pinch / drag works. Tap a pin: it grows and a card shows name, price, rating (and distance with a campus); tap **View**: the detail screen; Back returns to the map.
4. Pick a campus: a dashed ring and an amber dot appear, the map zooms to the ring; drag the radius slider (it is above the map on Home): the ring and pins update. Tap the tabs New / Hot / Best rated: pins change.
5. Tap the **locate** button (top right of the map): the permission dialog appears; allow: a purple dot with a halo and the map moves to you. On an emulator set a location first (Extended controls > Location). Deny: a short message, nothing else breaks.
6. Settings > turn **Use my location** off: locate and **Near me** now say location is off. Turn it on again.
7. **Near me** chip (first chip on Home): allow location: the campus is cleared, the radius slider shows "Within 3 km of you", cards show distances. Tap the chip again to switch it off.
8. Tap the **expand** button on the Home map: the full-screen map opens with a back button, the campus button (opens the picker) and the radius slider.
9. Open a place > **Show on map**: the full map centres on that place with its pin selected and the peek card open.
10. Airplane mode: the pins still show on a grey / cached background (or an empty background); no crash. Send me the red text of anything that fails, or the first Gradle error.

## Paste-this prompt for the next AI
You are taking over a native Android (Java only, XML + Fragments, Material 3, Gradle Groovy) port of the app BOFO - Boarding House Finder. The attached zip is the latest checkpoint; the old JavaFX source is still in the repo as reference (attach the original repo zip too if `src/` is missing), the new project is in `bofo-android/`.

1. Read `CLAUDE.md`, then the top entry of `HANDOFF_ANDROID.md`, then `ANDROID_PORT_PLAN.md` (sections 3, 7, 9 and 10). Do not read the rest of the repo until a task needs it (use `grep -n`, then read only the needed lines).
2. Follow every rule in `HANDOFF_ANDROID.md` and `CLAUDE.md`: Java only, Supabase backend unchanged unless a feature needs it (the user allowed changes, add a migration file and say what changed), `applicationId com.bofo.finder`, no secrets committed, version bump on every stage (`BuildConfig.VERSION_NAME` shown in Profile > About and Settings, PR title, `CHANGES.md`, `HANDOFF_ANDROID.md`). Every change is delivered as a zip with ONLY the changed files. Remember: Android `org.json` throws a CHECKED `JSONException` and has no `JSONArray.isEmpty()`; the profile table is `public.users`; no Java 9+ library calls (minSdk 26, for example no `InputStream.readAllBytes`).
3. Current stage: **Stage 6 - Directions + trip planning (v2.0.6)**. Last completed: **Stage 5 (v2.0.5)**. Known issues: see the handoff.
4. Do only the current stage. If it is too big, split it (6a, 6b...). Never leave half-written files.
5. At the end of the stage, and at about 70% of your budget, deliver `BOFO-android-stageN-FULL.zip`, `BOFO-android-v2.0.N-changes.zip` (with `CHANGED_FILES.txt`) and an updated `HANDOFF_ANDROID.md` that ends with this same resume prompt filled in for the next stage. Also write a changelog md (every change, how to open it, a simple fix guide) as the user asked. Put everything in zips.
6. You cannot run Android Studio: never claim it builds or runs; say what was and was not checked (a JDK is available in the sandbox: `javac` may be missing from PATH, then run it with `java -m jdk.compiler/com.sun.tools.javac.Main` (without `--release`); there may be no JUnit jar, so compile the pure-Java classes with small `org.json` stubs and run a plain `main` check; Maven / Google repositories are NOT reachable, so Android and AndroidX classes cannot be compiled).

Start by telling me in 3 lines what the handoff says is done and what you will do now, then do it.

---
