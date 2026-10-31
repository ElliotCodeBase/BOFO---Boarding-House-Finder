# BOFO Android port plan (Stage 0, v2.0.0)

Source audited: repo at v1.20.0, 83 Java files, about 12,200 lines. Target: `bofo-android/`, Java 17, one Activity + Fragments, XML + ViewBinding, Material 3, Gradle Groovy. Backend (Supabase) unchanged. Not run in Android Studio (no Android SDK in the sandbox).

## 1. Findings that change the plan
1. **`java.net.http.HttpClient` does not exist on Android.** Used in `SupabaseClient`, `Connectivity`, `Router`, `TransitRoutes` (plus `WifiLocator` and the two tile retrievers, which are not ported). So "pure Java" classes `Router` and `TransitRoutes` are copied but their HTTP calls move to OkHttp behind one small `Http` helper in `util/`. Everything else in them stays.
2. **`javafx.application.Platform.runLater` is in `Async`, `Connectivity`, `Outbox` (comment), `AuthService` (1 call), `Main`.** All replaced by one `Async` (ExecutorService + main `Handler`) with the same method names, so callers barely change.
3. **File paths:** `DiskCache.root()`, `SessionStore`, `Outbox`, `Offline` use `user.home/.bofo` or Gluon `StorageService`. Replace with `context.getFilesDir()` / `getCacheDir()` through one `AppStorage.init(Context)` set in `BofoApp`.
4. **`AppConfig` reads `config.properties` from the classpath.** On Android: values come from `BuildConfig` (Supabase URL, anon key, web client ID, redirect, version) and the rest (map, routing, overpass, policy versions) from `assets/config.properties`. Same getter names, so services do not change. `isAndroid()` becomes `true`; `devLocation`, `dev*` removed or kept debug-only.
5. **Google sign-in is already native Credential Manager** in `src/android/.../GoogleSignInActivity.java` + `NativeGoogle.java`. That logic moves into `LoginFragment` (no separate translucent activity, no `bofo://google-signin` link). Needs an Android OAuth client (package + SHA-1) in Google Cloud.
6. **PayMongo return URL is a Supabase secret** (`BOOST_SUCCESS_URL` / `BOOST_CANCEL_URL` in `create-boost-checkout`), so I cannot assume it is a `bofo://` link. Plan: open checkout in Custom Tabs, and on `onResume` (and on `bofo://boost-done` if you point the secret there) re-fetch the boost status. Backend stays unchanged.
7. **Session encryption:** `SessionStore` is AES-256-GCM with the key in a sibling file. Plan prefers `EncryptedSharedPreferences` as you asked, but Jetpack `security-crypto` is deprecated in recent releases (verify in Stage 2). Fallback: keep the current AES-GCM code and wrap the key with the Android Keystore. Decision at Stage 2, stated in one line.
8. **Polling, not realtime:** `ChatView` polls (3 s, backs off to 12 s). Port as a `Handler.postDelayed` loop tied to the Fragment lifecycle (stop in `onPause`).
9. **Map is the biggest risk** (`MapPane` 1153 lines + `VectorTileRetriever` 482 + `Mvt` 241 + `MinimalTileRetriever` 96). All four are replaced by MapLibre Native Android with one style JSON; no tile decoding code is ported.

## 2. Pure-Java classes: copy as-is (remove JavaFX imports / paths only)
`model/*` (7), `util/Geo`, `Fuzzy`, `Validate`, `Pkce`, `ErrorText`, `Regions`, `Cache`, `Trip`, `TripPlanner`, `TransitMatch`, `LocalTransit` (+ `local-transit.json` into `assets/`), `service/BhService`, `ChatService`, `BoostService`, `BofoAiService`, `VerificationService` (they only call `SupabaseClient`; checked: no JavaFX imports).
Copy with small edits: `AppConfig`, `SupabaseClient` (OkHttp), `AuthService` (1 `Platform` call + browser opener), `SessionStore`, `DiskCache`, `Offline`, `Outbox`, `Connectivity` (ConnectivityManager callback), `Async`, `Router`, `TransitRoutes`.
Android's `org.json` replaces the library `org.json` (same API). Min SDK 26 keeps `java.time`. Some Java 11+ APIs used in the desktop code (`String.isBlank`, `List.of`, `Stream.toList`) may be flagged by Android lint on API 26; replace them or enable core-library desugaring when lint says so (check in Stage 2).

