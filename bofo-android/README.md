# bofo-android

Native Android (Java 17, XML + Fragments, Material 3, Gradle Groovy) port of BOFO - Boarding House Finder.
The JavaFX source in `../src` stays untouched as reference. Plan: `../ANDROID_PORT_PLAN.md`. Status and rules: `../HANDOFF_ANDROID.md`, `../CLAUDE.md` (section 5).

Open this folder (not the repo root) in Android Studio: File > Open > `bofo-android`. Needs JDK 17+.
Unit tests (plain JVM): `gradlew.bat testDebugUnitTest`.
Build from a terminal: `gradlew.bat assembleDebug` (Windows) / `./gradlew assembleDebug`.

Config: `supabase.url`, `supabase.anonKey`, `google.webClientId`, `auth.redirect.android` are read from `../src/main/resources/com/bofo/finder/config.properties`, overridden by `local.properties` (git-ignored). Never commit secrets or a service_role key.

Version line: v2.0.N per stage (`versionName` / `versionCode` in `app/build.gradle`, code = 200 + N). Current: **2.0.17** (first successful build). Release build and signing: `docs/RELEASE.md`.
Package root: `app/src/main/java/com/bofo/finder/{app,config,model,service,util,ui}`.
