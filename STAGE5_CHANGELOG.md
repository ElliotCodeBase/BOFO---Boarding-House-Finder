# BOFO Android v2.0.5 - Stage 5 changelog (map + location)

Not opened or built in Android Studio / Gradle by Claude. Everything below was written and checked as far as a sandbox without Android allows (see "What was checked").

## What was added or changed

| File | What I did |
|---|---|
| `app/src/main/assets/bofo-map-style.json` | NEW. Map look: OpenFreeMap vector tiles, BOFO colours, no labels or POI icons. |
| `ui/MapCardView.java` + `res/layout/view_map_card.xml` | NEW. The map widget: pins, radius ring, campus dot, live dot, peek card, locate / expand buttons, attribution, 50 MB tile cache cap. |
| `ui/MapFragment.java` + `res/layout/fragment_map.xml` | NEW. Full-screen map with campus picker, radius slider, locate button. |
| `util/LocationHelper.java` | NEW. Permission + position (one-time and live). Honours Settings > Use my location. |
| `util/MapGeo.java` | NEW. Pure helper: GeoJSON for pins / ring / dots, bounds. Unit tested. |
| `ui/HomeFragment.java`, `res/layout/fragment_home.xml` | CHANGED. List / Map toggle, **Near me** chip, map locate + expand. |
| `util/BrowseState.java` | CHANGED. Near me state and the reference point (`refLat`, `refLng`, `hasRef`). |
| `ui/BhDetailFragment.java`, `res/layout/fragment_bh_detail.xml` | CHANGED. **Show on map** button. |
| `res/navigation/nav_graph.xml` | CHANGED. New destination `mapFragment`. |
| `res/values/strings.xml` | CHANGED. New texts for the map, Near me and location messages. |
| `res/drawable/ic_my_location.xml`, `ic_expand.xml`, `bg_map_button.xml` | NEW. Map button icons and round background. |
| `app/build.gradle` | CHANGED. Version 2.0.5 / 205; libraries `org.maplibre.gl:android-sdk:13.0.0`, `play-services-location:21.3.0`. |
| `app/src/test/.../MapGeoTest.java` | NEW. 7 tests (70 in total). |
| `CHANGES.md`, `HANDOFF_ANDROID.md`, `ANDROID_PORT_PLAN.md`, `bofo-android/README.md`, `docs/HANDOFF_ANDROID_ARCHIVE.md` | CHANGED. Version 2.0.5 notes; the Stage 6 resume prompt is at the top of `HANDOFF_ANDROID.md`. |

No Supabase change. No new Google Cloud or Supabase step.

## How to open each feature
1. Unzip `BOFO-android-v2.0.5-changes.zip` over the repo root, open `bofo-android` in Android Studio, **Sync Gradle**.
2. Run the app and sign in. **Home**: top right of the list heading, tap **Map** (next to **List**).
3. Tap a pin: peek card, **View** opens the place.
4. **Near me**: first chip under the heading on Home. **Locate**: round button top right of any map.
5. **Expand** button on the Home map (under the locate button) = full-screen map. Or open any place and tap **Show on map**.
6. Unit tests: `gradlew.bat testDebugUnitTest` in `bofo-android` (expect 70 passed).

## If something goes wrong (simple fix guide)

| Problem | Try this |
|---|---|
| Gradle sync fails: "Could not find org.maplibre.gl:android-sdk:13.0.0" | Check internet. Open https://central.sonatype.com and search `org.maplibre.gl android-sdk`; put the newest version in `app/build.gradle`. Make sure `mavenCentral()` is in the repositories of `settings.gradle`. |
| Red `org.maplibre...` import or a method that "cannot be resolved" | The library may have renamed it in version 13. Send me the exact error, or temporarily try an older 11.x version (the code was written from the 11.x docs). |
| `play-services-location:21.3.0` not found | Use the newest 21.x from Maven Google (Android Studio suggests it when you hover the line). |
| Map area is white / grey, no roads | Phone has no internet, or the tile server is slow: wait, then reopen. Pins still show. |
| Map is completely blank or the app crashes when you tap Map | Vulkan problem on an emulator or old phone. In `app/build.gradle` change the line to `org.maplibre.gl:android-sdk-opengl:13.0.0`, sync, run again. |
| Pins show but the map has no colours / wrong colours | A layer name in `assets/bofo-map-style.json` does not match the tiles. Send me the Logcat line containing `Style`. |
| Locate / Near me says "Location is off in Settings" | Profile > Settings > turn **Use my location** on. |
| Locate does nothing and no dialog | Phone permission was denied twice: Android Settings > Apps > BOFO > Permissions > Location > allow. Emulator: set a location in Extended controls > Location. |
| "Could not find your position" | Turn on Location in the phone's quick settings; outdoors or set an emulator location. |
| Near me list is empty | Increase the radius slider (the ring is drawn around where you were when you tapped Near me). |
| Show on map opens but no pin is selected | The place is outside the current radius: it is still centred. Choose **No campus** to see every place. |
| App won't build after unzipping | Make sure you unzipped over the repo root (folders `bofo-android/...` merge), then **Build > Clean Project** and **Sync**. Send me the first red error. |

## What was checked
- All XML well-formed; style JSON parses; every Java file passes a `javac` parse (only missing Android / MapLibre / Play Services symbols remain).
- `MapGeo` compiled and run on the JVM: 18 checks passed. The JUnit file `MapGeoTest` itself was not run.
- NOT checked: every MapLibre and Play Services call, the Vulkan renderer, the look on a real phone, the live tile layer names, Android lint.