## 3. Class map: JavaFX -> Android
| JavaFX class | Android replacement | Stage |
|---|---|---|
| `app/Main` | `BofoApp` (Application) + `MainActivity` (single Activity, NavHost, deep link, insets) | 1 |
| `ui/SplashView` | `SplashFragment` (+ mascot drawable) | 1 |
| `ui/Shell`, `StudentShell`, `OwnerShell`, `AdminShell`, `Nav`, `DockIcon`, `Icons`, `Glyphs` | `StudentFragment` / `OwnerFragment` / `AdminFragment` hosting a `BottomNavigationView` + child NavHost; vector drawables for icons; owner "Create" tab = custom blue squircle drawable; `Nav.toast` -> `Snackbar`/`Toast`; `Nav` keyboard code -> `windowSoftInputMode=adjustResize` + `WindowInsetsCompat` | 1, 8 |
| `ui/Ui`, `bofo.css`, `Switch`, `Popup`, `FitPane`, `AppStorage` | `colors.xml`, `themes.xml`, `styles.xml`, shape drawables, `MaterialSwitch`, `BottomSheetDialogFragment`/`PopupMenu`; `State` -> `ViewModel` | 1 |
| `ui/LoginView` | `LoginFragment` (email code, password, Google via Credential Manager, deep link return) | 3 |
| `ui/RoleView` | `RoleFragment` | 3 |
| `ui/ProfileSetupView`, `ProfileForm`, `PersonalInfoView` | `ProfileSetupFragment`, shared `ProfileFormHelper`; `Capture` -> `ActivityResultContracts.TakePicture` / `PickVisualMedia` | 3 |
| `ui/ProfileView` (+ Settings, About) | `ProfileFragment`, `SettingsFragment` (location `MaterialSwitch`, version from `BuildConfig`) | 1, 3 |
| `ui/LegalOverlay`, `LegalText` | `LegalSheet` (BottomSheetDialogFragment, same text) | 3 |
| `ui/BhCard`, `CardGrid`, `Skeleton`, `AdSlot`, `LiveDot` | `item_bh_card.xml` + `BhAdapter` (RecyclerView, grid), `ShimmerView`/skeleton drawable, ad slot view, `LiveDot` custom View | 4 |
| `ui/HomeView`, `ExploreView`, `SavedView`, `CampusPicker`, `FilterSheet`, `PriceRange`, `AmenityPicker` | `HomeFragment`, `ExploreFragment`, `SavedFragment`, `CampusPickerSheet`, `FilterSheet` (`RangeSlider`), `AmenityChips` | 4 |
| `ui/BhDetailView` | `BhDetailFragment` (Glide photo pager, amenities) | 4 |
| `ui/MapPane`, `MapCard` | `MapFragment`/`MapCardView` on MapLibre; markers = GeoJSON SymbolLayer; radius slider + college search = overlay views | 5 |
| `util/VectorTileRetriever`, `Mvt`, `MinimalTileRetriever`, `META-INF/services/...TileRetriever` | **Not ported.** `assets/bofo-map-style.json` (OpenFreeMap vector source, BOFO colors, no labels / POI icons) | 5 |
| `util/Locator`, `WifiLocator` | `LocationHelper` (FusedLocationProviderClient + permission launcher). Windows / browser / Wi-Fi code is dropped (PC-only) | 5 |
| `ui/DirectionsCard` | `DirectionsCardView` (TabLayout / segmented control that fits the width) | 6 |
| `util/Router`, `TripPlanner`, `TransitRoutes`, `TransitMatch`, `LocalTransit`, `Trip` | copied; HTTP via OkHttp; JUnit from `dev/TripLogicCheck.java` | 6 |
| `ui/ChatsView`, `ChatView` | `ChatsFragment`, `ChatFragment` (RecyclerView, polling loop, outbox) | 7 |
| `ui/BofoPanel`, `Mascot` | `BofoPanelSheet` + `MascotView` (custom View, `ObjectAnimator` for thinking / sitting / running in circles / sleeping) as an overlay in `MainActivity` | 7 |
| `ui/DashboardView`, `ListingsView`, `ListingFormView`, `BoostView`, `PaymentsView`, `VerificationView` | `DashboardFragment`, `ListingsFragment`, `ListingFormFragment`, `BoostFragment` (Custom Tabs), `PaymentsFragment`, `VerificationFragment` | 8 |
| `ui/QueueView`, `ReportsView` | `QueueFragment`, `ReportsFragment` | 8 |
| `service/NativeGoogle`, `android/GoogleSignInActivity` | `GoogleSignIn` helper inside `LoginFragment` (Credential Manager, then `/auth/v1/token?grant_type=id_token`) | 3 |
| `util/Capture` (custom PNG encoder) | Activity Result APIs, compress with `Bitmap.compress(JPEG)` | 3 |
| `dev/TripLogicCheck.java` | JUnit tests in `app/src/test` | 2, 6 |
| `.github/workflows/android.yml`, GluonFX `pom.xml` parts | Left alone (old path). Optional new Gradle workflow in Stage 9 | 9 |

