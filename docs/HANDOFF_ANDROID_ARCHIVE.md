# Archived Android handoff entries (older than the newest 3)

# Handoff (Android) - v2.0.4 Stage 4: Home, Explore, Saved, place detail (2026-10-30)
- **PR title:** `v2.0.4 - Android port: Home, Explore, Saved, place detail, campus picker, filters (Stage 4)`

## Done
- `ui/HomeFragment`: campus button + nearest-college chips (from the chosen campus, else the middle of your region, else A-Z), New / Hot / Best rated tabs, rent row (Lowest / Middle / Highest via `util/PriceRange`), radius slider (only with a campus), offline banner, card grid.
- `ui/ExploreFragment`: search (400 ms debounce, name or address), `ui/FilterSheet` (price `RangeSlider` 0-10,000 step 100, amenity chips with `Fuzzy` search; must have ALL ticked), sort tabs, result count.
- `ui/SavedFragment`: hearted places from `BhService.favorites()` (offline copy works); un-hearting removes the card at once.
- `ui/BhDetailFragment` (+ `fragment_bh_detail.xml`, `item_review.xml`): photo pager, price (per bed / per room), rating, address, beds, free utilities, amenities, call / Facebook / e-mail buttons (system apps), reviews, disabled "Chat with owner" (Stage 7). Opens from any tab with `BhDetailFragment.open(fragment, id)`; nav destination `bhDetailFragment`.
- `ui/CampusPickerSheet`: search + list, "No campus" row, saves `users.default_college_id` through `ProfileService.update`.
- Shared: `ui/BhAdapter` (+ `Skeleton`), `ui/ListArea`, `ui/Chips`, `util/BrowseState`, `util/Favorites`, `util/ListRules`, `util/Money`, `util/PriceRange`; `ShellFragment` now shows the real tabs, resets `BrowseState` / `Favorites` on a fresh shell and remembers its tab when a screen is pushed on top.
- Libraries added: `recyclerview` 1.4.0, `swiperefreshlayout` 1.1.0, `viewpager2` 1.1.0 (versions from memory, not looked up). Version 2.0.4 / versionCode 204; README, CHANGES, plan (section 9) updated.
- **Supabase:** nothing changed, no migration.
- **Tests:** 13 new JUnit tests in `PriceRangeTest` and `ListRulesTest` (63 in total). The JUnit files themselves were NOT run (no JUnit jar in the sandbox this time); instead the same assertions (21 checks) were run on the JVM through a small main class against the real `Money`, `PriceRange`, `ListRules`, `Geo`, `BoardingHouse`, `College` sources (with `org.json` stubs): all passed. The 50 older tests were not re-run.

## Next stage
**Stage 5 - Map (MapLibre) + location** (v2.0.5; split 5a / 5b if needed): `assets/bofo-map-style.json` (OpenFreeMap vector source, BOFO colours, no labels / POI icons), `MapFragment` / `MapCardView` on `org.maplibre.gl:android-sdk` (check the current version), markers as a GeoJSON SymbolLayer, radius slider + college search overlays, `util/LocationHelper` (FusedLocationProviderClient + runtime permission, honours Settings > Use my location), live dot, map on Home (and a "show on map" action from the detail screen), attribution visible, `OfflineManager.setMaximumAmbientCacheSize`. Reuse `BrowseState` (campus, radius) and `BhService.list`; Home currently has no map and no "closest to you" mode, add it with location.

## Decisions made
- Home needs no location in this stage: distance reference = the chosen campus; no campus = the whole list (up to 60 cards, featured pinned first, as in `BhService.list`).
- Explore has no radius; with a campus it sorts nearest-first. Min price is filtered on the phone, max price and amenities on the server.
- One shared `BrowseState` (campus, radius, filters) instead of per-screen state, so Home and Explore agree. It is in memory only (campus is also saved on the profile).
- Cards: 2 columns (3 from 600 dp wide), heart toggles optimistically and reverts with a toast when the server refuses.
- Reviews are read-only here (no "write a review" yet). Place detail does not show a map.
- Every role still opens the student shell (owner / admin shells are Stage 8).

## File map (Stage 4)
| JavaFX | Android |
|---|---|
| `ui/BhCard`, `CardGrid`, `Skeleton` | `res/layout/item_bh_card.xml`, `item_bh_skeleton.xml`, `ui/BhAdapter`, `ui/ListArea` |
| `ui/HomeView`, `PriceRange` | `ui/HomeFragment`, `util/PriceRange` |
| `ui/ExploreView`, `FilterSheet`, `PriceRange`/`AmenityPicker` | `ui/ExploreFragment`, `ui/FilterSheet`, `ui/Chips` |
| `ui/SavedView` | `ui/SavedFragment` |
| `ui/CampusPicker` | `ui/CampusPickerSheet` |
| `ui/BhDetailView` | `ui/BhDetailFragment` + `fragment_bh_detail.xml`, `item_review.xml` |
| (new) | `util/BrowseState`, `util/Favorites`, `util/ListRules`, `util/Money` |

## Known issues / not verified
- **Not opened or built in Android Studio / Gradle.** What WAS checked: every Java file passes a `javac` parse (the only errors left are missing Android / AndroidX / generated-binding symbols, which only Gradle can resolve); the pure classes run on the JVM (above); all XML is well-formed; every `@string / @color / @drawable / @style` reference and every `R.*` use in the new Java resolves; view-binding ids used in the fragments exist in their layouts.
- **Not checked at all (needs Gradle / a device):** every AndroidX / Material call in the new screens (`RangeSlider`, `Slider`, `TabLayout`, `ViewPager2`, `SwipeRefreshLayout`, `ListAdapter` payload binding), Glide with a `null` url, the `include` + view-binding of `view_list_area.xml`, Android lint, and how the new layouts look on a real phone (sizes were chosen by eye: card photo 130 dp, detail photo 260 dp).
- **Reviews:** the column names for reviewer name and photo in `reviews_v` were guessed (see plan section 9). If reviews show "Student" and no photo, send me one row of `reviews_v` (Supabase > Table editor).
- Supabase rows for `bh_cards.amenity_names`, `rooms`, `bh_contacts` were read from `BhService` code, not from the live project this time.
- Facebook link: a bare name becomes `https://m.me/<name>`; a value with a dot or slash gets `https://` in front. Check with a real listing.
- The filter sheet is not scrollable as a whole (only the amenity list scrolls): on a very small or landscape screen the buttons may be pushed down.
- `Settings > Use my location` still only stores the switch (no permission asked until Stage 5).
- Sign-in on a second account in the same session relies on `ShellFragment` resetting `BrowseState` / `Favorites`; list caches (`Cache`, `Offline`) are keyed per query, not per user: check that two accounts on one phone do not see each other's Saved list offline (`Offline` key `favorites`).

## How I test this stage
1. Unzip the changes zip over the repo, open `bofo-android`, sync Gradle (new libs: recyclerview, swiperefreshlayout, viewpager2). No Supabase or Google steps are new.
2. **Unit tests:** `gradlew.bat testDebugUnitTest` in `bofo-android`. Expect 63 tests passed.
3. Sign in. **Home:** grey skeleton cards, then cards (photo, name, price, rating, beds, free items). Tap the tabs New / Hot / Best rated: the list changes; the rent row shows Lowest / Middle / Highest (or just "Rent" for one price).
4. Tap **Pick a campus** (or a chip): choose one. Cards now show distances, the radius slider appears: drag it, the list reloads. Close and reopen the app: the campus is still chosen. Choose "No campus": the slider disappears.
5. Tap a **heart** on a card: it turns red at once. Open **Saved**: the place is there; tap the heart there: it leaves the list. Airplane mode, Saved still opens (last copy).
6. **Explore:** type part of a name or street: results update after a short pause. **Filters**: move the price slider, type "aircon" or a typo like "wify" in the amenity box, tick one, **Show results**: the button reads "Filters (n)". **Reset** clears them.
7. Tap a card: **detail** opens. Swipe the photos (counter 1 / n), heart works, Call opens the dialer, Facebook / E-mail open their apps, reviews list. Back returns to the SAME tab (try it from Explore and from Saved). "Chat with owner" is greyed out.
8. Pull down on a list to refresh; airplane mode on Home: "You are offline" banner + last copy, or the "No connection" message with Try again. Send me the red text of anything that fails, or the first Gradle error.

## Paste-this prompt for the next AI
You are taking over a native Android (Java only, XML + Fragments, Material 3, Gradle Groovy) port of the app BOFO - Boarding House Finder. The attached zip is the latest checkpoint; the old JavaFX source is still in the repo as reference (attach the original repo zip too if `src/` is missing), the new project is in `bofo-android/`.

1. Read `CLAUDE.md`, then the top entry of `HANDOFF_ANDROID.md`, then `ANDROID_PORT_PLAN.md` (sections 3, 7, 8 and 9). Do not read the rest of the repo until a task needs it (use `grep -n`, then read only the needed lines).
2. Follow every rule in `HANDOFF_ANDROID.md` and `CLAUDE.md`: Java only, Supabase backend unchanged unless a feature needs it (the user allowed changes, add a migration file and say what changed), `applicationId com.bofo.finder`, no secrets committed, version bump on every stage (`BuildConfig.VERSION_NAME` shown in Profile > About and Settings, PR title, `CHANGES.md`, `HANDOFF_ANDROID.md`). Every change is delivered as a zip with ONLY the changed files. Remember: Android `org.json` throws a CHECKED `JSONException` and has no `JSONArray.isEmpty()`; the profile table is `public.users`.
3. Current stage: **Stage 5 - Map (MapLibre) + location (v2.0.5)**. Last completed: **Stage 4 (v2.0.4)**. Known issues: see the handoff.
4. Do only the current stage. If it is too big, split it (5a, 5b...). Never leave half-written files.
5. At the end of the stage, and at about 70% of your budget, deliver `BOFO-android-stageN-FULL.zip`, `BOFO-android-v2.0.N-changes.zip` (with `CHANGED_FILES.txt`) and an updated `HANDOFF_ANDROID.md` that ends with this same resume prompt filled in for the next stage. Put everything in zips.
6. You cannot run Android Studio: never claim it builds or runs; say what was and was not checked (a JDK is available in the sandbox: `javac` may be missing from PATH, then run it with `java -m jdk.compiler/com.sun.tools.javac.Main`; there may be no JUnit jar, so compile the pure-Java classes with small `org.json` stubs and run a plain `main` check; Maven / Google repositories are NOT reachable, so Android and AndroidX classes cannot be compiled).

Start by telling me in 3 lines what the handoff says is done and what you will do now, then do it.

---

# Handoff (Android) - v2.0.3 Stage 3: login, roles, profile setup (2026-10-30)
- **PR title:** `v2.0.3 - Android port: login (Google, e-mail code, password), roles, profile setup, settings (Stage 3)`

## Done
- `service/AuthService`: Google native id token (Credential Manager), Google in the browser (PKCE + `bofo://auth-callback`), e-mail OTP code, e-mail + password (sign in / sign up), session restore + auto refresh, sign out, delete account (RPC `delete_my_account`). Sets `SupabaseClient.setTokenProvider` and `Outbox.userId`. Initialised in `BofoApp`.
- `service/ProfileService`: own row in `public.users` (fetch with offline copy, create, update, `switch_role`), avatar upload to bucket `avatars`, agreements into `user_agreements` (terms + privacy, owner agreement for owners).
- Screens: `LoginFragment`, `RoleFragment`, `ProfileSetupFragment` (also used for editing; camera through FileProvider, gallery through the system photo picker, square 512 px JPEG), `SettingsFragment`, `LegalSheet` + `LegalText`, `AuthRouter` (login / role / setup / shell decision), `SplashFragment` now restores the session and has a "Try again" button. Profile tab shows an account card (photo, name, e-mail, role) with Edit profile / Settings; version shows in Profile > About and at the bottom of Settings.
- `MainActivity` keeps the sign-in link (`bofo://auth-callback`) both on cold start and while open; `DebugFragment` now signs in through the real `AuthService`.
- Helpers: `util/AuthLinks`, `util/Images`, `util/AppPrefs`, `Validate.{username, age, phone, email}`.
- Libraries added: `androidx.credentials` 1.5.0 (+ `credentials-play-services-auth`), `googleid` 1.1.1, `exifinterface` 1.3.7, `glide` 4.16.0. Version 2.0.3 / versionCode 203; README, CHANGES, plan (section 8) updated.
- **Supabase:** inspected only (tables, RLS policies, RPCs, buckets). **Nothing was changed**, no migration needed.
- **50 JUnit tests** (41 old + 9 new: `AuthLinksTest`, `ProfileRulesTest`). The 9 new ones were compiled and run for real with JDK + JUnit 4.13.2: OK. The older 41 were not re-run this time (their code did not change except `Validate`, which only gained methods).

## YOU must do these once (the app cannot sign in with Google before)
1. **Supabase > Authentication > URL Configuration > Redirect URLs:** add `bofo://auth-callback`.
2. **Supabase > Authentication > Providers > Google:** enabled, with the Web client ID + secret (already done for the PC app). Leave "Skip nonce check" off unless Google sign-in says "nonce" in the error.
3. **Google Cloud > APIs & Services > Credentials > Create credentials > OAuth client ID > Android:** package name `com.bofo.finder` and the **debug SHA-1**. Get it with `gradlew.bat signingReport` (in `bofo-android`), line `SHA1` under `Variant: debug`; or `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android`. The Web client ID in `config.properties` (`google.webClientId`) stays the one the app uses; the Android client only has to EXIST for that package + SHA-1. Add the release SHA-1 later (Stage 9).
4. **E-mail code login:** Supabase > Authentication > Email Templates > "Magic link" must contain `{{ .Token }}` (for example "Your code: {{ .Token }}"), otherwise the mail only has a link. For e-mail + password sign-up, keep "Confirm email" ON (the app then tells the user to check the mail) or turn it OFF to log in right away.
5. Test accounts: the Google consent screen is in testing mode, so only the test users you added can use Google sign-in.

## Next stage
**Stage 4 - Home, Explore, Saved (list screens)** (v2.0.4): `item_bh_card.xml` + `BhAdapter` (RecyclerView grid, Glide photos already a dependency), skeleton loading, `HomeFragment` (New / Hot / Best tabs, nearest-college row, review counts), `ExploreFragment` (search with `Fuzzy`, `FilterSheet` with price `RangeSlider` + amenity chips), `SavedFragment` (favorites), `CampusPickerSheet` (writes `default_college_id`), `BhDetailFragment` (photo pager, amenities, contact, reviews, "Chat with owner" button disabled until Stage 7). No map yet (Stage 5). Use `AuthService` / `ProfileService.fetchMe()` for the user's college and region. Read the `BhService` methods before writing any query.

## Decisions made
- `public.users` is the profile table; the row is created by the app after login (no DB trigger). Only the full name is required; username, phone, age, sex, college and region are optional. `sex` is saved as `male` / `female` / `other`.
- After the role chooser the user always sees the profile form once (even if Google already gave a name).
- Every role opens the student shell until Stage 8 (owner / admin shells).
- Google: Credential Manager first, "Continue in the browser" link as fallback. The PKCE verifier is kept in private SharedPreferences so it survives the app being killed during the browser trip.
- The sign-in link arriving on a cold start is stashed (`AuthRouter.setPendingLink`) and finished by the splash.
- Avatar: one fixed file `avatars/{uid}/avatar.jpg`, URL gets `?v=timestamp` so Glide refreshes.
- `ui/LegalText.java` texts are NEW (the desktop `LegalText` was not available): replace with the desktop wording and have them reviewed before release.

## File map (Stage 3)
| JavaFX | Android |
|---|---|
| `service/AuthService` | `service/AuthService` (+ `service/ProfileService`) |
| `ui/LoginView` | `ui/LoginFragment` + `fragment_login.xml` |
| `ui/RoleView` | `ui/RoleFragment` + `fragment_role.xml` |
| `ui/ProfileSetupView`, `ProfileForm`, `PersonalInfoView` | `ui/ProfileSetupFragment` + `fragment_profile_setup.xml` |
| `ui/ProfileView` (settings, account) | `ui/ProfileFragment`, `ui/SettingsFragment` |
| `ui/LegalOverlay`, `LegalText` | `ui/LegalSheet`, `ui/LegalText` |
| (new) | `ui/AuthRouter`, `util/AuthLinks`, `util/Images`, `util/AppPrefs`, `res/xml/file_paths.xml` (FileProvider) |

## Known issues / not verified
- **Not opened or built in Android Studio / Gradle.** What WAS checked: the new pure-Java classes + 9 tests compile and pass on the JVM; every Java file passes a `javac` syntax-level parse (symbol errors for Android / AndroidX classes were expected and ignored, because the sandbox cannot download android.jar or AndroidX); all XML is well-formed; every `@string / @color / @style / @drawable / @xml` reference, every view-binding id used in the fragments and every nav action id resolves.
- **Not checked at all (needs Gradle / a device):** the Credential Manager and googleid API calls (written from the library docs, versions 1.5.0 / 1.1.1: if a class or method name does not resolve, tell me the exact error), Glide `load(Bitmap)`, `ExifInterface` constructor with a stream, `MaterialSwitch` / exposed dropdown layouts, FileProvider + camera on a real phone, Android lint.
- Supabase `/auth/v1/verify` (type `email`) and `/auth/v1/token?grant_type=id_token` / `pkce` were written from the Supabase Auth API, not run against the live project (no test account is available in the sandbox).
- Editing the profile from the Profile tab pushes a new screen on top of the shell; Back returns to the same tab.
- Owner ID / selfie verification is Stage 8; an owner can already be created but cannot list yet.
- Deleting an account relies on `delete_my_account` cascading: if it fails with a foreign-key message, send me the red text.

