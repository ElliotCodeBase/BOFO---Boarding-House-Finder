# BOFO Finder v1.16.7 - Changelog

## What I changed
1. **Dashboard now shows your created listings**
   - File: `src/main/java/com/bofo/finder/ui/DashboardView.java`
   - Before: the Dashboard only had the verification banner, 3 stat cards and the map. There was no list.
   - Now: a new "Your listings" section under the map shows one card per listing you created (same card as My listings). It appears once you have at least one listing.
2. **Campus names on the map only show when zoomed in**
   - File: `src/main/java/com/bofo/finder/ui/MapPane.java` (label pass in the layer update, search `boolean names`)
   - Before: names showed from zoom 12.8, or always when the zoom was unknown, so they overlapped.
   - Now: names show only at real zoom 14.5 or higher (read from `map.getZoom()`, so it also works after pinch/scroll) and a name is hidden if it would overlap another. The campus dots stay visible at all zooms.
3. Version bump to 1.16.7 (`config.properties`, `pom.xml` versionCode 64), plus `CHANGES.md` and `HANDOFF.md` entries.

## How to access
- Log in as an owner -> **Dashboard** tab. Scroll down: Overview -> map -> **Your listings**.
- Map: zoom in (pinch, scroll wheel or the + gestures) until campus names appear.

## Install
Unzip `BOFO-v1.16.7-changes.zip` over `C:\Users\user\bofo-finder` (overwrite), then run:
`mvn -q compile` then `mvn javafx:run`

## If something goes wrong
| Problem | Fix |
|---|---|
| Dashboard still has no list | Check My listings tab: if it is empty too, the listing was not saved (or it belongs to another account). Log in with the account that created it. |
| List missing but My listings has items | Make sure `DashboardView.java` was overwritten (it must contain `Your listings`). Run `mvn clean compile`. |
| Build error `getZoom()` not found | In `MapPane.java` replace `map.getZoom()` with `map.zoomProperty().get()`. |
| Names never appear | Zoom in more, or lower `14.5` in `boolean names = zReal >= 14.5;` (e.g. 13.5). |
| Names still overlap | Raise `14.5` to `15.5`. |
| Want to undo | `git checkout -- src/main/java/com/bofo/finder/ui/DashboardView.java src/main/java/com/bofo/finder/ui/MapPane.java` |

Note: not compiled or run on screen by Claude (no Maven/JavaFX in the sandbox).
