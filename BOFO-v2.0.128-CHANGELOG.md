# BOFO Android v2.0.128 - Changelog

## What was wrong
Pressing the red 2,200 price moved the map to the right house, but the pin there still said 3.8k. The first fix (v2.0.126) hid pins within ~4 m of the pressed house, which was too small: the 3.8k pin sits close but not that close.

## What I changed
| File | Change |
|---|---|
| `ui/MapCardView.java` | While a price is pressed, any pin overlapping the pressed house's pin on screen (within 34dp across, 30dp up/down) is hidden. Press again: all pins return. |
| `app/build.gradle` | 2.0.128, versionCode 328 |
| `CHANGES_ANDROID.md` | New entry |

## How to test
Install, Home > choose campus > press the red 2,200: the pin under the glow must say 2.2k. Press again: 3.8k comes back.

## If something goes wrong
| Problem | Fix |
|---|---|
| Still shows 3.8k | Check the app really updated: Profile/Settings should show version 2.0.128. If not, run `.\gradlew.bat installDebug` again and watch for `BUILD SUCCESSFUL`. |
| Too many pins disappear | In `MapCardView.java` lower `34 * d` to `26 * d` and `30 * d` to `22 * d`. |
| Pin still hidden by another | Raise them to `44 * d` and `40 * d`. |
| Undo | `git checkout -- bofo-android/app/src/main/java/com/bofo/finder/ui/MapCardView.java bofo-android/app/build.gradle CHANGES_ANDROID.md` |
