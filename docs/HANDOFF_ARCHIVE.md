# Handoff archive

# Handoff - v1.16.8 Owner map dots/bubbles + locate button (2026-10-27)
- **PR title:** `v1.16.8 - Owner map shows dots and number bubbles; locate button works`. Version in `config.properties`, `pom.xml` (versionCode 65), CHANGES.md.
- `MapPane.PinLayer.zoomNow()` falls back to `map.getZoom()` when `zoomVal` is NaN (scroll/pinch/page-scroll set it NaN, so `far` was false and pins stayed full-size, no bubbles). `MapPane.goTo` null-safe, closes an open card, draws the blue dot first. `Locator.locate` uses a known `State.gps` immediately whatever the status (was waiting for status ON, so nothing moved) and restarts tracking quietly. Not compiled/run by Claude.


---

---

# Handoff - v1.16.7 Dashboard listing list + map names on zoom (2026-10-27)
- **PR title:** `v1.16.7 - Dashboard lists created listings; campus names only when zoomed in`. Version in `config.properties`, `pom.xml` (versionCode 64), CHANGES.md.
- `DashboardView`: added "Your listings" section with `BhCard` per `myListings` result (was only stats + map). `MapPane` label pass: names need `map.getZoom() >= 14.5` (real zoom, also after pinch) and are hidden when they would overlap; the chosen-campus exception was removed. Not compiled/run by Claude.

---

# Handoff - v1.16.6 Preview 4 screen sizes at once (2026-10-27)
- **PR title:** `v1.16.6 - Dev script that opens the app in 4 screen sizes`. Version in `config.properties`, `pom.xml` (versionCode 63), CHANGES.md.
- `Main` accepts `--size=WxH@X,Y` (position optional; title shows the size). New `dev/preview-sizes.ps1`: `mvn -q compile` once, then 4 `mvn javafx:run -Djavafx.args=--size=...` processes, 8 s apart (separate JVMs: `Nav`/`AuthService` are singletons, so one JVM cannot show 4 windows). Run: `powershell -ExecutionPolicy Bypass -File dev\preview-sizes.ps1`. Edit the `$sizes` list for other sizes. All windows share one saved login; for different accounts set `BOFO_PROFILE` per window. Not compiled/run by Claude.


---

# Handoff - v1.16.5 Number bubble: zoom until the houses separate (2026-10-27)
- **PR title:** `v1.16.5 - Number bubble zooms until its houses separate (one tap)`. Version in `config.properties`, `pom.xml` (versionCode 62), CHANGES.md.
- v1.16.4 framed all houses of the bubble, but 3 of 4 were still close, so they formed a new "3", then "2" bubble (user video: still 3 taps). `MapPane.clusterAt` now stores the houses' lat/lng (`pts`) and `separated(pts, z)` finds the lowest zoom (half-level steps, max 18) where no two houses are within the bubble reach (34 px x marker scale + 8) of each other; one tap `flyTo`s there, centred on the bubble. Small area (needs zoom > 16.5) and map still below 14.2: first tap stops at 14.8 (bubble still visible), second tap finishes (two taps). Bubbles only exist below zoom 15 (`far`). Not compiled/run by Claude.


---

# Handoff - v1.16.4 One-tap zoom on number bubbles (2026-10-27)
- **PR title:** `v1.16.4 - Number bubble on the map zooms to its houses in one tap`. Version in `config.properties`, `pom.xml` (versionCode 61), CHANGES.md.
- `MapPane` cluster bubble (`clusterAt`): each bubble now stores the box of its houses (`bb`); one tap `flyTo`s the zoom from `Geo.fitZoom` that frames them all (at least +1 level, max 17). If the houses are packed in a small area (fit would need zoom 17) and the map is still more than 4 levels away, the first tap goes half way and the second finishes (two taps). Was: +2.2 levels per tap around the bubble centre (3 taps for 4 houses). Not compiled/run by Claude.

---


---

