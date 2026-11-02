# BOFO Android v2.0.129 - Changelog

## What was wrong
Pressing "Middle 2,200" on the Home rent card lit and moved the map to the 3.8k house.

## Real cause
In `RentCardView.onTouchEvent` the dots on the line were checked first (22 dp). The 3.8k dot sits almost under the "Middle" text, so it took the touch. The map was doing what it was told; the wrong house was chosen.

## What I changed
| File | Change |
|---|---|
| `ui/RentCardView.java` | Price texts are checked first. Dots only react when the finger is on the line (within 16 dp). |
| `ui/MapCardView.java` | Back to the original (the pin-hiding from v2.0.126 / v2.0.128 was a wrong guess; it is removed). |
| `app/build.gradle` | 2.0.129, versionCode 329 |
| `CHANGES_ANDROID.md` | New entry |

## How to test
Home > choose campus > press the "₱2,200" text: the red dot must be the 2,200 dot (left of the middle), and the map must show the 2.2k pin. Press a dot on the line: it lights that house. Press the same again: off.

## If something goes wrong
| Problem | Fix |
|---|---|
| Dots hard to tap | In `RentCardView.java` raise both `16 * d` (in `onTouchEvent`) to `20 * d`. |
| Still the wrong house | Check the phone shows version 2.0.129: `adb shell dumpsys package com.bofo.finder \| findstr versionName` |
| Undo | `git checkout -- bofo-android/app/src/main/java/com/bofo/finder/ui/RentCardView.java bofo-android/app/src/main/java/com/bofo/finder/ui/MapCardView.java bofo-android/app/build.gradle CHANGES_ANDROID.md` |
