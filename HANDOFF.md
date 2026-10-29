# Handoff - v1.18.2 Home redesign v2 (2026-10-29)
- **PR title:** `v1.18.2 - Home redesign v2: light header, flat photo-first cards, flat live dot`. Version in `config.properties`, `pom.xml` (versionCode 77), CHANGES.md.
- Replaced the v1.18.1 CSS block at the end of `bofo.css` (hero gone). `HomeView`: headline removed, chips auto width in `chipWrap`, map clip 24. `BhCard` compact: `VBox(name, meta, priceRow)`. `LiveDot`: flat core, `clearHeading()`; `MapPane.moveMe` skips the cone when `State.gpsApprox`. Design notes: white canvas, one accent, one shadow tier, photo carries the card. Not compiled/run by Claude.

---

# Handoff - v1.18.1 Whole Home redesign (2026-10-29)
- **PR title:** `v1.18.1 - Redesigned Home (hero header, segmented tabs, price-on-photo cards); remove Approximate chip`. Version in `config.properties`, `pom.xml` (versionCode 76), CHANGES.md.
- `HomeView`: `locationPill()` removed -> `mapCount` chip (set in `updateHeader`, bound to `State.college` + `hasCount`); `headline` label in the header; `chips` wrapped in `chipWrap`, buttons grow equally. `BhCard` (compact): `price-chip` over the photo, price row removed from the text block. CSS block "v1.18.1" at the end of `bofo.css` (hero gradient, segmented `.home-chips`, `.price-chip`). Not compiled/run by Claude.

---

# Handoff - v1.18.0 Live location marker + Home/map redesign (2026-10-29)
- **PR title:** `v1.18.0 - New live location marker (ripples + view cone) and redesigned Home map`. Version in `config.properties`, `pom.xml` (versionCode 75), CHANGES.md.
- New `ui/LiveDot` (replaces halo/meDot/arrow/pulse in `MapPane.PinLayer`; API `setRunning / appear / ping / setHeading / reset`). `MapPane.goTo` pings the dot 560 ms after the camera starts flying. `HomeView`: `locationPill()` (bound to `Locator.status`, `State.gps`, `State.gpsApprox`, `State.college`), `entrance()` (only when `firstHome`), map clip radius 28. CSS block "v1.18.0" at the end of `bofo.css`. `VectorTileRetriever` palette constants lightened. Not compiled/run by Claude (Maven Central is unreachable in the sandbox; syntax-checked with javac only). If map colours look unchanged, clear the cached map tiles.

---