## How I test this stage
1. Do the 5 steps in "YOU must do" above. Unzip the changes zip over the repo, open `bofo-android`, sync Gradle (new libs: credentials, googleid, exifinterface, glide).
2. **Unit tests:** `gradlew.bat testDebugUnitTest` in `bofo-android`. Expect 50 tests passed.
3. Run on an emulator with Google Play (needs a Google account added in the emulator) or a real phone. First launch: splash, then the **login screen**.
4. **Google:** tap *Continue with Google*, pick the account: expect the role screen (new user) or the app (existing user with a name). If the sheet says no account / error, tap *Continue in the browser*: after Google, the browser must return to the app by itself.
5. **E-mail code:** type an e-mail, *Send me a code*, enter the 6-digit code from the mail, *Verify*. **Password:** *Use a password instead*, *Create account* (or *Sign in* for an existing account).
6. **New user:** pick Student, tick the box, Continue, fill the form (name is required), take or choose a photo, Save: expect the app with the 5 tabs. **Profile tab:** your photo, name, e-mail, "Student". Close the app completely and open it again: it must go straight to the app (no login).
7. **Profile > Settings:** Edit profile (change the name, Save, Back: the tab shows the new name), the location switch stays where you leave it, Terms / Privacy open a sheet, *Switch to an owner account* then back, *Sign out* returns to login, *Delete my account* only on a throwaway account.
8. Airplane mode after login, reopen the app: it still opens the app (saved profile copy). Send me the red text of anything that fails, or the first Gradle error.

## Paste-this prompt for the next AI
You are taking over a native Android (Java only, XML + Fragments, Material 3, Gradle Groovy) port of the app BOFO - Boarding House Finder. The attached zip is the latest checkpoint; the old JavaFX source is still in the repo as reference (attach the original repo zip too if `src/` is missing), the new project is in `bofo-android/`.

1. Read `CLAUDE.md`, then the top entry of `HANDOFF_ANDROID.md`, then `ANDROID_PORT_PLAN.md` (sections 3, 7 and 8). Do not read the rest of the repo until a task needs it (use `grep -n`, then read only the needed lines).
2. Follow every rule in `HANDOFF_ANDROID.md` and `CLAUDE.md`: Java only, Supabase backend unchanged unless a feature needs it (the user allowed changes, add a migration file and say what changed), `applicationId com.bofo.finder`, no secrets committed, version bump on every stage (`BuildConfig.VERSION_NAME` shown in Profile > About and Settings, PR title, `CHANGES.md`, `HANDOFF_ANDROID.md`). Every change is delivered as a zip with ONLY the changed files. Remember: Android `org.json` throws a CHECKED `JSONException` and has no `JSONArray.isEmpty()`; the profile table is `public.users`.
3. Current stage: **Stage 4 - Home, Explore, Saved (v2.0.4)**. Last completed: **Stage 3 (v2.0.3)**. Known issues: see the handoff.
4. Do only the current stage. If it is too big, split it (4a, 4b...). Never leave half-written files.
5. At the end of the stage, and at about 70% of your budget, deliver `BOFO-android-stageN-FULL.zip`, `BOFO-android-v2.0.N-changes.zip` (with `CHANGED_FILES.txt`) and an updated `HANDOFF_ANDROID.md` that ends with this same resume prompt filled in for the next stage. Put everything in zips.
6. You cannot run Android Studio: never claim it builds or runs; say what was and was not checked (a JDK + JUnit are available in the sandbox to compile and run the pure-Java tests for real; Maven / Google repositories are NOT reachable, so Android and AndroidX classes cannot be compiled).

Start by telling me in 3 lines what the handoff says is done and what you will do now, then do it.

---

# Handoff (Android) - v2.0.1 Stage 1: project skeleton (2026-10-30)
- **PR title:** `v2.0.1 - Android port: Gradle skeleton, splash, bottom-nav shell, About (Stage 1)`

## Done
- `bofo-android/` is a Gradle (Groovy, Java 17, no Kotlin plugin) project: AGP 8.11.1, Gradle 8.13 (wrapper included), compileSdk/targetSdk 36, minSdk 26, ViewBinding + BuildConfig on, `applicationId com.bofo.finder`.
- `versionName 2.0.1`, `versionCode 201` in `app/build.gradle`. Profile > About shows `BuildConfig.VERSION_NAME` (`ProfileFragment`).
- `BuildConfig` fields `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GOOGLE_WEB_CLIENT_ID`, `AUTH_REDIRECT` are read from `../src/main/resources/com/bofo/finder/config.properties`, overridden by `bofo-android/local.properties` (git-ignored). Not used by code yet (Stage 2).
- Manifest: INTERNET, network state, fine + coarse location; `MainActivity` is `singleTask`, `adjustResize`, deep link `bofo://auth-callback`; cleartext off, `allowBackup=false`.
- Theme / colors / button + card styles ported from `bofo.css` (`colors.xml`, `themes.xml`, `styles.xml`); light only.
- Adaptive icon + splash/About mascot are vector drawables converted from `design/mascot/bofo-happy.svg` / the app-icon colors.
- `MainActivity` (edge-to-edge, insets for system bars + keyboard) hosts a NavHost: `SplashFragment` (1.2 s) -> `ShellFragment` (bottom nav: Home, Explore, Saved, Chats, Profile; placeholders except Profile).

## Next stage
**Stage 2 - Data layer (no UI)** (v2.0.2): models, `AppConfig` (from `BuildConfig` + `assets/config.properties`), `Http` (OkHttp), `SupabaseClient`, `SessionStore` (decide EncryptedSharedPreferences vs Keystore-wrapped AES-GCM, say which), `Cache` / `DiskCache`, `Connectivity`, `Offline` / `Outbox`, `Async` (ExecutorService + main Handler), JUnit for `Geo`, `Fuzzy`, `Validate`, `Pkce`, `ErrorText`, and a debug screen that fetches the boarding-house list.

## Decisions made
- AGP 8.11.1 + Gradle 8.13 + compileSdk 36 chosen from what I could confirm; Android Studio may offer a newer AGP: accept only via its Upgrade Assistant if sync complains.
- Libraries: appcompat 1.7.1, material 1.13.0, constraintlayout 2.2.1, fragment 1.8.9, navigation 2.9.8, junit 4.13.2 (navigation 2.9.8 and fragment 1.8.9 confirmed in the AndroidX stable list; the others from memory).
- Manifest does NOT declare CAMERA / storage / media permissions (the old GluonFX manifest did): the system camera app and system photo picker need none, and a declared-but-ungranted CAMERA permission makes the camera intent crash. Location permission is requested at runtime in Stage 5.
- Single Activity + Navigation component (no Safe Args); `ShellFragment` swaps tab fragments with `replace` (no tab back stack: Back leaves the app).
- Splash -> shell uses `popUpToInclusive`, so Back never returns to the splash.
- Light theme only (`MODE_NIGHT_NO`), as the desktop app.

## File map (Stage 1)
| JavaFX | Android |
|---|---|
| `app/Main` | `app/BofoApp`, `app/MainActivity` |
| `ui/SplashView` | `ui/SplashFragment` + `fragment_splash.xml` |
| `ui/Shell`, `StudentShell`, `DockIcon`, `Icons` | `ui/ShellFragment` + `fragment_shell.xml`, `menu/bottom_nav_student.xml`, `ic_nav_*.xml` |
| `ui/ProfileView` (About block only) | `ui/ProfileFragment` + `fragment_profile.xml` |
| `bofo.css`, `Ui` (colors, buttons, card) | `values/colors.xml`, `themes.xml`, `styles.xml` |
| `Mascot` (HAPPY) | `drawable/mascot_happy.xml` |

## Known issues / not verified
- **Not opened or built in Android Studio.** Checked here: all XML well-formed, every `@color/@string/@drawable/@id/...` reference resolves, (Correction in Stage 2: the Java syntax check I first reported for Stage 1 had not actually run, because the sandbox had no `javac`. With a JDK installed, all Stage 1 + 2 Java files compile apart from the missing AndroidX / generated-binding classes, which only Gradle can resolve.)
- AGP / library versions were not looked up on Maven; if Gradle sync fails on a version, change that one line in `app/build.gradle` or `build.gradle`.
- Nav icon paths are the standard Material shapes typed by hand; the floating pill nav of the desktop app is not reproduced yet (plain Material 3 bottom bar).
- `gradle-wrapper.jar` was downloaded from the Gradle GitHub repo (tag v8.13.0). If Android Studio complains, delete `gradle/wrapper/gradle-wrapper.jar` and let it regenerate, or use its bundled Gradle.
- `ShellFragment` is the student shell only; role routing comes in Stage 3 / 8.

## How I test this stage
1. Android Studio: **File > Open** `C:\Users\user\bofo-finder\bofo-android` (the folder, not the repo root). Use JDK 17 or newer (Settings > Build > Gradle > Gradle JDK). Let Gradle sync finish.
2. Create / pick an emulator (API 34+ is fine) and press Run.
3. Expect: purple-on-light splash with the mascot, "BOFO", "Boarding House Finder", about 1 s later the shell with 5 tabs; Home / Explore / Saved / Chats show a title and a "arrives in Stage N" line; **Profile** shows an About card with the mascot, "BOFO Finder" and **Version 2.0.1**.
4. Rotate the emulator: you stay on the same tab. Press Back on the shell: the app leaves (it does not go back to the splash). Check the app icon on the launcher (mascot on lavender).
5. Send me the first Gradle error text if sync or build fails.

---
# Handoff (Android) - v2.0.0 Stage 0: audit and plan (2026-10-30)
- **PR title:** `v2.0.0 - Android port: plan and project layout (Stage 0)`

## Done
- Audited the repo (v1.20.0, 83 Java files, about 12,200 lines). Wrote `ANDROID_PORT_PLAN.md` (class map, risks, decisions).
- Created `bofo-android/` layout (empty package folders, `.gitignore`, `README.md`). No Gradle files, no app code.
- Android version rule written into `MAP_FEATURE_CHANGES.md` and `CLAUDE.md` (section 5); `CHANGES.md` has the v2.0.0 entry.
- No `app/build.gradle` exists yet, so `versionName` / `versionCode` are first set in Stage 1 (`2.0.1` / code 201).

## Next stage
**Stage 1 - Project skeleton that builds** (v2.0.1): Gradle (Groovy) project, manifest + deep link, `BofoApp`, `MainActivity`, theme from `bofo.css`, adaptive icon from `design/mascot/*.svg`, `SplashFragment`, bottom-nav shell with placeholder tabs, Profile > About with `BuildConfig.VERSION_NAME`.

## Decisions made
- Android line: v2.0.0 = Stage 0, stage N = v2.0.N. versionCode = 200 + N.
- minSdk 26, Java 17, Groovy DSL, ViewBinding, Navigation component without Safe Args, no version catalog.
- HTTP = OkHttp (`java.net.http` does not exist on Android). Map = MapLibre with a hand-written style; the vector/raster tile code is not ported.
- Google sign-in logic moves from `GoogleSignInActivity` into `LoginFragment`.
- Session storage choice (EncryptedSharedPreferences vs Keystore-wrapped AES-GCM) is decided in Stage 2.
- PayMongo return: refresh on resume; backend secrets are not changed by me.

## File map
See the table in `ANDROID_PORT_PLAN.md` section 3 (old JavaFX class -> new Android class). New classes appear here as stages add them.

## Known issues / not verified
- Nothing was built or run: no Android SDK in the sandbox. Only text/Markdown and folder layout were produced.
- Library versions and the OpenFreeMap schema are not verified yet (done per stage).
- Lint compatibility of Java 11+ calls in copied desktop code is not checked yet (Stage 2).

## How I test this stage
Nothing to run. Check that `bofo-android/` exists next to `src/`, that `ANDROID_PORT_PLAN.md` reads correctly, and that `git status` shows only new files plus `CLAUDE.md`, `CHANGES.md`, `MAP_FEATURE_CHANGES.md`, `HANDOFF_ANDROID.md`.

---

# Handoff (Android) - v2.0.2 Stage 2: data layer (2026-10-30)
- **PR title:** `v2.0.2 - Android port: data layer, Supabase client, session store, tests (Stage 2)`

## Done
- Ported (same package names, same method names): models (`Amenity`, `BoardingHouse`, `College`, `Conversation`, `Message`, `Profile`, `Role`), `util/{Geo, Fuzzy, Validate, Pkce, ErrorText, Regions, Cache, Offline, Outbox}`, `service/{BhService, ChatService, BoostService, BofoAiService, VerificationService}`.
- Rewritten for Android: `config/AppConfig` (BuildConfig + `assets/config.properties`), `util/Http` (OkHttp 4.12.0), `service/SupabaseClient` (OkHttp: REST, RPC, auth, storage upload, signed URLs, edge functions, one quiet retry for GET), `service/SessionStore` (AES-256-GCM, key in Android Keystore), `util/DiskCache` (private files folder), `util/Async` (ExecutorService + main Handler, `Async.post`), `util/Connectivity` (`OFFLINE` is a `LiveData<Boolean>`, OkHttp probe), `util/Url`. `BofoApp` now initialises `AppConfig` and `DiskCache`.
- Debug screen (debug builds only): **Profile > Developer: data layer check** shows the build config, signs in with email + password (token in memory only), fetches boarding houses and colleges through `BhService`.
- **41 JUnit tests** (Geo, Fuzzy, Validate, Pkce incl. the RFC 7636 vector, ErrorText, Cache, Offline, DiskCache, Url, Regions, models). **They were compiled and run for real in the sandbox with JDK 21 + JUnit 4.13.2: OK (41 tests).** The Gradle task `testDebugUnitTest` itself has not been run.
- Version 2.0.2 / versionCode 202; README, CHANGES, plan (section 7) updated.

## Next stage
**Stage 3 - Login, roles, profile setup** (v2.0.3): `AuthService` (sets `SupabaseClient.setTokenProvider` and `Outbox.userId`), `LoginFragment` (email code, email + password, Google via Credential Manager), PKCE + `bofo://auth-callback` in `MainActivity.onNewIntent`, session restore in `SplashFragment`, role chooser, `ProfileSetupFragment` (camera/gallery), Settings (edit profile, location switch, sign out, delete account), `LegalSheet`. Tell the user exactly what to add in Google Cloud (Android OAuth client: package `com.bofo.finder` + debug SHA-1) and Supabase redirect URLs.

## Decisions made
- HTTP: OkHttp 4.12.0. Session crypto: Android Keystore AES-GCM (no deprecated `security-crypto`).
- Android `org.json` has a CHECKED `JSONException`: model `from(...)` methods declare `throws JSONException`; `JSONArray.isEmpty()` replaced by `length() == 0`.
- Java 11+ APIs removed from ported code (see `ANDROID_PORT_PLAN.md` section 7). Records kept.
- `Outbox` no longer imports `AuthService`: it reads `Outbox.userId` (a `Supplier<String>` that Stage 3 sets). `VerificationService.looksSharp` now takes an `android.graphics.Bitmap`.
- `DiskCache.slot()` (PC two-window testing) and the `BOFO_PROFILE` env var were dropped.
- Libraries added: `okhttp 4.12.0`, `lifecycle-livedata 2.9.2`; tests: `org.json:json:20240303`. `testOptions.unitTests.returnDefaultValues = true`.

## File map (Stage 2)
| JavaFX | Android |
|---|---|
| `config/AppConfig` | `config/AppConfig` (BuildConfig + assets) |
| `service/SupabaseClient` | same name, OkHttp |
| `service/SessionStore` | same name, Keystore AES-GCM |
| `util/Async`, `Connectivity`, `DiskCache` | same names, Android versions |
| `model/*`, `util/{Geo,Fuzzy,Validate,Pkce,ErrorText,Regions,Cache,Offline,Outbox}`, `service/{Bh,Chat,Boost,BofoAi,Verification}Service` | copied with the small edits above |
| (new) | `util/Http`, `util/Url`, `ui/DebugFragment` |
| `dev/TripLogicCheck.java` | Stage 6 |

## Known issues / not verified
- **Not opened or built in Android Studio / Gradle.** What WAS checked here: every non-UI Java file compiles with `javac --release 17` against OkHttp 3.13 and Android's `android.jar` stubs (so the checked `JSONException` problems were found and fixed); the 41 unit tests run green on the JVM; all XML is well-formed and every resource / view-binding id reference resolves. UI classes could only be checked for syntax (their AndroidX / generated classes need Gradle).
- OkHttp was compiled against 3.13.1 here but the app uses 4.12.0: the calls used exist in both (`RequestBody.create(MediaType, ...)` is the old argument order: deprecated warning in 4.x, still valid).
- Android API-level lint (anything newer than API 26 that slipped through) was NOT run; I removed every Java 9+ call I could find by grep. Android Studio's lint will show any left.
- RLS: all tables only answer SIGNED-IN users, so the debug fetch shows 0 boarding houses until you sign in with a real account (email + password). A Google-only account has no password: create or reset one in Supabase, or wait for Stage 3.
- `Connectivity` has no `ConnectivityManager` callback yet: coming back online is detected by the 4 s probe (same as the desktop app).
- `AppConfig` keys `policy.*`, `route.*`, `transit.*` come from `assets/config.properties`; map / tile keys of the desktop file were intentionally not carried over.

