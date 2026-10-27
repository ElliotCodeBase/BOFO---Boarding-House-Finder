# Handoff - v1.16.12 Locate button reliability (2026-10-27)
- **PR title:** `v1.16.12 - Fix round locate button sometimes not responding (touch slop, late fix, stale timers)`. Version in `config.properties`, `pom.xml` (versionCode 69), CHANGES.md.
- `MapPane`: locate fires on `MOUSE_RELEASED` within 14 px slack (no ACTION handler); `MOUSE_DRAGGED` filter ignores drags that start on the button; busy pulse limited to 12 cycles then `endBusy()`. `Locator`: `start()`/`browserFix()` stop the previous `timeout`; `locate()` keeps only the newest waiting callback; `fail()` no longer clears `waiting` (a late fix still runs it; `stop()` clears). Not compiled/run by Claude.

---

# Handoff - v1.16.11 Locate button smooth (2026-10-27)
- **PR title:** `v1.16.11 - Smooth round locate button (no camera/dot fight, press + busy feedback)`. Version in `config.properties`, `pom.xml` (versionCode 68), CHANGES.md.
- `MapPane`: `meT` listener (follow mode) skips `setCenterNow` while `cam` is RUNNING (it fought `flyTo` every frame). New `tapLocate()` / `startBusy()` / `endBusy()`: ignores taps while a fix is pending, pulses the icon until `Locator.status` leaves REQUESTING (also stopped when the map leaves the scene). `bofo.css`: `.map-fab:pressed` scale 0.9. Not compiled/run by Claude.

---

# Handoff - v1.16.10 Dashboard loading skeleton (2026-10-27)
- **PR title:** `v1.16.10 - Owner Dashboard loading skeleton (stats, map, listing cards)`. Version in `config.properties`, `pom.xml` (versionCode 67), CHANGES.md.
- `Skeleton.dashboard()` (new; `statRow` / `plainCard` helpers extracted, one pulse for the group) is the skeleton for the `Ui.load` in `DashboardView` (was `Skeleton.stats(3)`). `Ui.load` clears it on success and on error, so the map and list sections still appear after it as before. Not compiled/run by Claude.

---

# Handoff - v1.16.9 Narrow-screen UI fixes (2026-10-27)
- **PR title:** `v1.16.9 - Fix clipped text on Profile and My listings (320 px); compact header + action buttons`. Version in `config.properties`, `pom.xml` (versionCode 66), CHANGES.md.
- Cause: at 320 px the rows had a stretchy spacer + `HBox` gap 14 (4 gaps), so titles got ~90 px and wrapped mid-word / ellipsized. `Ui.settingsRow` now has no spacer and shows the value as a `settings-sub` line (`settings-ok` green for "Verified"); gaps 12, hairline inset 64 (also `ProfileView.addRow`). `Ui.pageHeader`: title has `Hgrow`, header buttons keep their pref width. `ListingsView`: round `hdr-btn-cta` "+" instead of the pill, `btn-sm` action buttons (min width = pref), card + actions in one `VBox`, `.heart-fab` hidden. New CSS block at the end of `bofo.css`. `ProfileView` bottom padding 110. Parse-checked with javac only; not run on screen.

---
