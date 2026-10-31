# BOFO Android v2.0.6 - Stage 6 changelog (directions + trip planning)

Not opened or built in Android Studio / Gradle by Claude. Everything below was written and checked as far as a sandbox without Android allows (see "What was checked").

## What was added or changed

| File | What I did |
|---|---|
| `util/Router.java` | NEW. Road route between two points: OSRM, then Valhalla, then a straight line flagged "estimate". Remembers answers; two callers share one server call. Ported from the desktop app. |
| `util/HttpText.java` | NEW. The only place that talks OkHttp for routes / jeepney lines (`get`, `postForm`, `head`). |
| `util/Trip.java` | NEW (ported). Fares (LTFRB, 19 March 2026), time estimates, the list of options, which one is "best". |
| `util/TripPlanner.java` | NEW (ported). Builds the ways to go. `planWithin` never waits more than 12 seconds. `quick` works with no network. |
| `util/TransitRoutes.java` | NEW (ported). Jeepney / bus lines mapped in OpenStreetMap (Overpass), saved on the phone for 14 days. |
| `util/TransitMatch.java` | NEW. Copied unchanged: which line really goes from A to B in the right direction. |
| `util/LocalTransit.java` + `assets/local-transit.json` | NEW. Hand-made lines (the file ships empty, as on the desktop). Read once at start-up. |
| `util/DirectionsRules.java` | NEW. The rules of the card with no Android in them: tab meanings, what is missing, first tab, nearest campus, when to plan again, summary texts, OpenStreetMap link. |
| `util/MapGeo.java` | CHANGED. `lineJson` (route as GeoJSON) and `pathBounds`. |
| `ui/DirectionsCardView.java` + `view_directions_card.xml`, `item_dir_option.xml`, `item_dir_note.xml`, 8 `bg_dir_*` drawables | NEW. The Directions card. |
| `ui/MapCardView.java` | CHANGED. Route line + end dots, `setRoute`, `clearRoute`, `fitRoute`, pin taps and locate button can be turned off, map does not lose drags to the page scroll. **Also fixes a Stage 5 compile error** (view binding `inflate` needs 3 arguments here). |
| `ui/BhDetailFragment.java`, `res/layout/fragment_bh_detail.xml` | CHANGED. The card sits between the contact buttons and the reviews. |
| `app/BofoApp.java` | CHANGED. Loads the hand-made lines at start-up. |
| `res/values/strings.xml` | CHANGED. Texts for the card. |
| `app/build.gradle` | CHANGED. Version 2.0.6 / 206. No new libraries. |
| `app/src/test/.../TripLogicTest.java`, `Stage6LogicTest.java` | NEW. 23 tests (93 in total). |
| `CHANGES.md`, `HANDOFF_ANDROID.md`, `ANDROID_PORT_PLAN.md`, `bofo-android/README.md`, `docs/HANDOFF_ANDROID_ARCHIVE.md` | CHANGED. Version 2.0.6 notes; the Stage 7 resume prompt is at the top of `HANDOFF_ANDROID.md`. |

No Supabase change. No new Google Cloud or Supabase step.

## How to open each feature
1. Unzip `BOFO-android-v2_0_6-changes.zip` over the repo root, open `bofo-android` in Android Studio, **Sync Gradle**.
2. Run the app, sign in, open any place, scroll down past the contact buttons: the **Directions** card.
3. Tabs: **Me -> BH**, **BH -> Campus**, **Me -> Campus**. Tap a way in **Ways to go** to draw it on the map. **More info** shows sources; **Open in OpenStreetMap** opens the same trip in the browser.
4. **Use my location** (Me tabs) asks for permission only when you tap it.

## What was checked
- 23 new tests run on the JVM against the real source files (through a small runner with stand-ins for JUnit, `org.json` and the network layer): **23 passed**.
- `TripPlanner.plan` end to end with no network and a hand-made line: the line is matched and its checked fare shown; far trips and timeouts give estimates.
- All XML is well-formed; every string / drawable / colour / style / id used by the new layouts and the new Java resolves; the Android Java files pass a `javac` parse (only "missing Android symbol" errors remain, which need the SDK).
- NOT checked: Gradle build, the MapLibre line-layer calls on 13.0.0, `MaterialButtonToggleGroup` rendering, the real routing / Overpass servers (no network in the sandbox), how the card looks on a phone.

## Simple fix guide
- **Build error in `MapCardView` or `DirectionsCardView` mentioning `withFilter`, `lineCap`, `lineJoin`, `lineDasharray` or `Expression`**: send me the exact line; MapLibre 13.x may have renamed it. The route line is the only thing that depends on these.
- **Route line is always dashed / straight**: the route servers did not answer (they are free and can be busy). Wait and tap the tab again. The note under the summary says so.
- **No jeepney lines, only "Jeepney or multicab (estimate)"**: nobody mapped a line there in OpenStreetMap and `assets/local-transit.json` is empty. Add a checked line (see `docs/LOCAL_TRANSIT.md` in the repo; use `stops`, or a `path` with a point about every 100 m).
- **"Use my location" says location is off**: Profile > Settings > Use my location.
- **Page will not scroll when my finger is on the map**: by design; scroll with your finger outside the map.
