# Handoff - v1.9.10 Splash: hat pop off / pop on (2026-10-25)
- **PR title:** `v1.9.10 - Loading screen: hat pops off and pops back on with sparks, squash and rebound`. Version in `config.properties`, `pom.xml` (versionCode 25), CHANGES.md.
- **Why:** v1.9.9 hat (-20/-26 lift, 5 deg tilt, rotating around the middle of its bounds, sparse key frames) still looked off. Now `SplashView` has instance fields (`hat`, `hr` Rotate pivot 100,52, `hs` Scale pivot 100,59, `ring`, `sparks`) and `addJump(k, t0, height, hatUp, tilt, shadowMin)` -> `hatKey(...)` rows (lag 420, apex 560, POP 650, hover 770, fall 900, land 950, rebound 1015, settle 1070) + `addBurst(t, dur)` at t0+640 and t0+950. Jump 1 `-44,-28,-9`, jump 2 `-58,-34,+9`. `CYCLE` = 2400. Sparks live in `Mascot.fx()`.
- Not compiled/run by Claude (no Maven, no javac). Check: hat leaves with a visible pop + ring/sparks, comes back with a squash + second burst, no leftover spark at the loop restart. Tune the `hatKey` rows and `d = 36` (spark distance).

---

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
