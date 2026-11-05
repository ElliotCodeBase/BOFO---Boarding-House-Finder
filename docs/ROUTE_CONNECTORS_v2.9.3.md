# Route line fix v2.9.3: no more "line through buildings"

## What was wrong
The map drew one solid line from your exact blue dot to the exact house pin. The first and last stretch (dot to the nearest road, nearest road to the house) cross open ground and buildings, but looked like a real path.

## What changed
| File | Change |
|---|---|
| `util/Router.java` | Only the road part is simplified; the first and last segment are always the two connectors (`withEnds`). |
| `util/MapGeo.java` | `routeJson`: the two connector legs are separate **dashed** features, the road is solid. Segments under 5 m stay solid; estimates and short paths look as before. |
| `ui/MapCardView.java` | Draws routes with `routeJson`. |
| `RouteConnectorsTest.java` (new) | Tests the dashing and the ends. |
| `build.gradle`, `CHANGES_ANDROID.md`, `HANDOFF_ANDROID.md` | Version 2.9.3. |

No server or database change.

## How to see it
Open a boarding house > Directions > Me -> BH. The solid orange line is the road; short dashed pieces at the start and end are the walk to / from the road.

## If a problem occurs
| Symptom | Fix |
|---|---|
| Long dashed piece | OpenStreetMap has no walkable path near that spot. Add the path on openstreetmap.org, or move the pin onto a road. |
| Whole line dashed | The routing server was unreachable (estimate). Check internet, try again; `Router.lastError()` has the reason. |
| Old solid line still shown | Old build installed: Settings must show 2.9.3, then `gradlew.bat clean installDebug`. |
| Undo | `git revert` the v2.9.3 commit. |

Not compiled or run in the sandbox that made this change: run `gradlew testDebugUnitTest assembleDebug` and look at a route on a phone.
