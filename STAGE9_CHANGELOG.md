# Stage 9 changelog (v2.0.16) - Release prep

## What changed
| File | Change |
|---|---|
| `bofo-android/app/build.gradle` | Release type: `minifyEnabled true` + `shrinkResources true` (R8). New `signingConfigs.release`, read from `bofo-android/keystore.properties` or the `BOFO_*` environment variables; no key = unsigned release APK, the build does not fail. Version 2.0.16 / 216 |
| `bofo-android/app/proguard-rules.pro` | NEW content: keep line numbers (readable crash reports), keep `MascotView.setMascotAngle` (animated by name), keep Fragments / Sheets, `-dontwarn` for optional classes |
| `bofo-android/keystore.properties.example` | NEW template (the real `keystore.properties` is git-ignored) |
| `bofo-android/.gitignore` | adds `keystore.properties` |
| `.github/workflows/android-gradle.yml` | NEW, optional: unit tests + debug APK on every push to `bofo-android/`; manual run can also build the signed release APK + AAB. The old `android.yml` (GluonFX) is untouched |
| `bofo-android/docs/RELEASE.md` | NEW: make a key, build, Google SHA-1, R8 fix guide, what is still missing before a public release |
| `bofo-android/README.md`, `CHANGES.md`, `HANDOFF_ANDROID.md` | version 2.0.16, notes |

No app code changed, no Supabase change, no new library, no new test.

## How to open it
1. Unzip the changes zip over the repo root. Open `bofo-android` in Android Studio, sync Gradle.
2. First (stages 5 to 8h were never built): `gradlew.bat testDebugUnitTest`, then `gradlew.bat assembleDebug`. Fix compile errors first; they are not caused by this stage.
3. Then follow `bofo-android/docs/RELEASE.md`.

## Simple fix guide
See `docs/RELEASE.md` section 5 (R8 table). Fast escape: `minifyEnabled false` in `app/build.gradle`.
