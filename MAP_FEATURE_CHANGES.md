# Release rule (applies to every pull request / merge)

**Every pull request or merge bumps the app version.** Do all of this in the same PR:

1. Raise the version shown in **Profile > About** (`app.version` in `src/main/resources/com/bofo/finder/config.properties`) and **everywhere the old version appears** (`pom.xml` `<version>`, `<versionName>`, `<versionCode>` +1, docs). Search the repo for the old number before finishing.
2. Put the new version in the **PR title** (for example `v1.9.0 - short summary`).
3. Add it to the **changelog**: top entry in `CHANGES.md` and `HANDOFF.md`.

Patch (x.y.Z) for fixes, minor (x.Y.0) for new features. The v1.2.0 merge once left About showing v1.1.1: this rule exists so that never happens again.

---

# v1.17.0 - Profile setup + map opens on your region (2026-10-29)

`ui/ProfileForm`, `ui/ProfileSetupView`, `ui/PersonalInfoView`, `util/Regions`, `ui/MapPane.openOnRegion()`, `supabase/migrations/009_profile_setup.sql` (run it in Supabase). Details: `CHANGES.md`.

---

# v1.16.1 - Chat menu UI (2026-10-27)

`Ui.menu()`, `bofo.css` (`.context-menu`), `ChatView`, `ChatsView`. Details: `CHANGES.md`.

---

# v1.16.0 - Delete messages and chats (2026-10-27)

`ChatService`, `ui/ChatView.java`, `ui/ChatsView.java`, `supabase/migrations/008_delete_chat.sql` (run it in Supabase). Details: `CHANGES.md`.

---

# v1.15.5 - Card wider than the screen (2026-10-27)

`ui/CardGrid.java`: a single-column card gets exactly the column width. Details: `CHANGES.md`.

---

# v1.15.4 - My listings fits the screen (2026-10-27)

`ui/ListingsView.java`: one-column `CardGrid` instead of a VBox; equal-width Boost / Unlist / Delete buttons. Details: `CHANGES.md`.

---

# v1.15.3 - Cleanup + faster loading (2026-10-27)

Unused code and bundled mascot images removed; gzip + parallel list loading in `SupabaseClient` / `BhService`; batched, parallel listing publish; shorter splash. Details: `CHANGES.md`.

---

# v1.9.2 - Directions: BH → Campus fix + new look (2026-10-25)

`ui/DirectionsCard.java`, `bofo.css` (plus version, changelog). The map is cleared and the new trip drawn at once when a tab is tapped.

---

# v1.9.1 - Directions tabs: Me → BH, Me → Campus, BH → Campus (2026-10-25)

`ui/DirectionsCard.java` only (plus version, changelog).

---

# v1.9.0 - Offline jeepney cache, hand-made lines, options on the map card (2026-10-25)

| What | Where |
|---|---|
| Jeepney lines saved on the device (fresh 14 days, older copy still used offline) | `util/DiskCache.java`, `util/TransitRoutes.java`, `ui/AppStorage.java` |
| Hand-made lines with checked fares for places OpenStreetMap does not cover (file ships empty) | `util/LocalTransit.java`, `local-transit.json`, `docs/LOCAL_TRANSIT.md` |
| Tap a pin on Home / Explore / owner map: the card shows the best way (walk, tricycle, jeepney, bus/van) with time and fare, and draws it | `ui/MapPane.java` (`planOptions`), `Trip.Option.summary()` |
| Offline plans are quick: no waiting on a dead route server for every leg | `util/TripPlanner.java` |

---

# v1.8.0 - Directions card: commuter data and mode options (2026-10-25)

| What | Where |
|---|---|
| Directions card fits the screen: tabs share the width, texts wrap, buttons take the full row | `ui/DirectionsCard.java`, `bofo.css` (`.seg-fit`, `.dir-*`) |
| Options for each trip: walk, tricycle, jeepney (mapped lines), bus/van when far; time, fare, "Best for you" | `util/TripPlanner.java`, `util/Trip.java` |
| Public commuter data: jeepney / multicab / bus lines from OpenStreetMap (Overpass API) matched to the trip, in the right direction | `util/TransitRoutes.java`, `util/TransitMatch.java` |
| Official fares: LTFRB, effective 19 March 2026 (traditional P14 + P2/km after 4 km, modern P17 + P2.40/km) | `Trip.java` constants |
| Far away = bus/van, never "1266 min walk"; long times read "21 h 6 min" | `Geo.fmtTime`, `Router.MAX_WALK_KM`, `TripPlanner.FAR_KM` |
| PC locations are coarse: the card says so | `State.gpsApprox`, `Locator` |

Settings (all optional): `route.drive.url`, `transit.online=false`, `transit.overpass`. Logic check: `dev/TripLogicCheck.java`.

---

# BOFO Finder: Map tracking + route feature (2026-10-24)

Design reference: the blue map mockup (live location, radius circle, BH pins, bottom card, route line).
**Status: written but NOT compiled or run by the author** (no Maven / JavaFX in the authoring environment; only a
Java syntax check was done). Build it once and use the fix guide in section 5 if anything complains.

## 1. What was added

