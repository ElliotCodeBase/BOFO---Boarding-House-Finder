# Handoff - v1.15.3 Cleanup + faster loading (2026-10-27)
- **PR title:** `v1.15.3 - Remove unused code, faster loading and publishing`. Version in `config.properties`, `pom.xml` (versionCode 54), CHANGES.md.
- Deleted unused code/resources (list in CHANGES.md). `SupabaseClient`: `Accept-Encoding: gzip` + decode, GET timeout 15 s, new `insert(table, JSONArray)`. `BhService.list`: featured query runs in parallel; `publish`: batched rooms/amenities/photos, parallel photo upload. `Capture.png`: Sub filter; listing photos 1024 px. `Main`: `SPLASH_MIN_MS = 1200`, `PauseTransition` instead of a raw thread. Parse-checked only, not compiled or run by Claude: build once and test publish with 2-3 photos + a slow connection.

---

# Handoff - v1.15.2 Camera and Gallery fix (2026-10-26)
- **PR title:** `v1.15.2 - Fix Camera and Gallery buttons on PC and phone`. Version in `config.properties`, `pom.xml` (versionCode 53), CHANGES.md.
- Cause: `Capture` used only Gluon Attach `PicturesService`, which does not exist on desktop, so the buttons did nothing on PC. Now `Capture.camera()/gallery()` fall back to a JavaFX `FileChooser` on PC (no webcam in JavaFX) and catch errors. `AndroidManifest.xml`: added READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE (max SDK 28), READ_MEDIA_IMAGES. Phone path still uses Attach and is untested: if it fails, check `adb logcat` for "Camera failed"/"Gallery failed" and the Attach pictures runtime permission. Same fix covers the verification ID/selfie photo. Not compiled/run by Claude.

---

# Handoff - v1.15.1 New listing polish (2026-10-26)
- **PR title:** `v1.15.1 - New listing: blue vector pin, no mascot on the form, compact bottom bar`. Version in `config.properties`, `pom.xml` (versionCode 52), CHANGES.md.
- `MapPane.enableCenterPin`: drawn blue pin + ground dot instead of the emoji. `ListingFormView`: progress label and bar on one row. Mascot hidden on the form (`Nav.show(..., false)` in OwnerShell, ListingsView). CSS block at end of bofo.css. Not compiled/run by Claude.
