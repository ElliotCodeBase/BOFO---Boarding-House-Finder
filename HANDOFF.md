# Handoff - v1.9.12 Always open when signed in (2026-10-25)
- **PR title:** `v1.9.12 - Open the app even with no internet when signed in`. Version in `config.properties`, `pom.xml` (versionCode 27), CHANGES.md.
- **Cause (screenshot: `UnresolvedAddressException`):** v1.9.11 only opened offline when a profile had been saved by an earlier ONLINE start; a first start offline hit `showNoInternet()`. `AuthService.restore()` now always runs `restoreOnline()` with a 4 s cap and, on OFFLINE/timeout with no profile, uses the saved profile or falls back to `new Profile(userId, STUDENT, ...)` built from the session (role unknown offline, never written to disk). `Main.dispatch` OFFLINE branch then opens the app as before.
- Not compiled/run by Claude (no Maven, no javac). Test: signed in, Wi-Fi off, start the app -> opens with the banner.

---

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