| Feature | What you see | Where |
|---|---|---|
| **Live tracking marker** | Blue dot that *glides* between GPS fixes, pulses, and shows a small direction arrow once you move 4 m or more. Pulse stops when the map is off screen (saves battery). | every map |
| **Follow me** | The round locate button flies the camera to you and keeps following. Dragging or zooming the map turns follow off (button un-highlights). | every map |
| **Pins pop in** | Purple pins pop in one after another. Selected pin grows and turns dark purple. Only *new* pins animate, so the radius slider does not re-pop everything. | Home, Explore, Owner dashboard |
| **Preview card** | Tap a pin: a card slides up (photo, name, price, stars, distance, Water/Power/Wi-Fi tags). Tap the card to open the BH page, X or empty map to close. Locate button lifts above the card. | Home, Explore, Owner dashboard (no details page) |
| **Walking route that draws itself** | Real walking path (follows roads), white outline + purple line "walking" from the start to the BH, camera glides to fit it, badge on the middle of the route: `750 m · 10 min`. | Home, Explore, BH detail, Owner |
| **Route start** | Chosen college, else (owner screens) the **nearest college** to that BH, else your location. | |
| **Dotted you-to-college line** | Faint dotted line from you to the college when you are more than 120 m away from it. | Home / Explore / detail |
| **Radius badge + growing circle** | College circle grows/shrinks smoothly to the new radius, with a `1.0 km` badge on its edge. | Home |
| **Owner dashboard map** | "Your listings on the map" under Overview; fits all your pins; tap one to see the walk from the nearest campus. | Owner > Dashboard |
| **Owner pin check** | In *New listing*, press "Set pin at map center": the route from the nearest college is drawn and the label shows e.g. `Pin: ... · 850 m from NU Cebu (about 11 min walk)`. | Owner > Create |
| **Admin** | The admin app has no map screens (Queue, Payments, Reports, Profile), so nothing was added there. | n/a |

## 2. How to access / test

1. `git pull origin main`, extract `bofo-map-update.zip` over the project folder (section 4), then `mvn clean` and run the app as usual.
2. **Student > Home:** choose a college, tap a purple pin. Watch the card slide up and the route draw.
3. **Student > Explore > Map:** same behavior over the whole list.
4. **Student > any BH page:** the small map now shows an animated real route from your college (or you).
5. **Owner > Dashboard:** scroll to "Your listings on the map" (appears once you have a listing). **Owner > Create:** set a pin.
6. Dev logins: Student / Owner / Admin buttons on the login screen (see SESSION_CHANGES.md).
7. The marker only *moves* with real GPS (phone). On a PC you get one location fix, so it appears but will not glide.

## 3. Files

New: `util/Router.java` (walking route + straight-line fallback), `ui/MapCard.java` (preview card).
Rewritten: `ui/MapPane.java` (same public API as before, plus `enablePreview`, `setColleges`, `fitPins`, `showRoute`, `clearRoute`, `framed`).
Edited: `util/Geo.java` (bearing, fitZoom), `config/AppConfig.java` (`route.url`, `route.online`), `ui/HomeView.java` (map 240 to 300 px, preview on), `ui/ExploreView.java` (preview on), `ui/DashboardView.java` (owner map), `ui/ListingFormView.java` (route after Set pin), `bofo.css` (appended map styles).
All paths are under `src/main/java/com/bofo/finder/` (css under `src/main/resources/com/bofo/finder/`).

## 4. Install

```powershell
cd C:\Users\user\bofo-finder
git pull origin main
Expand-Archive -Path "$HOME\Downloads\bofo-map-update.zip" -DestinationPath . -Force
mvn clean
```
Then run the app. Commit when happy: `git add -A; git commit -m "Map: tracking, preview card, animated walking routes"; git push origin main`.

Optional settings (`config.properties` or your `config.local.properties`, all have defaults):
```properties
# route.url=https://routing.openstreetmap.de/routed-foot/route/v1/foot/{lng1},{lat1};{lng2},{lat2}?overview=full&geometries=geojson
# route.online=false     # always draw a straight line, never call the routing server
```

## 5. If something goes wrong

| Problem | Fix |
|---|---|
| Build fails with "cannot find symbol" / "incompatible types" | Copy the first 5 error lines and send them. Fastest rollback: `git checkout -- .` then `git clean -fd src` (undoes this update only if you have not committed it). |
| Route is a straight line and the card says "(straight line)" | The routing server could not be reached (offline, blocked, or busy). Check internet, or set `route.url` to another OSRM-style foot server. Straight line is the built-in fallback. |
| Card says "Turn on location or choose a college" | No start point: pick a college on Home or allow location (Profile > location switch). Owners get the nearest college automatically once colleges load. |
| Blue dot does not move on PC | Normal: PCs report one fix. Test movement on the phone. |
| Pins/map look unstyled, no badges | Old CSS cached: run `mvn clean` and start again. |
| Tapping a pin opens the BH page directly (no card) | That screen was not switched to preview mode: it needs `map.enablePreview(...)` instead of `map.onPinTap(...)`. Home, Explore and the owner dashboard already have it. |
| Map jumps back to me while I drag | Follow mode is on; dragging turns it off. If it does not, tap the locate button once more and drag again. |
| Route takes long / huge routes lag | Routes are thinned to 400 points; if still slow lower `400` in `Router.walk` (`thin(pts, 400)`). |
| Public routing server limits | The free OpenStreetMap server is for light use. For heavy use, host OSRM or use a paid routing API and set `route.url`. |

Walking minutes are computed at 5 km/h from the route distance, not taken from the server.
