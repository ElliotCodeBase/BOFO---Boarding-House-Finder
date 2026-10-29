# Handoff - v1.18.3 Home redesign v3 (2026-10-29)
- **PR title:** `v1.18.3 - Home redesign v3: hero card, stat tiles, amber price chips`. Version in `config.properties`, `pom.xml` (versionCode 78), CHANGES.md.
- `HomeView`: `hero` VBox (`.home-hero`) + `searchRow` margin -27 overlap; `statRow` (`statTile`, filled in `updateHeader` from `current`); amber `title-bar`. `BhCard` compact: `price-chip` / `rate-chip` on the photo. CSS block "v1.18.3" replaces the v1.18.2 block at the end of `bofo.css`. Not compiled/run by Claude.

---

# Handoff - v1.18.2 Home redesign v2 (2026-10-29)
- **PR title:** `v1.18.2 - Home redesign v2: light header, flat photo-first cards, flat live dot`. Version in `config.properties`, `pom.xml` (versionCode 77), CHANGES.md.
- Replaced the v1.18.1 CSS block at the end of `bofo.css` (hero gone). `HomeView`: headline removed, chips auto width in `chipWrap`, map clip 24. `BhCard` compact: `VBox(name, meta, priceRow)`. `LiveDot`: flat core, `clearHeading()`; `MapPane.moveMe` skips the cone when `State.gpsApprox`. Design notes: white canvas, one accent, one shadow tier, photo carries the card. Not compiled/run by Claude.

---

# Handoff - v1.18.1 Whole Home redesign (2026-10-29)
- **PR title:** `v1.18.1 - Redesigned Home (hero header, segmented tabs, price-on-photo cards); remove Approximate chip`. Version in `config.properties`, `pom.xml` (versionCode 76), CHANGES.md.
- `HomeView`: `locationPill()` removed -> `mapCount` chip (set in `updateHeader`, bound to `State.college` + `hasCount`); `headline` label in the header; `chips` wrapped in `chipWrap`, buttons grow equally. `BhCard` (compact): `price-chip` over the photo, price row removed from the text block. CSS block "v1.18.1" at the end of `bofo.css` (hero gradient, segmented `.home-chips`, `.price-chip`). Not compiled/run by Claude.

---