## How I test this stage
1. Unzip the changes zip over the repo, open `bofo-android` in Android Studio, sync Gradle (new libs: OkHttp, lifecycle-livedata, org.json for tests).
2. **Unit tests:** terminal in `bofo-android`: `gradlew.bat testDebugUnitTest` (or right-click `app/src/test` > Run Tests). Expect 41 tests passed.
3. Run the app on an emulator (needs internet). Profile tab > scroll down > **Developer: data layer check**. The top box must show your version `2.0.2 (202)`, the Supabase host, "anon key set", "google web client id set", network online.
4. Enter an email + password of a real BOFO account > **Sign in** > expect "Signed in". Tap **Fetch boarding houses**: expect "N boarding houses in X ms" and up to 8 names with price and beds. Tap **Fetch colleges**: a list of colleges. Turn on airplane mode and tap Fetch again: expect either the saved copy (same list) or a "No connection to the server" message, and the box shows `network OFFLINE`.
5. If anything fails, send me the red text in the output box or the first Gradle error.

## Paste-this prompt for the next AI
You are taking over a native Android (Java only, XML + Fragments, Material 3, Gradle Groovy) port of the app BOFO - Boarding House Finder. The attached zip is the latest checkpoint; the old JavaFX source is still in the repo as reference (attach the original repo zip too if `src/` is missing), the new project is in `bofo-android/`.

1. Read `CLAUDE.md`, then the top entry of `HANDOFF_ANDROID.md`, then `ANDROID_PORT_PLAN.md` (sections 3 and 7). Do not read the rest of the repo until a task needs it (use `grep -n`, then read only the needed lines).
2. Follow every rule in `HANDOFF_ANDROID.md` and `CLAUDE.md`: Java only, Supabase backend unchanged, `applicationId com.bofo.finder`, no secrets committed, version bump on every stage (`BuildConfig.VERSION_NAME` shown in Profile > About, PR title, `CHANGES.md`, `HANDOFF_ANDROID.md`). Remember: Android `org.json` throws a CHECKED `JSONException` and has no `JSONArray.isEmpty()`.
3. Current stage: **Stage 3 - Login, roles, profile setup (v2.0.3)**. Last completed: **Stage 2 (v2.0.2)**. Known issues: see the handoff.
4. Do only the current stage. If it is too big, split it (3a, 3b...). Never leave half-written files.
5. At the end of the stage, and at about 70% of your budget, deliver `BOFO-android-stageN-FULL.zip`, `BOFO-android-v2.0.N-changes.zip` (with `CHANGED_FILES.txt`) and an updated `HANDOFF_ANDROID.md` that ends with this same resume prompt filled in for the next stage. Put everything in zips.
6. You cannot run Android Studio: never claim it builds or runs; say what was and was not checked (a JDK + JUnit can be installed in the sandbox with apt to compile and run the pure-Java tests for real).

Start by telling me in 3 lines what the handoff says is done and what you will do now, then do it.

---

<!-- moved from HANDOFF_ANDROID.md -->
# Handoff (Android) - v2.4.6: bofo-announce LLM retry (backend only; app unchanged)
- Live logs showed `google/gemma-4-26b-a4b-it:free` and `google/gemma-4-31b-it:free` -> HTTP 429 and `nvidia/nemotron-3-ultra-550b-a55b:free` timing out, so every draft was `degraded:true` (template). `llm.ts` now uses up to 6 models + one 429 retry; function redeployed (v2). 22 node tests pass.
- Real fix for quality: set more working model ids (or a paid one) in the `LLM_MODELS` secret.

# Handoff (Android) - v2.4.5: AI shortcut in the owner chat menu
- `BoardingChatFragment` menu item `boarding_menu_announce_ai` -> `openAnnounce(true)` -> `AnnounceFragment.ARG_OPEN_AI` opens `AnnounceAiSheet` once (only when `state == null`). Version 2.4.5 / 20405. Live `app_flags.ai_enabled` was set to true on 2026-11-04. Not compiled here; not tested on a device.

# Handoff (Android) - v2.4.4 fix: Write for me did not show for the owner
- **Cause 1:** `app_flags.ai_enabled` was `false` on the live project, so `AnnounceFragment` hid the button (`AiFlag`). Owners will not see it until an admin turns **BOFO AI for users** back ON. Left unchanged on purpose (it is an admin choice).
- **Cause 2:** the edge function `bofo-announce` was never deployed and migration 031 never applied. Both are now done on project `zfxldzcoirqolflkkhrh` (function v1 ACTIVE, verify_jwt on; `ai_usage.kind` exists).
- **Code change:** `AnnounceFragment.showWriteForMe()` = AI on OR admin. Version 2.4.4 / 20404.
- **Not verified:** no live call with an owner login, no Gradle build, LLM secrets (`OPENROUTER_API_KEY`, `LLM_MODELS`) not checked (without them drafts are templates, `degraded:true`).

# Handoff (Android) - AI Announcement Writer: Step 7 of 7 done (QA + docs; feature complete, still v2.4.3 / 20403, NO version bump)
- **State:** documents only: `docs/ANNOUNCE_AI.md` (owner guide), `docs/ANNOUNCE_AI_TEST_MATRIX.md`, `FINAL-HANDOFF.md`, status section K. No app code, resource or `supabase/` file changed, so no bump and no `CHANGES_ANDROID.md` entry.
- **BLOCKER (fixed by `BOFO-android-v2.4.3-step7-fix-changes.zip`, the 5 files copied unchanged from the 2.4.2 zip; never unzip the full 2.4.2 zip over the repo):** the repo copy used in Step 7 was missing the Step 5 files (`util/AnnounceFormState.java`, `layout/item_announce_type.xml`, `drawable/bg_ann_badge_urgent.xml`, `drawable/bg_ann_badge_important.xml`, `test/.../AnnounceFormStateTest.java`); details in `FINAL-HANDOFF.md` section 0.
- **Tested:** 21 of 21 backend Node tests pass. JVM tests NOT re-run (no JUnit / Gradle in the sandbox). Nothing compiled against the Android SDK, nothing run on a device, function not deployed, migration 031 not applied.
- **Review:** no logic bug; nothing logged or stored; owner / ai_enabled / limit order correct; Java ids == backend ids. Low-risk leftovers are listed in `FINAL-HANDOFF.md` section 5.
- **Next action:** none for an AI. The user does the list in `FINAL-HANDOFF.md` section 7 (apply Step 5 zip, migration 031, deploy `bofo-announce`, build in Android Studio, live smoke test, native-speaker review, device checks).

