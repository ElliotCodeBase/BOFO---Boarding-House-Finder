# Handoff - v1.9.8 Splash mascot: two jumps per cycle (2026-10-25)
- **PR title:** `v1.9.8 - Loading screen: two jumps per cycle`. Version in `config.properties`, `pom.xml` (versionCode 23), CHANGES.md.
- `SplashView.addJump(...)` builds the key frames of one jump (1.1 s) at an offset; the timeline calls it twice (t0 = 0: apex -44, hat -34; t0 = 1100: apex -58, hat -44) plus a final 2200 ms frame. Same pose at both ends, so the loop is seamless. Tune the numbers in the two `addJump` calls.
- Not compiled/run by Claude (no Maven, no javac). Check: two jumps, second one higher, hat leaves and lands each time, shadow touches the feet.

---

# Handoff - v1.9.7 Splash mascot: shadow on the feet + hat drop (2026-10-25)
- **PR title:** `v1.9.7 - Loading screen: shadow touches the feet, hat falls off at the top of the jump and lands back on`. Version in `config.properties`, `pom.xml` (versionCode 22), CHANGES.md.
- **Cause of the gap:** `SplashView` centred the mascot in a 210 px box but put the shadow at the bottom (about 30 px apart), and `scaleY` squash pivoted on the body centre, lifting the feet. Now the mascot is bottom-aligned (`ground` = 12), the shadow is centred on that line, and a `Scale` with pivot at the soles (`mb.getMaxY()`) replaces `scaleX/scaleY`.
- **Hat:** `Mascot.hat()` returns the hat as its own `Group` (chimney + roof + brim, drawn last). Timeline: 560 ms apex, pops to -34 / -14 deg by 680 ms, falls with an ease-in so it is back at 0 at 950 ms (landing), press +3 at 1010 ms, 0 at 1100 ms.
- Not compiled/run by Claude (no Maven, no javac). Please run it: shadow should touch both feet on landing, hat should leave at the top and land with the feet. Tune: `-34` height, `-14` tilt, `ground`.

---

# Handoff - v1.9.6 Map credit pill, New listing form redesign, owner chat title (2026-10-25)
- **PR title:** `v1.9.6 - Neater map credit, redesigned New listing form, owner chat shows student name`. Version in `config.properties`, `pom.xml` (versionCode 21), CHANGES.md (v1.9.4, v1.9.5, v1.9.6 entries).
- **Map credit (MapPane):** the OpenFreeMap / OpenMapTiles / OSM credit is a rounded `.map-attr` pill (wraps, stays clear of the find-me button). Text is kept visible on purpose (ODbL / CC-BY).
- **ListingFormView (rewritten):** 7 numbered cards, `RoomRow` with - / + `Spinner` steppers (occupants capped at beds, remove room), `Ui.segmented` Per bed / Per room, photo thumbnails with remove, pin status box (green when set), bottom `.sticky-bar` with "x of 6 required details done" + Publish. CSS block "New listing form" in `bofo.css`.
- **ChatsView:** owner opens a chat with the student's name as title (`c.otherName()`), students still get the BH name.
- Not compiled/run by Claude (no Maven, no javac). Please run: Create tab (check steppers, previews, sticky bar), map credit on Home, owner chat header.
- Old versionCode steps: 1.9.3 = 18, so 1.9.4 = 19, 1.9.5 = 20, 1.9.6 = 21.

---
