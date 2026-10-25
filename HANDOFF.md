# Handoff - v1.12.1 Idle moods easier to see (2026-10-25)
- **PR title:** `v1.12.1 - BOFO chat head: idle moods start sooner and move more`. Version in `config.properties`, `pom.xml` (versionCode 36), CHANGES.md.
- Checked by running the real `Mascot` and `BofoPanel` under JavaFX 21 + Xvfb (software render): all four moods draw and the scheduler starts/ends them. Only timing (`scheduleIdle`: 4-10 s) and amplitudes (Mascot idle methods) changed.

---

# Handoff - v1.12.0 Idle moods for the chat head (2026-10-25)
- **PR title:** `v1.12.0 - BOFO chat head: thinking, sitting, running and sleeping idle animations`. Version in `config.properties`, `pom.xml` (versionCode 35), CHANGES.md.
- **`Mascot`:** `enum Idle {THINKING, SITTING, RUNNING, SLEEPING}`, `idle(Idle)`, `endIdle()`. Pure vector: parts are kept in fields (`eyeL/eyeR/footL/footR/thought`), a `pose` Scale pivoting on the feet does the sit/sleep squash. `set()` rebuilds everything, so `endIdle()` resets by calling `set(HAPPY)`.
- **`BofoPanel`:** `scheduleIdle()` (8-18 s quiet) -> `startIdle()` (random, not the last one) -> `stopIdle(true)` after 5-16 s. `bob` (the fab's up/down bob) is stopped during a mood. Interrupted by press/drag, chat open, a question (`busy`), mascot hidden or scene removed.
- Not compiled/run by Claude (no Maven, no javac). Test: leave the floating head alone ~10 s and watch all four; tap it mid-mood; ask BOFO a question (thinking face, no idle); hide it in Profile > Show BOFO mascot. The head is only 46 px, so z's and the circle are small: tune `size * 0.34` (run radius) and the z font sizes if you want them bigger.

---

# Handoff - v1.11.0 In-app legal reader (2026-10-25)
- **PR title:** `v1.11.0 - Privacy Policy, Terms and Agreements open in an in-app overlay`. Version in `config.properties`, `pom.xml` (versionCode 34), CHANGES.md.
- **New `ui/LegalOverlay`** (bottom sheet in the style of `FilterSheet`; `LegalOverlay.open(Doc.PRIVACY|TERMS|AGREEMENTS)`) and **`ui/LegalText`** (the document text, edit there; `policy.*.version` shown in the header). `Nav.pushOverlay/popOverlay` add a layer above the mascot. Wired in `ProfileView` (3 rows) and `LoginView` (2 links). Optional `policy.contact` in config.properties.
- Not compiled/run by Claude (no Maven, no javac). Test: Profile > tap each of the 3 rows, scroll, close with X / outside / Close / Esc; login screen links.

---

# Handoff - v1.9.13 Chat offline: saved history + outbox (2026-10-25)
- **PR title:** `v1.9.13 - Chats offline: read saved chats, write messages that send when online`. Version in `config.properties`, `pom.xml` (versionCode 28), CHANGES.md.
- **New `util/Outbox`:** `add(conv, body)` saves to `user_outbox.json` (DiskCache), `flushAsync()` sends oldest-first through `ChatService.send` (one flush at a time); network error = stays queued + `waiting` callbacks; other errors = dropped + `failed`. Flushed from `Main` on OFFLINE -> false, in `route()`, in `ChatView` constructor and every `tick()` while the queue is not empty.
- **`ChatView`:** `send()` = `Outbox.add` + `addPending` bubble (`queued` map, `Pending` record); listener `onSent/onFailed/waiting`; `restoreQueued()` re-adds bubbles after the history is drawn; history error while offline shows a note (`needsReload`, retried in `tick()`) instead of `errorState`. Removed the old in-memory `retries`/`backOnline`.
- **`ChatService.inbox()`** prefetches page 1 of the 20 latest chats (`PREFETCHED`/`INFLIGHT` keyed by chat id + last_message_at), so every chat has a saved copy.
- Known gap: if a send reaches the server but the reply is lost, the retry can post the message twice (messages have no client id yet).
- Not compiled/run by Claude (no Maven, no javac). Test: open Chats online, Wi-Fi off, open a chat (history visible), send two messages (faded, "Waiting for internet"), close + reopen the app, Wi-Fi on -> both go out in order.

---

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