# Handoff (Android) - AI Announcement Writer: Step 6 of 7 done (generate / review / use, v2.4.3 / 20403)
- **State:** `util/AnnounceDraftState.java` (pure: phases IDLE/LOADING/SUCCESS/ERROR, keeps the `Submission`, `first/again/adjust/changeLang/retry` each return ONE `Request` or null, `onResult(draft, error, retryAfter, field, problem)` ignores late/double results, `cancel`, `tick` countdown, `needsConfirmBeforeRegenerate`, `prepareUse`, `needsReplaceConfirm`). `ui/AnnounceAiSheet` has stage C (`ann_stage_result`: loading / error / draft) and calls `AnnounceAiService` itself (one service per sheet, cancelled in `onDestroyView`); `Use this` -> fragment result `announce_ai_use` {title, message} (Listener is now `onUse`). `AnnounceFragment`: `Write for me` button (`write_for_me`, hidden when `!AiFlag.on()`, `AiFlag.refresh` in `onResume`), asks before replacing existing text, never posts.
- **Rules kept:** no retry loop (retry = the owner's tap, same request, after the RATE_LIMITED wait); nothing logged or stored (draft, fields, sign-off name); edits are lost only after a confirmed regenerate; rotation returns to the form (draft not kept); `supabase/`, `BhGroupService.postAnnouncement`, `BoardingRules`, the service, `AnnounceDraft`, `AnnounceError` untouched.
- **Tested:** 18 new JVM tests (`AnnounceDraftStateTest`) + 33 older model/util announce tests = 51 pass, mutation-checked. **Stand-in JUnit and org.json** (the real junit jar was not in this sandbox); service tests (23) not re-run (unchanged). Sheet / fragment NOT compiled, not run on a device.
- **Needs the user's OK (not done):** apply `031_ai_usage_kind.sql`, `supabase functions deploy bofo-announce`, live smoke test with an owner account. The master prompt `BOFO-AI-ANNOUNCE-PROMPT-7-STEPS.md` was not available in this session: give it to the Step 7 AI.
- **Next action:** Step 7 (QA + docs + final) with `NEXT-STEP-PROMPT.md`.

# Handoff (Android) - AI Announcement Writer: Step 5 of 7 done (sheet stages A + B, v2.4.2 / 20402)
- **State:** `util/AnnounceFormState.java` (pure, unit tested: `Stage LIST/DETAILS`, `pick`, `back`, `restore(typeId)`, `defaults(type, lang, tone, length, emoji)`, `toneToRemember`, `filter(all, query, labelOf)`, `badgeStringName`, `closingDefault`, `describe(type, Problem)` -> `ProblemText{stringName, fieldKeys, focusKey()}`, `check(...)` -> `Check{submission | problem}`, immutable `Submission{type, lang, tone, length, emoji, closingOn, closingName, fields}` with `toRequest(adjust)`), `ui/AnnounceAiSheet.java` (`BottomSheetDialogFragment`), `res/layout/sheet_announce_ai.xml`, `item_announce_type.xml`, `drawable/bg_ann_badge_urgent|important.xml`, 15 new strings in `strings_announce_ai.xml` (`announce_sheet_title`, `announce_pick_type_title`, `announce_search_hint`, `announce_no_types`, `announce_details_intro`, `announce_required_label`, `announce_options_title`, `announce_label_language|tone|length`, `announce_closing_name_hint`, `announce_back_cd`, `announce_close_cd`, `announce_type_row_cd`, `announce_type_row_urgency_cd`). Version **2.4.2 / 20402**. Not opened from anywhere yet (`AnnounceFragment` unchanged). No service call, nothing under `supabase/`, `AnnounceAiService` / `BhGroupService` untouched.
- **How Step 6 uses it:** in the host fragment's `onCreate`: `AnnounceAiSheet.listen(this, submission -> {...})`; open with `AnnounceAiSheet.show(this)` (child fragment manager; ignored when already open / state saved / host detached). The sheet hands over ONE validated `AnnounceFormState.Submission` through a fragment result (`RESULT_KEY = "announce_ai_write"`, survives rotation) and closes itself; `AnnounceAiSheet.fromBundle(bundle)` rebuilds + re-validates it. Step 6 then calls `AnnounceAiService.generate(s.type, s.lang, s.tone, s.length, s.emoji, s.closingOn, s.closingName, s.fields, adjust, cb)` and shows stage C. **Decision:** stage C is a NEW screen state in a second sheet or the same sheet re-opened with the result: Step 6 decides; the sheet currently closes on Write for me. Keep the Submission to re-run Try again / Shorter / More friendly / More formal / Change language (re-send the same facts with changed tone / length / lang; `adjust` optional).
- **Behaviour:** stage A rows in catalogue order (icon via `AnnounceType.icon`, fallback `ic_sparkle`; title/desc by resource NAME with fallback to the id, never crashes); search = all typed words in the label or id. Stage B has 4 fixed input slots (so Android restores typed text after rotation), label = `announce_field_<key>` (+ ` *` when required), placeholder = `announce_hint_<id>_<key>`, max 200; options as chips (language, tone, length) + emoji switch + sign-off switch with name (prefilled from `BrowseState.me.fullName()`, switch ON when a name is known, max 60). Remembered: language, tone (only when different from the type's default, else "" = type default), length, emoji; written when Write for me succeeds. Typed details are never written to prefs or logs (they live in the view state only). Back on the form returns to the list; Back on the list closes. AI switched off (`!AiFlag.on()`, admins exempt like the server) -> toast `announce_err_ai_disabled` and the sheet closes. A problem shows `announce_err_missing | one_of | too_long` on the field (first field of a oneOf group) and focuses it.
- **Tested (12 new JVM tests in `AnnounceFormStateTest`):** stage/back/restore, defaults + remembered tone rule, search, badges, closing-name prefill, problem mapping (missing, too long, oneOf, null type, null map), strings exist, clean Submission + `toRequest`, sign-off needs a name, no values in `toString`. With Steps 3-4 = **56 run, 56 passed** (JDK `javac`, real `junit-4.13.2.jar` + hamcrest 1.3; stand-ins for org.json, SupabaseClient.ApiException, Connectivity.isNetworkError, android.os).
- **Checked, not run:** `AnnounceAiSheet.java` parses (JDK parser) and every `R.id / R.string / R.drawable` it uses exists in the repo; the 4 new XML files and the strings file parse. **NOT compiled** (no Android SDK, so Material / AndroidX symbols are unverified: `ComponentDialog`, `MaterialSwitch`, `Chip.setChipMinHeight`, `TextInputLayout.setPlaceholderText`), **not run** on a device or emulator, no UI test (none exist). Please build in Android Studio and look at: sheet height with the keyboard open, chip rows scroll sideways on 360dp, urgent badge colours, back button, rotation on the form.
- **Still needs the user's OK (not done):** apply `031_ai_usage_kind.sql`, `supabase functions deploy bofo-announce`, live smoke test (Step 6 on a device needs them).

# Handoff (Android) - AI Announcement Writer: Step 4 of 7 done (Android service, v2.4.1 / 20401)
- **State:** `model/AnnounceDraft.java` (immutable `{title, message, notes?, degraded}`, `fromJson`: notes optional / wrong type -> null, `degraded` only a real boolean, clips 120 / 1000 (notes 300) without splitting an emoji, title one line, message keeps paragraphs, throws `IllegalArgumentException` for a missing / blank / non-string title or message), `model/AnnounceError.java` (enum SIGNED_OUT, NOT_OWNER, AI_DISABLED, RATE_LIMITED, INVALID, INVALID_DETAILS (local), SERVER, OFFLINE, GENERIC; `stringName()` = string resource name, `canRetry()`), `service/AnnounceErrors.java` (pure mapping `from(int status, String body)` / `from(Throwable)` -> `Failure{error, retryAfterSeconds, field}`), `service/AnnounceAiService.java`. Version **2.4.1 / 20401**. Strings added: `announce_err_signed_out`, `announce_err_server`. No screen, no layout, nothing under `supabase/`, `BhGroupService.postAnnouncement` untouched.
- **How Step 5-6 call it:** `AnnounceAiService svc = new AnnounceAiService();` then `Call c = svc.generate(type, lang, tone, length, emoji, closingOn, closingName, fields, adjustOrNull, result -> {...});`. The callback runs ONCE on the main thread with a `Result`: `result.ok()` -> `result.draft` (check `draft.degraded` -> show `announce_degraded_note`, `draft.notes` -> show as a hint); else `result.error` -> show string `getIdentifier(error.stringName())`; `RATE_LIMITED` string takes `result.retryAfterSeconds` (%1$d; 30 when the server sent nothing, max 3600); `INVALID_DETAILS` (the app's own check, no network was used) -> `result.problem.code/field` -> `announce_err_missing | one_of | too_long` with the field label; `INVALID` -> `result.field` may name the server's field. Keep ONE service per sheet; call `svc.cancelAll()` in `onDestroyView` (late results are then never delivered). A newer `generate` supersedes an older one (only the latest answer is delivered). Invalid details are reported at once on the calling thread, before any thread hop.
- **Rules kept:** no retry loop (one request per call, the owner presses Try again); nothing is logged in the service (no field values, closing name, wifi password or draft text); a degraded draft is a success; the service only returns text.
- **Error table (backend contract, section F):** 401 SIGNED_OUT; 403 `not_owner` NOT_OWNER; 403 `ai_disabled` AI_DISABLED; 403 any other body GENERIC; 429 RATE_LIMITED (`retry_after` number or numeric string, decimals round up, absent/bad/<=0 -> 30); 400 INVALID (+ `field`); 405, 500, 502 and any >= 500 SERVER; network failure (`Connectivity.isNetworkError`) OFFLINE; anything else GENERIC; a 200 body without title/message GENERIC.
- **Test seam:** `new AnnounceAiService(Transport, Runner)` (package-visible tests use a fake `Transport` and an immediate or parked `Runner`); the no-arg constructor uses `SupabaseClient.get().function("bofo-announce", body)` + `Async.run`, created lazily so JVM tests never touch the network.
- **Tested (30 new JVM tests, JUnit 4.13.2):** `AnnounceDraftTest` 7, `AnnounceErrorsTest` 10, `AnnounceAiServiceTest` 13; with the 14 Step 3 tests = 44 run, 44 passed. Compiled with the JDK `javac` and run with the REAL `junit-4.13.2.jar` + hamcrest 1.3 (from `/opt/gradle-8.14.3/lib`, found in the sandbox). **Stand-ins:** `org.json` (small hand-written parser, no `org.json:json:20240303` jar offline), `SupabaseClient` (only `ApiException` with the same fields + a `get()` that refuses), `Connectivity` (only `isNetworkError`, copied verbatim), `android.os.Handler/Looper`, `android.util.Log`. **Not run:** Gradle build, Android lint, the app, the live function (not deployed), Android's real `org.json` (checked `JSONException`; the code only uses `opt`, `put`, `getString`, `new JSONObject(String)`).
- **Still needs the user's OK (not done):** apply `031_ai_usage_kind.sql`, `supabase functions deploy bofo-announce`, a live smoke test with an owner account. Step 5 (sheet stages A and B) can be built without them; Step 6 on a device needs them.

# Handoff (Android) - AI Announcement Writer: Step 3 of 7 done (Android catalogue + rules, v2.4.0 / 20400)
- **State:** `model/AnnounceType.java` (26 types, `Urgency/Lang/Tone/Length/Adjust` enums with wire strings, resource-name conventions), `util/AnnounceRules.java` (`normalize`, `validateFields` -> `Problem{code,field}`, `clean`, `closingName`, `buildRequest` -> JSONObject with exactly the backend keys), `AppPrefs` announce getters/setters (lang/tone/length/emoji; wiped by `clear()` on sign-out, decided), `res/values/strings_announce_ai.xml` (211 English strings), 19 icons (`ic_sparkle` + 18 `ic_ann_*`; 8 types reuse `ic_am_*`). Version bumped **2.4.0 / 20400** (`build.gradle` only; no other file carried the old string). No screen, no service, no network, nothing under `supabase/` touched.
- **Conventions for Step 4-6:** strings `announce_type_<id>` / `_desc`, `announce_hint_<id>_<key>`, `announce_field_<key>`, `announce_lang_/tone_/length_/adjust_<wire>`; resolve by name with `Resources.getIdentifier` (icon name = `AnnounceType.icon`, a drawable). Icons are tintable line icons (black, tint in layout). Error strings already exist: `announce_err_not_owner|ai_disabled|rate_limited(%1$d)|invalid|offline|generic`, `announce_degraded_note`.
- **Drift guard:** `AnnounceTypeTest.javaCatalogueEqualsTheBackendCatalogue` parses `supabase/functions/bofo-announce/catalogue.ts` and compares ids, order, urgency, tone, emoji, field keys, required flags, oneOf, hints (vs the strings file) and the enums. If you change the backend catalogue, update `AnnounceType.java` + strings in the same change.
- **Tested:** 14 JVM tests (AnnounceTypeTest 5, AnnounceRulesTest 9) compiled with the JDK compiler API and run with a tiny JUnit/org.json stand-in (the sandbox has no Gradle, no real JUnit); drift test proven to fail when a flag/emoji is changed; XML of strings and drawables parsed; `AppPrefs` compiled against Android stubs. **Not run:** Gradle build, real JUnit 4.13.2 + org.json:json:20240303, Android lint, icon rendering (paths are hand-written, look at them in Android Studio's preview), AppPrefs on a device.
- **Next action:** Step 4 (Android service) with `NEXT-STEP-PROMPT.md`. Backend still NOT deployed, migration 031 still NOT applied (needs the user's OK).

# Handoff (Android) - AI Announcement Writer: Step 2 of 7 done (backend only, still v2.3.3)
- **State:** Edge Function `bofo-announce` written (not deployed) + migration `031_ai_usage_kind.sql` (not applied). No Android file changed, no version bump. Details and decisions: `BOFO-AI-ANNOUNCE-STATUS.md` section F.
- **Tested:** 21 node tests (`node --experimental-strip-types --test supabase/functions/bofo-announce/announce.test.ts`): catalogue, validation, injection, cleaning, clipping, 26 types x 4 languages, generate fallback, LLM fallback (fake fetch), limits. **Not tested:** Deno runtime / `index.ts` (syntax-checked only), live OpenRouter, live DB, auth.
- **For Step 3 (Java mirror):** same 26 ids and field keys, same enums (`lang en|fil|ceb|taglish`, `tone friendly|formal|firm|short`, `length short|normal|detailed`, `adjust again|shorter|friendlier|formal`), field max 200, closing name max 60. Error mapping for Step 4: 403 `not_owner`/`ai_disabled`, 429 `rate_limited`+`retry_after`, 400 `{error,message,field}`.
- **Next action:** run Step 3 with `NEXT-STEP-PROMPT.md`. Deploy + migration 031 + smoke test need the user's explicit OK.

# Handoff (Android) - AI Announcement Writer: Step 1 of 7 done (audit + plan only, still v2.3.3)
- **State:** no project code changed, no version bump. Plan, verified repo facts, 26-entry catalogue contract and migration decision are in `BOFO-AI-ANNOUNCE-STATUS.md`. Master prompt: `BOFO-AI-ANNOUNCE-PROMPT-7-STEPS.md`.
- **Key findings:** new function `bofo-announce` (do not touch `bofo-chat`); owner check = query `boarding_houses.owner_id` (not `is_bh_owner`, it needs a house id); migration **031** will add `ai_usage.kind` (needed for the 40/day limit); no fil/ceb UI localisation (UI stays English); style names are `Widget.Bofo.Button.RdAccent` / `Widget.Bofo.RdField` (not `Accent`/`Field`); `CLAUDE_ANDROID.md` says 2.1.N but the repo is 2.3.3 (followed the repo).
- **Next action:** run Step 2 (backend only) with `NEXT-STEP-PROMPT.md`. Nothing deployed or applied; deploy + migration 031 need the user's OK.

# Handoff (Android) - v2.3.3 admin switch for the BOFO AI
- Settings (admin only) > **BOFO AI for users** switch. Off = the floating BOFO button and chat are hidden for every account (also an open chat closes on the next resume). Flag: `app_flags` row `ai_enabled` (default true), changed only by RPC `set_ai_enabled(boolean)` (admins; migration 030, applied live 2026-11-03). Client: `util/AiFlag` (unknown/offline = on), `ShellFragment` (button, `openBofo`, `showCompare`, refresh in `onResume`), `SettingsFragment.setAi`.
- **Limit:** the hide/disable is in the app. The Edge Function `bofo-chat` was NOT changed, so an old app version or a hand-made request could still call it. Server enforcement = check `app_flags.ai_enabled` at the top of `bofo-chat` (non-admins get 403).
- Not built or run (no Android SDK).

# Handoff (Android) - v2.3.2 Settings tweaks
- Notifications row: removed the subtext line. Orange theme row is now visible to admins only (`orange_theme_row`, hidden in `SettingsFragment.applyRole`; the saved theme is untouched for everyone else). `settings_notifications_desc` string is now unused.

# Handoff (Android) - v2.3.1 build fix
- Fixed compile error in `util/PushNotifier.java:110`: `Message.getPersonCompat()` does not exist, replaced by `getPerson()`. No behaviour change. Everything else is as in v2.3.0 below.

# Handoff (Android) - v2.3.0 phone notifications (push)
- **Done:** FCM push for student/owner chats (`messages`), boarding group chats (`bh_group_messages`, incl. announcements) and admin-only reports (`reports`). Flow: trigger (029) > edge function `supabase/functions/send-push` > FCM > `app/BofoMessagingService` > `util/PushNotifier`. Tap routing: `ui/PushRouter` + `ShellFragment.openFromPush`. Token lifecycle: `service/PushService` (register at shell open, unregister at sign-out via `AuthService.signOut`, `forgetLocal` in `clearLocal`). Settings > Notifications switch (`AppPrefs.pushEnabled`).
- **Admin-only reports:** server picks `users.role='admin'` at send time; phone drops report pushes unless `PushService.role()` is "admin" (`PushRules.allowedFor`); sign-out deletes the token.
- **NOT applied / NOT set up:** Firebase project + `bofo-android/app/google-services.json` (git-ignored), secrets `FCM_SERVICE_ACCOUNT` + `PUSH_WEBHOOK_SECRET`, function deploy, migration 029 and the `push_settings` insert. Steps + test table: `docs/PUSH_SETUP.md`. Until done, no push (app unchanged).
- **Not built or run** (no Android SDK). Build, then test the table in `docs/PUSH_SETUP.md` on two real phones. Likely first-build issues: Firebase BoM version (`33.7.0`, bump if Gradle complains), google-services plugin `4.4.2`.
- **Ideas next:** per-category switches in the app, unread-count badge on the app icon, reply from the notification, `bh_group_messages` mentions.

# Handoff (Android) - v2.2.6 UI redesign: step 2 of 2 done (Chats list, Edit profile)
- Redesign list is finished except the **Profile** screen (`fragment_profile.xml`, see `REDESIGN_STEP2.md` section 3). Changed in step 2: `item_conversation`, `item_chat_skeleton`, `fragment_chats`, `fragment_profile_setup` (XML), `ChatsFragment` (unread last-message emphasis), styles/colors/strings. Save stays in the scroll flow (not pinned to the bottom).
- Test: Chats with unread / read / empty / loading, long names; Edit profile in both modes (set up vs edit), validation errors, dropdowns, orange and classic themes, font scale 1.3, 360 dp width.

# Handoff (Android) - v2.2.5 UI redesign: step 1 of 2 done (shared look, Dashboard, My places)
- **Next:** step 2 = Chats list + Edit profile (+ Profile screen if budget allows). Full spec: `REDESIGN_STEP2.md` (any AI can continue from it). Step 2 version = 2.2.6.
- Step 1 changed: `DashboardFragment` (render, placeCard, banner, grid helpers `gridRow/cell/hairline`, `styled()`), `ListingsFragment` (row adapter uses `item_my_place`), `fragment_dashboard/listings.xml`, `item_my_place.xml`, `CornerMaskView.setRadiusDp`. No Supabase change. Test: owner Dashboard with 0 and with several places, not verified / pending / rejected notice, My places row status chips (active, unlisted, taken down), orange and classic themes, font scale 1.3.

# Handoff (Android) - v2.2.0 Boarding series: DONE (steps 1-5 of 5)
- **Polish after step 5 (no new version):** announcement card = plain white card (`bg_ann_card`); the pinned bar shows only announcements still waiting for Got it and hides after it (`BoardingRules.toAck`); the full pinned list is in the chat's 3-dot menu (`boarding_menu_pinned` > `showPinned()`). Test: student with 2 pinned: bar shows the first + "+1 more"; Got it: next one; Got it again: bar gone; menu > Pinned announcements (2) lists both with "You acknowledged this"; owner: no bar, menu has the list.
- **PR title (step 5):** `v2.2.0 - Boarding groups step 5 of 5: invite code, members, notice` (version stays 2.2.0 by the brief). Steps 2-4 must be merged first. **The series is finished: there is no step 6.**
- **Step 5 done:** `ui/GroupInfoFragment` (nav `groupInfoFragment`, args `bhId`, `title`; owner only, opened from the chat's 3-dot menu > **Group info and invite code**): invite code (`BoardingRules.formatCode`, Copy, Share with `BoardingRules.shareText`, New code with confirm; no code yet = "Create code") and the member list (`BhGroupService.members` > `BoardingRules.memberOrder`; 3-dot per active student: Mute / Unmute, Remove with confirm; list reloads after each change; loading, empty and error + Try again). `BoardingChatFragment`: owner menu item + owner empty text (`boarding_empty_sub_owner`). `ShellFragment` (students): Boarding tab has an unread badge (`BhGroupService.totalUnread`, errors ignored) and a notice bar (Snackbar above the dock, `BhGroupService.unackedAnnouncements` > `BoardingRules.newNotices`, once per announcement per app run via static `SHOWN`, "Open" = Boarding tab). `DockBarView.setBadge(tabId, n)`. 22 JVM tests pass. No Supabase change.
- **Supabase (still NOT applied live, needs the user's OK; the user allowed Claude to run migrations):** apply **023_ban.sql first, then 024_boarding_groups.sql**. Until 024 is live the Boarding tab shows an error + Try again and students cannot join. After applying, check: tables `bh_invite_codes, bh_members, bh_group_messages, bh_announcement_acks`, view `bh_group_feed_v`, RPCs `join_bh_group, my_bh_groups, post_announcement, pin_announcement, ack_announcement, bh_members_list, set_member_muted, remove_member, regenerate_invite_code`, and that every existing house got an owner member row + invite code (backfill).
- **Known limits (whole series):** owner has no unread badge (no tab); "Pinned" tag in the list can be stale until the chat is reopened; member count in the chat header is from when it opened; a message deleted by someone else disappears only after reopening; brute-force limit on invite-code guesses is NOT built (8 chars of a 32-letter alphabet); owner cannot un-remove a removed student (no RPC); the notice bar only shows when the Shell screen resumes (not while the app sits open on one screen).
- **Test steps (step 5, after 024 is applied, copy of the data first):** (1) owner: Profile > Boarding groups > a house > 3 dots > Group info: the code shows as ABCD-2345; (2) Copy: toast, paste elsewhere works; Share: chooser with the join text; (3) New code: confirm, the new code shows, the old one is refused when a student joins; (4) a student joins: appears in Members with "Student"; (5) 3 dots > Mute: status "Student - Muted", the student's send shows the blocked text; Unmute: works again; (6) Remove: confirm, status "Student - Removed", no 3 dots, the student cannot rejoin; (7) house with no members: empty text; airplane mode: error + Try again; (8) owner's empty chat shows the "Share your invite code" text; (9) student: owner posts an announcement, student returns to the app: bar "New announcement: ..." with Open; once only per app run; Boarding tab badge shows unread; Got it removes it from the next notice.
- **PR title (step 4):** `v2.2.0 - Boarding groups step 4 of 5: owner Boarding groups screen + Announce form` (version stays 2.2.0 by the brief). Steps 2 and 3 must be merged first.
- **Step 4 done:** `ui/OwnerBoardingFragment` (nav `ownerBoardingFragment`, opened from **Profile > Boarding groups**, owners only; list uses `item_conversation`, loads `BhGroupService.myGroups()` filtered to role owner, reloads on every `onStart`), `ui/AnnounceFragment` (nav `announceFragment`, optional arg `bhId` ticks a house; `BhGroupService.postAnnouncement`; form checked by `BoardingRules.checkAnnouncement`), `BoardingChatFragment` for owners (nav `boardingChatFragment` takes the bundle from `BoardingChatFragment.args(...)`, myRole = owner, showBack = true, canLeave = false): header menu > New announcement, long press > Pin / Unpin / Edit (own announcements), Delete (own announcement or any message, with confirm), `BoardingRules` (`checkAnnouncement, canManageAnnouncement, canDelete, pinIsFull, preview`, `Row.withPinned / withContent`; 18 JVM tests pass). No Supabase change.
- **Placement decision:** the owner tab bar already has 5 slots, so Boarding groups lives in Profile (like Saved for students). If the user wants a tab instead, add `tab_boarding` to the owner list in `ShellFragment.tabsFor()` and show `OwnerBoardingFragment` in `showTab` (it is a normal Fragment).
- **Not in step 4 (step 5):** mute / remove member screen (`BhGroupService.members / setMuted / removeMember` exist), invite code display + regenerate (`inviteCode / regenerateInviteCode` exist; today the owner must read the code from the database, so students cannot join until step 5 shows it), announcement notification / unread badge on tabs, polish of empty / error / loading states, final handoff.
- **Known limits (step 4):** the owner list shows the last message as plain text; an announcement edited by the owner is not marked "edited"; the owner's own Boarding chat has no "Seen by 7 of 12" total, only the count of members who pressed Got it.
- **Test steps (after 024 is applied, copy of the data first):** (1) owner: Profile > Boarding groups lists his houses; (2) Announce > empty title / no house show the red messages; (3) tick two houses, Post: toast "posted to 2 houses", both chats show the amber card + pinned strip; (4) a student presses Got it: owner's card shows "Seen by 1 member" after reopening; (5) long press announcement > Unpin: strip count drops; pin 4th: "Only 3 can be pinned"; (6) Edit: text changes; (7) long press a student's message > Delete message > confirm: gone; (8) a student's long press has no Pin / Edit / owner Delete.
- **PR title (step 3):** `v2.2.0 - Boarding groups step 3 of 5: announcement card, pinned banner, Got it, report message` (version stays 2.2.0 by the brief). Step 2's PR must be merged first (step 3 edits files that step 2 created).
- **Step 3 done:** `BoardingChatFragment` (announcement card via `ann*` views in `item_group_msg.xml`; pinned strip `pinned_bar` loaded with `BhGroupService.pinned()` after the first page and every 5th poll; `acknowledge()` calls `BhGroupService.ack()` then `markAcked()` on both lists; banner tap `showPinned()` dialog; long press > Report message via `ReportDialog.show(f, senderId, bhId, BoardingRules.reportQuote(row))`), `ReportDialog` (new 4-argument overload: quote goes in front of the details, title / hint change, details limit = 500 - quote), `BoardingRules` (Row got `pinned, iAcked, ackCount`; new `canAck, canReport, reportQuote, bannerOrder, markAcked, unackedCount, oneLine`; 15 JVM tests pass). No Supabase change.
- **Owner side already supported by the same screen (step 4 only has to open it):** with `myRole = owner` the card shows "Seen by N members" (the view only fills `ack_count` for the owner) and no Got it button. Pin / unpin / edit / delete of announcements and the Announce form are step 4; moderation (mute / remove), notification and polish are step 5.
- **Known limits (step 3):** a "Pinned" tag on a card in the list can be stale until the chat is reopened (the 3 s poll only fetches new messages); reporting a second message of the same person in the same house while the first report waits shows "already reported" (database rule `reports_one_pending`); the banner keeps its last state when offline.
- **Test steps (after 024 is applied, copy of the data first):** (1) owner posts an announcement (SQL `select post_announcement(...)` until step 4): student sees the amber card with Pinned + Got it; (2) Got it: card shows "You got it", banner button disappears; (3) 2 announcements, press Got it on one: the banner shows the other; (4) tap the banner: dialog lists all pinned; (5) long press a housemate's message > Report message > pick reason > Send: Admin > Reports shows the quote in the details; send again: "already reported"; (6) own message has no Report; (7) airplane mode: Got it shows the offline text, banner stays.
- **PR title (step 2):** `v2.2.0 - Boarding groups step 2 of 5: student Boarding screen` (version stays 2.2.0 by the brief: bumped once in step 1)
- **Series plan (user's rule: ONE step per turn, reply "next" / "do step N"):** 1 DB + models + services (DONE) | 2 Student Boarding screen (join + chat, Icons, nav center slot, Saved moves) (DONE) | 3 Pinned banner + "Got it" + report message (DONE) | 4 Owner Boarding/Groups screen + Announce form (DONE) | 5 Moderation, announcement notification, invite code display/regenerate, empty/error/loading states, final handoff. Later steps only add a line to CHANGES_ANDROID.md / this file (version is bumped once, here).
- **Step 2 done (v2.2.0, no new version):** `ui/BoardingFragment` (student tab: join with code, or the chat), `ui/BoardingChatFragment` (chat of ONE house: reusable, args `bhId, title, members, myRole, showBack, canLeave`; step 4 opens it for owners with `showBack=true, myRole=owner`), `util/BoardingRules` (+ `BoardingRulesTest`, 8 tests, run on a plain JVM with stubs: pass), `Outbox` now also queues group messages (entries with `bh`, `Outbox.addGroup / pendingGroup / GROUP_LISTENERS`), dock icon `ic_dock_boarding`, `tab_boarding` in the centre slot of `ShellFragment` (student only), role badge colours `bofo_role_owner_*` (day + night), strings in `strings_boarding.xml`.
- **Saved decision:** Saved left the tab bar and is now **Profile > Saved places** (students only; new `savedFragment` in the nav graph, `SavedFragment` got a back button). The Home top bar was not touched.
- **Join method:** owner's invite code (8 characters, spaces / dashes / lower case are accepted). Wrong code, removed member and "already in another house" each show their own message.
- **Not in step 2 on purpose:** announcement look + pinned banner + "Got it" + report message (step 3), unread badge on the Boarding tab, owner screen (step 4), moderation / notification / empty-error polish (step 5). Announcements that already exist in the database are shown as plain bubbles with a bold title.
- **Known limits:** a message deleted by someone else disappears only after the chat is reopened; the member count in the header is the count when the chat was opened.
- **Test steps (after 024 is applied, on a copy of the data first):** (1) student without a house opens Boarding: join screen; (2) wrong code: red message; right code: chat opens; (3) send a message: shows name "You" + Student badge + time; (4) airplane mode, send: "Sending...", then it goes out when back online; (5) long press own message: Copy / Delete my message; (6) menu (3 dots) > Leave group: join screen again; (7) Profile > Saved places opens the list and Back returns to Profile; (8) Home / Explore / Chats / Profile tabs still work, Chats badge still counts.
- **Mapping used (the brief names JavaFX classes; this is the Android app, v2.1.x line):** ChatView -> `ChatFragment`, ChatsView -> `ChatsFragment`, Nav/StudentShell/OwnerShell -> `ShellFragment` + `DockBarView`, ReportsView -> `ReportsFragment` / `ReportDialog` / `ReportService`. Skeleton/Popup/Icons names do not exist in Android: use the Android equivalents found at step 2.
- **Supabase: 024 is written, NOT applied live (waiting for the user's OK).** Needs 022 + 023 first (both live / 023 pending: apply 023 before 024). Until 024 is applied the app is unchanged (no screen calls the service).
- **024 adds:** `bh_invite_codes` (owner-only read), `bh_members` (bh_id, user_id, role, status active/left/removed, joined_at, muted, + `last_read_at` for unread counts), `bh_group_messages` (+ title, pinned, pinned_at, edited_at, is_announcement), `bh_announcement_acks`, view `bh_group_feed_v` (adds sender name, `sender_role` owner/student, `i_acked`, `ack_count` for the owner only), RPCs `join_bh_group, leave_bh_group, mark_group_read, my_bh_groups, post_announcement, pin_announcement, edit_announcement, ack_announcement, announcement_stats, bh_members_list, set_member_muted, remove_member, regenerate_invite_code`, helpers `is_bh_owner, is_bh_member, can_post_group`, a trigger that gives every NEW house an owner member row + invite code, and a backfill for existing houses.
- **Decisions:** (1) invite code lives in its own table, NOT as a column on `boarding_houses`, because that table is readable by every signed-in user (a column there would let outsiders read the code). (2) A student is in ONE house at a time (partial unique index); leaving frees them to join another. (3) A removed member cannot rejoin with the code; a member who left can. (4) New announcement is pinned automatically; if the house already has 3 pinned, the OLDEST is unpinned (still in the chat); manual pin refuses a 4th. (5) Students can delete their own messages; owner can delete any message in their house; nobody edits chat messages; only the owner edits announcements. (6) The owner is a member of their own groups (own `bh_members` row, role `owner`); banned/suspended users cannot post (uses `is_suspended()` from 023). (7) Reporting a message will reuse table `reports` (reported = sender, `bh_id`, snippet in `details`): no schema change needed. (8) Unread counts ignore my own messages; announcement notification (step 5) will be derived from unread announcements, no new table.
- **Not built (LATER, only listed):** house rules card (from migration 011), rent due reminders linked to PaymentsView, maintenance / issue threads with status, polls, photo / file sharing, member list with privacy settings, per-group mute and quiet hours, realtime (the screen will poll every 3 s like ChatFragment), brute-force limit on code guesses (code is 8 characters of a 32-letter alphabet, about 10^12 combinations).
- **Risks:** not built or run by Claude (no Android SDK, no database). `BhGroupService` and the 3 models were compiled with `javac` against stubs of org.json / SupabaseClient only; the SQL was read through but not run. Test after approval on a copy first: join with a wrong / right code, student posting, muted student refused, owner announcement to 2 houses, 4th pin, ack count visible to the owner only.
---
# Handoff (Android) - v2.1.10 owner card on the place page
- **PR title:** `v2.1.10 - Place page: owner profile card with Report inside it`
- No Supabase change. `BhService.detailOnline` now selects `owner_name,owner_photo` from `bh_owner_v`; `BhDetailFragment.bindOwner()` fills the card and the Report pill (replaces `bindReport`).
- Risks: not built or run by Claude. Check on a device: place with photo, place without photo/name, own place (no Report), admin (no Report), long owner name (ellipsized on one line).
---
# Handoff (Android) - v2.1.9 safety series step 3 of 3
- **PR title:** `v2.1.9 - Safety series step 3 of 3: ban by ID, Banned users, Unban`
- **Supabase:** migration **022 IS APPLIED LIVE** (project BOFO, 2026-11-02, with the user's OK; verified: `reports`, `admin_review_report`, `is_suspended`, `my_suspension`, `reports_admin_v`, 7 `susp_block_*` policies, 2 `users` columns). **023 is written but NOT applied live** (needs the user's OK): it adds `users.banned_at/banned_by/ban_reason`, private `app_secrets` (random pepper), `id_hash()`, `owner_verifications.id_hash` (+ trigger `protect_ov`), `banned_identities`, `check_id_number`, `submit_id_number`, `admin_approve_verification`, `admin_unban`, `admin_banned_list`, and replaces `admin_review_report` (adds `ban`), `review_verification` (approve is now refused: use `admin_approve_verification`), `is_suspended`, `my_suspension`, `protect_users`.
- **Order matters:** apply 023 BEFORE installing the 2.1.9 app. Until then a new owner verification fails at the ID check (the RPC does not exist) and the Ban / Banned users screens show an error. Old pending requests (no stored hash) can still be approved: the admin's typed number is stored then.
- Scope A (owners only). Students have no ID step; a banned student can open a new Google account. A banned owner who deletes the account and signs up again is still blocked by the ID hash.
- Risks: not built or run by Claude (no Android SDK); `BanRules` checked on a plain JVM only. If the number cannot be attached after the insert (network), the request stays pending without a hash; the admin's number is stored at approval. Unban does not put taken-down places back.
- Test list: BOFO-SAFETY-TEST-CHECKLIST.md (step 3 section).
---
# Handoff (Android) - v2.1.8 safety series step 2 of 3
- **PR title:** `v2.1.8 - Safety series step 2 of 3: Report button, admin review, timed suspension`
- **Supabase: 022 NOT applied live yet** (waiting for the user's OK). It adds table `reports`, `users.suspended_until / suspension_reason`, `is_suspended()`, `my_suspension()`, `admin_review_report(rid, act, hrs, reason, notes)` (act = dismiss / reviewed / suspend; ban comes in step 3), view `reports_admin_v`, a new `protect_users()` that also locks the two suspension columns, and RESTRICTIVE insert/update policies on messages, reviews, boarding_houses, blocks_reports, reports. Until it is applied the app still works (the suspension check fails open) but Admin > Reports shows an error and reports cannot be sent.
- Next: step 3 = v2.1.9: ban by hashed verification ID (blocked at registration AND at ID verification), Banned Users list, Unban, audit log. Existing: `owner_verifications` is the only ID table (owners); students have no ID step yet, so ask the user which ID the ban should scan before building. Plan: BOFO-SAFETY-STATUS.md.
- Risks: not built or run by Claude (no Android SDK). A suspended user's open session is only caught at the next app start / Check again; writes are blocked by the database immediately. Admin has no "lift suspension early" button yet (SQL: `update users set suspended_until = null where id = ...`).
---
# Handoff (Android) - v2.1.7 safety series step 1 of 3
- **PR title:** `v2.1.7 - Safety series step 1 of 3: owner's name on the boarding house page`
- **Supabase: 021 IS APPLIED LIVE** (project BOFO, 2026-11-02, with the user's OK): only the view `bh_owner_v` (SELECT for authenticated, nothing else). No tables or data changed.
- Next: step 2 (Report button + admin review + suspend) = v2.1.8; step 3 (ban by ID hash, Banned list, unban) = v2.1.9. Series plan: BOFO-SAFETY-STATUS.md.
- Risks: not built or run by Claude (no Android SDK); `ic_set_user` reused as the owner icon.
---
# Handoff (Android) - v2.1.5 compare table fits the card
- **PR title:** `v2.1.5 - Fix: Ask BOFO compare table fits the card for up to 3 places`
- Files: BofoChatOverlay (addCompare/cell sizes), build.gradle (2.1.5 / 20105). Not built or run by Claude. v2.1.4 (peek column) is superseded, merge it first or apply this zip over main anyway: this zip contains the full 2.1.4 files too.
---
# Handoff (Android) - v2.1.4 compare table UI fix
- **PR title:** `v2.1.4 - Fix: Ask BOFO compare table (3 places no longer look cut off)`
- Files: BofoChatOverlay (addCompare widths + hint), item_bofo_compare.xml (fading edge), strings_bofo.xml, build.gradle (2.1.4 / 20104). Not built or run by Claude.
---
# Handoff (Android) - v2.1.3 AI smarter, step 3 of 3 (series finished)
- **PR title:** `v2.1.3 - AI smarter step 3: chat UI (clarify chips, thumbs, New chat, restored chat) + admin Needs attention`
- Files: BofoChatOverlay, view_bofo_chat.xml, item_bofo_rate.xml, 3 drawables, strings.xml (hello/hint), strings_bofo.xml, AiUnansweredFragment/Service/Rules (new), 2 layouts, strings_ai_unanswered.xml, nav_graph, fragment_profile.xml, ProfileFragment, AiUnansweredRulesTest, build.gradle (2.1.3 / 20103). Function and Supabase untouched.
- Needs on the server: `supabase functions deploy bofo-chat` (step 1 code, NOT deployed by Claude) and migration 020 (applied live). Without the deploy there is no `log_id`, so no thumbs and no clarify chips from the server; the offline planner still shows chips.
- Risks: not built or run by Claude (no Android SDK); the 5 rules tests pass on a plain JVM only; thumb / New chat icons are new vector drawables.
- Next: nothing queued. Real-phone check = acceptance list in BOFO-v2.1.3-CHANGELOG.md.
---
# Handoff (Android) - v2.1.2 AI smarter, step 2 of 3
- **PR title:** `v2.1.2 - AI smarter step 2: Android data layer (BofoMemory, session + history, rate(), smarter offline planner)`
- Files: BofoAnswer, BofoAiService, BofoMemory (new), BofoIntentLocal, AuthService, BofoChatOverlay (2 small lines), 4 tests, build.gradle (2.1.2 / 20102). Function and Supabase untouched.
- Risks: remembered wifi/budget persist until New chat (step 3); not built or run by Claude.
- Next: step 3 (UI) in BOFO-AI-SMARTER-PROMPT.md, version 2.1.3.
---
# Handoff (Android) - v2.1.1 AI smarter, step 1 of 3
- **PR title:** `v2.1.1 - AI smarter step 1: planner, memory, clarify question, knowledge base (bofo-chat + migration 020)`
- Migration 020 is applied live. Function NOT deployed: `supabase functions deploy bofo-chat`. Next: step 2 in `BOFO-AI-SMARTER-PROMPT.md` (v2.1.2). Status: `BOFO-AI-SMARTER-STATUS.md`.
- Risky: every answer now waits for the model (up to ~5 s) before the template; set secret `LLM_WORDING=0` to undo. Free models may ignore the JSON plan: then the old regex path answers.
---
# Handoff (Android) - v2.0.122 Minimum app version for everyone
- **PR title:** `v2.0.122 - Server can refuse old app versions for all users (switch OFF), edge functions forward the version header`
- **Supabase (NOT applied live):** run `019_min_app_version_all.sql` in the SQL editor after 018. It only creates the switch (`set_min_app_version`) and the check (`enforce_min_app_version`), both OFF. Hook step at the bottom of the file is manual. Earlier chat stopped mid-way (usage limit) so do not assume any of 019 exists live.
- **Edge functions (NOT deployed):** `supabase functions deploy bofo-chat delete-account create-boost-checkout`.
- **Order:** install 2.0.122 on your phone > run 019 > deploy functions > `select public.set_min_app_version('2.0.122');` > hook step. Undo: `select public.set_min_app_version('');` or `alter role authenticator reset pgrst.db_pre_request;`.
- **Not done yet:** the app does not show a nice "please update" screen for HTTP 426.
---
# Handoff (Android) - v2.0.119 Admin two-step verification
- **PR title:** `v2.0.119 - Admin two-step verification (authenticator app), server switch and in-app setup`
- **Supabase:** `017_admin_mfa.sql` is ALREADY APPLIED to the live project (2026-11-01, by Claude, with the user's OK). Switch `app_flags.require_admin_mfa` = false, so nothing changed yet. `is_admin()` = admin role AND (switch off OR token aal2). `set_admin_mfa_required(bool)` is owner / SQL editor only; turning ON refuses unless an admin has a verified TOTP. `015` and `016` are NOT applied live yet (files only).
- **App:** `AuthService` (`aal`, `mfaVerifiedFactor`, `mfaEnroll`, `mfaVerify`, `mfaRemove`), `SupabaseClient.authCall` (authed GET / POST / DELETE on /auth/v1), new `AdminMfaSheet` (+ `sheet_admin_mfa.xml`, `strings_admin_mfa.xml`) with states INTRO > SETUP (key + code) / ENTER / ON. `SettingsFragment`: row "Two-step verification" for admins only. `AuthRouter.route` sets `mfaPrompt` for an admin with an authenticator but an aal1 session; `ShellFragment` opens the sheet once.
- **Order to switch it on:** docs/SECURITY.md section 5. Not built or run by Claude (no Android SDK); Java syntax-checked with javac (only missing-Android-class errors), XML parsed, string ids cross-checked.
- Supabase dashboard: Authentication > Sign In / Providers > Multi-Factor must have TOTP enabled (default on).

---
# Handoff (Android) - v2.0.118 Security system: restricted AI role + hardening
- **PR title:** `v2.0.118 - Security: restricted database role for AI, helper-function lockdown, view throttle, security docs`
- No app code changed; only `versionName` / `versionCode` (318). **Not applied to the live Supabase project**: run in the SQL editor, in order: `015_security_hardening.sql`, `016_ai_dev_role.sql` (013 is still not run live; 015 works with or without it).
- 015: `is_admin` / `is_verified_owner` / `is_owner_or_admin` and the app RPCs are no longer executable by anon / other roles; `bump_view` counts one view per user per place per hour (new table `bh_view_seen`, no policies).
- 016: role `bofo_ai_dev` (nologin until the user sets a password): catalog read, demo listings and reviews only for `@example.com` test accounts, no access to users / auth / chats / ID scans / storage / payments. Tested on local Postgres 16 with stubbed Supabase schemas, not on the live project.
- `docs/SECURITY.md` = findings table, dashboard to-dos, test list. `CLAUDE.md` section 7 = rules for any AI: no user PII, nothing under real accounts, ask before touching the live DB.
- User to-do: delete or re-password the Dev Admin / Dev Student accounts before release (SQL at the bottom of 015).

---
# Handoff (Android) - v2.0.117 Orange theme + Settings switch
- **PR title:** `v2.0.117 - Android: orange theme (default) with a Settings switch back to the classic purple look`
- **How it works:** `res/values/colors.xml` = CLASSIC purple (values unchanged, new tokens added). `res/values-night/colors.xml` = ORANGE (same 76 names). "night" is only the switch: `util/ThemeMode` sets `AppCompatDelegate.MODE_NIGHT_YES` (orange) / `MODE_NIGHT_NO` (classic), never FOLLOW_SYSTEM. Choice saved in `AppPrefs.orangeTheme` (default on, survives sign-out). `BofoApp.onCreate` applies it before any activity; Settings > "Orange theme" (`orange_theme` row) calls `ThemeMode.set`, the activity recreates itself on Settings.
- **Fixes the night switch does not do by itself:** `MainActivity` passes explicit LIGHT `SystemBarStyle` to `EdgeToEdge.enable` (default would draw white status icons on the light app in orange); `values-v29/themes.xml` sets `forceDarkAllowed=false` (`Theme.Bofo` now = `Base.Theme.Bofo` + that); `MapCardView.themed()` swaps the lavender basemap colours in `assets/bofo-map-style.json` for `map_*` colours; `res/values*/bools.xml splash_light_bars` drives the splash status/nav icon colour.
- **Loading screen:** `splash_*` colours (classic = dark ink as before, orange = white); `SplashStageView` / `SplashDotsView` / `fragment_splash.xml` / `SplashFragment` read them. Animation unchanged. Orange mascot gets an outline ring (`splash_mascot_outline`, transparent in classic).
- **First frame:** the system draws it before the app runs, so it follows the PHONE's dark setting, not the switch. `Theme.Bofo.Launch` = white (orange default); Android 13+ also switches to `Theme.Bofo.Launch.Classic` (dark) via `SplashScreen.setSplashScreenTheme` when Classic is chosen. Android 8-12 + Classic: white first frame, then the dark loading screen.
- **Hardcoded purples removed** from res (drawables, layouts, `res/color/commute_chip_fg`) and Java (`MapCardView`, `RentCardView`, `DockBarView`, `ListingFormFragment`, splash views). Kept on purpose: launcher icon (`ic_launcher_*`, drawn by the phone's launcher), meaning colours (success green, error red, Google/location blue `2F6FED`, map glow rose `F43F5E`, campus label `B45309`, offline pill amber, white/black alphas).
- Primary orange is `#E85D0C` (white on `#F26A1B` = 3.06:1, on `#E85D0C` = 3.5:1; AA needs `#C94F0A` = 4.56:1). Not built or run by Claude (no Android SDK); Java/XML only parse-checked.

---
# Handoff (Android) - v2.0.116 Loading screen: plainer
- **PR title:** `v2.0.116 - Android: loading screen less flashy`
- Removed the glow and sparkles from `SplashStageView`; `SplashDotsView` is plain white again (same smooth wave); `SplashFragment.enter()` is a simple fade (mascot first, text 200 ms later). Jump / blink / dust from v2.0.113 kept. Not built or run by Claude.

---
# Handoff (Android) - v2.0.115 Better loading screen
- **PR title:** `v2.0.115 - Android: better loading screen animation`
- `SplashFragment.enter()`: mascot pops in (overshoot), title / subtitle / dots / status float up in turn (ids `stage`, `title`, `subtitle`, `dots` added to fragment_splash.xml). `SplashDotsView`: one smooth sine wave, lavender to white. `SplashStageView`: radial glow behind the mascot (brighter at the jump apex) + 7 rising sparkles (`SPARKS`). Same 2400 ms loop. Not built or run by Claude.

---
# Handoff (Android) - v2.0.114 Chat mascot alive + new thinking animation
- **PR title:** `v2.0.114 - Android: livelier chat mascot and new thinking animation`
- `BofoMascot`: blink every 3.7 s (standing + thinking), breathing squash in standing / thinking, always redraws; THINKING pose gets a raised-eyebrow wiggle, mouth drifting with the gaze and a new `thoughtBubble` (white cloud, `mascot_outline` ring, 3 dots lighting in turn). New `TypingDotsDrawable` (3 bouncing dots, bofo_primary) replaces the text "● ○ ○" in `BofoChatOverlay.showTyping` (`dotsTick` / `DOTS` now unused). Not built or run by Claude.

---
# Handoff (Android) - v2.0.113 Livelier loading mascot
- **PR title:** `v2.0.113 - Android: loading mascot animation is more lifelike`
- `SplashStageView`: new tracks `lean`, `feetUp`, `eyeX/eyeY/eyeSy`, `smile` (per jump in `addJump`) + `dust` puffs at take-off / landing. Same 2400 ms loop, same hat pop. Body leans into the jump, feet tuck and spread, eyes squint on the crouch, look up / sideways in the air, blink on impact; smile grows at the apex. Not built or run by Claude.

---
# Handoff (Android) - v2.0.112 Mascot colours in colors.xml + outline
- **PR title:** `v2.0.112 - Android: mascot colours are editable resources, outlined body, white button`
- `values/colors.xml` `mascot_*` (body, outline, screen, glass, face, roof, roof_dark, feet, light) drive `drawable/mascot_happy.xml` and `BofoMascot` (loaded in its constructor). Body has a 3-unit `mascot_outline` stroke so it shows on white. `bg_bofo_fab` white again. `SplashStageView` still has its own colours. Not built or run by Claude.

---
# Handoff (Android) - v2.0.111 Mascot button purple, not black
- **PR title:** `v2.0.111 - Android: mascot button background is app purple`
- `bg_bofo_fab` solid = `bofo_primary` (was `bofo_ink` in v2.0.110). Used by the floating button and the chat header mascot. Not built or run by Claude.

---
# Handoff (Android) - v2.0.110 Mascot colours = splash mascot
- **PR title:** `v2.0.110 - Android: every mascot uses the splash mascot colours`
- `BofoMascot` colour constants (white body, #3A22B8 screen, lavender roof/feet, white face); `drawable/mascot_happy.xml` recoloured the same; `bg_bofo_fab` is now dark ink (white body needs it) and the chat header mascot sits on it. Launcher icon not touched. Not built or run by Claude.

---
# Handoff (Android) - v2.0.109 Map: less clutter
- **PR title:** `v2.0.109 - Android: remove duplicate km tag from the Home map`
- `MapCardView.paintRingLabel` now always clears the "3.0 km" chip on the circle edge (the radius pill at the top shows the same number). Not built or run by Claude.

---
# Handoff (Android) - v2.0.108 Rent card: top 3 prices
- **PR title:** `v2.0.108 - Android: Home price card shows only 3 prices`
- `RentCardView.setData`: at most 3 columns (Lowest / Middle / Highest); more than 3 houses = cheapest, middle, dearest. Was up to 4. Not built or run by Claude.

---
# Handoff (Android) - v2.0.107 Best rated: top 3 only
- **PR title:** `v2.0.107 - Android: Best rated tab shows only the top 3 rated houses and their prices`
- `HomeFragment.load`: BEST list re-sorted by rating / reviews / distance, cut to 3; `basis` = that list (was every house nearby, so the card showed prices of unrated houses). Empty -> `home_empty_best`, card hidden. `RentCardView`: single price on Best rated is labelled "Best rated". Not built or run by Claude.
---
# Handoff (Android) - v2.0.106 Rent card shows all prices
- **PR title:** `v2.0.106 - Android: Home price card shows all prices`
- `RentCardView.setData`: columns = up to 4 houses sorted by price (more than 4: cheapest, two spread, dearest), spread over the width; labels Lowest / Highest, Middle when 3, "Best rated" on the top rated house in the Best rated tab. Replaces the v2.0.105 single-price card. Not built or run by Claude.
---
# Handoff (Android) - v2.0.105 Rent card: Best rated only
- **PR title:** `v2.0.105 - Android: Best rated tab shows only the best rated price on the Home price card`
- `RentCardView.setData(basis, best)`: when `best` and a house has reviews, one cell ("Best rated") and one marker; otherwise unchanged (Lowest / Middle / Highest). If no house has reviews yet, Best rated still shows the normal three. Not built or run by Claude.
---
# Handoff (Android) - v2.0.104 Revert v2.0.103
- **PR title:** `v2.0.104 - Android: revert Best-rated-only on Home`
- `HomeFragment` back to the v2.0.102 chips (New / Hot / Best rated, starts on New). Not built or run by Claude.
---
# Handoff (Android) - v2.0.103 Home: Best rated only
- **PR title:** `v2.0.103 - Android: Home shows Best rated only (New / Hot chips hidden)`
- `HomeFragment`: `tab` starts at 2 (BEST), `chipNew` / `chipHot` are GONE. Explore is unchanged. Not built or run by Claude.
---
# Handoff (Android) - v2.0.102 Keyboard does not push the app
- **PR title:** `v2.0.102 - Android: keyboard covers the page instead of pushing the app up (Home + Explore search)`
- `MainActivity.holdStill(on, view)` sets `keepAppStill` (already used by BofoChatOverlay) and re-applies insets, so `mainRoot` bottom padding ignores the keyboard. Called from the Home and Explore search focus listeners; released when the view detaches. Other screens (chat boxes, forms) still lift above the keyboard. Not built or run by Claude.
---
# Handoff (Android) - v2.0.101 Search by place
- **PR title:** `v2.0.101 - Android: city search finds all houses near the city, not only addresses that contain the name`
- Cause: Explore searched only name/address text (`BhService` ilike), so a Dumaguete house with address "Demo address 5, Negros" was missed. New `util/PlaceSearch`: city centre from the campus list (city in brackets, averaged) or a built-in list; Explore runs a second `BhService.list` with `RADIUS_KM` (8) around it and merges by id; offline copy uses `PlaceSearch.keep`. A city with no campus and not in the built-in list is still text-only. CI fix: `CampusMatchTest.initialsMatch` ("norsu") failed because only first letters were compared; `CampusMatch.abbreviation` now also matches 4+ letters in order from the name's first letter. Not built or run by Claude.
---
# Handoff (Android) - v2.0.100 Card: verified text + location
- **PR title:** `v2.0.100 - Android: listing card shows Not verified text and the location under the name`
- `item_bh_card.xml`: `verifiedText` visible (11sp) after the shield; new `location` line under the name. `ListRules.locationText(address)` = last two comma parts without "Philippines". Address is free text, so the card shows what the owner typed. Not built or run by Claude.
---
# Handoff (Android) - v2.0.99 Search bar keyboard + place search
- **PR title:** `v2.0.99 - Android: Home search keeps the keyboard, place search (Dumaguete, Bayawan) with "no boarding houses available" message`
- Cause: opening the keyboard (adjustResize) moved the AppBar a few px, `onOffsetChanged` -> `HomeSearchBar.closeOnScroll()` -> `dismiss(true)` hid the keyboard. Now ignored for 1.5 s after focus; keyboard also requested explicitly.
- Home dropdown: typed text always gets a last row `Search "text"` -> `host.searchPlace` -> Explore (name/address ilike, already in `BhService`). Explore empty text = `emptyText()` (`explore_empty_place`). Not built or run by Claude.
---
# Handoff (Android) - v2.0.98 Write a review + new listing card
- **PR title:** `v2.0.98 - Android: students can review a place (stars + comment, edit, delete); calmer listing card`
- **No Supabase change.** `reviews` table, RLS (`reviews_insert` students only, `reviews_update`, `reviews_delete` own), the rating trigger (`trg_reviews_agg`, updates the card rating) and `reviews_v` already existed; only the app had no screen. `BhService.upsertReview` was already there (one review per student per place: posting again edits it); added `BhService.deleteReview`.
- App: `ReviewSheet` (bottom sheet, 5 tappable stars, comment up to 1000, "Post review" / "Save changes", "Delete my review" when one exists; layout `sheet_review.xml`, strings `strings_review.xml`). Place page (`BhDetailFragment`): reviews header has a "Write a review" / "Edit your review" button (`write_review`), shown to STUDENT role only and not on a place the user owns; it finds the user's own review by `student_id` and marks it "(You)". After a change `refreshReviews()` reloads the rating numbers and the list without leaving the page. Reviews list item restyled (`item_review.xml`).
- Listing card (`item_bh_card.xml`): 144 dp photo, name + shield icon on one line (the "Verified owner" text is hidden, the shield colour says it), price = `Text.Bofo.Price`, one rating/distance line, smaller heart button. Ids unchanged, so `BhAdapter` is untouched. Not built or run by Claude.
- Not done: owner reply to a review (column `owner_reply` exists, no UI); reviews are not limited to students who stayed there (same as the database rule).
---
# Handoff (Android) - v2.0.97 Launch screen, tab fade, no Developer button
- **PR title:** `v2.0.97 - Android: dark loading screen first (no light mascot splash, no white strips), smoother tab switch, remove Developer button`
- Launch: manifest activity theme = `Theme.Bofo.Launch` (dark ink window; `values-v31` also sets the Android 12+ system splash to dark ink with the transparent `splash_empty` icon). `MainActivity.onCreate` calls `setTheme(R.style.Theme_Bofo)` first. White strips = `main_root` (light, padded by the system bars) behind the dark `SplashFragment`: it is dark on a fresh launch and `SplashFragment` paints it dark in `onStart` / light again in `onStop`.
- Tabs: `ShellFragment.showTab` uses `tab_fade_in` / `tab_fade_out` (180 / 90 ms) and ignores a tap on the tab that is already open. Screens are still rebuilt on every switch (replace); keeping Home / Explore alive (hide / show) would remove the reload but needs `onHiddenChanged` handling for `ExploreHandoff`, `BrowseState` and Saved, so it was NOT done.
- Profile: the debug-only "Developer: data layer check" button and its code are removed (`DebugFragment` stays in the nav graph). Not built or run by Claude.
---
# Handoff (Android) - v2.0.96 UI refresh, calmer
- **PR title:** `v2.0.96 - Android: calmer UI (plain Profile header, no coloured panel, no amber/condensed type)`
- Reverts the loud parts of v2.0.95: Profile has no dark panel / amber frame / key-tag; avatar is a plain circle, role is a small grey tag. Condensed bold type removed (titles, prices, Home greeting use `sans-serif-medium`). Dock highlight is the neutral `bofo_tile`, not amber. Kept: neutral colours, 12 / 20 dp shapes, 14 dp buttons, sentence-case labels, ink active tab. Not built or run by Claude.
---
# Handoff (Android) - v2.0.95 UI refresh (less generic)
- **PR title:** `v2.0.95 - Android: UI refresh (flat ID-style Profile, key-tag role, ink/amber dock, price-tag type, no purple gradients or ALL-CAPS labels)`
- Colours (`colors.xml`): lavender wash removed (`bofo_bg` #F7F7F9, `bofo_border` #E9E8EE, icon tile = neutral `bofo_tile`), new `bofo_amber_wash`, `bofo_card_ink`, `bofo_idle`. Shapes: 8 / 12 / 20 dp (was 8 / 16 / 24). Buttons: 14 dp corners, not pills; Tint button is neutral.
- Type: one entry per face in `styles.xml`. `Text.Bofo.Title` / `Text.Bofo.Price` use `sans-serif-condensed`, `Text.Bofo.Label` is sentence case (replaces tracked ALL-CAPS labels in Profile and Settings). To use a real font later: add `res/font/<name>.ttf` and replace the `fontFamily` values there.
- Profile (`fragment_profile.xml`): left-aligned ID panel (flat colour, `ShapeableImageView` avatar with amber frame, amber key-tag role chip), flat hairline rows, no cards. Ids unchanged; `ProfileFragment` only swaps `circleCrop()` for `centerCrop()`. Settings: labels only.
- Dock bar: ink active icon / neutral idle, amber wash behind the active tab, neutral shadow (was violet glow), 20 dp corners. Card (`item_bh_card.xml`): price in `Text.Bofo.Price` (ink, not violet). Gradients removed from `bg_profile_hero`, `bg_dash_hero`, `bg_avatar_ring`.
- Not done: map pins and `RentCardView` (drawn in code), Login, Explore, Chat, Owner screens still use the old shapes and the lavender tint where they hard-code it. Not built or run by Claude.
---
# Handoff (Android) - v2.0.94 Group labels: rounded square
- **PR title:** `v2.0.94 - Android: map group price labels are rounded squares, not capsules`
- `MapCardView.bubble(count, from)` price variant: corner radius `8 * d` (outer) / `8 * d - 2.5 * d` (inner) instead of `h / 2`. Bitmap size and anchor unchanged. Price pins (`pricePin`) and route chips (still `h / 2`) were NOT changed. Not built or run by Claude.
---
# Handoff (Android) - v2.0.93 Settings + Profile redesign
- **PR title:** `v2.0.93 - Android: redesigned Settings and Profile screens (icon rows, hero card)`
- Layout-only: every view id used by `SettingsFragment` / `ProfileFragment` / `AiChatLogFragment.attachEntry` is kept (rows are TextViews with start icon + end chevron, so `switchRole.setText` still works). Row dividers = `showDividers="middle"` (hidden rows skip their divider). Not built or run by Claude. Not done: Edit profile screen (`ProfileSetupFragment`) still the old look.
---
# Handoff (Android) - v2.0.92 Switch role without re-login
- **PR title:** `v2.0.92 - Android: switching Student / Owner updates the app at once (no re-login)`
- `SettingsFragment.switchRole` sets `AuthRouter.role = target` after the server call, then `AuthRouter.go(nav(), R.id.action_global_shell)` (global action pops the whole graph, so ShellFragment rebuilds its tabs). Not built or run by Claude.
---
# Handoff (Android) - v2.0.91 AI Chats monitor (admin only)
- **PR title:** `v2.0.91 - Android: admin-only AI Chats monitor (what users ask BOFO), on/off switch, 90-day retention, privacy notice`
- **Supabase (run in this order):** 1) `supabase/migrations/014_ai_chat_log.sql` (adds `app_flags`, `ai_chat_log`, `ai_chat_log_wipe()`, `ai_chat_log_purge()`, pg_cron job `ai-chat-log-retention` daily 03:15 UTC; if pg_cron is off it prints a notice: enable it under Database > Extensions and run the file again). 2) `supabase functions deploy bofo-chat`. 3) Release the app, THEN switch on: `update public.app_flags set value = true, updated_at = now() where key = 'ai_chat_monitor_enabled';` (seeded OFF so older app versions, which do not show the privacy notice, are not logged before the update is out). Existing tables and policies are untouched.
- **Switch = Supabase `app_flags` row `ai_chat_monitor_enabled`** (read by the edge function with the service role, cached 30 s; read by the app for admins). Missing row / read error / false = nothing stored and the admin button is hidden. **Retention = pg_cron** (`ai_chat_log_purge(90)`), plus a backstop: about 1 in 100 logged answers also calls `ai_chat_log_purge` from the edge function.
- **Security:** `ai_chat_log` RLS: SELECT only for `is_admin()`; no INSERT / UPDATE / DELETE policy and the grants are revoked, so only the service role (edge function) writes. "Delete all" = RPC `ai_chat_log_wipe` (admin check inside, writes an `audit_logs` row `ai_chat_wipe`). FK `user_id -> users(id) on delete cascade`: deleting an account also deletes that person's AI chats. Name comes from `users.full_name` (fallback auth metadata), e-mail from the auth user.
- **Edge function:** `logAfterAnswer` is called at the two answer points (normal answer; the private-info refusal). It runs after the response (`EdgeRuntime.waitUntil` when present), catches everything, and skips when the flag is off. Not logged: rate-limit replies (429), errors, empty messages.
- **App:** `AiChatLogFragment` (+ `AiChatLogAdapter`, `AiChatLogService`, `AiChatRules` with `AiChatRulesTest`, layouts `fragment_ai_chat_log` / `item_ai_chat*`, strings `strings_ai_chats.xml`). Entry: admin Profile button `ai_chats_open`, shown by `AiChatLogFragment.attachEntry` (called from `ProfileFragment.load`) only for role ADMIN while the flag is on; the screen is a nav destination `aiChatLogFragment` and re-checks the role and the flag itself. Search is server side (`or=(...ilike...)`, term cleaned by `AiChatRules.searchTerm`), the date filter is `created_at=gte.`, paging is `limit=31&offset=n` (the 31st row only says "Show more"), merged without duplicates. "Group by user" regroups the rows already loaded (no new request). Privacy text: `LegalText` (PRIVACY) and a 2nd bubble in `BofoChatOverlay.open()` (`ai_chats_notice`).
- **Remove later:** `REMOVE_AI_CHATS.md` (flag off, `014_ai_chat_log_REMOVE.sql`, delete files, remove 6 hook points).
- **Verified by Claude:** the 12 `AiChatRulesTest` tests were compiled and run with a stand-in for JUnit (all pass); XML files parse; every resource and view id the new code uses exists; the Java files have no syntax errors (symbol errors only, because there is no Android SDK). **Not verified:** Gradle build, app run, the SQL on a real database, the Deno edge function (not type-checked or deployed), pg_cron.
- **Risky / not done:** "Group by user" only groups what is loaded (30 per page, "Show more" adds more); an insert fails (logged, chat unaffected) if the user has no `public.users` row yet; users on app versions older than 2.0.91 get logged without seeing the notice once the flag is on; `policy.privacy.version` was NOT bumped (decide if users must re-accept); `ilike` search on question / answer is a table scan (fine for 90 days of chats; add a trigram index if it gets slow); `HANDOFF_ANDROID.md` is still long (older entries are not yet moved to `docs/HANDOFF_ANDROID_ARCHIVE.md`).
---
# Handoff (Android) - v2.0.90 Verified shield icons
- **PR title:** `v2.0.90 - Android: green / grey shield shows verified owners on cards, place page and map pins; solid map pins`
- App: new `ic_shield_verified` (green) / `ic_shield_unverified` (grey) drawables. Student card (`item_bh_card.xml` `verified`, `BhAdapter`) shows the shield next to the name; place page chip (`BhDetailFragment`) has the shield icon; map pin badge (`MapCardView.shield`) is a shield instead of a circle; BOFO chat result badge has the green shield. Pin edge is now solid and the zoomed-out dot halo is a solid tint (they were 25 % / 18 % opacity = see-through). No Supabase change. Not built or run by Claude.
---
# Handoff (Android) - v2.0.89 Unverified owners can list
- **PR title:** `v2.0.89 - Android: unverified owners can list places; "not verified" icon on pins and place page`
- **Supabase:** run `supabase/migrations/013_unverified_can_list.sql` (new `is_owner_or_admin`; `bh_insert` / `bh_update` no longer need verification; `get_or_create_conversation` no longer needs a verified owner). Boosts / featured still need verified (`is_featured` and the boost code are unchanged). Custom amenities (`get_or_create_amenity`) still need a verified owner.
- App: `ListingsFragment.add()` always opens the form (no verify gate); banner / state texts reworded; `MapCardView.pricePin` draws a grey "!" badge (top-left) when the owner is not verified, the blue check when verified; `BhDetailFragment` adds a "Not verified" chip. Not built or run by Claude.
---
# Handoff (Android) - v2.0.88 Verified badge on map pins
- **PR title:** `v2.0.88 - Android: verified-owner badge replaces the green "New" dot on map pins`
- `MapCardView.pricePin`: the green "New" dot (top-left) is gone; a verified owner now gets a blue check badge with a white ring on that corner, and the old check inside the pill is removed (the pill is narrower). The `fresh` argument is still passed but unused; "New" still shows as the star text on cards. No Supabase change. Not built or run by Claude.
---
# Handoff (Android) - v2.0.87 Who can stay (female only / male only / both)
- **PR title:** `v2.0.87 - Android: owners choose who can stay (female only, male only, both); shown on the card and filterable`
- **Supabase:** run `supabase/migrations/012_gender_policy.sql` (adds `boarding_houses.gender_policy` = any / female / male, default any; copies the old "Female only" / "Male only" amenities into it and removes those two amenities; recreates `bh_cards` with `gender_policy`).
- Owner: `ListingFormFragment` page 2 has "Who can stay here?" (`gender_group`) -> `Draft.gender` / `syncGender` (same pattern as rules: written on edit only if the place came back with the column; on create only sent when not "any").
- Student: `item_bh_card.xml` `gender` badge (bottom-start of the photo, `BhAdapter`), a chip on the place page (`BhDetailFragment`), and a text on the map strip card (`MapCardView.fillCard`). Hidden when the database has no column yet (`GenderRules.normalize` -> "").
- Filter: `BrowseState.gender` ("" / female / male / any = both), `FilterSheet` "Who can stay" segments (All / Female / Male), map chips "Female only" and "Male only" (same value), server filter `gender_policy=eq.x` in `BhService.list`, `GenderRules.keep` on the phone and in `OfflineExplore.apply` (new overload; the old one still works). `BhService.cards` retries without the column if it is missing (`genderCol`).
- Risky: filter is an exact match (Female = female-only places, Both = open places); a girl who wants every place she may stay in should pick "Female" and "Both" one after the other (one choice at a time). The sign-up "Sex" field is unchanged and is not used for filtering. Not built or run by Claude.
---
# Handoff (Android) - v2.0.86 Solid opened pins
- **PR title:** `v2.0.86 - Android: opened places are solid (no more see-through price pins)`
- Removed `viewedOpacity` (the 0.6 opacity of v2.0.81). Viewed state is now part of the pin bitmap (`seen` = viewed and not selected, key `pp|price|sel ver feat saved new seen`) and of the dot colour (`isViewed` expression). `pinStateVer` still triggers the redraw when the viewed list changes.
- Risky: the muted colours are fixed values (not theme colours). Not built or run by Claude.
---
# Handoff (Android) - v2.0.85 Cleaner map cards
- **PR title:** `v2.0.85 - Android: locate button no longer covered, solid white map card, proper Commute button`
- Strip `RecyclerView` clips children now; adapter margins (6 / 6 / 6 / 12 dp) leave room for the 6 dp card shadow. Card colour `#FEFEFF` on `item_map_strip.xml` (a colour equal to `colorSurface` gets a Material 3 tint). Chip text and icon colours come from `res/color/commute_chip_fg.xml` (activated = white), so `onBindViewHolder` only calls `setActivated`.
- Risky: if the card still looks tinted on a device, set `app:cardElevation` to 0 and draw the shadow another way; the tray margin (178 dp) follows the card height. Not built or run by Claude.
---
# Handoff (Android) - v2.0.84 Map polish and checks (Step 5 of 5)
- **PR title:** `v2.0.84 - Android: TalkBack labels, 44 dp Commute touch area and lighter strip refresh on the Explore map`
- `MapCardView.refreshStripRows` (visible range only) is used by `setCompareIds`, `setCommuteBusy`, `clearCommute`. `StripAdapter.onCreateViewHolder` posts a `TouchDelegate` on the chip's parent column; `onBindViewHolder` sets the chip's content description. `ExploreFragment.paintChips` sets `ViewCompat.setStateDescription` (`map_state_on/off`), `paintTray` sets the tray description (`map_compare_cd`), `startCommute` rejects NaN positions. `offline_pill` has `accessibilityLiveRegion="polite"`.
- Risky: the touch delegate is built once after the first layout of a card (a card whose text column changes size a lot keeps the old rectangle until it is re-created); the quick chips are 34 dp high (the filter sheet is the other way to set the same filters). Manual test list: `FINAL_CHECKLIST.md`. Not built or run by Claude.
---
# Handoff (Android) - v2.0.83 Commute and offline on the map (Step 4 of 5)
- **PR title:** `v2.0.83 - Android: commute route on the house card and offline last-search map on Explore`
- Commute: `item_map_strip.xml` `strip_commute` -> `StripAdapter` -> `Listener.commuteTapped(id)` -> `ExploreFragment.startCommute` (campus null: `pendingCommuteId` + `CampusPickerSheet`, resumed from the `BrowseState.CHANGED` observer) -> `TripPlanner.planWithin(from, to, 8)` on `Async` -> `CommuteRules.best/line/drawable` -> `MapCardView.showCommute(id, path, approx, line, badge)`. `showCommute` saves the camera (`beforeCommute`), sets `commuteId` + `stripLocked` (so `rebuildStrip` / `syncStrip` do nothing for other houses), `select(id)`, `setRoute`, `setRouteLabel`, `fitRoute`. `clearCommute` is the reverse and eases back. `onTap` ignores other pins and no longer deselects while a commute is drawn. `ExploreFragment.endCommute` runs on Clear route, any `BrowseState` change, house gone from the results, List mode and `onDestroyView`. `commute_clear` replaces `map_chips` while a route shows (`paintCommuteUi`).
- Offline: `OfflineExplore.save` is called in `reload()` right after `BhService.list` (not when `Connectivity.isOffline()`, so an old copy served by `Offline.array` is not re-saved; an empty result never overwrites). `fail()` -> `showSaved(softNow)` (only for network errors and only when a copy exists) runs `OfflineExplore.apply` (text, price, amenity names incl. ticked catalog ids by label, Verified, Has beds, sort, campus distance, areaBox). `paintOffline` toggles `offline_pill`, the " · offline" count suffix and `setSearchAreaEnabled`. `Connectivity.OFFLINE` observer: back online while `offlineShown` -> `soft = true; reload()`.
- Risky: the saved set is the server answer after the server-side filters (text, max price, ticked catalog amenities), so offline filters only narrow it. A strip card is now about 30 dp taller; the compare tray margin was raised to 168 dp (change it again if the card changes). While a commute shows, campus dots are hidden by the existing route rule; tapping one still reaches `campusTapped` and ends the route (campus change). The planner can take up to 8 s on slow servers (chip says "Finding…"). Not built or run by Claude.
---
# Handoff (Android) - v2.0.82 Filters and compare on the map (Step 3 of 5)
- **PR title:** `v2.0.82 - Android: quick filter chips and long-press compare tray on the Explore map`
- Chips: built in `ExploreFragment.setupChips` into `map_chips_row` (inside `map_frame`, top margin 52 dp); `chipOn(i)` / `toggleChip(i)` read and write `BrowseState` (`maxPrice == QUICK_PRICE`, `hasAmenityNamed` / `setAmenityNamed` for `AMEN_WIFI` / `AMEN_FEMALE`, `verifiedOnly`, `bedsOnly`). A chip tap sets `keepArea` + `soft` then `BrowseState.bump()`; the observer keeps `areaBox`, `reload()` skips the camera fit and calls `mapCard.softFade()`. `FilterSheet` adopts amenity names into ticked catalog ids once the catalog loads (`adoptNames`) and writes `BrowseState.amenityLabels` (id -> name) on apply so the chips can see ticked amenities.
- Compare: `MapCardView.Listener.placeLongPressed(id)` (pin via `addOnMapLongClickListener` / `placeIdAt`, strip card via the card's long-click in `StripAdapter`); `setCompareIds` outlines compared cards. `ExploreFragment.compare` (max 3, id -> `BoardingHouse`), tray ids `compare_tray/thumbs/label/clear` in `fragment_explore.xml`. `ShellFragment.showCompare` -> `BofoChatOverlay.openCompare` (opens the card, then 300 ms later says "Compare these n places" and shows `BofoIntentLocal.compareAnswer`, built on the phone).
- Risky: the tray sits 136 dp above the map bottom, tuned for the strip card height (about 94 dp + 30 dp); a different card height needs `compare_tray` margin changed. A house that leaves the results after a filter change stays in the tray (its data is kept). Pins do not show the compare state (only strip cards do). Not built or run by Claude.
---
# Handoff (Android) - v2.0.81 Cards that follow the map (Step 2 of 5)
- **PR title:** `v2.0.81 - Android: swipeable house card strip and pin states (viewed, saved, new) on the Explore map`
- `MapCardView`: `setStripEnabled(true)` (Explore) makes `select()` skip the peek card and call `syncStrip`; `rebuildStrip(force)` (from `setPlaces`, `render`, camera idle) fills `stripItems` from `viewBox()`, nearest to centre first, max `STRIP_MAX` 20, selected house always at index 0; `stripLockUntil` (900 ms after a pin tap or swipe) stops idle rebuilds from re-sorting under the card; swipe = `PagerSnapHelper` idle -> `userSelect` + `easeCamera`; recycler padding 30 dp, card width = recycler width - 60 dp. Pin states: feature props `viewed` (opacity 0.6 on `places` and `places-dots`), pin key `pp|price|sel ver feat saved new`, `pinStateVer` in the rebuild signature. `AppPrefs` key `viewed_ids` (comma list, newest last, 200). New ids: `strip`, `strip_photo/name/price/info/open`.
- Risky: the strip is 30 dp above the bottom, right margin 62 dp so the locate button and the Ask BOFO mascot stay clear; `placeSelected` is still unused on Explore. Step 3 needs the strip cards for long-press compare (`StripAdapter` in `MapCardView`). Not built or run by Claude.
---
# Handoff (Android) - v2.0.80 Explore map pins (Step 1 of 5)
- **PR title:** `v2.0.80 - Android: price pins, Search this area, locate button and campus chip on the Explore map`
- `MapCardView`: `pricePin(text, selected, verified, featured)` bitmaps cached in `imgs` / `imgSize` under `pp|<price>|<sel><ver><feat>`; clusters use `bubble(count, from)` key `cl|n|from`; `PIN_SEL_SCALE` 1.12. "Search this area": `gestureMove` (set by `REASON_API_GESTURE` and cluster taps), `holdArea` (pin tap easing), `areaBase`, `checkArea()` on camera idle, `Listener.searchArea(s, w, n, e)`, enabled by `setSearchAreaEnabled(true)` (Explore only). `setCampusChip(name)`, `setLocateLift(dp)`. New layout ids `locate_col`, `near_campus`, `search_area`; strings `map_search_area`, `map_near_campus`.
- `ExploreFragment`: `locateMe()` (own `LocationHelper`), `areaBox` filter inside `reload()` (cleared by new search text, `BrowseState.CHANGED`), `pushMap(box == null)`. `Money.shortPeso`. No new `AppPrefs` keys, no Supabase change.
- Risky / not done: Home and the full-screen map keep the old look except that they now also draw price pins (shared view). Locate lift is a fixed 72 dp. Not built or run by Claude.
---
# Handoff (Android) - v2.0.79 Map house card
- **PR title:** `v2.0.79 - Android: map house card with photo, amber rating, solid open button, slide-up`
- `MapCardView.select`: loads `BoardingHouse.photoUrl()` into `peek_photo` with Glide (hidden when empty), amber `ForegroundColorSpan` on the star, slide-up/fade when the card goes from GONE to VISIBLE. `peek_photo` is clipped by a `ViewOutlineProvider` (16 dp) set in the constructor. Ids `peek_name/price/info/close/open` unchanged. No Supabase change. Not built or run by Claude.
---
# Handoff (Android) - v2.0.78 Locate button animation
- **PR title:** `v2.0.78 - Android: smoother locate button (ring pulse, found pop, press spring, colour fade)`
- `MapCardView`: pulse is a single `ValueAnimator` driving `locate_ring` scale / alpha + button breathing; `stopLocatePulse` pops and settles; `setupLocatePress` (touch listener returns false); `setLocateActive` uses a `TransitionDrawable` + `ArgbEvaluator` colour filter. `view_map_card.xml`: locate button wrapped in a 44 dp `FrameLayout` with `clipChildren=false` (also on the root and the button column). No Supabase change. Not built or run by Claude.
---
# Handoff (Android) - v2.0.77 Home header
- **PR title:** `v2.0.77 - Android: nicer Home header (avatar ring, soft bell, floating search bar)`
- Layout / drawables only, no Java change: `fragment_home.xml` greeting row + `search_pill` (ids unchanged), new `bg_avatar_ring`, `bg_icon_soft`, reworked `bg_bell_button`, `bg_search_pill` (radius 28 dp, shared with `fragment_explore.xml`). No Supabase change. Not built or run by Claude.
---
# Handoff (Android) - v2.0.76 One map credit
- **PR title:** `v2.0.76 - Android: remove the duplicate blue map (i)`
- `MapCardView.onMapReady`: `setAttributionEnabled(false)` (the native MapLibre button duplicated our `setupAttribution` credit). No Supabase change. Not built or run by Claude.
---
# Handoff (Android) - v2.0.75 Dots + map credit
- **PR title:** `v2.0.75 - Android: indigo house dots with halo, map credit folds into an (i) button`
- `MapCardView`: dots use `Expression.color` (the old `Expression.literal(int)` made them black); `places-dots-halo` layer under `places-dots`. `setupAttribution` / `setAttribution` toggle the `attribution` TextView between pill and round (i) (auto-fold 4 s / 5 s). No Supabase change. Not built or run by Claude.
---
# Handoff (Android) - v2.0.74 Path always in view
- **PR title:** `v2.0.74 - Android: pathfind fix, the path stays in view`
- `MapCardView`: `fit()` / `overview()` call `fitRoute()` when `routeShown()`; `glideToRing` returns while a path is shown; `fitRouteBounds` caps padding (<= 70 % height, <= 20 % width), falls back to plain padding then centre + zoom 15, and retries when the card has no size yet. No Supabase change. Not built or run by Claude.
---
# Handoff (Android) - v2.0.65 Own amenity icons + place page UI
- **PR title:** `v2.0.65 - Android: own line icons for amenities (no emoji), cleaner place page`
- New `AmenityIcons.res(name)` maps the amenity NAME to one of 25 vector drawables `ic_am_*` (water, bolt, wifi, snow, shower, kitchen, laundry, parking, cctv, book, flame, fan, bed, closet, lock, users, moon, paw, female, male, tv, fridge, shield, spark = fallback for unknown / owner-made names, rule = check circle). The emoji in `amenities.icon` is no longer shown anywhere (the column is untouched). `Chips.create(parent, Amenity, suffix, checkable, checked)` adds the icon as chip icon: used by the filter sheet and the listing form. Place page: amenities are two-column tiles (`showAmenities` / `amenityTile`, `bg_amenity_tile`), House rules is a card with check icons, the duplicate star line and the "Free Wi-Fi" line under the tiles are hidden, section spacing 20 dp. To add an icon: new `ic_am_*.xml` + one line in `AmenityIcons`. No Supabase change. Not built or run by Claude (icons were only previewed on a rendered sheet).
---
# Handoff (Android) - v2.0.64 House rules
- **PR title:** `v2.0.64 - Android: owners can list house rules, students see them on the place page`
- **Supabase: run `supabase/migrations/011_rules.sql` once** (adds `boarding_houses.rules text[]`, max 20). Until then the app still works: `BhService.publish` only sends `rules` when the list is not empty, `detailOnline` reads `rules` in its own optional request, and `updateListing` writes it only when `Draft.syncRules` (the place came back with a rules key).
- `ListingFormFragment` page 3: "House rules" (input + Add, list with remove, max 20, 100 chars each; a typed-but-not-added rule is added on publish). `BhDetailFragment.showRules` + `rules_title` / `rules_list` in `fragment_bh_detail.xml` (hidden when empty). Rules are on the place page, not on the small list cards. Not built or run by Claude.
---
# Handoff (Android) - v2.0.63 See-through path switch
- **PR title:** `v2.0.63 - Android: Settings switch makes the map path 80% transparent`
- `AppPrefs.seeThroughPath` (key `path_see_through`, default off) + new row `path_fade` in `fragment_settings.xml` / `SettingsFragment`. `MapCardView.applyRouteOpacity()` sets `lineOpacity` 0.2 on `route-casing`, `route-line`, `route-dash`; runs from `paintRoute()` and `onResume` (so it updates when you come back from Settings). No Supabase change. Not built or run by Claude.
---
# Handoff (Android) - v2.0.62 accurate road path + auto refresh
- **PR title:** `v2.0.62 - Android: path follows the real road and refreshes by itself`
- `Router.simplify` (Douglas-Peucker) replaces even thinning; `fetch` tries OSRM, Valhalla, OSRM demo (drive only) and keeps the shorter believable road. `MapFragment.maybeReroute` (40 m / 8 s, `routeLive`, `rerouting`); `DirectionsCardView.runPlan` re-runs every 15 s while an option is approximate. No Supabase change. Not built or run by Claude.
---
# Handoff (Android) - v2.0.61 auto path + readable Ask BOFO
- **PR title:** `v2.0.61 - Android: map path refreshes by itself, cleaner map UI, readable Ask BOFO replies`
- `MapFragment.drawRoute/fetchRoute/retryLater/onResume`: walk <= 3 km else `Router.drive`; estimate first, retries until `!approximate`. New style `Widget.Bofo.Button.MapPill`. `bofo-chat/index.ts`: `distText`, `lines`, `listReply`, `nearWhere` (numbered list replies). **bofo-chat was deployed to the live project (version 17) from Claude on 2026-11-01; redeploy with `supabase functions deploy bofo-chat` only if you change the file.** Also fixed: "under 5k" / "2.5k" / "4 libo" are read as thousands (was PHP 5). If the build fails: `Widget.Bofo.Button.MapPill` attrs (`icon`, `iconTint` null) on the MaterialButton. Not built or run by Claude.
---
# Handoff (Android) - v2.0.60 Ask BOFO chat redesign
- **PR title:** `v2.0.60 - Android: Ask BOFO chat redesign (listing cards, compare table, starter chips, typing dots)`
- `BofoChatOverlay`: `addResults` (HorizontalScrollView of `resultCard`, card width 236 dp or full width for one result), `addCompare` (columns share the card width, zebra rows, green best cell, `bofo_wins` line, header tap = open_detail), `addStarters` (4 chips in the empty chat, removed by `send()` like the other chips), `DOTS` typing indicator (`dotsTick`, removed in `finish()` and `onDetachedFromWindow`), `pop()` entrance animation. Card buttons fire `show_on_map` (ids = [id]) and `directions` (ids = ["me"] when the server's directions action started at the phone). No server change. If the build fails: binding fields `chipDist`, `chipRating`, `chipBeds`, `amen`, `addr`, `badge`, `btnMap`, `btnRoute` (from the layout ids), `android:clipToOutline` on the card needs the white rounded background to give the outline. Not built or run by Claude.
---
# Handoff (Android) - v2.0.59 Ask BOFO compare without names
- **PR title:** `v2.0.59 - Android: Ask BOFO searches first when you say compare without place names`
- Only `bofo-chat/index.ts` (+ build.gradle 2.0.59 / 259). `compareSearch` flag: intent `compare` with no `names`, no `refs`, no previous results is switched to `search`, then set back to `compare` after `picked` (note `no_compare` when < 2). New `inPhrase()` ("in / sa <area>" -> `params.text`), `landmarkPhrase` cuts a trailing "and compare / show ...". **`supabase functions deploy bofo-chat` required.** `BofoIntentLocal` (offline answers) was NOT changed and still says it needs two names. Not built or run by Claude.
---
# Handoff (Android) - v2.0.58 Ask BOFO reasoning leak
- **PR title:** `v2.0.58 - Android: Ask BOFO no longer shows the AI's thinking in the chat`
- Only `bofo-chat/index.ts` (+ build.gradle 2.0.58 / 258). `callLLM(..., accept)` strips `<think>` blocks and rejects answers where `looksLikeReasoning()` is true (next model, then template); request body has `reasoning: { exclude: true }`; `parseLocal` sets `limit = 5` for "all / lahat / tanan". **`supabase functions deploy bofo-chat` required.** If it still leaks, check Edge Function logs for "unusable answer" and consider removing the reasoning model from `LLM_MODELS`. Not built or run by Claude.
---
# Handoff (Android) - v2.0.57 faster Ask BOFO
- **PR title:** `v2.0.57 - Android: Ask BOFO answers much faster`
- `bofo-chat/index.ts`: `fast = parseLocal()` first, LLM classify only when `fast.intent === "unknown"`; wording LLM only for chit-chat unless `LLM_WORDING=1`; `callLLM` timeout 5-6 s, max 2 models. `BofoChatOverlay`: location gate skipped when `BrowseState.collegeId != null`. **`supabase functions deploy bofo-chat` required.** Not built or run by Claude.
---
# Handoff (Android) - v2.0.56 bigger map circles
- **PR title:** `v2.0.56 - Android: bigger circles on the map`
- Only `MapCardView.java` (+ build.gradle 2.0.56 / 256). `BIG = 1.5f` scales every `circleRadius`, the user-dot animation sizes (`layerCircle`) and `bubble()`; pins `iconSize` x1.25; tap box wider. Not built or run by Claude.
---
# Handoff (Android) - v2.0.55 fix: Suggest BH here returned nothing
- **PR title:** `v2.0.55 - Android: Ask BOFO no longer filters by words like "BH here"`
- Only `bofo-chat/index.ts` (+ build.gradle 2.0.55 / 255). `GENERIC_TEXT` in `sanitizeParams`, fallback without `text` in the list branch, no `apply_filters` action without results. **`supabase functions deploy bofo-chat` is required**; check Supabase > Edge Functions > Logs if the old text "I couldn\x27t find a place that matches" still shows (= old function still running).
---
# Handoff (Android) - v2.0.54 Ask BOFO uses the chosen college + path drawn on the map
- **PR title:** `v2.0.54 - Android: Ask BOFO stays inside the chosen college area; "show me the path" draws on the map`
- `bofo-chat/index.ts`: origin = chosen college > phone (unless "near me" is said); list intents are limited to `radius_km` around the college (`campusScoped`, note `empty_area:`); `PATH_RE` / `SUGGEST_RE` force intent `directions` / `search`; `directions` action comes first and carries `ids:["me"]` when it should start at the phone. **Run `supabase functions deploy bofo-chat`.**
- App: `MapFragment.openRoute(from, id, fromMe)` shows one pin + `Router` path (+ `setRouteLabel`); `ShellFragment` `directions` -> map (open_detail still -> place screen); `BofoChatOverlay` auto-runs the `directions` action 1.2 s after a `directions` answer. Offline `BofoIntentLocal` mirrors the college scope and path words.
- Test on phone: pick a college, ask "suggest boarding house here" (only places in the radius), "show me the path to <name>" (map + line, not the place screen), "near me" (measures from phone), radius too small (friendly empty message). Not built or run by Claude.
---
# Handoff (Android) - v2.0.53 tab bar visible again + Ask BOFO diagnostics
- **PR title:** `v2.0.53 - Android: tab bar icons no longer hidden under the bar; bofo-chat logs why the LLM fails`
- `DockBarView`: bar had elevation 10 dp, tabs 0, so the bar painted over the icons. Highlight and cells now use the same elevation (drawn in child order).
- `bofo-chat/index.ts`: logs HTTP status / timeout / missing secrets per model. "Offline mode" text reworded (it means the LLM failed, not the phone).
- Ask BOFO reads the phone position (permission + phone Location popup) when a question needs it and sends it to bofo-chat; falls back to the campus.
- Ask BOFO: reads the phone position when a question needs it, understands named places (near Silliman) from the colleges table, limits results to the radius, shows **bold** names and prices.
- Needs `OPENROUTER_API_KEY` and current `LLM_MODELS` set with `supabase secrets set`. Not built or run by Claude.

---
# Handoff (Android) - v2.0.52 Ask BOFO AI assistant
- **PR title:** `v2.0.52 - Android: Ask BOFO answers with real listings (nearest, cheapest, compare, map)`
- Delivered in 3 steps (see `BOFO-AI-STEPS.md`, full docs in `BOFO-AI-CHANGELOG.md`). Backend must be DEPLOYED for the new answers: until then the app falls back to the rule-based answers (`BofoIntentLocal`) because the old function returns no `results`.
- Wiring: `BofoChatOverlay.send()` -> `BofoAiService.ask(msg, history, college)` -> `BofoAnswer`; actions go to `ShellFragment.runBofoAction` (map ids / detail / Explore filters). Directions = opens the place screen (it holds `DirectionsCardView`).
- Likely first problems if the build fails: binding names `ItemBofoResultBinding` / `ItemBofoCompareBinding` / `ItemBofoChipsBinding` (ids: result `photo,name,price,meta,verified`; compare `table`; chips `chips`), Glide `RoundedCorners` import, `Chips.create` + `setClickable(true)`.
- Not done / ideas: Tagalog `values-fil` strings (project has none), suggestion chips are sent as English text, a dedicated "directions" screen from the chat.

---

# Handoff (Android) - v2.0.51 chat header mascot
- **PR title:** `v2.0.51 - Android: Ask BOFO chat header uses the JavaFX mascot animation`
- Rebuilt on top of the real `main` (v2.0.50). An earlier attempt was made from a different folder snapshot (DockBarView with extra elevation, 56 dp button, other DashboardFragment) and the tab bar went invisible: do NOT bring those files along. Only `view_bofo_chat.xml`, `BofoChatOverlay.java`, `build.gradle`.

---

# Handoff (Android) - v2.0.50 JavaFX mascot animation (2026-10-31)
- **PR title:** `v2.0.50 - Android: Ask BOFO button uses a port of the JavaFX mascot animation`
- User: "re-use the javafx animation for the AI chat button instead of making another shitty version". Source of truth = `src/main/java/com/bofo/finder/ui/Mascot.java` + `BofoPanel.java` (idle timing). `BofoMascot.java` recomputes every frame from `SystemClock.uptimeMillis()` (no animator objects); JavaFX `Interpolator.EASE_*` curves are in `ease()`.
- Colors are the Android mascot colors (yellow hat, `design/mascot`), NOT the JavaFX lavender re-theme (`Mascot.java` palette). To match JavaFX exactly, change the 7 color constants at the top of `BofoMascot`.
- If the mascot is clipped when it hops / z's float: some parent has `clipChildren=true`; `FloatingBofoView` and its `bobber` already set it to false.
- Not done: the chat header `MascotView` (THINKING / SITTING) still uses the old vector; can switch to `BofoMascot` (`setExpression`) if asked.

---

# Handoff (Android) - v2.0.49 offline strip (2026-10-31)
- **PR title:** `v2.0.49 - Android: offline strip with icon and Try again`
- `offline_banner` is now a LinearLayout (icon, text, `offline_retry` calling `reload()`); same observer in `HomeFragment`.

---

# Handoff (Android) - v2.0.48 slow / no-internet states (2026-10-31)
- **PR title:** `v2.0.48 - Android: skeleton + "Slow network" note, clear "No internet connection" screen, map note`
- User screenshot: Home showed an empty area under "Nearby boarding houses" and a blank map. Now `ListArea.showLoading()` posts a 5 s `slowRun` (note over the skeleton, `setSlowListener` lets Home show a map note); `ListArea.showOffline()` is used by Home / Explore / Saved when `Connectivity.isNetworkError(e)`; `ListArea` observes `Connectivity.OFFLINE` and reloads when it flips back to online while the offline screen is showing.
- Test: airplane mode + open Home (offline screen, then turn it off = reloads by itself); Developer options > network throttling or a very weak connection (skeleton, then the slow note after 5 s, map note).
- Not done / ideas: other screens (Chats, Dashboard, Detail) still use their own plain text messages with the new `list_offline` wording.

---

# Handoff (Android) - v2.0.47 Directions auto-retry (2026-10-31)
- **PR title:** `v2.0.47 - Android: Directions card retries by itself`
- `DirectionsCardView.runPlan(t, my, from, to, tries)`; guarded by `token` and `planning`. If the user means "update as the phone moves", that already happens through `onFix` / `startWatching`.

---

# Handoff (Android) - v2.0.46 locate toggle (2026-10-31)
- **PR title:** `v2.0.46 - Android: Dashboard locate button toggles you / all places, faster camera moves`
- If the user still finds it slow: the first tap with no saved fix waits for GPS (`LocationHelper.requestOnce`); consider `lastKnown` first.

---

# Handoff (Android) - v2.0.45 Dashboard UI polish (2026-10-31)
- **PR title:** `v2.0.45 - Android: owner Dashboard UI (hero card, stat cards, place cards with photo and chips)`
- User screenshot showed flat stat tiles (old `bg_dir_option` is only a hairline) and plain text place rows; fixed with the `bg_dash_*` drawables. If the user wants more: same card style for My places (`ListingsFragment` still uses a plain list) and the Create wizard.

---

# Handoff (Android) - v2.0.44 owner Create wizard (2026-10-31)
- **PR title:** `v2.0.44 - Android: Create / Edit place as a 4-step wizard (map pin + route, amenity picker, photo thumbnails, review)`
- **Owner port is now COMPLETE (update 1 = v2.0.43 Dashboard, update 2 = v2.0.44 Create).** Everything from the JavaFX `DashboardView` and `ListingFormView` exists on Android, plus everything that was already there (stats, My places menu, Boost, edit mode).
- Sync Gradle, run a debug build, log in with the Owner test button. TEST LIST: (1) Dashboard: banner for an unverified owner, map pins, tap a pin = purple route + distance, cards open the place; (2) Create: step 1 map moves under a fixed pin, Set pin shows "x km from <campus>", Next is blocked until name/address/pin; (3) rooms steppers; (4) Camera + Gallery, x removes a thumbnail; (5) amenity search, "Add ..." creates one (verified owner only), tap a selected chip to remove; (6) Publish; Edit from My places shows the amenities and the pin.
- Files: `ui/ListingFormFragment.java` (wizard, all in one file; sections marked with comment bars), `layout/fragment_listing_form.xml` (4 `pageN` blocks), `MapCardView.center()`, `BhService.Draft.syncAmenities`.
- Likely first problems if the build fails: binding names in `ListingFormFragment` (camelCase of the layout ids), `Chips.create` use, `LinearProgressIndicator` (Material 1.13 is in `build.gradle`). Nothing was compiled by Claude.
- Supabase: 005_amenities + 006_admin_can_publish applied on 2026-10-31 to project `zfxldzcoirqolflkkhrh` (see v2.0.43). No new migration in this update.
- Next ideas (not asked): removing EXISTING photos in edit mode, re-ordering photos, showing amenity chips on the Dashboard cards.

---

# Handoff (Android) - v2.0.43 owner Dashboard + Create plan (2026-10-31)
- **PR title:** `v2.0.43 - Android: owner Dashboard gets verification banner, map with route, place cards`
- Task from the user: "re-add ALL owner features from the JavaFX app (Dashboard and Create) to Android, keep what was added". Delivered as 2 updates: **update 1 = v2.0.43 Dashboard (done)**, **update 2 = v2.0.44 Create wizard (see the next entry / `OWNER_PORT_PLAN.md` if v2.0.44 is not there yet)**.
- Supabase 005 + 006 applied to project `zfxldzcoirqolflkkhrh` on 2026-10-31 (amenities now exist, so the amenity picker can work and edit mode can show amenities).

---

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



