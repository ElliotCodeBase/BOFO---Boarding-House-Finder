# Google sign-in inside the app (Android)

On the phone, "Continue with Google" shows Google's own account sheet on top of BOFO ("Continue as ...").
No browser, no tab. On a PC window the browser is still used: Google does not allow sign-in inside an app window.
Nothing changes until `google.webClientId` is filled in, and if the sheet can't open the app falls back to the browser by itself.

## How it works
`LoginView` -> `AuthService.signInWithGoogle()` -> `NativeGoogle.signIn()` opens `bofo://google-signin` -> the Android screen
`src/android/java/com/bofo/finder/android/GoogleSignInActivity.java` shows the Google sheet and writes the Google ID token into a file in the
app's private folder -> `NativeGoogle` reads it -> `AuthService.exchangeIdToken()` sends it to Supabase (`/auth/v1/token?grant_type=id_token`).

## One-time setup
1. Google Cloud > Google Auth Platform > Clients > Create client > type **Android**: package name `com.bofo.finder`, SHA-1 of the key that signs the APK.
   Debug build: in Ubuntu run `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
   and copy the SHA1 line. Release / Play Store: add the SHA-1 of your release key (and the Play App Signing key from Play Console > App integrity) as extra Android clients.
2. Keep the existing **Web application** client ("BOFO Supabase"). Copy its Client ID (public, safe to commit) into
   `src/main/resources/com/bofo/finder/config.properties`: `google.webClientId=...apps.googleusercontent.com`
   The same Client ID must be in Supabase > Authentication > Providers > Google (it already is).
3. Gradle libraries. Build once (`mvn gluonfx:build gluonfx:package`), then copy
   `target/gluonfx/aarch64-android/gensrc/android/build.gradle` to `src/android/build.gradle` and add inside `dependencies { }`:
   ```
   implementation 'androidx.credentials:credentials:1.3.0'
   implementation 'androidx.credentials:credentials-play-services-auth:1.3.0'
   implementation 'com.google.android.libraries.identity.googleid:googleid:1.1.1'
   ```
   If Gradle complains about AndroidX or minSdk: add `android.useAndroidX=true` to `gradle.properties` (next to build.gradle) and make sure `minSdkVersion` is 23 or higher.
4. Rebuild and install (see ANDROID_BUILD.md). The phone needs Google Play services and a Google account (emulators: use a "Google Play" image).

## If it does not work
| Symptom | Cause |
|---|---|
| Browser opens instead of the sheet | The Android screen did not start in 4 s: check the manifest entry was merged, `src/android/build.gradle` has the 3 libraries, and the APK was rebuilt. |
| Sheet shows "No credential available" | No Google account on the phone, or the Android client (step 1) has the wrong package name / SHA-1. |
| `Google sign-in failed: ...` in red | Paste the full text; most often the SHA-1 or the Web client ID is wrong. |
| Supabase error "nonce" / "audience" | `google.webClientId` is not the same Web client that is enabled in Supabase > Google. |

NOT compiled or run by Claude (no Maven / Android SDK in its sandbox): send the first build error or log lines if anything fails.
