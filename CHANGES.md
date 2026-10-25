# v1.12.6 - Better sitting and sleeping poses

- Sitting and sleeping no longer squash the body into a flat slab. BOFO keeps its normal body and face, sits a little lower with the feet stretched out in front, and the arms rest at the sides. Sleeping: eyes shut, small breathing mouth, head slowly drooping to the side with the hat tipping, z z z floating up.

# v1.12.5 - Poses change with a hop instead of a fade

- Switching idle poses (or going back to normal after a tap) is now an animation: BOFO crouches, hops up, changes pose in the air, lands with a squash and settles (about 0.6 s). Replaces the fade of v1.12.4.

# v1.12.4 - Smooth change between idle poses

- Switching from one idle pose to the next (or back to normal after a tap) no longer snaps: the old pose keeps moving while it fades out (0.13 s), then the new pose fades in (0.2 s). A tap or question during a fade still wins at once.

# v1.12.3 - Fix: effects of one idle pose stayed on screen in the next

- The z z z (sleeping) and speed lines (running) were left on the mascot when the pose ended and showed up in the other poses. They are now cleared whenever a pose ends or a new one starts.

# v1.12.2 - Idle poses redrawn; the floating head no longer disturbs the UI

- **Real poses, not squashed copies:** Thinking = one hand on the chin (tapping) and a foot tapping. Sitting = plops down, body low, feet out in front swinging, arms resting, blinks and glances. Running = leans forward, arms pump, feet stride, speed lines. Sleeping = slumped sit, eyes shut, mouth breathing, head nods with the hat drooping, z z z.
- **Fixed:** the head used to resize and drift while animating (floating z's, the running circle, tilt changed the button size and bumped things around). The mascot now sits in a fixed 46 px box inside a fixed 56 px button, and running happens in place.

# v1.12.1 - Idle moods start sooner and are easier to see

- The chat head now starts a mood after 4-10 s of quiet (was 8-18 s) and rests 4-10 s between moods.
- Bigger movement on the small 46 px head: wider head tilt and eye movement when thinking, deeper sit with feet spread, bigger running circle, deeper sleep and larger z's.

# v1.12.0 - The floating BOFO head has 4 idle moods

- After 8-18 seconds of quiet the chat head plays one mood, then rests and picks another (never the same one twice in a row): **Thinking** (head tilts, eyes look around, thought bubble floats), **Sitting down** (sinks and spreads its feet, breathes, blinks), **Running in a circle** (happy face, runs a lap every 1.8 s, leans into the turn, feet take turns), **Sleeping** (eyes closed, head nodding, z z z floating up).
- A tap, a drag, opening the chat or asking BOFO something ends the mood at once. Nothing plays while the chat is open, while BOFO is answering, or when the mascot is hidden in Settings.

# v1.11.0 - Privacy Policy, Terms of Service and Agreements open inside the app

- Profile > Security and privacy: **Privacy Policy**, **Terms of Service** and **Agreements and Guidelines** now slide up as a reader over the app (no browser, works offline). Close with the X, the Close button, a tap outside, or Esc.
- The two links on the login screen (Terms of Service, Privacy Policy) open the same reader.
- The text is a draft written for how BOFO really works (Google sign-in, location, owner ID + selfie, chats, PayMongo boosts, Data Privacy Act 2012). Have a Philippine lawyer review it before public release.

# v1.9.13 - Chats offline: read every chat, write messages that send later

- **Read offline:** the newest messages of your 20 latest chats are now saved on the device automatically whenever the chat list loads (before, only chats you had opened). A chat with nothing saved yet no longer shows a dead "No internet" screen: you see a short note and the message box still works.
- **Write offline:** every message is saved in a queue on the device first. With no internet it shows as a faded bubble "Waiting for internet - sends automatically" and is sent in order by itself when the connection returns, even if you closed the app in between. Messages sent online show "Sending..." and turn normal as before.
- If the server refuses a queued message (for example you are blocked) the bubble says "Not sent (tap to remove)" instead of retrying forever.
- The saved copy of an open chat is refreshed after new or sent messages, so reopening it offline shows the latest.

# v1.9.12 - Always open the app when signed in, even with no internet

- Fixed: a signed-in user who started the app with no internet (and nothing saved yet) still got a dead "No internet connection" screen. Now the app always opens after a 4 s wait: with the saved profile if there is one, otherwise with a basic profile from the Google account (student view until the real role can be loaded). The offline banner shows and every section says "No internet connection" until the network is back.
- Only a user who never signed in on this device still needs internet (to sign in).

# v1.9.11 - Works without internet (saved chats, saved places, listings) + fewer false "no internet" screens

- **Opens offline:** the signed-in profile is now kept on the device. Starting the app with no internet (or a server that answers slower than 4 s) opens it straight into Home / Dashboard with the "No internet connection - showing saved data" banner instead of a dead "No internet" screen. Only the very first sign-in on a device needs internet.
- **Saved data on the device:** chat list, the newest messages of every chat you opened, the Saved tab, listing lists, each place you opened, and the campus list are stored and shown when offline. Older messages, sending, saving or removing a place, reviews and sign-in still say "No internet connection" when you try them.
- **Back online:** screens that showed saved data reload by themselves when the connection returns.
- **False "no internet":** a read request now retries once before the app flips to offline (one dropped connection no longer kills the whole app); connect timeout 8 s -> 12 s.
- **Real reason on error screens:** a small grey line under "No internet connection" / "Something went wrong" names the actual error (for example `UnknownHostException: xxx.supabase.co`), so a failure while you ARE online can be told apart (wrong URL, paused Supabase project, DNS, firewall).
- Sign-out / session expiry wipes the saved copies.

# v1.9.10 - Loading screen: hat pops off and pops back on

- The hat now has a proper toss: it sits on the head while the body launches, **pops off** just after the top of the jump (stretches upward, tilts 9 degrees, a white ring and five sparks burst from the head), hovers, falls back stretched, then **pops on** at the landing (squashes onto the head, rebounds, second burst) and settles. The second jump tilts the hat the other way.
- Hat tilt now pivots on the brim centre and the squash pivots on the underside of the brim, so it presses onto the head instead of wobbling around its own middle.
- The loop is 2.4 s now (two jumps + a short rest); the mascot recovers from the landing squash and crouches again so the restart is smooth.
- New `Mascot.fx()` overlay group (art-grid units) used for the sparks.

# v1.9.9 - Loading screen: hat no longer looks like a flying plate, visible shadow, livelier dots

- Fixed: when the hat came off it lifted far above the head and tilted 14 degrees, so the wide brim read as a flat tilted plate. It now lifts a short way (20 / 26 units) and tilts only 5 degrees (the second jump tilts the other way), so it still reads as a hat.
- Fixed: the ground shadow was black on the dark background and could not be seen. It is now soft light purple, a little wider, so the mascot looks grounded.
- The mascot is a bit bigger (172 px) with more room above it for the jump; the title, subtitle and status text are larger and brighter.
- The three loading dots now rise and fade in a wave (bigger, 10 px apart) and stop when the screen is removed (before they kept running).

# v1.9.8 - Loading screen: two jumps per cycle

- The mascot now does two jumps in a row before the loop restarts (about 2.2 s). The second jump is a bit higher and the hat is tossed higher; in both, the hat comes off at the top and lands back on with the feet. The shadow still touches the feet on landing.

# v1.9.7 - Loading screen: shadow under the feet, hat drops off at the top of the jump

- Fixed: the shadow did not touch the feet when the mascot landed. The feet now stand on a ground line with the shadow centred under them, and the squash / stretch pivots on the soles so the feet stay on the ground.
- New: at the top of the jump the hat comes off, floats and tilts, then drops back onto the head exactly as the feet land (with a small press).

# v1.9.6 - Owner chat shows the student's name

- Owner side: the chat header now shows the student's name instead of the boarding house name. Students still see the boarding house name. The Chats list is unchanged.

# v1.9.5 - New listing form redesign

- The form is now seven numbered cards (Basics, Pin location, Monthly payment, Rooms, Amenities, Photos, Contact), each with a short hint.
- Rooms: beds and "Occupied now" use clean - / + steppers; occupants can never be higher than beds; extra rooms can be removed.
- Price: "Per bed / Per room" is now a switch under the price field.
- Pin: the status under the map turns green once the pin is set (with distance from the nearest campus).
- Photos: added photos show as small previews with an x to remove them; the label says "No photos yet", "1 photo", "2 photos".
- The Publish button and a "x of 6 required details done" line stay at the bottom while you scroll; errors show in a red box above it.
- Removed the emoji from the buttons (they showed as black glyphs on Windows).

# v1.9.4 - Neater map credit

- The credit under the map ("OpenFreeMap (c) OpenMapTiles, data from OpenStreetMap") is now a small rounded pill with a soft border and shadow, in the app's purple-grey text colour, instead of a plain grey strip.
- It is wrapped so it never runs under the "find me" button on narrow phones, and sits slightly inside the map corner.
- The credit text itself is unchanged and still visible (required by the OpenStreetMap and OpenMapTiles licences). Edit its look in `bofo.css` (`.map-attr`).

# v1.9.3 - Home map overview + clearer map

- Home opens zoomed out showing every boarding house and your location; the location button switches it off (go to me) and back on, and is highlighted while it is on. Searching or choosing a campus turns it off.
- Clearer markers: purple dots, white numbered bubbles for groups, a red pin for the selected boarding house, orange campus dots.
- Faster map: tiles are drawn one at a time, no buildings or alleys, fewer outlines; smoother zoom when tapping a boarding house.

# v1.9.2 - Directions: BH â†’ Campus fix and new look

- Fixed: tapping "BH â†’ Campus" (or any tab) left the previous tab's line on the map while it said "Finding the best wayâ€¦". The old line is cleared at once and the new trip is drawn straight away (a guide line), then replaced by the real route.
- New look: From/To card with dots, a big summary (time, distance, fare), tidy notes with small symbols, softer buttons, and loading placeholders instead of plain text.

# v1.9.1 - Directions tabs

- The three tabs now read Me â†’ BH, Me â†’ Campus and BH â†’ Campus (current location to the boarding house, current location to the campus, boarding house to the campus). The labels fit on one line.

# v1.9.0 - Directions work offline, local routes, options on the map card

- Jeepney lines are saved on your phone after the first time, so directions still show them without internet (the card says when they come from an older copy).
- Local lines: the team can add jeepney / multicab routes by hand (with a fare someone checked) for places OpenStreetMap does not cover. See docs/LOCAL_TRANSIT.md.
- Tapping a pin on the Home or Explore map now shows the best way to get there (walk, tricycle, jeepney or bus/van) with time and fare, not just the walk.
- Directions with no internet are ready in about 3 seconds instead of 9.

# v1.8.0 - Better directions

- The Directions card now fits the screen (the tabs and buttons no longer run off the right edge).
- Shows the real ways to get there with time and fare: walk, tricycle, jeepney (from public jeepney route data) or bus/van when it is far.
- No more silly times like "1266 min walk": long times read "21 h 6 min", and a place far away is a bus/van trip, not a walk.
- Tells you when your location comes from a computer (it can be off by several km) and can show the route from the campus instead.

# v1.7.0 - New boarding house page

- Redesigned page: photo hero, price and status at the top, quick facts, and separate cards for amenities, rooms, location and reviews.
- Removed the Call button; only "Chat with owner" is left.

# v1.6.3 - Boarding house page opens again

- A place now opens even if its amenities cannot be loaded.

# v1.6.2 - Smarter amenity search, fixed search dropdown and List/Map switch

- Typing in the amenity filter suggests matching amenities (also similar words and typos), only ones a boarding house really has; clear message when none does.
- Campus dropdown on Explore/Home floats under the search bar and no longer gets cut off.
- List | Map switch no longer turns into "..." buttons.

# v1.6.0 - Amenities, one search bar on Home, new Explore filter

- New shared amenity catalog (20 starters + anything owners add). Owners pick or create amenities when adding a listing; students filter by them in Explore (popular chips + search any amenity). Cards, map card and detail page show them.
- Home has a single search bar (campus / name / area) instead of two.
- Explore: nicer search bar, quick-filter row, bottom-sheet filter (price, sort, amenities). Search matches name or address.
- Database: run `supabase/migrations/005_amenities.sql`.

# v1.2.1 - Sharp vector map (OpenFreeMap), own style (not run on screen yet)

- The map is now drawn inside the app from OpenFreeMap vector tiles (free, no key, commercial use allowed): `util/VectorTileRetriever` + `util/Mvt`. Lavender land, soft green parks, light-blue water, light buildings, white roads with a thin outline (main roads tinted brand purple). No place names, no shop icons. Rendered at 512 px per tile, so lines and edges stay crisp on phones.
- `map.renderer=vector|raster` in `config.properties` switches back to the old picture tiles; a tile that cannot be loaded falls back to the old tiles by itself. Works offline from the 30-day tile cache.
- Attribution under the map: "OpenFreeMap (c) OpenMapTiles, data from OpenStreetMap" (follows the renderer).
- No map API key is needed any more for the default map.

# v1.2 - Map style, "find me" location, in-app Google sign-in (not compiled: no JavaFX/Maven/Android SDK in the editing environment)

- **Plain map**: `util/MinimalTileRetriever.java` (registered in `META-INF/services/com.gluonhq.maps.tile.TileRetriever`) replaces the default OpenStreetMap tiles with CARTO "light, no labels": roads, water, parks and building shapes only; no place names, no shop / bus-stop icons. Tile URL and key are in `config.properties` (`map.tiles.url`, `map.tiles.key`). Tiles are cached on disk for 30 days and still used offline. Without a key (or if the server refuses it) it falls back to plain OpenStreetMap tiles. Free CARTO keys are for non-commercial use: switch `map.tiles.url` to a plan you may use commercially before selling Boost plans.
- **Location**: `util/Locator.java` asks for location permission when Home opens and keeps the newest fix in `State.gps`. Maps show a blue "you are here" dot and a round find-me button (bottom right). Home has a "Near me" chip (sorts by where you are instead of a college) and a hint with "Try again" when location is off or blocked. PC testing: `dev.location=10.3157,123.8854` in `config.local.properties`.
- **Google sign-in**: Android shows Google's account sheet inside the app (`service/NativeGoogle.java`, `src/android/java/.../GoogleSignInActivity.java`, `AuthService.exchangeIdToken`). Needs the setup in `docs/NATIVE_GOOGLE_SIGNIN.md`; until `google.webClientId` is set nothing changes. The PC still uses the browser (Google does not allow sign-in inside an app window), now with a Cancel button, a nicer "you're signed in" page and the app window returns to the front.

# Redesign v4 - flat, professional UI (not compiled: no JavaFX/Maven in the editing environment)

- `bofo.css` rewritten as one design system: radius scale (chips 999, buttons/inputs 12, cards 14), 1px lines instead of drop shadows, neutral grey-white canvas with purple as the single brand colour, 12/14/16/20/26 type scale.
- Native controls now styled: CheckBox, Slider, ComboBox (+ popup), ScrollBar, Tooltip, dialogs (`Ui.style(dialog)`).
- `Shell.java`: floating pill nav replaced by a full-width bottom tab bar (icon + label, tinted active tab). Content no longer sits under the bar.
- `BhCard.java`: sentence-case badges, "No reviews yet" instead of empty stars, house icon on photo placeholders.
- Back/more buttons share the `.icon-btn` class (no inline styles). Bottom paddings reduced to match the new tab bar.

# BOFO Finder â€” Redesign Changelog (purple / professional)

> Note: these changes were written without compiling (no Java/Maven in the editing environment). Run the app once and use the troubleshooting guide below if anything misbehaves.

## What I did

**1. Theme: new professional purple (`bofo.css`, fully rewritten)**
- New palette: primary `#5B34B8`, dark `#3F2285`, tint `#F0EBFA`, background `#FAF9FC`, border `#E7E2F1`.
- Removed the amber accent. Purple is now the only brand color; gold `-bofo-gold` is used for rating stars only.
- Flatter look: cards use a 1px border instead of drop shadows; radii tightened (buttons/inputs 10, cards 12).
- Tags/badges are small rounded labels; hover states added on buttons, cards and the Google button.
- Floating mascot button is now white with a thin border and a soft purple shadow.
- All old variable names are kept (`-bofo-accent` now equals primary), so no screen breaks.

**2. Loading screen: white animated mascot (`SplashView.java`, rewritten; `Mascot.java` extended)**
- Flat deep-purple background with a WHITE BOFO mascot that jumps in a loop (squash, stretch, airtime, landing) with a ground shadow that shrinks and fades.
- "BOFO" wordmark, subtitle, three pulsing dots and a status line.
- Built from JavaFX shapes and a `Timeline` (no image files). Stops animating when the screen is removed.
- `Mascot` has a new constructor: `new Mascot(Expression.HAPPY, 150, true)` gives the white version (white body, deep-purple face). The old 2-argument constructor still gives the purple one.
- Splash minimum display time raised from 1.2 s to 2.2 s (`Main.java`) so the jump is actually seen.

**3. Smaller polish**
- `Nav.java`: every real screen fades in (260 ms); toast styled via CSS class.
- `Shell.java`: offline banner restyled (lavender, no amber).
- `BhCard.java`: emojis replaced with clean text tags (Water, Power, Wi-Fi, FEATURED, HOT, VERIFIED OWNER).
- `BofoPanel.java`: emojis removed from chat greeting text.
- `MapPane.java` and `VerificationView.java`: purple colors updated to the new palette; route line is dark purple.
- Status bar color (`Main.java`) set to `#3F2285` to match the splash.

## How to access
- Desktop: `mvn javafx:run` â€” the splash appears first, then login (or home if a session exists).
- Android: `mvn -Pandroid gluonfx:build gluonfx:package` then `gluonfx:install gluonfx:nativerun`.
- Edit colors in one place: `src/main/resources/com/bofo/finder/bofo.css` (top `.root` block).
- Preview the splash alone: in `Main.start`, temporarily skip the `Async.run(...)` call and just keep `Nav.show(splash, false)`.

## If a problem occurs (quick fixes)
| Problem | Fix |
|---|---|
| Build error in `Mascot.java` ("variable might not have been initialized") | Make sure both branches of the `if (white)` block in the constructor assign all 7 colors. |
| `Mascot.circle(...)` error | The method now takes 4 arguments (`cx, cy, r, color`). Update any other call you added. |
| Splash is not purple / unstyled | Confirm `bofo.css` is on the classpath (`Main` adds it) and the root node has style class `splash`. Run `mvn clean javafx:run`. |
| Mascot does not jump | The animation starts when the splash joins a Scene. Check `Nav.show(splash, false)` is called after `stage.show()`. |
| Old amber / old colors still visible | Run `mvn clean` (stale resources), then search for hard-coded colors: `grep -rn "#FFB400\|#6C3FC5" src`. |
| Emojis/tags look cramped on a card | Adjust `.tag` padding or font size in `bofo.css`. |
| Splash feels too long or short | Change `2200` in `Main.java` (milliseconds). |
| Want the old look back | Restore `bofo.css`, `SplashView.java` from your git history (`git checkout -- <file>`). |

## Update: developer force-login
- `LoginView.java`: added "Developer: force login" with Student / Owner / Admin buttons. They skip Google sign-in and open the app with a fake profile.
- `AuthService.java`: added `devForce(Role)` which sets a local fake profile (no real session; Supabase calls use the anon key, so data protected by RLS may appear empty).
- Remove before release: delete the `devLbl`/`devRow` lines in `LoginView` and the `devForce` method in `AuthService`.

## Update: inside screens redesigned (user-friendly)
**Shared building blocks (`Ui.java`, `bofo.css`)**: friendly empty states (mascot + one sentence), section titles, card helper, stat cards, round initials avatars, on/off filter chips, sticky action bar, notice banners (ok / warning / problem). Bottom navigation now shows a purple indicator on the active tab.

**Screens**
- `HomeView`: purple greeting header with a one-tap college selector (replaces "Change college" button and the bell), map as a rounded block, radius slider with a clear label, tabs renamed New / Hot / Best rated, "Nearby boarding houses" heading, friendly empty state.
- `ExploreView`: search + tappable filter chips (Free water / electricity / Wi-Fi) instead of checkboxes, max price and sort on one row, empty state "No matches".
- `SavedView`, `ChatsView`: empty states; chat rows now have an avatar, owner name, listing name, last message, time and a purple unread count.
- `BhDetailView`: tags for inclusions and verified owner, "Rooms" and "Reviews" sections, and a sticky bottom bar with Call and Chat with owner always visible.
- `DashboardView`: three stat cards (Listings, Beds available, Avg rating) and a colored status banner.
- `ProfileView`: header with avatar, name and role; settings grouped into cards (Edit profile, Preferences, Account, Legal, Danger zone); Log out as its own button.
- All remaining emojis in these screens removed.

**If a problem occurs**
| Problem | Fix |
|---|---|
| Build error mentioning `Ui.Chip`, `Ui.emptyState`, `Ui.avatar`, `Ui.card` or `Ui.section` | `Ui.java` was not copied. Copy the new `Ui.java` and rebuild. |
| Layout looks unstyled | Copy the new `bofo.css` and press F5 in the app (or restart). |
| Want one old screen back | Copy it from the `_backup_before_inside` folder the update script creates. |

## Update: soft rounded redesign (inspired by the reference screens)
- **Floating icon navigation**: pill-shaped bar with vector icons; active tab is a filled purple circle; unread badge on Chats. New `Icons.java` (SVG icons, no images).
- **Login**: soft circle logo, "Welcome to BOFO", full-width purple pill Google button, "Developer: force login" row, terms links.
- **Home**: avatar + "Welcome back" + bell, rounded search bar (opens Explore), college chip, rounded map, radius card, big photo listing cards.
- **Listing cards (`BhCard`)**: large rounded photo, heart button on the photo, "N beds left" pill, name + price, distance/walk time, rating, tags.
- **Explore**: pill search with search button, filter chips (price, water, electricity, Wi-Fi), result count and sort.
- **Profile**: identity card plus grouped cards with icon rows and chevrons (Switch mode, Switch Google account, Terms, Privacy, Agreements, Log out).
- **Global style**: light top bars with round back buttons, 22px card radius, pill buttons and chips, very soft shadows. Emojis removed from remaining screens.

| Problem | Fix |
|---|---|
| Build error about `Icons` | `Icons.java` is new: make sure it was copied to `src/main/java/com/bofo/finder/ui/`. |
| Nav icons missing or squashed | Check `Icons.java` copied and run `mvn clean javafx:run`. |
| Listing photo not filling the card | Photos load online; blank until the image arrives. A lavender placeholder shows meanwhile. |
| Map corners not rounded | The map is clipped in `HomeView` (`mapWrap`). Remove the `setClip` line if the map renders oddly. |

## Bottom dock redesign (matches the reference picture)
- `Shell.java`: the dock is now one dark shape - pill + curved neck + round button (drawn as an SVG path, so it stays crisp at any size). Admin/Student have the pill only.
- Icons are outline when idle; the active icon is filled purple with a dot under it (`DockIcon.java`, outline paths in `Icons.java`).
- Pressing a tab: the dot glides over (and stretches slightly) and a circle pulse grows out of that tab. The round button gives the same pulse.
- Student: removed the round "+" (it only jumped to Explore, which has its own tab). Owner keeps it (add listing). To bring it back: `setAction(() -> select(1));` in `StudentShell`.

## Theme v5 "Dock" (app theme redesigned around the new navigation)
- `bofo.css`: palette taken from the dock - indigo `#5B3BF0` for actions/active state, ink `#17142A` (the dock colour) for selected chips, strong buttons, tooltips, toast and splash. Page background is a cool near-white `#F5F6FC`.
- Same shape language as the dock: buttons, chips, search and tooltips are pills; cards are 20px, inputs 16px, notices 16px. Top bar has no strip or line any more.
- Cards stay flat on purpose (smooth scrolling on phones); shadows only on floating things (dock, mascot, toast, primary button, popups).
- Hard-coded colours in `Ui`, `HomeView`, `MapPane`, `VerificationView`, `LoginView`, `BhCard`, `Mascot`, `Main` (status bar) updated to match. All colours still live in the `.root` block of the CSS.

## Responsive layout (phones, tablets, landscape)
- `Nav`: the app column is at most 840 px wide and centred; wider screens keep the page colour on the sides. Splash stays full-bleed.
- `Shell`: the dock re-fits to the screen width (tabs shrink on small phones, never overflow; stay compact on tablets). Tab buttons are sized in code now.
- `CardGrid` (new): listing lists show 1 column on phones, 2 on tablets/landscape (Home, Explore, Saved).
- `BhCard` / `Ui.coverPhoto`: photos crop to fill instead of stretching; taller on wide cards.
- Forms (Profile, Add listing, Verification, Boost) are at most 560 px wide on tablets. Login scrolls on short screens and is at most 440 px wide.
- Fixed pixel widths removed from Explore filters, Verification/Queue photos, chat bubbles and the BOFO mascot panel (now relative to the screen).
- Desktop window is resizable (min 320x480). Preview any device: `mvn javafx:run -Djavafx.args="--size=820x1180"`
  (phone small 320x640, phone 390x844, tablet portrait 820x1180, tablet landscape 1280x800, phone landscape 800x360).

## Loading skeletons + "No internet connection"
- `Skeleton.java` (new): placeholders shaped like the real content (listing cards, chat/list rows, stat tiles, detail page, chat bubbles). They pulse while loading and stop when off screen.
- Used on: Home, Explore, Saved, Chats, Owner dashboard + listings, Admin queue/payments/reports, listing detail, owner verification, boost plans, chat history.
- `Ui.load(...)` is the standard flow (skeleton -> load -> render, or error state with retry; stale answers ignored). `Ui.errorState(...)` shows "No internet connection" (or "Something went wrong") with Try again, and reloads by itself when the connection returns.
- `Connectivity.java` (new): the HTTP layer (`SupabaseClient.send`) reports success/failure, so the banner "No internet connection - showing saved data" and the error screens switch on from any screen. While offline a tiny probe (every 4 s) detects when the network is back. `State.offline` now points at it.
- Launch with no internet and no saved account: full-screen "No internet connection" (Try again / auto-continue) instead of splash text.
- Connect timeout 15 s -> 8 s so a dead network is detected sooner.

## Photo loading, chat send failures, favorite failures
- Photos (listing cards, detail photo strip): grey pulsing backdrop while the picture downloads; plain tint + icon if there is no picture or it failed. A failed picture reloads by itself when the internet returns (`Ui.trackLoading`, `Ui.photoLoading`, auto-retry in `Ui.photo` / `Ui.coverPhoto`; broken cached images are no longer reused).
- Chat: a sent message appears at once as "Sending...", then becomes a normal bubble. If it fails it stays in the chat with a red "No internet - tap to retry" / "Not sent - tap to retry" (blocked users get a clearer line). Network failures resend by themselves when the connection is back. Message ids are tracked so nothing shows twice.
- Favorites: the heart flips instantly and flips back if saving fails, with a toast saying why (`BhCard.saveFavorite`). `BhCard` now takes `(on, undo)` instead of `on`.
- Other failed actions (review, start chat, report, profile save/delete, unlist/delete, refund, approve/reject) use `Ui.failToast`: "No internet connection..." when offline, the old message otherwise.

## UI rework: tab bar (video 2), Explore, Settings (GCash-style)
- Tab bar (`Shell`, `DockIcon`, CSS): replaced the dark pill + round button with a white rounded bar. Each tab = outline icon + label. A lavender highlight sits behind the active tab and stretches to the pressed tab (leading edge first, trailing edge follows) while icon and label fade from pale to strong. Tab width adapts to the screen (54-84 px).
- Owner tabs are now Dashboard | Listings | Create | Chats | Profile. "Create" opens the add-listing form (the old round "+"). Verification moved into Profile > Verification (with a back arrow).
- Explore (`ExploreView`): one search bar with a search icon (searches as you type, clear button), a filter button with a count badge, quick amenity chips, a collapsible filter panel (max price + sort, Reset), result count and a List | Map switch. The map only loads when opened, so the list gets the whole screen.
- Profile / Settings (`ProfileView`, `PersonalInfoView`): gradient header (avatar, name, role / verification chips), then grouped white cards of icon rows with chevrons: Account (Personal information, Verification for owners, Switch mode), Preferences (switches), Security and privacy, About; outlined Log out button and a quiet Delete account link. Personal information is its own sub-screen.
- New: `Glyphs` (outline icon paths), `Ui.settingsRow / settingsToggle / settingsGroup / switchControl / segmented / backBar`, `MapPane.fillAvailable`, `Shell.pendingTab` (return to the Profile tab after a sub-screen).
