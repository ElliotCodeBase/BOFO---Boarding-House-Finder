# BOFO Android v2.0.131 - Changelog

## What you asked for
1. A loading animation while the app is still calculating.
2. BH -> Campus (directions) should refresh by itself; no second tap on the tab to show the path.
3. Explore should refresh by itself after leaving a boarding house page (it was blank).

## What I did
| # | Change | Where |
|---|---|---|
| 1 | Directions card: moving progress bar + breathing grey boxes while the plan is worked out; thin bar above the plan while exact roads are still loading | `view_directions_card.xml`, `DirectionsCardView.java`, `strings.xml` |
| 2 | First look waits 5 s (was 12 s), shows a rough plan, then asks the road servers again by itself (2, 4, 8, 15 s ..., up to 8 tries) and swaps in the exact path. A failed retry keeps the plan instead of showing an error. Applies to all three tabs. Note text no longer says "tap the tab again". | `DirectionsCardView.java`, `DirectionsRules.java` (FIRST_PLAN_SECONDS), `TripPlanner.java` |
| 3 | Cause: Explore remembered the last "filters changed" tick, so when its screen was rebuilt after a place page it thought nothing changed and never loaded. It now forgets the tick when its view is destroyed (Home already did this). "Search this area" box is kept. | `ExploreFragment.java` |
| - | Version 2.0.131 (versionCode 331) | `app/build.gradle`, `CHANGES_ANDROID.md` |

## How to see it
- Open any boarding house > scroll to Directions > BH -> Campus: you see the moving bar, then the plan; if the roads were slow the thin bar stays on top and the exact path appears by itself.
- Open a house from Explore, press Back: the list skeleton pulses and the list fills in.

## If something goes wrong
| Problem | Fix |
|---|---|
| Build error `dirRefresh` / `dir_refresh` not found | Copy `view_directions_card.xml` again; the id `dir_refresh` must exist inside `plan_box`. Then Build > Clean Project. |
| Build error `LinearProgressIndicator` | Needs `com.google.android.material:material` (already in build.gradle, 1.13.0). Sync Gradle. |
| Path still shows only after a second tap | Raise the tries: in `DirectionsCardView.java` change `refineTries < 8` to `refineTries < 20`. Check the phone has internet. |
| Bar never goes away | The servers keep failing: it stops after 8 tries by design. Use "Retry"/tap the tab. |
| Plan appears too late | Lower `FIRST_PLAN_SECONDS = 5` to `3` in `DirectionsRules.java`. |
| Explore still blank after Back | Check `ExploreFragment.onDestroyView` contains `lastChange = -1;`. |
| Undo | `git checkout -- bofo-android CHANGES_ANDROID.md` |

Also needs the earlier v2.0.125 to v2.0.130 files applied.
