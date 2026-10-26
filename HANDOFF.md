# Handoff - v1.15.1 New listing polish (2026-10-26)
- **PR title:** `v1.15.1 - New listing: blue vector pin, no mascot on the form, compact bottom bar`. Version in `config.properties`, `pom.xml` (versionCode 52), CHANGES.md.
- `MapPane.enableCenterPin`: drawn blue pin + ground dot instead of the emoji. `ListingFormView`: progress label and bar on one row. Mascot hidden on the form (`Nav.show(..., false)` in OwnerShell, ListingsView). CSS block at end of bofo.css. Not compiled/run by Claude.

---

# Handoff - v1.15.0 Blue Create button + step-by-step New listing (2026-10-26)
- **PR title:** `v1.15.0 - Raised blue Create button and a 4-step New listing form`. Version in `config.properties`, `pom.xml` (versionCode 51), CHANGES.md.
- `Shell`: action tab (Create) is now a raised blue rounded square (`makeBulge`, `TOP=18` room above the bar; no highlight slide for it). `ListingFormView`: rewritten as 4 steps (Place & pin, Price & rooms, Amenities & photos, Contact & review) with a segment header, per-step checks, Back/Next/Publish and a review card; `submit()`/validation unchanged. CSS block at end of bofo.css. Not compiled/run by Claude.

---

# Handoff - v1.14.2 Cleaner compact card (2026-10-25)
- **PR title:** `v1.14.2 - Two-column card: only photo, name, price and one line`. Version in `config.properties`, `pom.xml` (versionCode 50), CHANGES.md.
- `BhCard` (compact only): no tags row, no beds pill unless Full, one `meta` line. Not compiled/run by Claude.

---

# Handoff - v1.14.1 Unstyled 2-column cards (2026-10-25)
- **PR title:** `v1.14.1 - Fix unstyled cards in the two-column list`. Version in `config.properties`, `pom.xml` (versionCode 49), CHANGES.md.
- `BhCard` has a 6-arg constructor with `compact`; `setCompact` and the width-based switching are gone. `CardGrid` only lays out. Callers in HomeView, ExploreView, SavedView pass `true`. If cards are STILL unstyled the cause is elsewhere (check the console for CSS warnings). Not compiled/run by Claude.

---

# Handoff - v1.14.0 Two-column list + ad slots (2026-10-25)
- **PR title:** `v1.14.0 - Two-column listing grid with room for ads`. Version in `config.properties`, `pom.xml` (versionCode 48), CHANGES.md.
- `CardGrid(gap, minCol, maxCols)` rewritten (row stretch, `AdSlot` cells, calls `BhCard.setCompact(colW < 280)`). `AdSlot` new: `ENABLED=false`, `placeholder()` is where the real ad goes, `fullWidth()` for a whole-row ad. Hooks: `HomeView.renderMore` (`adSpots`), `ExploreView` result loop. CSS block at end of bofo.css. Not compiled/run by Claude (uses no pattern-matching instanceof).

---

# Handoff - v1.13.2 Home cleanup (2026-10-25)
- **PR title:** `v1.13.2 - Home: one location button, shorter map, count in subtitle, slim map credit`. Version in `config.properties`, `pom.xml` (versionCode 47), CHANGES.md.
- `HomeView`: `nearMe` is no longer added to the layout (still used for the overview state; the map button is the only visible one), `MapPane(230)`, `updateHeader` appends the count to `nearbySub`, `countPill` not in the layout. `MapPane`: credit `setWrapText(false)`. CSS block at end of bofo.css. Not compiled/run by Claude.

---

# Handoff - v1.13.1 Light top section (2026-10-25)
- **PR title:** `v1.13.1 - Top section: light, clean header (no gradient band)`. Version in `config.properties`, `pom.xml` (versionCode 46), CHANGES.md.
- Only the CSS block at the end of bofo.css was replaced (`.page-header` transparent, dark text, white `.hdr-btn`) and the Home bell icon is dark again. Java structure from v1.13.0 unchanged. Not compiled/run by Claude.

---

# Handoff - v1.13.0 Top section redesign (2026-10-25)
- **PR title:** `v1.13.0 - Branded gradient top section on all main tabs`. Version in `config.properties`, `pom.xml` (versionCode 45), CHANGES.md.
- `Ui.pageHeader(title, sub, right...)` + CSS block at the end of bofo.css (`.page-header`, `.page-title`, `.page-sub`, `.hdr-btn`, overrides for avatar / near-btn / filter-btn / btn-cta inside the band). Used by HomeView (own band), ExploreView, SavedView, ChatsView, DashboardView, ListingsView, PaymentsView, QueueView, ReportsView. Sub-screens keep `Ui.appBar` / `backBar`. Not compiled/run by Claude.

---

