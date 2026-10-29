# Handoff - v1.19.15 Gluon license for Android packaging (2026-10-29)
- **PR title:** `v1.19.15 - Android build: add Gluon license step`. Version in `config.properties`, `pom.xml` (versionCode 79), CHANGES.md.
- Native compile passes since v1.19.14; `gluonfx:package` then needs a Gluon license. Get one at https://gluonhq.com/activate, add it as GitHub secret `GLUON_LICENSE` (repo > Settings > Secrets and variables > Actions), re-run **Build Android APK**. Not run by Claude.
- **Update + push (PowerShell, repo `C:\Users\user\bofo-finder`):** `cd C:\Users\user\bofo-finder; Expand-Archive "$HOME\Downloads\BOFO-vX.Y.Z-changes.zip" -DestinationPath . -Force; git add -A; git commit -m "<PR title>"; git push` (if rejected: `git pull --rebase` then `git push`).
---
# Handoff - v1.19.14 Android build fix (2026-10-29)
- **PR title:** `v1.19.14 - Fix Android native build (resource patterns), real APK artifact`. Version in `config.properties`, `pom.xml` (versionCode 78), CHANGES.md.
- Cause of the "green but no APK" run: `pom.xml` `resourcesList` used `\.` and GluonFX writes it unescaped into JSON -> GraalVM `Invalid escape character`; `| tee build.log` hid the failure. Fixed with `[.]` patterns + `pipefail`. `packageType` -> `apk`. Next unknown: later GluonFX stages (reflection/JNI, signing) may show new errors; send the `BOFO-build-log` artifact. Not compiled/run by Claude.
---
# Handoff - v1.19.13 Calmer Directions card (2026-10-29)
- **PR title:** `v1.19.13 - Calmer Directions card`. Version in `config.properties`, `pom.xml` (versionCode 77), CHANGES.md.
- `DirectionsCard.render()` rewritten: result + one quiet line (`quickLine`) + link row; notes/credit in a collapsible `moreBox`. Not compiled/run by Claude.
---
# Handoff - v1.19.0 Price range in radius (2026-10-29)
- **PR title:** `v1.19.0 - Home shows lowest, middle and highest rent in the radius`. Version in `config.properties`, `pom.xml` (versionCode 76), CHANGES.md.
- New `ui/PriceRange` (lowest / median / highest, ignores price <= 0). `HomeView`: `priceRange` row inside the list title block, filled in `reload()` via `showPriceRange`; on the Best rated tab it fetches the NEW list for the same radius so the numbers match every tab. Not compiled/run by Claude.
- The earlier v1.19.0 redesign attempt was reverted by the user locally; this version number is for the price range only. Delete older `BOFO-v1.19.0-changes*.zip` downloads before using this one.
---
# Handoff - v1.18.0 Live location marker + Home/map redesign (2026-10-29)
- **PR title:** `v1.18.0 - New live location marker (ripples + view cone) and redesigned Home map`. Version in `config.properties`, `pom.xml` (versionCode 75), CHANGES.md.
- New `ui/LiveDot` (replaces halo/meDot/arrow/pulse in `MapPane.PinLayer`; API `setRunning / appear / ping / setHeading / reset`). `MapPane.goTo` pings the dot 560 ms after the camera starts flying. `HomeView`: `locationPill()` (bound to `Locator.status`, `State.gps`, `State.gpsApprox`, `State.college`), `entrance()` (only when `firstHome`), map clip radius 28. CSS block "v1.18.0" at the end of `bofo.css`. `VectorTileRetriever` palette constants lightened. Not compiled/run by Claude (Maven Central is unreachable in the sandbox; syntax-checked with javac only). If map colours look unchanged, clear the cached map tiles.
---
# Handoff - v1.17.3 Pinned spot no longer persists (2026-10-29)
- **PR title:** `v1.17.3 - PC: pinned spot is session-only; log location source`. Version in `config.properties`, `pom.xml` (versionCode 74), CHANGES.md.
- `Locator`: `manual` no longer loaded/saved (`location.properties`), cleared in `setEnabled(true)`; `src` field + `[location]` println in `fix()`. Not compiled/run by Claude.
