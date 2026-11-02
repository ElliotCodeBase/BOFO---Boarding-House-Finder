# BOFO Android v2.0.130 - Changelog

## What was wrong
The dot under "Middle ₱2,200" on the Home rent card was the ₱3,800 house. The line was spaced by price, and 3,800 fell at the exact centre, under the Middle label. Pressing that dot (correctly) lit the 3.8k house, which looked like the app going to the wrong price.

## What I changed
| File | Change |
|---|---|
| `ui/RentCardView.java` | `markX`: the Middle price is placed at the exact centre of the line (under its label). Lowest is the left end, Highest the right end. Other houses sit between, in price order. |
| `app/build.gradle` | 2.0.130, versionCode 330 |
| `CHANGES_ANDROID.md` | New entry |

## How to test
Home > choose campus. The dot right under "₱2,200" is the 2,200 house. Press it (or the text): the map goes to the 2.2k pin. The 3.8k dot is now to the right of it.

## If something goes wrong
| Problem | Fix |
|---|---|
| Dots look unevenly spaced | Expected: the line is now split in two halves (Lowest to Middle, Middle to Highest). |
| Want the old price-scaled line | In `RentCardView.java` `markX`, delete the `if (!Double.isNaN(mid) ...) { ... }` block. |
| Check the version on the phone | `adb shell dumpsys package com.bofo.finder \| findstr versionName` must say 2.0.130 |
| Undo | `git checkout -- bofo-android/app/src/main/java/com/bofo/finder/ui/RentCardView.java bofo-android/app/build.gradle CHANGES_ANDROID.md` |
