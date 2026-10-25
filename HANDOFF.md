# Handoff - v1.9.11 Offline mode + connection robustness (2026-10-25)
- **PR title:** `v1.9.11 - Offline mode: open the app, chats, saved places and listings without internet; fewer false no-internet screens`. Version in `config.properties`, `pom.xml` (versionCode 26), CHANGES.md.
- **New `util/Offline`** (`array(key, fetch)`, `object(key, fetch)`): online = fetch + save `user_<key>` via `DiskCache`; network error = serve the saved copy, else rethrow. Used in `ChatService` (inbox, newest page per chat), `BhService` (lists, `detail`, `favorites`, `colleges`). `DiskCache.deletePrefix("user_")` runs in `AuthService.clearLocal()`. `AppStorage` is public and `Main.start` calls `AppStorage.init()` first.
- **`AuthService.restore()`:** profile row saved to `user_profile.json` in `loadProfile()`; with a saved profile, restore waits max 4 s then returns OFFLINE with `profile` = saved one, which `Main.dispatch` already opens with the banner. `lastError()` feeds the no-internet screen.
- **`SupabaseClient.send`:** GET retried once after 500 ms; connect timeout 12 s. **`Ui.errorState`** shows the real cause in small text; **`Ui.load`** reloads once when OFFLINE flips to false after rendering offline.
- Unverified guess for "won't connect while online": nothing in the code changed in v1.9.8-1.9.10 touches networking. Look at the grey reason line: `UnknownHostException` = wrong `supabase.url` or paused project; timeout = firewall/VPN/IPv6.
- Not compiled/run by Claude (no Maven, no javac). Test: sign in online, open Chats + a chat + Saved + a place, close the app, turn Wi-Fi off, reopen.

---

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
