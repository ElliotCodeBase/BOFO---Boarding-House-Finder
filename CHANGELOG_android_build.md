# Changelog - Android build tooling (v1.19.15)

## What was added / changed
- **src/android/AndroidManifest.xml**: added `android:versionCode="79"` and `android:versionName="1.19.15"` to the
  `<manifest>` tag. Fixes `Version code not found in manifest` (packageDebugBundle failed).
- **build-apk.ps1** (new, project root): one-command update + build. Syncs version from pom.xml into the manifest,
  syncs the folder to Ubuntu, runs `gluonfx:build gluonfx:package` offline, copies the APK to the Desktop.
- **ANDROID_BUILD.md**: new section "Update + rebuild the APK" with the checklist and fix guide.

## How to use
`.\build-apk.ps1` in PowerShell from the project folder. Flags: `-PackageOnly`, `-Online`, `-Clean`.

## If something goes wrong
See the "Fix guide" table in ANDROID_BUILD.md. Build log: `~/bofo-finder/build.log` inside Ubuntu.
