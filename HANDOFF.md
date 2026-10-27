# Handoff - v1.16.10 Dashboard loading skeleton (2026-10-27)
- **PR title:** `v1.16.10 - Owner Dashboard loading skeleton (stats, map, listing cards)`. Version in `config.properties`, `pom.xml` (versionCode 67), CHANGES.md.
- `Skeleton.dashboard()` (new; `statRow` / `plainCard` helpers extracted, one pulse for the group) is the skeleton for the `Ui.load` in `DashboardView` (was `Skeleton.stats(3)`). `Ui.load` clears it on success and on error, so the map and list sections still appear after it as before. Not compiled/run by Claude.

---

# Handoff - v1.16.9 Narrow-screen UI fixes (2026-10-27)
- **PR title:** `v1.16.9 - Fix clipped text on Profile and My listings (320 px); compact header + action buttons`. Version in `config.properties`, `pom.xml` (versionCode 66), CHANGES.md.
- Cause: at 320 px the rows had a stretchy spacer + `HBox` gap 14 (4 gaps), so titles got ~90 px and wrapped mid-word / ellipsized. `Ui.settingsRow` now has no spacer and shows the value as a `settings-sub` line (`settings-ok` green for "Verified"); gaps 12, hairline inset 64 (also `ProfileView.addRow`). `Ui.pageHeader`: title has `Hgrow`, header buttons keep their pref width. `ListingsView`: round `hdr-btn-cta` "+" instead of the pill, `btn-sm` action buttons (min width = pref), card + actions in one `VBox`, `.heart-fab` hidden. New CSS block at the end of `bofo.css`. `ProfileView` bottom padding 110. Parse-checked with javac only; not run on screen.

---

# Handoff - v1.16.8 Owner map dots/bubbles + locate button (2026-10-27)
- **PR title:** `v1.16.8 - Owner map shows dots and number bubbles; locate button works`. Version in `config.properties`, `pom.xml` (versionCode 65), CHANGES.md.
- `MapPane.PinLayer.zoomNow()` falls back to `map.getZoom()` when `zoomVal` is NaN (scroll/pinch/page-scroll set it NaN, so `far` was false and pins stayed full-size, no bubbles). `MapPane.goTo` null-safe, closes an open card, draws the blue dot first. `Locator.locate` uses a known `State.gps` immediately whatever the status (was waiting for status ON, so nothing moved) and restarts tracking quietly. Not compiled/run by Claude.


---
