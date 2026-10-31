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
