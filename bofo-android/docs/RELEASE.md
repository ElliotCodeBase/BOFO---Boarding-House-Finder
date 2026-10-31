# Release build (Stage 9, v2.0.16)

Written without Android Studio or Gradle: nothing here was run. Do the checks at the end first.

## 1. Make a signing key (once; keep it safe)
```
keytool -genkeypair -v -keystore bofo-release.jks -alias bofo -keyalg RSA -keysize 2048 -validity 10000
```
Put `bofo-release.jks` OUTSIDE the repo (or keep it: `*.jks` is git-ignored). **Back it up and write the passwords down:** if you lose it you can never update the app on Google Play under the same listing (unless you use Play App Signing, recommended: Play keeps the real key, yours is the upload key).

## 2. Tell Gradle about it
Copy `bofo-android/keystore.properties.example` to `bofo-android/keystore.properties` and fill in the path and passwords (git-ignored). Without this file the release APK is built UNSIGNED and cannot be installed.

## 3. Build
- Windows: `gradlew.bat assembleRelease` (APK, to test on a phone) or `gradlew.bat bundleRelease` (AAB, for Google Play), in `bofo-android`.
- Results: `app/build/outputs/apk/release/app-release.apk`, `app/build/outputs/bundle/release/app-release.aab`.
- Keep `app/build/outputs/mapping/release/mapping.txt` for every version you publish (upload it in Play Console > App bundle explorer > Downloads, so crash reports are readable).
- GitHub: Actions > Build Android (Gradle) > Run workflow > tick "Also build the signed release". Needs the 4 secrets listed at the top of `.github/workflows/android-gradle.yml` (`base64 -w0 bofo-release.jks` for the first one; on Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("bofo-release.jks"))`).

## 4. Google sign-in with the release key (easy to forget)
Google Cloud Console > Credentials > the Android OAuth client for `com.bofo.finder`: add the **SHA-1 of the release key** (`keytool -list -v -keystore bofo-release.jks -alias bofo`). If you use Play App Signing, also add the SHA-1 shown in Play Console > App integrity. Without it Google sign-in fails in the release build while the debug build works. E-mail sign-in is not affected.

## 5. If the release build misbehaves (R8)
| Symptom | Fix |
|---|---|
| Release works, debug does not (or the reverse) | Compare: `gradlew.bat assembleDebug` vs `assembleRelease`; only release shrinks code. |
| Crash on start / on one screen, only in release | Run `adb logcat`, find the `ClassNotFoundException` / `NoSuchMethodException`, add `-keep class that.Name { *; }` to `app/proguard-rules.pro`. Un-obfuscate a stack trace with `retrace mapping.txt trace.txt` (Android SDK cmdline-tools). |
| Build stops with `Missing class ...` from R8 | Add `-dontwarn that.package.**` to `proguard-rules.pro` (R8 prints the exact line in `app/build/outputs/mapping/release/missing_rules.txt`). |
| Mascot stops running in circles (release only) | The rule for `setMascotAngle` in `proguard-rules.pro` is missing or the method was renamed. |
| A picture / icon is missing in release only | `shrinkResources` removed it: add it to `app/src/main/res/raw/keep.xml` (`<resources xmlns:tools="http://schemas.android.com/tools" tools:keep="@drawable/name" />`). |
| Map blank in release only | Add `-keep class org.maplibre.** { *; }` as a test; MapLibre has native code that looks up Java classes by name. |
| Quick escape | In `app/build.gradle` set `minifyEnabled false` and `shrinkResources false` for `release`, rebuild, and send me the error. |

## 6. Before a public release (not done by this stage)
- Boost payments cannot work: the Supabase project has no `create-boost-checkout` Edge Function (needs PayMongo keys). Also no `bofo-chat` (Ask BOFO falls back to the built-in FAQ) and no amenities tables.
- Privacy policy / terms are drafts: have a Philippine lawyer read them (also needed for the Play data-safety form: location, photos, e-mail, ID photos).
- Test the release APK on a real phone: sign-in, a chat, the map, a photo upload.