# Handoff - v1.12.9 Listing card redesign (2026-10-25)
- **PR title:** `v1.12.9 - Listing card: big price line, one-row rating + distance, badges first`. Version in `config.properties`, `pom.xml` (versionCode 44), CHANGES.md.
- `BhCard` text block rebuilt (`head` VBox, `meta` FlowPane, `tags`); CSS `.price-big`, `.price-unit`, `.meta-dot`, `.card:hover` appended to bofo.css. Not compiled/run by Claude.

---

# Handoff - v1.12.8 Home polish (2026-10-25)
- **PR title:** `v1.12.8 - Home: time-of-day greeting, list subtitle + count, map outline`. Version in `config.properties`, `pom.xml` (versionCode 43), CHANGES.md.
- `HomeView`: `greeting()`, `nearbySub` + `countPill` (`updateHeader()` in `reload()`), `mapFrame` Region over the map; CSS `.count-pill`, `.map-frame` appended to bofo.css. Needs v1.12.6/v1.12.7 Mascot files too if not merged yet. Not compiled/run by Claude.

---

# Handoff - v1.12.7 Thinking arm simplified (2026-10-25)
- **PR title:** `v1.12.7 - BOFO chat head: cleaner thinking pose`. Version in `config.properties`, `pom.xml` (versionCode 42), CHANGES.md.
- `Mascot.build` THINKING: chin arm is now `Polyline(160,130 -> 138,138)` + hand circle (132,138). Not compiled/run by Claude.

---

# Handoff - v1.12.6 Sit/sleep poses redrawn (2026-10-25)
- **PR title:** `v1.12.6 - BOFO chat head: better sitting and sleeping poses`. Version in `config.properties`, `pom.xml` (versionCode 41), CHANGES.md.
- `Mascot.build`: `low` (sit/sleep) now uses the NORMAL body/face geometry with `torso.setLayoutY(12)`, feet at y 164 out front, arms at the normal shoulders. Sleeping nod 3 to 15 degrees, hat droop 6, z start (150,48). Not compiled/run by Claude.

---

# Handoff - v1.12.5 Hop between poses (2026-10-25)
- **PR title:** `v1.12.5 - BOFO chat head: hop animation between idle poses`. Version in `config.properties`, `pom.xml` (versionCode 40), CHANGES.md.
- `Mascot.swap()` now plays crouch (90 ms) -> launch (130 ms, `art.translateY` up `size*0.22`, `pose` Scale stretch) -> runs the change at the top -> fall/impact/settle (380 ms). Latest request wins; no scene = immediate. `cancelSwap()` resets everything (used by public `set()`). `stopAnims()` leaves `pose` alone while a hop plays.
- Tune the numbers in `swap()` if the hop is too big/small at 46 px. Not compiled/run by Claude.

---

# Handoff - v1.12.4 Smooth pose changes (2026-10-25)
- **PR title:** `v1.12.4 - BOFO chat head: smooth fade between idle poses`. Version in `config.properties`, `pom.xml` (versionCode 39), CHANGES.md.
- `Mascot`: `swap(Runnable)` (fade `art` out 130 ms, run change, fade in 200 ms, latest request wins, no scene = immediate), `stopAnims()`, `idle()` and `endIdle()` go through `swap`; public `set()` is immediate and cancels any fade (BofoPanel uses it for the THINKING face while answering).
- Not compiled/run by Claude.

---

# Handoff - v1.12.3 Idle effects leaked between poses (2026-10-25)
- **PR title:** `v1.12.3 - Fix idle effects (z's, speed lines) leaking into other poses`. Version in `config.properties`, `pom.xml` (versionCode 38), CHANGES.md.
- Cause: `fx` (effects layer) survives `set()` by design (splash sparks), so `endIdle()` now clears `fx.getChildren()`. One line in `Mascot.endIdle()`.
- Not compiled/run by Claude.

---

# Handoff - v1.12.2 Idle poses redrawn + fixed-size chat head (2026-10-25)
- **PR title:** `v1.12.2 - BOFO chat head: real idle poses (think/sit/run/sleep), no more layout shifts`. Version in `config.properties`, `pom.xml` (versionCode 37), CHANGES.md.
- **`Mascot`:** `set()` now calls `build(Expression, Idle pose)`; each idle pose is its own drawing (arms via `arm(Rotate,...)`, `low` body for sit/sleep, feet in front when sitting, `torso` Group + `torsoRot` lean/nod, `rotL/rotR` arm swing, `hand` for the thinking chin arm, `mouth` for sleep). Running is IN PLACE (the old circle ran outside the button). The old squash `pose` Scale is always 1 now.
- **`BofoPanel`:** mascot is wrapped in `faceBox` (fixed 46x46 Pane) and the fab is fixed 56x56, so z's / bubble / lean never change the button size or shift the UI.
- Not compiled/run by Claude (no Maven/JavaFX in the sandbox; only a syntax check). Test: wait ~5-10 s on any screen and watch all four poses; tap mid-pose; ask BOFO a question. If a pose looks off at 46 px tune the numbers in `Mascot.build` / `idle*`.

---

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