## 4. Libraries (versions to verify when each stage starts)
AndroidX AppCompat/Fragment/Navigation/Lifecycle, Material 3, ConstraintLayout, RecyclerView, SwipeRefreshLayout, **OkHttp**, **Glide**, **MapLibre Native Android** (`org.maplibre.gl:android-sdk`), Play Services Location, **androidx.credentials** + `googleid`, `androidx.browser` (Custom Tabs), JUnit 4. No Kotlin plugin in Gradle; some AndroidX / MapLibre artifacts are Kotlin-built internally, which is fine (app code stays Java). Say so if any stage adds a library.

## 5. Risks
| Risk | Why | Mitigation |
|---|---|---|
| Map look | BOFO style was drawn by a custom renderer | Write a MapLibre style over OpenMapTiles layers (water, landcover, park, transportation, building), no symbol layers for labels / POIs. Check against OpenFreeMap's current schema in Stage 5. Keep attribution visible. |
| Google sign-in | Needs Android OAuth client with the **debug SHA-1** (and release SHA-1 later) | Stage 3 gives exact Google Cloud + Supabase steps; web client ID stays `google.webClientId`. |
| Deep links | `bofo://auth-callback` needs `singleTask` + `onNewIntent` | Done in `MainActivity`; must also stay in Supabase redirect URLs. |
| PayMongo return | Return URL is a backend secret | Refresh on resume; optional `bofo://boost-done` if you set the secret (your call, backend not touched by me). |
| Session security | `security-crypto` deprecated | Decide in Stage 2 (see finding 7). |
| Main-thread network | Android throws `NetworkOnMainThreadException` | Every service call goes through `Async.run`. |
| Offline cache size | Tile cache gone, MapLibre has its own ambient cache | Cap with `OfflineManager.setMaximumAmbientCacheSize`. |
| Cleartext | Manifest has `usesCleartextTraffic=false` | Keep; all endpoints are https. |
| Size of stages 5 and 8 | Map and Owner form are large | Split 5a/5b and 8a/8b if needed. |

## 6. Decisions made in Stage 0
- `minSdk 26`, `compileSdk`/`targetSdk` = current stable at Stage 1 (verify), Java 17, ViewBinding on, Groovy DSL, version catalog not used (plain strings, fewer moving parts).
- Single Activity, Navigation component with Safe Args **off** (Bundle args by hand, no extra plugin).
- Config: `local.properties` (git-ignored) supplies `supabase.url`, `supabase.anonKey`, `google.webClientId` with fallbacks to the same public values as `config.properties`, so a fresh clone still builds.
- Version line: Android starts at **v2.0.0** (this stage); stage N = **v2.0.N**. The JavaFX app stays at v1.20.0 and is not touched.