# Handoff - v1.16.3 Chat side from the chat + separate login per window (2026-10-27)
- **PR title:** `v1.16.3 - Chat header follows the chat, second test window with its own login`. Version in `config.properties`, `pom.xml` (versionCode 60), CHANGES.md. **Zip is cumulative since v1.16.0** (the user's folder only got part of v1.16.1: Maven compiled 85 files, Popup.java missing).
- Bug: two app windows showed the same account because the saved login is one folder (`~/.bofo/session`), and `ChatView` chose the "Student / Boarding house owner" label and quick replies from the account's current role. Now `ChatView(convId, title, photo, otherUserId, iAmOwner, onBack)` takes the side from the chat (`ChatsView`: `c.ownerId().equals(me)`; `BhDetailView`: false).
- PC testing: `DiskCache.slot()` reads env `BOFO_PROFILE` (or `-Dbofo.profile`); `SessionStore` and the disk cache use `session-<name>` / `cache-<name>`. PowerShell: `$env:BOFO_PROFILE="owner"; mvn javafx:run`. Empty = normal; Android unaffected.
- Data note (Supabase): test accounts had swapped roles (from "Switch to ... mode"). Fixed by Claude with SQL: "Dev Owner" = owner (verified, 6 listings), "Dev Student" = student. The app also caches the profile on disk, so sign out and in after a role change.
- Not compiled/run by Claude.

---

---

# Handoff - v1.16.2 Action sheets replace native menus (2026-10-27)
- **PR title:** `v1.16.2 - In-app action sheets instead of grey right-click menus`. Version in `config.properties`, `pom.xml` (versionCode 59), CHANGES.md. **Zip is cumulative with v1.16.1** (user had not applied 1.16.1 yet).
- `Popup.menu(title, Popup.Action...)` = bottom sheet (`popup-sheet*` css). Replaced all 3 `ContextMenu`s: message long-press (`ChatView.messageMenu`), chat 3-dots (`ChatView.chatMenu`), chat card (`ChatsView`). No `ContextMenu`/`Alert`/`TextInputDialog` left in the code: use `Popup` for every new menu/dialog. ComboBoxes were already styled in css (`combo-box-*`) and left alone; FileChooser is the OS picker (cannot be styled). Not compiled/run by Claude.

---

---

# Handoff - v1.16.1 Chat delete fix + one pop-up style (2026-10-27)
- **PR title:** `v1.16.1 - Fix "Could not delete the chat" and use one in-app pop-up everywhere`. Version in `config.properties`, `pom.xml` (versionCode 58), CHANGES.md.
- **Cause of "Could not delete the chat":** migration 008 was never run on Supabase (no `delete_conversation` RPC, no `hidden_for`/`*_cleared_at` columns). Claude applied it to project `zfxldzcoirqolflkkhrh` via MCP as migration `delete_chat`; file `supabase/migrations/008_delete_chat.sql` unchanged. Nothing to run by hand.
- New `ui/Popup.java` (in-app card pop-up: `confirm`, `ask`, `alert`, `input`; classes `popup-*` at the end of `bofo.css`). It replaces every JavaFX `Alert` / `TextInputDialog`: `ChatView` (delete chat, delete message, report), `ChatsView`, `ListingsView` (delete + error), `ProfileView` (delete account), `PaymentsView` (refund reason), `QueueView` (reject reason), `Locator.ask` (location prompt). New dialogs must use `Popup`, never `Alert`.
- Only `javac` syntax-checked, not built or run by Claude.

---

---

# Handoff - v1.16.0 Delete messages and chats (2026-10-27)
- **PR title:** `v1.16.0 - Delete messages and chat cards for students and owners`. Version in `config.properties`, `pom.xml` (versionCode 57), CHANGES.md.
- **Supabase: run `supabase/migrations/008_delete_chat.sql` first** (adds `messages.hidden_for`, `conversations.student_cleared_at/owner_cleared_at`, new `msg_read` policy, new `inbox_v`, RPCs `delete_message(msg, everyone)` and `delete_conversation(conv)`). Without it the delete buttons fail with a toast and nothing else changes.
- `ChatService.deleteMessage/deleteConversation` (also clear the device copies); `ChatView` message menu + `chatMenu` + `dropDeletedByOther` (every 3rd poll); `ChatsView` context menu on cards. Not compiled/run by Claude.

---

---

# Handoff - v1.15.5 Card wider than screen (2026-10-27)
- **PR title:** `v1.15.5 - Fix listing card wider than the screen in My listings`. Version in `config.properties`, `pom.xml` (versionCode 56), CHANGES.md.
- `CardGrid.flow`: single-column cells (BhCard etc.) use `min(w, maxWidth)` instead of `max(minWidth, ...)`. Follow-up to v1.15.4 (`ListingsView` now a one-column `CardGrid`). Not compiled/run by Claude.

---

---

# Handoff - v1.15.4 My listings fits the screen (2026-10-27)
- **PR title:** `v1.15.4 - Fix My listings card overflow and button overlap`. Version in `config.properties`, `pom.xml` (versionCode 55), CHANGES.md.
- `ListingsView`: rows is now `CardGrid(10, 150, 1)` (was a VBox, so the card took the photo's width and ran off the screen, and its height was measured for the wrong width); the action buttons are equal-width with `Priority.ALWAYS`. Not compiled/run by Claude.

---

# Handoff - v1.15.3 Cleanup + faster loading (2026-10-27)
- **PR title:** `v1.15.3 - Remove unused code, faster loading and publishing`. Version in `config.properties`, `pom.xml` (versionCode 54), CHANGES.md.
- Deleted unused code/resources (list in CHANGES.md). `SupabaseClient`: `Accept-Encoding: gzip` + decode, GET timeout 15 s, new `insert(table, JSONArray)`. `BhService.list`: featured query runs in parallel; `publish`: batched rooms/amenities/photos, parallel photo upload. `Capture.png`: Sub filter; listing photos 1024 px. `Main`: `SPLASH_MIN_MS = 1200`, `PauseTransition` instead of a raw thread. Parse-checked only, not compiled or run by Claude: build once and test publish with 2-3 photos + a slow connection.

---

# Handoff - v1.15.2 Camera and Gallery fix (2026-10-26)
- **PR title:** `v1.15.2 - Fix Camera and Gallery buttons on PC and phone`. Version in `config.properties`, `pom.xml` (versionCode 53), CHANGES.md.
- Cause: `Capture` used only Gluon Attach `PicturesService`, which does not exist on desktop, so the buttons did nothing on PC. Now `Capture.camera()/gallery()` fall back to a JavaFX `FileChooser` on PC (no webcam in JavaFX) and catch errors. `AndroidManifest.xml`: added READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE (max SDK 28), READ_MEDIA_IMAGES. Phone path still uses Attach and is untested: if it fails, check `adb logcat` for "Camera failed"/"Gallery failed" and the Attach pictures runtime permission. Same fix covers the verification ID/selfie photo. Not compiled/run by Claude.

---

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

---

 (older entries)

Only read this when you need old details. Newest entries are in ../HANDOFF.md.

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

# Handoff - v1.9.2 Directions: BH -> Campus fix + redesign (2026-10-25)
- **PR title:** `v1.9.2 - Directions: BH to Campus tab fix, new Directions card design`. Version in `config.properties`, `pom.xml` (versionCode 17), CHANGES.md.
- **Bug:** `DirectionsCard.select()` never cleared the map, so while the plan was loading (and for slow/failed networks) the previous tab's route (Me -> Campus, 123.8 km) stayed drawn under "Finding the best way...". Now `select()` calls `map.clearRoute()`, then `previewLine()` draws the saved road (`Router.peek`) or a straight guide line for THIS tab and moves the camera to it; the real plan replaces it when ready.
- **Design:** `tripHeader` (From/To dots), `dir-hero` summary (icon, time, distance, fare), `note()` rows (ok/warn/bad symbols), `btn-tint` / `btn-link` buttons, skeleton placeholders while loading, map 250 px high. New CSS block "Directions card redesign (v1.9.2)" at the end of `bofo.css`.
- Not compiled/run by Claude (no JavaFX or Maven access here); parse-checked with javac only. Please run on the phone: open a BH, tap each of the three tabs, check the line and layout.
- Process: CLAUDE.md now says every change is delivered as a zip of CHANGED files only; old handoff entries moved to `docs/HANDOFF_ARCHIVE.md` (read it only when needed).

---

---

# Handoff - v1.9.1 Directions tabs (2026-10-25)
- **PR title:** `v1.9.1 - Directions tabs: current location to BH, current location to campus, BH to campus`. Version in `config.properties`, `pom.xml` (versionCode 16), CHANGES.md.
- `DirectionsCard`: tab 2 is now BH -> campus (it was campus -> BH), labels "Me → BH", "Me → Campus", "BH → Campus" (one line each); start name reads "Current location". The far-away button reads "Show BH → Campus instead" (that tab does not need GPS). Jeepney direction matching therefore runs from the BH to the campus on tab 2.
- Type-checked against OpenJFX (only Gluon symbols unresolved); not run on screen.

---

---

# Handoff - v1.9.0 offline jeepney cache, hand-made lines, options on the map card (2026-10-25)
- **PR title:** `v1.9.0 - Directions: offline jeepney cache, local transit file, tricycle/jeepney options on map cards`. Version bumped in `config.properties` (Profile > About), `pom.xml` (`<version>`, `<versionName>`, `<versionCode>` 15), CHANGES.md.
- **Disk cache** (`util/DiskCache`, `ui/AppStorage`): `TransitRoutes.around` = memory, then device copy younger than 14 days, then Overpass (writes the copy, keeps the newest 30), then, when offline, ANY older copy (`lastSource()` = online / saved / old / none). Files: `<private storage or ~/.bofo>/cache/transit-<bbox>.json`. A corrupt copy is ignored. `AppStorage.init()` is called from `MapPane` and `DirectionsCard` static blocks.
- **Local lines** (`util/LocalTransit`, `local-transit.json`, `docs/LOCAL_TRANSIT.md`): hand-made lines from `stops` or `path`, optional checked `fareLow/fareHigh` and `note`, both directions by default, rank 20% better than OSM lines, bad entries skipped one by one. Two files are merged: the one in resources (ships empty ON PURPOSE: I had no verified Dumaguete/Bacolod routes or fares, nothing was invented) and `local-transit.json` in the device's BOFO folder. `pom.xml` Android `resourcesList` now includes `.*\.json$`.
- `TransitMatch.Line` got `note, fareLow, fareHigh` (old 5-argument constructor kept); `Trip.jeepney(..., fareLow, fareHigh, note)`; `Trip.Option.summary()`.
- **Map card (Home / Explore / owner map)**: after the walking route is drawn, `MapPane.planOptions` runs `TripPlanner.plan` in the background and shows the best option on the card ("Jeepney 20 min, P14-17, 3.0 km  +2 more, from <campus>"); if it is not a walk, its path replaces the walking line (far trips also move the camera). Tap the card for all options.
- `TripPlanner`: if the first route call shows the route server is unreachable, drive and stop legs use estimates at once (offline plan 8.7 s to 2.8 s); notes from several causes are joined, not overwritten.
- Tested by Claude (fake OSRM/Overpass server, separate JVMs): local-line parsing incl. broken entries, checked fare shown, disk cache online -> servers down (fresh copy), 20-day-old copy, corrupt copy, plus the full v1.8.0 suite. `dev/TripLogicCheck.java` extended. UI files type-check against OpenJFX except the Gluon parts; NOT run on screen: please tap a pin on Home and look at the card text.

---
# Handoff - v1.8.0 directions: commuter data, mode options, layout fix (2026-10-25)
- **PR title:** `v1.8.0 - Directions: walk/tricycle/jeepney options from public commuter data, card layout fix`. Version bumped in `config.properties` (Profile > About), `pom.xml` (`<version>`, `<versionName>`, `<versionCode>` 14), CHANGES.md.
- New `DirectionsCard` (ui) replaces the old Location card on `BhDetailView`: tabs Me > Here / Me > Campus / Campus > Here (equal-width grid, may wrap, nothing has a min width bigger than the screen: that was what pushed the old card off the right edge), map, list of options, "Open in OpenStreetMap". The page's bottom padding is 96 so the last card scrolls above the floating mascot.
- New util classes: `Trip` (records, LTFRB fares, time estimates, "best" pick; pure), `TransitMatch` (which mapped line really goes A to B in the right direction; pure), `TransitRoutes` (downloads jeepney/bus/share_taxi route relations from OpenStreetMap via Overpass; open data, ODbL), `TripPlanner` (puts it together). `Router.drive()` added (car profile, OSRM + Valhalla `auto`).
- Over 25 km straight line = only "Bus or van" (no walk, no fare); `Router.walk` skips the foot server beyond 30 km (`Router.MAX_WALK_KM`) and `MapPane` no longer retries those 4 times. `MapPane.showPath()` draws a ready-made path with its own badge; `fitBounds` zooms out to 6 for far trips.
- `Geo.fmtTime` ("1 h 5 min"), `Geo.walkText`, `Geo.pathKm`. "min walk" texts on BhCard / BhDetailView / MapPane / ListingFormView use them (over 3 km = "12 km away", no walking time).
- `State.gpsApprox` (true when the fix came from a PC / `dev.location`): the card warns that PC locations can be off by km (this was the 105 km "walk" in the screenshot).
- Data honesty: jeepney lines exist only where OSM volunteers mapped them. Where none are found the card says "Jeepney or multicab (estimate)" (fare from the LTFRB formula, ask the driver). Tricycle fares are the commonly paid Dumaguete range (city-set). Fares: traditional jeepney P14 first 4 km + P2/km, modern P17 + P2.40/km (LTFRB, effective 19 Mar 2026). Check `Trip` constants if LTFRB changes them.
- Tested by Claude: `Trip`, `TransitMatch`, `TripPlanner`, `TransitRoutes.parse`, `Router` against a fake OSRM/Overpass server (including servers down), see `dev/TripLogicCheck.java`. UI files were type-checked against OpenJFX but NOT run (no Gluon Maps / display in Claude's environment): run the app and look at the Directions card on a BH page.

---

---
# Handoff - v1.7.0 boarding house page redesign, Call button removed (2026-10-25)
- `BhDetailView.render()` rewritten: photo hero (friendly placeholder when no photos), name + address with pin, big purple price + "/ month · per room", status pills (Verified, "N beds available" / "Fully booked", distance), 3 quick-fact tiles (beds free, rooms, rating), then white cards: Amenities, Rooms (per-room "N free"/"Full"), Location (rounded map + route), Reviews (+ review form for students). Bottom bar = only "Chat with owner".
- Call button removed on purpose (phone number is still stored in `bh_contacts`, just not shown on this page).
- New CSS: `.hero-empty`, `.stat-tile`, `.pill-ok`, `.pill-full`; new glyph `Glyphs.PIN`.
- Not compiled by Claude (no JavaFX here).

---
# Handoff - v1.6.3 boarding house page failed to open (2026-10-25)
- Cause: `BhService.detail()` (v1.6.0) also loads `bh_amenities`; when `005_amenities.sql` was not run that request failed and took the whole detail page with it.
- Fix: the amenities request is optional now (`exceptionally` -> empty list), so a place always opens; amenities simply show once the SQL is run.

---
# Handoff - v1.6.2 amenity autocomplete, floating search dropdown, List|Map fix (2026-10-24)
- Amenity search in the filter is now smart and offline (`util/Fuzzy.java`, no API): word starts, words inside names ("wifi" -> Free Wi-Fi), nicknames ("aircon", "cr", "internet") and small typos ("laundy"). Results show how many boarding houses have each amenity. Students only see amenities that at least one ACTIVE listing has; if none: "No boarding house has ... yet. Try a similar word...". Owners still see the whole catalog + "Add".
- `005_amenities.sql` changed slightly (usage counts only count active listings). It is safe to re-run. The filter says "Amenities aren't available right now" until this SQL has been run (console shows `[AmenityPicker] couldn't load ...`). After running it, restart the app (v1.6.1 fallback stays on for the session).
- `CampusPicker` dropdown is now unmanaged and floats under the bar (`layoutChildren`), parents use `setViewOrder(-1)`; it no longer gets squeezed or pushes the page. Long campus names wrap (no sideways scrollbar).
- `Ui.segmented` buttons never shrink to "..." (min width = pref); Explore count text shortens with an ellipsis instead.
- Not compiled by Claude (no JavaFX here). `Fuzzy` was tested separately with plain javac.

---
# Handoff - v1.6.1 Explore "Something went wrong" fix (2026-10-24)
- Cause: v1.6.0 asks `bh_cards` for the new `amenity_names` column; if `005_amenities.sql` was not run (or failed) Supabase answers HTTP 400 and every list showed the error screen.
- Fix: `BhService.cards()` retries once without the amenity column/filter, so lists load either way (no amenity tags/filter until the migration is run). Explore also prints `[Explore] load failed: ...` to the console.
- Still do: run `supabase/migrations/005_amenities.sql` (in one go, check it ends without a red error).

---
# Handoff - v1.6.0 amenities, merged Home search, new Explore filter (2026-10-24)
- **RUN FIRST:** `supabase/migrations/005_amenities.sql` in the Supabase SQL editor (adds `amenities`, `bh_amenities`, `amenities_v`, `get_or_create_amenity()`, 20 starter amenities, backfills water/electricity/Wi-Fi of existing listings, and rebuilds `bh_cards` with `amenity_ids` + `amenity_names`). The app will get HTTP 400 on lists until it is run. Optional: redeploy `bofo-chat` (now knows `amenity_names` and an `amenities` filter).
- Home: the two search bars are ONE `CampusPicker` (synced with `State.college`) + the round near-me button. Pick a campus = map and list below follow it; Enter on text that is not a campus = Explore search for that name/area.
- Explore: search bar (shadow + purple focus ring), ONE scrolling quick-filter row (active price/sort chips, chosen amenities, top 6 popular amenities, "Clear all"), and a bottom-sheet filter `FilterSheet` (price chips + custom max, sort, amenities). Nothing reloads until "Show results". Text search now matches name OR address and URL-encodes spaces (multi-word searches used to break).
- Amenities: `AmenityPicker` (new). Students: popular chips, search box finds ANY amenity in the catalog (incl. ones owners added); no match = just says so (students cannot create). Owners (`ListingFormView`): same picker with "+ Add ..." to create a new amenity (a leading emoji becomes its icon). Filter = `amenity_ids=cs.{...}` (place must have ALL chosen). Cards show up to 4 amenity tags, map card 3, detail page shows all with icons.
- Old `free_water/electricity/wifi` columns are still written on publish (kept in sync from the three starter amenities) so nothing else breaks.
- Not done: owners cannot edit amenities of an EXISTING listing yet (only when creating one). Amenity moderation = admin can delete rows in `amenities`; owners are limited to 30 custom amenities.
- NOT compiled by Claude (no JavaFX/Maven here). If the build complains, check `FilterSheet`, `AmenityPicker`, `ExploreView` first.

---
# Handoff - v1.5.2 one search bar, solid road route, removable filters (2026-10-24)
- Explore now has ONE search bar (`CampusPicker`): type a campus (autocomplete, arrow = full list, pick = list+map jump to it) or type a BH name/area (searches as you type; the dropdown hides when no campus matches). X on the campus name clears the campus; X on typed text clears the text. Fixed the pill-inside-a-pill look (`.college-field` border now transparent).
- Filters: Free water / electricity / Wi-Fi toggles moved INTO the filter panel (Amenities). Under the search bar, only filters that are ON show, as dark pills with an x (tap = turn off). Max price shows too. Row is hidden when nothing is on.
- Route: no more dotted line (always solid). `Router` now tries OSRM foot, then Valhalla pedestrian (valhalla1.openstreetmap.de, polyline6 decoder tested), 2 tries each. If both fail the straight line is drawn and the card shows the reason (`Router.lastError()`), and MapPane keeps retrying 4x. NOT verifiable from Claude's sandbox (routing hosts blocked): if the road path still does not show, read the reason on the card / `[Router]` console lines.
- Still NOT compiled by Claude (no Maven central access). Version 1.5.2 / versionCode 8.
- Idea if public routing servers keep failing: build roads locally from the OpenFreeMap vector tiles (A*) - bigger job.

---
# Handoff - v1.5.1 route fix: real roads, not the dotted line (2026-10-24)
- Bug in 1.5.0: I shortened the route server timeouts (4/6 s) and retries; the free OSRM server then often failed and the app drew the dashed STRAIGHT-LINE fallback instead of the road path.
- Fix: timeouts back to 8/12 s, 3 attempts (600 ms apart), and `MapPane.fetchRoute` keeps retrying quietly (4x, every 2.5 s) while showing the guide line, swapping in the real road path the moment it arrives. Dashed line only appears if the server stays unreachable. Cached roads and the instant km/min estimate on the card are kept.
- If it is still dotted: console shows `[Router] ...` lines (HTTP code / exception). `route.online=false` in config would force the straight line; check config.local.properties.

---
# Handoff - v1.5.0 faster walking route (2026-10-24)
- The route (path) is computed by the free OSRM server (`Router.walk`); the wait was network + retries. Changes:
  1. `Router.peek()`: known routes (and the reverse direction) are drawn instantly from a 300-entry cache (was 40), no network.
  2. `MapPane.startRoute`: shows an instant offline estimate (straight line x1.3, "about X km - Y min walk (finding the exact route...)") and starts the camera move at once; the real road route replaces it. The camera re-fits only if the road leaves the straight-line box.
  3. `Router`: identical in-flight requests share ONE server call; timeouts 8/10 s -> 4/6 s; retry pause 700 -> 250 ms; `warmUp()` (called in `Main.start`) opens the TLS connection early.
- Not changed: still needs the internet for a NEW route; without it the straight line is used as before. Routing itself is the server's job (it is not computed in the app).
- NOT compiled/run. Check: tap a BH pin twice (second should be instant), and tap a new pin (card shows estimate first).
- Possible next step: save routes to disk, or a self-hosted/faster routing server (the public one is the main limit).

---
# Handoff - v1.4.1 compile fix (2026-10-24)
- `CampusPicker`: fixed "variable sync might not have been initialized" (state listener lambda was a field initializer reading the final `sync`; now assigned in the constructor). v1.4.0 compiled-failed, so this supersedes it. Version bumped to 1.4.1 (versionCode 5).

---
# Handoff - v1.4.0 campus search with autocomplete (2026-10-24)
- NEW `ui/CampusPicker`: campus search box with our OWN autocomplete (local matching in Java on the cached campus list; no per-keystroke API, no Google Places). Order: starts-with, word starts-with, contains, initials (e.g. "norsu"). Dropdown arrow lists all campuses; Up/Down/Enter/Esc work; typed part is bold.
- Explore: picker above the search bar. Choosing a campus sets `State.college`, so the LIST sorts nearest-first and the MAP recenters/zooms (works in both List and Map modes). Clearing it goes back to GPS. Count reads "N places near <campus>".
- Home: the fake search pill is now a CampusPicker (`syncWithState=false`): pick a campus -> opens Explore near it; Enter on non-campus text -> Explore name search.
- Version: `app.version` in config.properties is now the one number shown in Profile > About (was hard-coded "v1.3.0"); pom 1.4.0 / versionCode 4. RULE (CLAUDE.md): bump all of these in EVERY pull request.
- NOT compiled/run (no Maven/JavaFX in sandbox). Risks: `TextFlow` cells in the ListView, `sceneProperty` listener add/remove in ExploreView/CampusPicker.
- Campus list still comes from Supabase `colleges` (1 h cache, same list the map uses); no schema change needed.

---
# Handoff - Vector map from OpenFreeMap (2026-10-24)
- NEW `util/VectorTileRetriever` (registered in META-INF/services, replaces MinimalTileRetriever as the Gluon Maps provider) + `util/Mvt` (own MVT decoder). Draws land (faint lavender), parks/wood/grass (soft green), water (light blue), buildings (light, outlined from z16), roads (white + thin outline; main roads outlined in brand-purple tint #C5BAF7) into a 512 px Canvas image per tile. Gluon shows tiles at 256 px (`TileImageView.fitWidth=256`, checked in maps 2.0.0-ea+6 source), so it is 2x sharp. No labels, no POI icons: the layers place/poi/*_name/housenumber/boundary are skipped while decoding.
- Config: `map.renderer=vector|raster` (config.properties, default vector), `map.vector.tilejson`, `map.attribution.vector`. `AppConfig.mapAttribution()` follows the renderer, so the MapPane label now reads "OpenFreeMap (c) OpenMapTiles, data from OpenStreetMap". `raster` = old MinimalTileRetriever (CARTO/Esri URL still from map.tiles.* / config.local.properties). A single tile also falls back to raster if TileJSON/tile/draw fails.
- Verified by fetching (2026-10-24): TileJSON `https://tiles.openfreemap.org/planet` = TileJSON 3.0.0, tiles URL is VERSIONED (`.../planet/20261018_080001_pt/{z}/{x}/{y}.pbf`; the build id changes, so the app reads the TileJSON at run time, caches it 24 h, and re-reads it on a 404), minzoom 0 / maxzoom 14 (zoom 15-19 is drawn by enlarging the z14 tile). 16 layers: aerodrome_label aeroway boundary building housenumber landcover landuse mountain_peak park place poi transportation transportation_name water water_name waterway. Fields used: transportation `class` (motorway trunk primary secondary tertiary minor service track path pedestrian ...), `brunnel`, `ramp`; landcover `class` (wood grass wetland sand ice); landuse `class` (pitch cemetery school hospital); waterway `class` (river ...). Class lists cross-checked with OpenFreeMap's own `styles/liberty`.
- Decision: NO decoder dependency in pom.xml (asked for one). protobuf-java/JTS based MVT libraries use reflection and are not proven under GluonFX/GraalVM on Android; `Mvt.java` reads the protobuf wire format directly (no reflection, JDK only). pom changed only in `reflectionList` (+VectorTileRetriever). Checked: `Mvt` output (geometry + properties) is identical to @mapbox/vector-tile on 16 real fixture tiles incl. a 2,759-feature tile.
- Disk cache: raw tiles in `<private storage>/tiles/ofm/{z}/{x}/{y}.pbf`, 30 days fresh, older copy used when offline (same rule as before); TileJSON in `.../tiles/ofm/tilejson.json`. Max 4 parallel downloads (OpenFreeMap asks for <= 4).
- Tune the look in `VectorTileRetriever` (palette constants at the top, `W14` road widths, `width()` growth 1.5x per zoom).
- Compiled only against JavaFX STUBS (signatures checked in the openjfx jfx21 source) + real Gluon `TileRetriever`/org.json; behaviour tested with a fake local OpenFreeMap server (cache, gunzip, offline, 404, overzoom math, fallback). NOT run with real JavaFX, NOT run against the real server (sandbox cannot reach it), NOT seen on screen.
- UNTESTED: how it looks (colors, road widths, whether tiles join without seams); first `mvn clean javafx:run` compile with real JavaFX; Canvas.snapshot speed/behaviour on Android; GluonFX native-image build (no reflection used apart from the ServiceLoader entry, `java.util.zip` and org.json were already fine); whether OpenFreeMap answers 404 for ocean-only tiles (we draw plain land then); real-tile layer fields beyond those listed above.
- If the map stays on the old look: check console for "bofo-vtiles" errors and that `map.renderer` is not overridden in config.local.properties.

---
# Handoff - v1.2.0 location, Profile switch, map tiles (2026-10-24)
- Version bumped to 1.2.0 (versionCode 3). Fixed AuthService compile error (parenthesize the HTML string before .getBytes).
- Location: `util/Locator` has a saved on/off choice (`~/.bofo/location.properties`), a non-blocking "Allow location?" prompt after login, and `setEnabled()`. Android uses Attach PositionService. PC asks Windows through PowerShell (System.Device GeoCoordinateWatcher), then falls back to a browser page calling navigator.geolocation. `dev.location` in config.local.properties overrides everything (remove it to use the real location).
- `ProfileView`: "Use my location" switch under "Show BOFO mascot". `MapPane`: opens centered on the user's first fix.
- Map tiles: CARTO now returns an "API KEY REQUIRED" image without a key (HTTP 200, so no fallback). config.local.properties currently points at Esri tiles (no key). The MapTiler key was removed on purpose.
- NEXT: draw the map inside the app (vector tiles from OpenFreeMap, own style, no labels, sharp) as a new TileRetriever. Not started.
- NOT compiled by the assistant; the user ran the app on Windows. Android build not tested.

---
# Handoff - Map style, location, in-app Google sign-in (2026-10-24)
- Files: `util/MinimalTileRetriever`, `util/Locator`, `service/NativeGoogle`, `ui/MapPane` (rewritten), `ui/HomeView`, `service/AuthService`, `ui/LoginView`, `app/Main` (focusApp), `config/AppConfig`, `config.properties`, `bofo.css`, `Glyphs`, `pom.xml` (resourcesList + reflectionList), `AndroidManifest.xml`, `src/android/java/com/bofo/finder/android/GoogleSignInActivity.java`, `docs/NATIVE_GOOGLE_SIGNIN.md`.
- NOT compiled, NOT run. Likely first compile risks: Attach `PositionService` / `Position` calls in `Locator` (same calls HomeView used before: `create()`, `start()`, `positionProperty()`, `getLatitude/getLongitude`), `MapLayer.markDirty()` called through `PinLayer.refresh()`.
- Map: get a free CARTO key (dashboard.basemaps.carto.com) and put `map.tiles.key=...` in `config.local.properties` (git-ignored). Without it the map falls back to plain OpenStreetMap tiles.
- Android Google sheet: unverified assumptions = `HostServices.showDocument("bofo://google-signin...")` launches our own activity; Attach `getPrivateStorage()` is the same folder as Android `getFilesDir()`; GluonFX compiles `src/android/java` and uses `src/android/build.gradle` for libraries. Failure of the first one is handled (falls back to the browser after 4 s).
- The Android browser flow still cannot finish on a phone (README: the `bofo://auth-callback` link is never delivered into Java). The in-app sheet avoids that problem.

---
# Handoff - Delete account fix (2026-10-23)
- Cause 1: `delete_my_account()` ran `delete from storage.objects`; Supabase forbids that from SQL ("Use the Storage API instead"), so it ALWAYS failed.
- Cause 2: `recompute_bh()` re-inserted a `bh_activity` row for a listing being cascade-deleted -> FK violation (also broke deleting listings/owner accounts).
- Fix (already applied to the live Supabase project BOFO): migration `supabase/migrations/004_delete_account_fix.sql` (fixes recompute_bh, strips storage SQL from the RPC, `owner_verifications.reviewed_by` -> ON DELETE SET NULL) and Edge Function `supabase/functions/delete-account` (deployed, verify_jwt on): deletes the user's files in `id-verification` and `bh-photos` via the Storage API, then the auth user (everything cascades).
- App: `ProfileView.confirmDelete` now calls `SupabaseClient.function("delete-account", ...)` instead of the RPC.
- Tested: DB deletion of the dev owner (with listings) succeeded in a rolled-back transaction. NOT tested: the Edge Function call and the Java change (no network/Maven in Claude's sandbox).
- If delete still fails: Supabase dashboard > Edge Functions > delete-account > Logs (look for "delete-account failed"), and paste the app's toast/console text.

---
# Handoff - Online mode: real Supabase sign-in for dev (2026-10-23)

## What changed
- Supabase project **BOFO** (ref `zfxldzcoirqolflkkhrh`, ap-southeast-1) is live: 001-003 migrations applied, 15 colleges, 3 boost plans, buckets `bh-photos` (public) and `id-verification` (private). `config.properties` already points to it.
- Created 3 REAL test accounts (auth.users + public.users): student, owner (verified), admin. Emails/passwords are in `src/main/resources/com/bofo/finder/config.local.properties` (git-ignored, NEVER commit).
- Seeded 6 demo boarding houses (names start with "Demo BH", owned by the dev owner) near NORSU, Silliman, Foundation, St. Paul, La Salle and CHMSU Alijis, so Home/Explore/map are not empty.
- `AppConfig`: loads optional `config.local.properties`; `devEmail(role)` / `devPassword(role)`.
- `AuthService.devSignIn(Role)`: real password sign-in (`/auth/v1/token?grant_type=password`), saves the session, loads the profile. Gives a genuine JWT, so RLS, favorites, chat, listings and storage work online (the old fake `devForce`/`devLogin` could not).
- `LoginView`: the Student / Owner / Admin dev buttons now call `devSignIn` and show "No internet connection" or the error text on failure.
- Removed the stale duplicate nested folder `BOFO---Boarding-House-Finder-main/BOFO---Boarding-House-Finder-main/` from the zip (older copy of the same sources).

## Google login (see docs/GOOGLE_LOGIN_SETUP.md)
Steps done 2026-10-23: Google Cloud project "BOFO Finder", consent screen (External), test user, Web OAuth client "BOFO Supabase" -> then paste Client ID/secret into Supabase Auth > Providers > Google and add redirect URLs. Keys are in `KEYS.local.md` (git-ignored). Status: finish steps 4-6 of that doc if not yet done.

## NOT verified (no Maven/network access in Claude's sandbox)
- Not compiled; the password login was not tested against Supabase from here. If the dev button shows "Dev sign-in failed", paste the message.
- Edge functions (`bofo-chat`, `create-boost-checkout`, `paymongo-webhook`) are NOT deployed and need secrets (LLM key, PayMongo). BOFO AI chat and Boost payments will fail until then.
- Google login still needs the Google provider + redirect URLs (`bofo://auth-callback`, `http://localhost:54321/callback`) in Supabase Auth settings.

## Before release
- Delete the dev buttons in `LoginView` and `devSignIn`/`devLogin`/`devForce` in `AuthService`; delete the 3 test users and "Demo BH" rows in Supabase.

## Git
Follow the "Clean repo update guide" below. Check `git status` first: `config.local.properties` must NOT appear (it is in `.gitignore`).

---
# Handoff – Dev login bypass (2026-10-22)

## What changed
- `config.properties`: added `dev.bypassLogin=true` and `dev.role=student` (student | owner | admin).
- `AppConfig`: `devBypass()` / `devRole()`.
- `AuthService.devLogin(Role)`: fake local profile (verified, fixed UUID `00000000-0000-0000-0000-0000000de000`), no token.
- `Main.start`: when bypass is on, skips splash + Google login and routes straight to the home for `dev.role`.
- `LoginView`: when bypass is on (e.g. after Sign out) shows "DEV: skip login as Student / Owner / Admin" buttons.

## Caveats
- No real Supabase session: requests use the anon key, so anything behind RLS (saving, chat, publishing listings, owner/admin data) may return 401/403 or empty. Public reads (colleges, listings) should work.
- The fake user id has no row in `users`, so inserts with FK to `users` will fail. For full testing, use a real login or create a test user row in Supabase.
- NOT compiled here (no Maven network access) – run `mvn javafx:run` / your usual build to verify.

## Before release
Set `dev.bypassLogin=false` in `src/main/resources/com/bofo/finder/config.properties` (or remove the dev code).

## Local folder & update commands
User's local repo folder: `C:\Users\user\bofo-finder`

PowerShell (adjust the zip path if it is not in Downloads):
```powershell
cd C:\Users\user\bofo-finder
Expand-Archive -Force $HOME\Downloads\bofo-dev-bypass.zip $env:TEMP\bofo-new
Copy-Item -Recurse -Force "$env:TEMP\bofo-new\BOFO---Boarding-House-Finder-main\*" .
git pull origin main
git checkout -b dev-login-bypass
git add -A
git status
git commit -m "Add dev login bypass for testing"
git push -u origin dev-login-bypass
```
Notes: use `master` instead of `main` if that is the default branch; `git stash` / `git stash pop` if pull complains about uncommitted changes; check `git status` for stray files (target/, __pycache__) before committing.

---
# Session log, run guide & known issues (updated 2026-10-22)

## Environment (user)
- OS: Windows, PowerShell. Repo folder: `C:\Users\user\bofo-finder`
- GitHub repo: https://github.com/ElliotCodeBase/BOFO---Boarding-House-Finder.git
- Run on desktop: `cd C:\Users\user\bofo-finder` then `mvn javafx:run` (needs JDK 17+ and Maven; first run downloads deps)
- Claude's sandbox CANNOT reach Maven Central or compile this project (network blocked). Every compile error comes from the user's terminal; ask for the pasted output. Don't guess API names: have the user run
  `javap -cp (Get-ChildItem $HOME\.m2 -Recurse -Filter "maps-2.0.0-ea+6.jar").FullName com.gluonhq.maps.MapView`
  and read the real method list.

## Git setup (folder was a ZIP download, not a clone)
PowerShell: do NOT type the `<` `>` around the URL (PowerShell treats `<` as an operator).
```powershell
cd C:\Users\user\bofo-finder
git init -b main
git remote add origin https://github.com/ElliotCodeBase/BOFO---Boarding-House-Finder.git
git fetch origin
git reset origin/main        # keeps working files, links history (use origin/master if default is master)
git status                   # should list only the changed files; check the untracked bofo/ folder first
git checkout -b dev-login-bypass
git add -A
git commit -m "Add dev login bypass for testing"
git push -u origin dev-login-bypass
```
After this one-time setup the folder is a normal clone: later updates are just `git add -A`, `git commit -m "..."`, `git push`.

## Compile errors hit so far
1. `MapPane.java:41` `map.getMapPoint(...)` does not exist on `com.gluonhq.maps.MapView` (Gluon Maps 2.0.0-ea+6).
2. First attempted fix `map.centerLat()/centerLon()` ALSO does not exist.
3. Current fix (in this zip, UNVERIFIED against ea+6): convert the map's center to scene coords and call `map.getMapPosition(sceneX, sceneY)`:
   `Point2D s = map.localToScene(w/2, h/2); MapPoint p = map.getMapPosition(s.getX(), s.getY());`
   If that fails too, try `map.getCenter()` (MapPoint) or check the javap output above.
   One-line PowerShell patch for an older copy: replace the body of `center()` in `ui/MapPane.java` (about line 41).

## Supabase status
- User's Supabase account has NO BOFO project (only "Coffee Shop" and "ryhar-portfolio", unrelated: never modify them).
- `config.properties` still holds placeholders (`supabase.url`, `supabase.anonKey`). With the dev bypass the app opens but data screens are empty/error until a real project is configured.
- To finish: create a new Supabase project "BOFO" (may add cost: ask the user first), run `supabase/migrations/001_schema.sql`, `002_performance.sql`, `003_boosts.sql`, put the Project URL + ANON/publishable key (never service_role) in config.properties, insert rows into `colleges`. User has granted Claude access to their Supabase for this.
- Google login (non-bypass) additionally needs the Google provider + redirect URLs (see README Setup).

## Dev bypass reminder
`dev.bypassLogin=true` and `dev.role=student|owner|admin` in `src/main/resources/com/bofo/finder/config.properties`. Set to false before release.

---
# Clean repo update guide (for the AI to follow on the next update)

## Current git state (as of 2026-10-22)
- `C:\Users\user\bofo-finder` started as a ZIP download. It was linked with `git init` + `git remote add origin ...`.
- The first `dev-login-bypass` commit was made BEFORE linking to origin, so GitHub said "main and dev-login-bypass are entirely different commit histories" and refused a pull request.
- Fix given: `git fetch origin` -> `git reset --soft origin/main` -> commit -> `git tag -fa v1.0.1` -> `git push --force-with-lease -u origin dev-login-bypass` -> `git push -f origin v1.0.1`.
- Once that works the folder is a normal clone sharing history with `main`. Do NOT run `git init` again.

## Rules for every next update
1. FIRST ask the user to run `git status` and `git branch -vv` in `C:\Users\user\bofo-finder` and paste the output; don't assume the state.
2. Never `git init` in a folder that already has `.git`. Never commit before the folder shares history with origin (`git log --oneline` should show origin/main's commits underneath).
3. Never type `<` `>` around URLs/placeholders in PowerShell commands; give ready-to-paste commands with real values.
4. Always start from fresh remote state: `git fetch origin` and base new work on `origin/main` (or the current feature branch), not on a stale ZIP copy.
5. Deliver code changes as either (a) a short PowerShell patch for 1-3 lines, or (b) a new zip. If a zip is copied over the folder, run `git status` and review the file list BEFORE `git add -A`. Expected files are only the ones changed this session.
6. Never commit: `target/`, `__pycache__/`, `*.class`, the stray `bofo/` folder (ask what it is), real keys/secrets (service_role key, LLM_API_KEY, `.env`). The anon key in config.properties is public by design, but still prefer not to commit real project values to a public repo.
7. Work on a feature branch (e.g. `dev-login-bypass`), then open a pull request into `main`. Don't push dev-only changes straight to `main`.
8. Each update: commit message starts with the version (`v1.0.2: ...`), tag it (`git tag -a v1.0.2 -m "..."`), push branch then `git push origin v1.0.2`. Semver: patch = fix, minor = feature, major = breaking. Also bump `<version>` in `pom.xml` to match when asked.
9. Before merging to `main`: set `dev.bypassLogin=false` in config.properties.
10. Force push only on the user's own feature branch/tag and only with `--force-with-lease`; never force push `main`.
11. If a push needs auth, the user signs in to GitHub as the account owning `ElliotCodeBase` (browser prompt or personal access token). If git asks who they are: `git config --global user.name` / `user.email`.

## Standard update sequence (after the folder is a proper clone)
```powershell
cd C:\Users\user\bofo-finder
git status
git fetch origin
git checkout -b <branch-name>        # or: git checkout <existing-branch>
# ...apply changes / copy files...
git status                            # review file list
git add -A
git commit -m "vX.Y.Z: <what changed>"
git tag -a vX.Y.Z -m "vX.Y.Z - <what changed>"
git push -u origin <branch-name>
git push origin vX.Y.Z
```
Then open the pull request link printed by `git push` (or GitHub > Pull requests > New).

## Common problems
- "not a git repository": run commands inside `C:\Users\user\bofo-finder`; if no `.git`, do the link steps in "Git setup" above.
- "entirely different commit histories": use the `git reset --soft origin/main` fix above.
- "origin/main unknown revision": run `git fetch origin` first; check if the default branch is `master`.
- `<` operator error: remove the angle brackets.
- Maven compile errors: paste the output; Claude's sandbox can't compile (Maven Central blocked); see compile-error history above.

---

# Handoff - v1.17.1 Live location on PC (2026-10-29)
- **PR title:** `v1.17.1 - PC: blue dot follows your location live`. Version in `config.properties`, `pom.xml` (versionCode 72), CHANGES.md.
- `Locator`: `startLive()` / `stopLive()` keep one PowerShell (`WIN_LIVE_SCRIPT`, GeoCoordinateWatcher) printing a fix every 3 s; started after the first successful `desktopFix`; stopped in `stop()` and `setManual()`; shutdown hook destroys it. Not compiled/run by Claude.

---

# Handoff - v1.17.0 Profile setup + region map start (2026-10-29)
- **PR title:** `v1.17.0 - First sign-up profile setup (username, picture, number, age, sex, region) + map opens on region`. Version in `config.properties`, `pom.xml` (versionCode 71), CHANGES.md.
- New: `ProfileForm` (shared fields + save), `ProfileSetupView` (shown once from `Main.firstProfileSetup` after `RoleView`), `util/Regions`, migration `009_profile_setup.sql`. `PersonalInfoView` now wraps `ProfileForm`. `Profile` record has `username, sex, age, region` (old 8-arg constructor kept). `Ui.avatar(name, size, url)` + `Ui.setAvatarImage`. `MapPane.openOnRegion()` called in `HomeView`, `ExploreView`, `DashboardView`. Not compiled/run by Claude.
- Run 009 in Supabase and redeploy `delete-account`.

---

# Handoff - v1.16.13 Owner locate button like student (2026-10-27)
- **PR title:** `v1.16.13 - Owner maps start location like student Home (locate button + blue dot)`. Version in `config.properties`, `pom.xml` (versionCode 70), CHANGES.md.
- `DashboardView` and `ListingFormView` now call `Locator.get().startWithPrompt()` after creating their `MapPane` (only student `HomeView.startGps()` did before, so owner maps had no GPS fix). Not compiled/run by Claude.

---

# Handoff - v1.16.12 Locate button reliability (2026-10-27)
- **PR title:** `v1.16.12 - Fix round locate button sometimes not responding (touch slop, late fix, stale timers)`. Version in `config.properties`, `pom.xml` (versionCode 69), CHANGES.md.
- `MapPane`: locate fires on `MOUSE_RELEASED` within 14 px slack (no ACTION handler); `MOUSE_DRAGGED` filter ignores drags that start on the button; busy pulse limited to 12 cycles then `endBusy()`. `Locator`: `start()`/`browserFix()` stop the previous `timeout`; `locate()` keeps only the newest waiting callback; `fail()` no longer clears `waiting` (a late fix still runs it; `stop()` clears). Not compiled/run by Claude.

---

# Handoff - v1.16.11 Locate button smooth (2026-10-27)
- **PR title:** `v1.16.11 - Smooth round locate button (no camera/dot fight, press + busy feedback)`. Version in `config.properties`, `pom.xml` (versionCode 68), CHANGES.md.
- `MapPane`: `meT` listener (follow mode) skips `setCenterNow` while `cam` is RUNNING (it fought `flyTo` every frame). New `tapLocate()` / `startBusy()` / `endBusy()`: ignores taps while a fix is pending, pulses the icon until `Locator.status` leaves REQUESTING (also stopped when the map leaves the scene). `bofo.css`: `.map-fab:pressed` scale 0.9. Not compiled/run by Claude.

---

# Handoff - v1.16.10 Dashboard loading skeleton (2026-10-27)
- **PR title:** `v1.16.10 - Owner Dashboard loading skeleton (stats, map, listing cards)`. Version in `config.properties`, `pom.xml` (versionCode 67), CHANGES.md.
- `Skeleton.dashboard()` (new; `statRow` / `plainCard` helpers extracted, one pulse for the group) is the skeleton for the `Ui.load` in `DashboardView` (was `Skeleton.stats(3)`). `Ui.load` clears it on success and on error, so the map and list sections still appear after it as before. Not compiled/run by Claude.

---

# Handoff - v1.16.9 Narrow-screen UI fixes (2026-10-27)
- **PR title:** `v1.16.9 - Fix clipped text on Profile and My listings (320 px); compact header + action buttons`. Version in `config.properties`, `pom.xml` (versionCode 66), CHANGES.md.
- Cause: at 320 px the rows had a stretchy spacer + `HBox` gap 14 (4 gaps), so titles got ~90 px and wrapped mid-word / ellipsized. `Ui.settingsRow` now has no spacer and shows the value as a `settings-sub` line (`settings-ok` green for "Verified"); gaps 12, hairline inset 64 (also `ProfileView.addRow`). `Ui.pageHeader`: title has `Hgrow`, header buttons keep their pref width. `ListingsView`: round `hdr-btn-cta` "+" instead of the pill, `btn-sm` action buttons (min width = pref), card + actions in one `VBox`, `.heart-fab` hidden. New CSS block at the end of `bofo.css`. `ProfileView` bottom padding 110. Parse-checked with javac only; not run on screen.

---

# Handoff - v1.17.2 PC Wi-Fi location (2026-10-29)
- **PR title:** `v1.17.2 - PC location from nearby Wi-Fi (like Google Maps)`. Version in `config.properties`, `pom.xml` (versionCode 73), CHANGES.md.
- New `util/WifiLocator` (netsh scan -> Geolocation API, Google if `geolocation.key` else beacondb). `Locator.desktopFix` tries it first, `wifiSource` selects the 15 s `startWifiLive` loop instead of the PowerShell watcher. Network and Windows scan not tested by Claude; not compiled/run.

---

# Handoff - v1.17.3 Pinned spot no longer persists (2026-10-29)
- **PR title:** `v1.17.3 - PC: pinned spot is session-only; log location source`. Version in `config.properties`, `pom.xml` (versionCode 74), CHANGES.md.
- `Locator`: `manual` no longer loaded/saved (`location.properties`), cleared in `setEnabled(true)`; `src` field + `[location]` println in `fix()`. Not compiled/run by Claude.
