# Handoff - v1.15.4 My listings fits the screen (2026-10-27)
- **PR title:** `v1.15.4 - Fix My listings card overflow and button overlap`. Version in `config.properties`, `pom.xml` (versionCode 55), CHANGES.md.
- `ListingsView`: rows is now `CardGrid(10, 150, 1)` (was a VBox, so the card took the photo's width and ran off the screen, and its height was measured for the wrong width); the action buttons are equal-width with `Priority.ALWAYS`. Not compiled/run by Claude.

---

# Handoff - v1.15.3 Cleanup + faster loading (2026-10-27)
- **PR title:** `v1.15.3 - Remove unused code, faster loading and publishing`. Version in `config.properties`, `pom.xml` (versionCode 54), CHANGES.md.
- Deleted unused code/resources (list in CHANGES.md). `SupabaseClient`: `Accept-Encoding: gzip` + decode, GET timeout 15 s, new `insert(table, JSONArray)`. `BhService.list`: featured query runs in parallel; `publish`: batched rooms/amenities/photos, parallel photo upload. `Capture.png`: Sub filter; listing photos 1024 px. `Main`: `SPLASH_MIN_MS = 1200`, `PauseTransition` instead of a raw thread. Parse-checked only, not compiled or run by Claude: build once and test publish with 2-3 photos + a slow connection.

---

# Handoff - v1.15.2 Camera and Gallery fix (2026-10-26)
- **PR title:** `v1.15.2 - Fix Camera and Gallery buttons on PC and phone`. Version in `config.properties`, `pom.xml` (versionCode 53), CHANGES.md.
- Cause: `Capture` used only Gluon Attach `PicturesService`, which does not exist on desktop, so the buttons did nothing on PC. Now `Capture.camera()/gallery()` fall back to a JavaFX `FileChooser` on PC (no webcam in JavaFX) and catch errors. `AndroidManifest.xml`: added READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE (max SDK 28), READ_MEDIA_IMAGES. Phone path still uses Attach and is untested: if it fails, check `adb logcat` for "Camera failed"/"Gallery failed" and the Attach pictures runtime permission. Same fix covers the verification ID/selfie photo. Not compiled/run by Claude.
