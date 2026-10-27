# Handoff - v1.16.8 Owner map dots/bubbles + locate button (2026-10-27)
- **PR title:** `v1.16.8 - Owner map shows dots and number bubbles; locate button works`. Version in `config.properties`, `pom.xml` (versionCode 65), CHANGES.md.
- `MapPane.PinLayer.zoomNow()` falls back to `map.getZoom()` when `zoomVal` is NaN (scroll/pinch/page-scroll set it NaN, so `far` was false and pins stayed full-size, no bubbles). `MapPane.goTo` null-safe, closes an open card, draws the blue dot first. `Locator.locate` uses a known `State.gps` immediately whatever the status (was waiting for status ON, so nothing moved) and restarts tracking quietly. Not compiled/run by Claude.

---

# Handoff - v1.16.7 Dashboard listing list + map names on zoom (2026-10-27)
- **PR title:** `v1.16.7 - Dashboard lists created listings; campus names only when zoomed in`. Version in `config.properties`, `pom.xml` (versionCode 64), CHANGES.md.
- `DashboardView`: added "Your listings" section with `BhCard` per `myListings` result (was only stats + map). `MapPane` label pass: names need `map.getZoom() >= 14.5` (real zoom, also after pinch) and are hidden when they would overlap; the chosen-campus exception was removed. Not compiled/run by Claude.

---

# Handoff - v1.16.6 Preview 4 screen sizes at once (2026-10-27)
- **PR title:** `v1.16.6 - Dev script that opens the app in 4 screen sizes`. Version in `config.properties`, `pom.xml` (versionCode 63), CHANGES.md.
- `Main` accepts `--size=WxH@X,Y` (position optional; title shows the size). New `dev/preview-sizes.ps1`: `mvn -q compile` once, then 4 `mvn javafx:run -Djavafx.args=--size=...` processes, 8 s apart (separate JVMs: `Nav`/`AuthService` are singletons, so one JVM cannot show 4 windows). Run: `powershell -ExecutionPolicy Bypass -File dev\preview-sizes.ps1`. Edit the `$sizes` list for other sizes. All windows share one saved login; for different accounts set `BOFO_PROFILE` per window. Not compiled/run by Claude.

---

# Handoff - v1.16.5 Number bubble: zoom until the houses separate (2026-10-27)
- **PR title:** `v1.16.5 - Number bubble zooms until its houses separate (one tap)`. Version in `config.properties`, `pom.xml` (versionCode 62), CHANGES.md.
- v1.16.4 framed all houses of the bubble, but 3 of 4 were still close, so they formed a new "3", then "2" bubble (user video: still 3 taps). `MapPane.clusterAt` now stores the houses' lat/lng (`pts`) and `separated(pts, z)` finds the lowest zoom (half-level steps, max 18) where no two houses are within the bubble reach (34 px x marker scale + 8) of each other; one tap `flyTo`s there, centred on the bubble. Small area (needs zoom > 16.5) and map still below 14.2: first tap stops at 14.8 (bubble still visible), second tap finishes (two taps). Bubbles only exist below zoom 15 (`far`). Not compiled/run by Claude.

---

# Handoff - v1.16.4 One-tap zoom on number bubbles (2026-10-27)
- **PR title:** `v1.16.4 - Number bubble on the map zooms to its houses in one tap`. Version in `config.properties`, `pom.xml` (versionCode 61), CHANGES.md.
- `MapPane` cluster bubble (`clusterAt`): each bubble now stores the box of its houses (`bb`); one tap `flyTo`s the zoom from `Geo.fitZoom` that frames them all (at least +1 level, max 17). If the houses are packed in a small area (fit would need zoom 17) and the map is still more than 4 levels away, the first tap goes half way and the second finishes (two taps). Was: +2.2 levels per tap around the bubble centre (3 taps for 4 houses). Not compiled/run by Claude.

---
