# BOFO Android v2.0.125 - Changelog

## What was wrong (from your screenshot)
1. The line under **Nearby boarding houses** ("Within 4.0 km of Negros Oriental State University - Main Campus - 6 places") ran under the floating Ask BOFO mascot and looked cut off ("Main Cam...").
2. On the rent card, the three dots at the left (prices close together) overlapped into one blob.

## What I changed
| File | Change |
|---|---|
| `res/layout/fragment_home.xml` | `section_sub` TextView: `paddingEnd` 76dp, `maxLines` 2, `ellipsize` end |
| `ui/RentCardView.java` | Each price dot gets a white ring (9.5dp) behind it, so overlapping dots stay separate. Positions unchanged. |
| `app/build.gradle` | versionName 2.0.125, versionCode 325 |
| `CHANGES_ANDROID.md` | New v2.0.125 entry at the top |

Changed files are copied to `v2.0.125/` in the same folder structure.

## How to access / see it
1. Copy the 4 files over the same paths in `bofo-android/` (or use the zip of the whole `v2.0.125/` folder).
2. Build and install: `cd bofo-android && ./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`), then run on the phone.
3. Open **Home**, pick a campus (e.g. Negros Oriental State University - Main Campus).
   - The subtitle should stop before the mascot button, with "..." if long.
   - The rent card dots should each show a white ring.

Not built or run by me, so please test on your phone.

## If something goes wrong
| Problem | Fix |
|---|---|
| Build error in `fragment_home.xml` | Check the `section_sub` TextView still has `/>` at the end and the 3 new attributes sit inside it. |
| Subtitle still runs under the mascot | Raise `android:paddingEnd="76dp"` to `90dp` in `fragment_home.xml`. |
| Subtitle too short / wraps too much | Lower `paddingEnd` to `64dp`, or set `maxLines` to `3`. |
| Dots look too big or touch each other | In `RentCardView.java` change `9.5f * d` to `8.5f * d`. |
| Want the old look back | Remove the 2 lines starting `p.setColor(0xFFFFFFFF); // v2.0.125` and `c.drawCircle(x, lineY, 9.5f * d, p);`. For the layout, delete the 3 added attributes and restore `android:paddingHorizontal="16dp"`. |
| "Version code" error when installing over the old app | versionCode must be higher than the installed one (325 > 324). Uninstall only if you changed signing keys. |
| Whole change should go | `git checkout -- bofo-android/app/src/main/res/layout/fragment_home.xml bofo-android/app/src/main/java/com/bofo/finder/ui/RentCardView.java bofo-android/app/build.gradle CHANGES_ANDROID.md` |