## 7. Stage 2 findings (v2.0.2)
- **Android's `org.json.JSONException` is a checked exception** (the desktop `org.json` library throws an unchecked one). Every method that calls `getString`, `getDouble`, `getJSONObject`... must declare `throws JSONException` (done in the models' `from(...)`) or sit inside a method that already `throws Exception` (all services do). Android's `JSONArray` has no `isEmpty()`: use `length() == 0`. Keep this in mind for every ported class from now on.
- Session storage decision: **no `security-crypto`**. `SessionStore` keeps the same AES-256-GCM idea, but the key is generated inside the Android Keystore (never in a file).
- Java 11+ calls removed from ported code: `isBlank` / `strip` -> `trim().isEmpty()` / `trim()`, `URLEncoder.encode(String, Charset)` -> `util/Url`, `List.of` -> `Arrays.asList`, `Files.readString/writeString/move` and `InputStream.readAllBytes` -> classic streams, `java.net.http` -> OkHttp. Records are kept (the Android build tools desugar them; Android lint is the final judge).
- `Connectivity.OFFLINE` is now a `LiveData<Boolean>` (observe it from the UI); `Outbox.userId` is a hook that `AuthService` sets in Stage 3.
- Unit tests need the real `org.json` library (`testImplementation 'org.json:json'`), because `android.jar` ships only stubs.

## 8. Stage 3 findings (v2.0.3)
- The user table is **`public.users`** (not `profiles`). There is no trigger that creates the row: the app inserts it (RLS `users_self_insert`: own id, role student / owner, unverified). A DB trigger (`protect_users`) silently keeps `role`, `is_verified`, `verification_status` unchanged on client updates; role changes go through RPC `switch_role`, account deletion through RPC `delete_my_account`. Avatars: public bucket `avatars`, path `{uid}/avatar.jpg`. Agreements: `user_agreements(user_id, document_type, document_version)`.
- Routing (`ui/AuthRouter`): no session -> login; no `users` row -> role chooser; empty `full_name` -> profile setup; else shell. Global actions in `nav_graph.xml` clear the back stack. A 401 on the profile call signs out.
- `AuthService` keeps the session in memory + `SessionStore`, refreshes it 60 s before expiry inside `token()` (SupabaseClient calls that on every request) and sets `Outbox.userId`.
- The JavaFX `AuthService`, `LoginView`, `RoleView`, `ProfileSetupView` and `LegalText` were not in the zips this stage was built from, so the Android versions were written from the live Supabase schema; `ui/LegalText.java` holds new short texts, to be replaced with the desktop wording.
- Owner / admin shells are still Stage 8: every role opens the student shell for now.

## 9. Stage 4 findings (v2.0.4)
- List screens share three small helpers: `ui/ListArea` (grid, skeleton, pull to refresh, message + retry), `util/BrowseState` (colleges, my profile, chosen campus, radius, Explore filters; `CHANGED` ticks when any of them changes) and `util/Favorites` (saved ids, optimistic heart). `ShellFragment` resets both when a fresh shell is created (new sign-in).
- Home needs no location yet: the reference point is the chosen campus (saved as `users.default_college_id`); without one the whole list shows. Device location and the map arrive in Stage 5 (Settings > Use my location is still only stored).
- Explore searches everywhere (no radius) and sorts nearest-first when a campus is chosen. Max price and amenities are sent to Supabase; min price is applied on the phone (`BhService.Filter` has no min price).
- Detail screen is a Navigation destination (`bhDetailFragment`, argument `id`) opened from any tab with `BhDetailFragment.open(fragment, id)`. `ShellFragment` remembers its tab so Back returns to it.
- Reviews come from `BhService.reviews` (`reviews_v`); the column names for reviewer name / photo were not visible from the sandbox, so the screen tries `reviewer_name`, `full_name`, `student_name`, `name` and `reviewer_photo`, `photo_url`, `avatar_url`, `student_photo`.

