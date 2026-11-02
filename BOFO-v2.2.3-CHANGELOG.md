# v2.2.3 - Boarding group chat: no tab bar, Back arrow

## What changed
- Student **Boarding** tab: when the group chat of your house is open, the bottom tab bar and the floating Ask BOFO button are hidden, so the chat uses the full screen (the message box sits above the system buttons).
- The chat header has a **Back arrow** (and the phone's Back does the same): it leaves the chat and opens the **Home** tab, with the tab bar again.
- The tab bar also comes back by itself when you leave the chat any other way (join / loading / error screens never hide it).
- Owner chat (Profile > Boarding groups) is unchanged: it already opens as its own screen with a Back arrow.
- Files: `ui/ShellFragment.java` (setChatFull / leaveBoarding), `ui/BoardingFragment.java`, `ui/BoardingChatFragment.java`, `app/build.gradle` (2.2.3, versionCode 20203). No database change.

## How to access
Student account > Boarding tab (centre of the bar) > your house chat. Back arrow top-left.

## Install (PowerShell)
```
Expand-Archive "$HOME\Downloads\BOFO-v2.2.3-changes.zip" "C:\Users\user\bofo-finder" -Force
cd C:\Users\user\bofo-finder\bofo-android
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat installDebug
cd ..; git add -A; git commit -m "v2.2.3 - Boarding chat full screen with Back arrow"
```

## If a problem occurs
| Problem | Fix |
|---|---|
| Tab bar missing on another tab after leaving the chat | Tap the Back arrow once; or switch accounts / restart the app. Cause would be `setChatFull(false)` not reached: check `BoardingFragment.onDestroyView`. |
| Message box hidden behind the system buttons | `ShellFragment.applyChatFull` pads the container by the system bar height; check that `bottomInset` is set (inset listener) on your device. |
| Back arrow not visible | Make sure BoardingFragment passes `showBack = true` (`newInstance(..., true, true)`). |
| Want the old look | Reinstall v2.2.2. |

## Not run
Not built or run by Claude (no Android SDK). Braces / parentheses of the three Java files were checked by script only.
