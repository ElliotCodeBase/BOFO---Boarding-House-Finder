# Handoff - v1.17.3 Pinned spot no longer persists (2026-10-29)
- **PR title:** `v1.17.3 - PC: pinned spot is session-only; log location source`. Version in `config.properties`, `pom.xml` (versionCode 74), CHANGES.md.
- `Locator`: `manual` no longer loaded/saved (`location.properties`), cleared in `setEnabled(true)`; `src` field + `[location]` println in `fix()`. Not compiled/run by Claude.

---

# Handoff - v1.17.2 PC Wi-Fi location (2026-10-29)
- **PR title:** `v1.17.2 - PC location from nearby Wi-Fi (like Google Maps)`. Version in `config.properties`, `pom.xml` (versionCode 73), CHANGES.md.
- New `util/WifiLocator` (netsh scan -> Geolocation API, Google if `geolocation.key` else beacondb). `Locator.desktopFix` tries it first, `wifiSource` selects the 15 s `startWifiLive` loop instead of the PowerShell watcher. Network and Windows scan not tested by Claude; not compiled/run.

---

# Handoff - v1.17.1 Live location on PC (2026-10-29)
- **PR title:** `v1.17.1 - PC: blue dot follows your location live`. Version in `config.properties`, `pom.xml` (versionCode 72), CHANGES.md.
- `Locator`: `startLive()` / `stopLive()` keep one PowerShell (`WIN_LIVE_SCRIPT`, GeoCoordinateWatcher) printing a fix every 3 s; started after the first successful `desktopFix`; stopped in `stop()` and `setManual()`; shutdown hook destroys it. Not compiled/run by Claude.

---

# Handoff - v1.17.0 Profile setup + region map start (2026-10-29)
- **PR title:** `v1.17.0 - First sign-up profile setup (username, picture, number, age, sex, region) + map opens on region`. Version in `config.properties`, `pom.xml` (versionCode 71), CHANGES.md.
- New: `ProfileForm` (shared fields + save), `ProfileSetupView` (shown once from `Main.firstProfileSetup` after `RoleView`), `util/Regions`, migration `009_profile_setup.sql`. `PersonalInfoView` now wraps `ProfileForm`. `Profile` record has `username, sex, age, region` (old 8-arg constructor kept). `Ui.avatar(name, size, url)` + `Ui.setAvatarImage`. `MapPane.openOnRegion()` called in `HomeView`, `ExploreView`, `DashboardView`. Not compiled/run by Claude.
- Run 009 in Supabase and redeploy `delete-account`.

---

# Handoff - v1.16.13 Owner locate button like student (2026-10-27)
- **PR title:** `v1.16.13 - Owner maps start location like student Home (locate button + blue dot)`. Version in `config.properties`, `pom.xml` (versionCode 70), CHANGES.md.
- `DashboardView` and `ListingFormView` now call `Locator.get().startWithPrompt()` after creating their `MapPane` (only student `HomeView.startGps()` did before, so owner maps had no GPS fix). Not compiled/run by Claude.

---

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
