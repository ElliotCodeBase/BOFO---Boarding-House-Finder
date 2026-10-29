# Handoff - v1.18.0 Live location marker + Home/map redesign (2026-10-29)
- **PR title:** `v1.18.0 - New live location marker (ripples + view cone) and redesigned Home map`. Version in `config.properties`, `pom.xml` (versionCode 75), CHANGES.md.
- New `ui/LiveDot` (replaces halo/meDot/arrow/pulse in `MapPane.PinLayer`; API `setRunning / appear / ping / setHeading / reset`). `MapPane.goTo` pings the dot 560 ms after the camera starts flying. `HomeView`: `locationPill()` (bound to `Locator.status`, `State.gps`, `State.gpsApprox`, `State.college`), `entrance()` (only when `firstHome`), map clip radius 28. CSS block "v1.18.0" at the end of `bofo.css`. `VectorTileRetriever` palette constants lightened. Not compiled/run by Claude (Maven Central is unreachable in the sandbox; syntax-checked with javac only). If map colours look unchanged, clear the cached map tiles.

---

# Handoff - v1.17.3 Pinned spot no longer persists (2026-10-29)
- **PR title:** `v1.17.3 - PC: pinned spot is session-only; log location source`. Version in `config.properties`, `pom.xml` (versionCode 74), CHANGES.md.
- `Locator`: `manual` no longer loaded/saved (`location.properties`), cleared in `setEnabled(true)`; `src` field + `[location]` println in `fix()`. Not compiled/run by Claude.

---

# Handoff - v1.17.2 PC Wi-Fi location (2026-10-29)
- **PR title:** `v1.17.2 - PC location from nearby Wi-Fi (like Google Maps)`. Version in `config.properties`, `pom.xml` (versionCode 73), CHANGES.md.
- New `util/WifiLocator` (netsh scan -> Geolocation API, Google if `geolocation.key` else beacondb). `Locator.desktopFix` tries it first, `wifiSource` selects the 15 s `startWifiLive` loop instead of the PowerShell watcher. Network and Windows scan not tested by Claude; not compiled/run.

---