## 10. Stage 5 findings (v2.0.5)
- MapLibre Native Android: package `org.maplibre.android.*` (the old `com.mapbox.mapboxsdk.*` names are gone since 11.0). From **13.0.0** `org.maplibre.gl:android-sdk` uses Vulkan by default; `org.maplibre.gl:android-sdk-opengl` is the OpenGL ES build (switch to it if an emulator or old phone shows a blank map). Version 13.0.0 is the one named in the MapLibre newsletter of March 2026; a newer one may exist (look at Maven Central).
- Style: `assets/bofo-map-style.json`, one vector source `https://tiles.openfreemap.org/planet` (OpenMapTiles schema: `landcover`, `park`, `landuse`, `water`, `waterway`, `building`, `transportation`), no `glyphs` / `sprite` and no symbol layers, so no labels or POI icons. The layer and class names were written from the OpenMapTiles schema, not checked against the live tiles.
- Data layers (added from code in `MapCardView.addLayers`): GeoJSON sources `places-src`, `radius-src`, `campus-src`, `user-src`; layers `radius-fill`, `radius-line`, `campus-dot`, `user-halo`, `user-dot`, `places` (SymbolLayer, icon from the feature property `icon`, bigger when `sel` = 1). GeoJSON text comes from the pure class `util/MapGeo` (unit tested).
- `MapCardView` creates its MapView lazily (`activate`) and follows the lifecycle of the owner you pass (Fragment view lifecycle). It never asks for location itself.
- Location: `util/LocationHelper` (one per Fragment, created with the Fragment). `requestOnce` for Near me / locate, `startWatching` for the live dot (only started when the permission is already granted). Honours Settings > Use my location.
- `BrowseState` gained `nearMe`, `userLat`, `userLng`, `refLat()`, `refLng()`, `hasRef()`: the reference point is the campus, else the phone (Near me), else none (whole list). Explore still uses only the campus.

## 11. Stage 6 findings (v2.0.6)
- The JavaFX trip code was already free of JavaFX; the only Android edits are: HTTP through `util/HttpText` (OkHttp, shared `Http` client) instead of `java.net.http`; `List.of` / `String.isBlank` replaced (minSdk 26); checked `JSONException` handled; `LocalTransit` reads `assets/local-transit.json` through `LocalTransit.init(Context)` (called in `BofoApp`) instead of a classpath resource. `TransitMatch` is byte-for-byte the desktop file.
- `Async.fanout()` (cached pool) is what lets `TripPlanner.planWithin` wait on its own sub-task from a pool thread; do not move it to `Async.pool()`.
- Routing servers (defaults in `AppConfig`): `routing.openstreetmap.de` foot and car (OSRM format), Valhalla `valhalla1.openstreetmap.de`, Overpass `overpass-api.de` with `overpass.kumi.systems` as the second server. All are free public services and may be slow or refuse; every path degrades to an estimate.
- `LocalTransit.parse` does NOT densify a `path` (only `stops`): matching uses the nearest vertex, so a typed `path` needs a point about every 100 m.
- Map route layers (added in `MapCardView.addLayers`, under the dots and pins): GeoJSON sources `route-src` (LineString, property `approx` 0 / 1), `route-start-src`, `route-end-src`; layers `route-casing`, `route-line` (approx = 0), `route-dash` (approx = 1), `route-start`, `route-end`. GeoJSON text from `MapGeo.lineJson`.
- Pure rules for the card are in `util/DirectionsRules` so they are unit tested; `DirectionsCardView` only moves views.

## 12. Stage 7 findings (v2.0.7)
- `ChatService`, `Outbox`, `Conversation`, `Message` were already ported (Stage 2) and are used unchanged. The JavaFX `ChatsView` / `ChatView` were NOT in the zip this stage was built from, so the two screens were written from `ChatService` and this plan.
- Screens: `ui/ChatsFragment` (tab, inbox), `ui/ChatFragment` (nav destination `chatFragment`, args `convId`, `title`, `place`; open with `ChatFragment.open(fragment, convId, title, place)`). Pure rules in `util/ChatRules` (merge, poll delay, ticks, time labels, preview, badge), unit tested.
- Polling: `Handler.postDelayed` chain in `ChatFragment`, started in `onResume`, cut in `onPause` and `onDestroyView`. Delay 3 s + 1.5 s per quiet tick, max 12 s. One tick = `Outbox.flushAsync()` + `fetchNewer` (or the first page when the chat is empty) + `fetchMyRead` (only when I have a one-tick message).
- Outbox: every send goes `Outbox.add` first, the bubble shows a clock until the Outbox says `sent`. `BofoApp` flushes the Outbox whenever `Connectivity.OFFLINE` turns false. A refused message (`Outbox.Listener.failed`) is kept only in memory as a red bubble: tap = send again.
- Conversation per (student, owner, place) comes from RPC `get_or_create_conversation` (`ChatService.openConversation`), called by the **Chat with owner** button.
- Not done in Stage 7 (still in the plan table row for stage 7): `BofoPanelSheet` + `MascotView`. Moved to the start of Stage 8 (8a).

