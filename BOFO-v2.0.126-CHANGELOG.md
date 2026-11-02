# BOFO Android v2.0.126 - Changelog

## What was wrong
Pressing the red price on the Home rent card (Middle, 2,200) moved the map to the right spot, but the pin there said 3.8k. Two houses sit almost on the same spot, and the other house's pin was drawn on top of the pressed one.

## What I changed
| File | Change |
|---|---|
| `ui/MapCardView.java` | While a price is pressed, pins of other houses within ~4 m of the pressed house are hidden. The map redraws pins when a price is pressed or un-pressed (`glowId` added to the redraw signature, `setGlow` calls `rebuildPlaces`). |
| `app/build.gradle` | 2.0.126, versionCode 326 |
| `CHANGES_ANDROID.md` | New entry |

(v2.0.125 changes are still needed: apply those too if not done yet.)

## How to see it
1. Copy the 3 files into the project (same paths), build: `cd bofo-android; .\gradlew.bat assembleDebug; .\gradlew.bat installDebug`
2. Home > choose the campus > press the red 2,200 price: the pin under the glow should say 2.2k. Press again: all pins return.

## If something goes wrong
| Problem | Fix |
|---|---|
| Still shows 3.8k | The two houses may be more than 4 m apart. In `MapCardView.java` raise both `0.00004` to `0.0002` (about 20 m). |
| Wrong pin hides when pressing | Lower both `0.00004` to `0.00002`. |
| Pins do not come back after pressing again | Check `setGlow` still has `rebuildPlaces(true);` on its first line. |
| Build error `gh` / `q` already defined | Rename them (e.g. `glowHouse`, `qq`) inside that one block. |
| Undo | `git checkout -- bofo-android/app/src/main/java/com/bofo/finder/ui/MapCardView.java bofo-android/app/build.gradle CHANGES_ANDROID.md` |
