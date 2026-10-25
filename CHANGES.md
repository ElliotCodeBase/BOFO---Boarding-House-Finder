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

# BOFO Finder — Redesign Changelog (purple / professional)

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
- Desktop: `mvn javafx:run` — the splash appears first, then login (or home if a session exists).
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
