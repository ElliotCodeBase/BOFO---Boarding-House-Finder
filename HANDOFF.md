# Handoff - v1.9.9 Splash: hat tilt/lift, visible shadow, dots wave (2026-10-25)
- **PR title:** `v1.9.9 - Loading screen: hat reads as a hat, visible shadow, bigger mascot and wave dots`. Version in `config.properties`, `pom.xml` (versionCode 24), CHANGES.md.
- **Cause (from the screen recording):** hat toss was -34 / -44 units with -14 deg tilt, so the 124-wide brim looked like a flat plate far above the head; the shadow was `rgba(0,0,0,.22)` on `-bofo-ink` (#17142A) so it was invisible; dots were 3.5 px and kept animating off screen.
- **Now:** `addJump(kf, t0, height, hatUp, hatTilt, shadowMin, ...)`: jump 1 `-44, -20, -5`, jump 2 `-58, -26, +5`. Shadow `#8D7BFF` @ 0.32, ellipse 54x8. Mascot 172 px, stage 230x250. Dots: r=5, rise 7 px + fade, 160 ms stagger, stopped in the scene listener. CSS `.splash-*` fonts 38 / 15 / 13 px.
- Not compiled/run by Claude (no Maven, no javac). Check: hat still clearly a hat at the top of both jumps, shadow visible and touching the feet, dots wave. Tune the numbers in the two `addJump` calls.
- `Skeleton` (list placeholders) not changed in this version.

---

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
