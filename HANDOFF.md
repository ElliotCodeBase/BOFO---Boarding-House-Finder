# Handoff - v1.19.0 Home redesign + CSS consolidation (2026-10-29)
- **PR title:** `v1.19.0 - Home redesign: plain header, flat cards, consolidated theme`. Version in `config.properties`, `pom.xml` (versionCode 76), CHANGES.md.
- `bofo.css` was rewritten as one cascade with tokens in `.root` (colours, `-bofo-hover`, `-bofo-skeleton`, `-bofo-amber`). Rules: 0 gradients (only `HomeView.paintTrack` still draws the radius-slider fill with one hard-stop gradient), two shadow tiers (0.14/12 float, 0.24/24 overlay), radii 16 / 8 / 999 / 3, bold set in one "Type roles" rule. Removed classes: `.home-header`, `.pill-green`, `.pill-grey` (now `.tag-open` / `.tag-full` in `BhCard`).
- `HomeView`: entrance() removed, header plain, map clip 16, new subtitle copy. `BhCard`: no house icon, `distance()` helper, meta line = distance + beds left. `CampusPicker`: ROW 56, MAX_ROWS 5, centred cells.
- Not compiled/run by Claude (no javac/Maven in sandbox). Check on a real device: 320/360 px widths, campus dropdown with long names, a card with no photo and 0 reviews, Chats unread row, Profile header (now solid).
- The screenshots in the brief (purple hero, stat tiles, amber price pills) were NOT in this repo zip (v1.18.0 has no stat tiles on Home), so those exact bugs could not be reproduced; the intent was applied to the code that exists.
---
# Handoff - v1.18.0 Live location marker + Home/map redesign (2026-10-29)
- **PR title:** `v1.18.0 - New live location marker (ripples + view cone) and redesigned Home map`. Version in `config.properties`, `pom.xml` (versionCode 75), CHANGES.md.
- New `ui/LiveDot` (replaces halo/meDot/arrow/pulse in `MapPane.PinLayer`; API `setRunning / appear / ping / setHeading / reset`). `MapPane.goTo` pings the dot 560 ms after the camera starts flying. `HomeView`: `locationPill()` (bound to `Locator.status`, `State.gps`, `State.gpsApprox`, `State.college`), `entrance()` (only when `firstHome`), map clip radius 28. CSS block "v1.18.0" at the end of `bofo.css`. `VectorTileRetriever` palette constants lightened. Not compiled/run by Claude (Maven Central is unreachable in the sandbox; syntax-checked with javac only). If map colours look unchanged, clear the cached map tiles.
---
# Handoff - v1.17.3 Pinned spot no longer persists (2026-10-29)
- **PR title:** `v1.17.3 - PC: pinned spot is session-only; log location source`. Version in `config.properties`, `pom.xml` (versionCode 74), CHANGES.md.
- `Locator`: `manual` no longer loaded/saved (`location.properties`), cleared in `setEnabled(true)`; `src` field + `[location]` println in `fix()`. Not compiled/run by Claude.
