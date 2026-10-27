# Handoff - v1.15.5 Card wider than screen (2026-10-27)
- **PR title:** `v1.15.5 - Fix listing card wider than the screen in My listings`. Version in `config.properties`, `pom.xml` (versionCode 56), CHANGES.md.
- `CardGrid.flow`: single-column cells (BhCard etc.) use `min(w, maxWidth)` instead of `max(minWidth, ...)`. Follow-up to v1.15.4 (`ListingsView` now a one-column `CardGrid`). Not compiled/run by Claude.

---

# Handoff - v1.15.4 My listings fits the screen (2026-10-27)
- **PR title:** `v1.15.4 - Fix My listings card overflow and button overlap`. Version in `config.properties`, `pom.xml` (versionCode 55), CHANGES.md.
- `ListingsView`: rows is now `CardGrid(10, 150, 1)` (was a VBox, so the card took the photo's width and ran off the screen, and its height was measured for the wrong width); the action buttons are equal-width with `Priority.ALWAYS`. Not compiled/run by Claude.

---

# Handoff - v1.15.3 Cleanup + faster loading (2026-10-27)
- **PR title:** `v1.15.3 - Remove unused code, faster loading and publishing`. Version in `config.properties`, `pom.xml` (versionCode 54), CHANGES.md.
- Deleted unused code/resources (list in CHANGES.md). `SupabaseClient`: `Accept-Encoding: gzip` + decode, GET timeout 15 s, new `insert(table, JSONArray)`. `BhService.list`: featured query runs in parallel; `publish`: batched rooms/amenities/photos, parallel photo upload. `Capture.png`: Sub filter; listing photos 1024 px. `Main`: `SPLASH_MIN_MS = 1200`, `PauseTransition` instead of a raw thread. Parse-checked only, not compiled or run by Claude: build once and test publish with 2-3 photos + a slow connection.
