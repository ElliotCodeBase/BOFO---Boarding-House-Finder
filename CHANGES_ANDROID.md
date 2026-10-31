# v2.0.75 - Android: nicer house dots + map credit as an (i) button
- **Fixed:** the house dots on the map were black. Cause: the colour was given to the map as a plain number (`Expression.literal(int)`), which MapLibre does not read as a colour; it now uses `Expression.color(...)`.
- **Now:** a house dot is indigo (dark indigo when selected) with a thick white ring and a soft indigo halo (new layer `places-dots-halo`). Taps are unchanged.
- **Map credit:** the "(c) OpenStreetMap contributors - OpenFreeMap" text shows for 4 s when the map appears, then folds into a small round (i); tap it to read the credit again (folds after 5 s). Softer white pill with a thin border (`bg_attribution`, new `bg_attribution_dot`). The licence text stays reachable.
- Files: `MapCardView.java` (`places-dots` / `places-dots-halo`, `setupAttribution`, `setAttribution`), `bg_attribution.xml`, `bg_attribution_dot.xml` (new), `view_map_card.xml`, `build.gradle` (2.0.75 / 275). Not built or run by Claude.

# v2.0.74 - Android: the path is always in view (pathfind fix)
- **Fixed:** after tapping a house the path (line, start / end dots) was not visible: only the "757 m - 9 min" chip showed and the map stayed on the zoomed-out overview. Cause: the "fit the path" move could be undone by a later overview / fit (first location fix, list reload), and the fit itself was silently skipped when its padding was bigger than the small map card.
- **Now:** while a path is shown, overview / fit / ring moves are ignored and only re-fit the path; the path padding is capped to 70 % of the card height and 20 % of the width, with fallbacks (plain padding, then centre + zoom 15) and a retry if the card is not laid out yet.
- Files: `MapCardView.java` (`fit`, `overview`, `glideToRing`, `fitRouteBounds`), `build.gradle` (2.0.74 / 274). Not built or run by Claude.

# v2.0.73 - Android: only the path stays on the map while a path is shown
- **Changed:** when you pathfind to a boarding house, every other circle disappears: the other houses (dots, pins, number bubbles and names), all campus dots and names, the chosen campus dot and the radius circle with its km chip. What stays: your dot, the path, its start / end dots and the "km - time" chip, and the house you are going to. Close the path (the X on the preview card) and everything comes back.
- Files: `MapCardView.java` (`routeShown`, `declutter`, `paintRing`, `paintCampusDot`, `isRouteTarget`, edits in `rebuildPlaces` / `rebuildColleges` / `setRoute` / `clearRoute`), `build.gradle` (2.0.73 / 273). Not built or run by Claude.

# v2.0.72 - Android: boarding house dot / pin no longer sits on the phone's dot
- **Fixed:** when a boarding house was near the user's own location, its circle (or pin / number bubble) was drawn on top of the phone's blue dot and its ripples.
- **Now:** a house whose marker would land within about 30 dp (dots), 22 dp (pins) or 34 dp (number bubbles) of the phone's dot is shown just outside that distance, straight away from the dot (straight down when exactly on it). Only the drawing moves; the house's real position, tap and route are unchanged. It follows the phone and the zoom.
- Files: `MapCardView.java` (`clearOfUser`, `rebuildPlaces`, `setUser`), `build.gradle` (2.0.72 / 272). Not built or run by Claude.

# v2.0.71 - Android: Home map stays exactly where it was after the place page
- **Fixed:** the first time you left a place page the map came back as it was, but later (no campus chosen, "Near me" / overview mode) it jumped to the zoomed-out "Finding your location" view. Cause: coming back, Home forgot it already had a location fix, so the first location update re-ran the overview; the list reload also re-ran overview / fit.
- **Now:** coming back from a place page Home keeps the saved camera, keeps the known location (blue dot shows at once) and skips the automatic overview / fit once. Picking a campus, radius, Near me or the locate button still moves the map as before.
- Files: `HomeFragment.java` (`viewShownBefore`, `keepCamera`, `haveFix` kept, `setUser` on return), `build.gradle` (2.0.71 / 271). Not built or run by Claude.

# v2.0.70 - Android: one bad house position no longer blanks the Home map
- **Fixed:** with some boarding houses the Home map still showed nothing. A house saved without a real position (0 / 0, open sea, or missing) was counted when the map fitted "all pins", so the camera zoomed out to include the sea.
- **Now:** `MapGeo.bounds` ignores houses whose position is empty or invalid (`MapGeo.validPoint`). The camera fits only the real ones.
- Files: `MapGeo.java`, `build.gradle` (2.0.70 / 270). Not built or run by Claude.
- To fix the data itself: in Supabase, `boarding_houses` rows with `lat = 0` or `lng = 0` need a real pin (owner can edit the place and move the pin).

# v2.0.69 - Android: Home map remembers where it was
- **Fixed:** after leaving a place page the Home map opened at the empty start view (lat 0 / lng 0, open sea) and showed nothing. The camera (centre + zoom) is now saved when the map stops moving and when the screen closes, and put back when Home opens the map again.
- Only Home uses it for now (`rememberCamera("home")`); other maps are unchanged. A new campus / radius / Near me still moves the camera as before.
- Files: `MapCardView.java` (`rememberCamera`, `saveCamera`, restore in `onMapReady`), `HomeFragment.java` (1 line), `build.gradle` (2.0.69 / 269). Not built or run by Claude.

# v2.0.68 - Android: blank Home map reloads itself (watchdog)
- **Fixed:** after leaving a place page the Home map could stay light blue (no land, water or roads). The map reported "fully drawn" even though its tile source had failed, so v2.0.67 never retried.
- **Now:** 6 s after the map style loads (and every time Home resumes) the map checks whether anything is actually drawn (land, water, roads, buildings). If nothing is, it loads the style again (up to 6 tries), which fetches the tiles again.
- Files: `MapCardView.java` (`blankCheck`, `scheduleBlankCheck`), `build.gradle` (2.0.68 / 268). Not built or run by Claude.

# v2.0.67 - Android: Home map refreshes by itself
- **Fixed:** the Home map could stay blank (light blue, no roads / pins) after coming back from a place page, on slow internet, or after the internet dropped and returned.
- **Now:** if the map did not finish drawing, it asks for the tiles again after 5 s (up to 6 tries, stops as soon as it is fully drawn); it also reloads at once when the internet comes back, and re-checks when you return to the screen. Pins, radius ring and route are redrawn; the camera does not jump.
- Files: `MapCardView.java` (new `reloadStyle` / `scheduleRefresh`, listeners in `activate`, small edits in `onResume` / `onDestroy`), `build.gradle` (2.0.67 / 267). Not built or run by Claude.

# v2.0.66 - Android: Home list refreshes after leaving the place page
- **Fixed:** after opening a boarding house (BH info page) and going back, the Home list under "Nearby boarding houses" stayed empty. Home now reloads the list by itself every time you come back to it (skeleton shows, then the cards). Also picks up new favourites, new listings and changed rent made while you were away.
- **Cause:** the tab Fragment is kept while its view is rebuilt; it remembered the last change tick (`lastChange`) and skipped the reload. `onDestroyView` now resets it to -1.
- Files: `HomeFragment.java` (one line in `onDestroyView`), `build.gradle` (2.0.66 / 266). Not built or run by Claude.

# v2.0.65 - Android: own amenity icons, cleaner place page
- **Amenity icons:** emoji replaced by the app's own line icons (water drop, bolt, Wi-Fi, snowflake, shower, pot, washing machine, P sign, camera, book, flame, fan, bed, closet, lock, people, moon, paw, venus / mars, TV, fridge, shield). Used on the place page, the owner's amenity picker and the Explore filter. Amenities with an unknown name (owner-made) get a sparkle icon.
- **Place page:** amenities are tiles with an icon bubble, House rules is a card with check marks, no repeated rating / "Free Wi-Fi" lines, more space between sections.
- Files: `AmenityIcons.java` (new), `Chips.java`, `BhDetailFragment.java`, `ListingFormFragment.java`, `FilterSheet.java`, `fragment_bh_detail.xml`, `bg_amenity_tile.xml` (new), 25 `ic_am_*.xml` (new), `build.gradle` (2.0.65 / 265). Not built or run by Claude.

# v2.0.64 - Android: house rules
- **Owners:** the Add / Edit place form has a new "House rules" section (last step with photos and amenities): type a rule, tap Add, remove with the cross. Up to 20 rules.
- **Students:** the place page shows a "House rules" list under Amenities (hidden when the owner added none).
- **Supabase:** run `supabase/migrations/011_rules.sql` once (new column `rules` on `boarding_houses`).
- Files: `011_rules.sql`, `BhService.java`, `ListingFormFragment.java`, `BhDetailFragment.java`, `fragment_listing_form.xml`, `fragment_bh_detail.xml`, `strings.xml`, `build.gradle` (2.0.64 / 264). Not built or run by Claude.

# v2.0.63 - Android: See-through path switch
- **Settings > Preferences > "See-through path":** when on, the route line on the map (white outline, purple line and the dashed estimate) is drawn 80% transparent, so you can see the road underneath and check the path follows it. Off by default; saved on the phone; the map updates when you return to it.
- Files: `AppPrefs.java`, `SettingsFragment.java`, `MapCardView.java`, `fragment_settings.xml`, `strings.xml`, `build.gradle` (2.0.63 / 263). Not built or run by Claude.

# v2.0.62 - Android: path follows the real road, refreshes by itself
- **More accurate path:** `Router` no longer thins the road to 400 evenly spaced points (that cut corners off the road). It now keeps every bend (Douglas-Peucker, 2 m tolerance, up to 1500 points). If the first server's road is more than 2.5x the straight distance (bad snap), the next server is asked too and the shorter real road wins. Third fallback for vehicle routes: the public OSRM demo.
- **Auto refresh:** on the map path, when it starts at your phone and you move 40 m, the road is redrawn from your new spot (at most every 8 s; the old road stays until the new one is ready, never swapped for a dashed guess; no camera jump). The Directions card re-asks every 15 s while any option is still a straight-line guess.
- Files: `Router.java`, `MapFragment.java`, `DirectionsCardView.java`, `build.gradle` (2.0.62 / 262). Not built or run by Claude (javac syntax check only).

# v2.0.61 - Android: map path refreshes by itself, cleaner map UI, readable Ask BOFO replies
- **Path on the map (Ask BOFO "show me the path"):** `MapFragment` now uses the road route for places farther than 3 km (it used the walking route, which gives up past 30 km, so a 165 km trip stayed a dotted "33 h walk" estimate forever). The straight guide line shows at once, then the real road path replaces it by itself: 5 quick retries, then one quiet try every 15 s, and again whenever you come back to the screen. Time is walking for close places, by road (about 30 km/h) for far ones. The label says "finding the road..." until the real path is drawn.
- **Map UI:** the campus button is a white pill with a campus icon (like the back button) instead of a wide lavender bar; the place preview card allows a 2-line name.
- **Ask BOFO replies:** a short headline plus a numbered list, one place per line (name - price/mo - distance), then a next-step question; distances over 3 km say "19.6 km away" (no more "33 h walk"); compare and details replies are bullet lists; the offline fallback uses the same layout. Chat card is bigger (up to 400 x 620 dp), bubbles have more line spacing.
- Files: `MapFragment.java`, `fragment_map.xml`, `view_map_card.xml`, `styles.xml`, `strings_bofo.xml`, `item_msg_theirs.xml`, `BofoChatOverlay.java`, `BofoIntentLocal.java`, `bofo-chat/index.ts` (already deployed to the live project as version 17; "under 5k" now means 5,000), `build.gradle` (2.0.61 / 261). Not built or run by Claude.

# v2.0.60 - Android: Ask BOFO chat redesign (listing cards, compare table, starter chips, typing dots)
- **Listing cards:** the results are now cards in a row you swipe sideways (one result = one full-width card). Each card has the photo with a rank badge (Nearest / Cheapest / Top rated on the first card, then #2, #3) and a green "Verified" badge, the name, a big price, pills for distance + walk time, rating (amber) and free beds, the free water / power / Wi-Fi line, the address, and **Map** and **Route** buttons that go straight to the map / the path. Tap the card = open the place.
- **Compare table:** tinted header with the place names (tap a name = open the place), columns share the width of the card, alternate row shading, the best cell of each row in green with a check mark, and a line "Most wins: <place> (4 of 8 rows)" when one place clearly leads.
- **Chat:** header has a subtitle ("Finds, compares and maps boarding houses"); an empty chat shows 4 starter chips (Nearest boarding house, Cheapest with wifi, Best rated, List all BH); the typing bubble is three moving dots; bubbles, cards, table and chips fade / slide in.
- Files: `BofoChatOverlay.java`, `item_bofo_result.xml` (rewritten), `item_bofo_compare.xml`, `view_bofo_chat.xml`, `strings_bofo.xml` (+15 strings), new drawables `bg_bofo_result_card`, `bg_bofo_pill`, `bg_bofo_pill_green`, `bg_bofo_pill_amber`, `bg_bofo_badge`, `bg_bofo_verified`, `bg_bofo_btn`, `bg_bofo_table_head`, `bg_bofo_best_green`, `bg_bofo_row_alt`, `build.gradle` (2.0.60 / 260). No server change: no redeploy needed. `ItemBofoResultBinding` lost the `meta` field (nothing else used it). Not built or run by Claude (XML checked to be well-formed, the Java file passes a javac parse: only missing-Android-symbol errors).

# v2.0.59 - Android: Ask BOFO understands "show available BH in Dumaguete and compare"
- Cause (screenshot): the word "compare" made the rules pick the compare intent, which needs two place names, so the answer was "I need two place names to compare" even though the message also asked for a search.
- `bofo-chat/index.ts` (**redeploy**): a compare with no names and no earlier results now searches first (up to 3 available places, nearest / best first) and then compares them in the table. If fewer than 2 places match, the old "I need two place names" help shows.
- "in Dumaguete" / "sa Dumaguete" is understood as an area: only places with that word in the name or address are listed (ignored when nothing matches, so it never empties the answer). "near Silliman and compare" no longer reads "Silliman and compare" as the place name.
- Includes the v2.0.58 fix (reasoning leak). Files: `supabase/functions/bofo-chat/index.ts` (**redeploy**), `build.gradle` (2.0.59 / 259). Not built or run by Claude (the new word rules were tested with Node on 8 sample messages; the Supabase query part was not run).

# v2.0.58 - Android: Ask BOFO no longer shows the AI's thinking in the chat
- Cause (seen in a screenshot after "List all BH"): the free model is a reasoning model and returned its own thinking ("The user wants to list all boarding houses. I have 5 results. I need to present them...") as the answer. The reply was cut at the token limit, so it ended mid-sentence.
- `bofo-chat/index.ts` (**redeploy**): the request now asks OpenRouter to leave the reasoning out; `<think>` blocks are stripped; an answer that looks like thinking (starts with "The user wants / I need to / I must ..." or repeats the prompt rules) is thrown away, the next model is tried, and then the code template answers (real listings, correct names and prices). The wording prompt also says "output only the final reply". The bad answer is logged in Supabase > Edge Functions > Logs.
- "List all BH" (also "lahat", "tanan") now returns up to 5 places instead of 3.
- Files: `supabase/functions/bofo-chat/index.ts` (**redeploy**), `build.gradle` (2.0.58 / 258). Not built or run by Claude (the leak check was tested on the screenshot text and 4 normal replies with Node).

# v2.0.57 - Android: Ask BOFO answers much faster
- Cause: every question made TWO model calls one after the other (understand, then word the answer), each up to 12 s per model and up to every model in `LLM_MODELS`; the chat also waited up to 20 s for the phone position even with a college chosen.
- `bofo-chat/index.ts` (**redeploy**): the rules answer most questions with no model call at all (the model is only asked when the rules cannot place the message); the answer is worded by the code template (set secret `LLM_WORDING=1` to let the model word every answer again); at most 2 models, 5-6 s each instead of 12 s.
- `BofoChatOverlay`: no phone-location wait when a college is chosen.

# v2.0.56 - Android: bigger circles on the map
- `MapCardView`: all circles are 1.5x bigger (one constant `BIG`): the number bubbles (clusters) and their numbers, the place dots, the campus / college dots, your blue location dot, the price glow and the route start / end dots. Pins are 1.25x bigger and the tap area is wider to match. Change `BIG` to make them bigger or smaller. Not built or run by Claude.

# v2.0.55 - Android: fix "Suggest BH here" finding nothing
- Cause (seen in the screen recording): the AI model put words like "BH here" into the search text, and the server then required that text inside every listing name / address, so nothing matched. Fix in `bofo-chat/index.ts` (**redeploy**): generic words (bh, boarding house, here, suggest ...) are dropped from the text filter; if a text filter still finds nothing it is ignored; no "See in Explore" chip when there are no results.

# v2.0.54 - Android: Ask BOFO knows the chosen college; "show me the path" draws on the map
- **College scope:** the chosen college now beats the phone position (before, the phone position was sent every time and won, so the answers ignored the college). With a college chosen, nearest / cheapest / best rated / search / availability only list boarding houses inside the app radius around it. Nothing inside = a clear message (bigger radius or another campus), not a far-away place. "near me" / "from me" in the message still measures from the phone; a named place ("near Silliman") still wins.
- **Suggest here:** "suggest / recommend a boarding house here", "irekomenda dito" are understood as a search around the chosen college.
- **Path on the map:** "show me the path / way / route to X", "directions" (also Tagalog / Hiligaynon) now open the main map with only that place and the walking path drawn (straight guide line first, then the real road, with a "1.7 km · 21 min" label). Starts at the chosen college, or at the phone when you say "from me" or no college is chosen. The chat goes there by itself 1.2 s after the answer. The Directions chip does the same; tapping a result card or Open still opens the place screen.
- Files: `supabase/functions/bofo-chat/index.ts` (**redeploy**), `MapFragment` (`openRoute`), `ShellFragment`, `BofoChatOverlay`, `BofoIntentLocal` (offline answers follow the same rules), `strings_bofo.xml`, `build.gradle` (2.0.54 / 254). Not built, deployed or run by Claude.

# v2.0.52 - Android: Ask BOFO answers with real listings (nearest, cheapest, compare, map)
- Ask BOFO is now a real assistant. The Edge Function `bofo-chat` (rewritten, Step 1) understands the question with a free OpenRouter model, then the CODE runs the query on `bh_cards` (distance, sort, filters, compare) and the model only words the answer. Answers carry real listings, a compare table and actions.
- App: result cards in the chat (tap = open place), compare table (best cell highlighted, scrolls sideways), chips (Show on map / Directions / Open / Compare / See in Explore + suggested questions), typing bubble, in-memory history (last 6 turns) so "which of those is cheapest?" works. Offline or function down: rule-based answers from the loaded list (`BofoIntentLocal`), then the old FAQ. 429 shows a friendly wait message.
- Map: `MapFragment.open(from, String[] ids)` shows only those pins, fitted, first one selected.
- New: `BofoAnswer`, `BofoIntentLocal`, `item_bofo_result/compare/chips.xml`, `bg_bofo_result/best.xml`. Changed: `BofoAiService`, `BofoChatOverlay`, `ShellFragment`, `MapFragment`, `strings.xml`, `strings_bofo.xml`, `build.gradle` (2.0.52 / 252), `supabase/functions/bofo-chat/index.ts`. Not built by Claude.

# v2.0.51 - Android: Ask BOFO chat header uses the JavaFX mascot animation
- `view_bofo_chat.xml` header `MascotView` -> `BofoMascot`; `BofoChatOverlay` plays the THINKING pose while waiting, `endIdle()` on reply. Only these 2 files + build.gradle. Not built by Claude.

# v2.0.50 - Android port: the Ask BOFO button uses the JavaFX mascot animation (JavaFX app stays v1.20.0)
- New `BofoMascot` = a line-by-line port of the JavaFX `Mascot.java`: same shapes on the 200 grid, same four poses (**thinking**: hand on chin, foot tap, eyes look around, bubble; **sitting**: plops down, feet swing, blinks; **running**: leans, arms pump, feet stride, speed lines; **sleeping**: slumped, nodding, hat droops, z z z) and the same **squash-and-hop** between poses. Same numbers and easing curves as the JavaFX Timelines.
- `FloatingBofoView` now works like `BofoPanel`: white round 56 dp button with a thin border, mascot in a fixed 46 dp box, plain 1.2 s bob (no tilt), first pose after 4-10 s, never the same pose twice, lengths 7 s / 8 s / 5.4 s / 12-16 s, tap or drag ends it. The old wiggle / hop / flip / dash moves and the "Zz" text are gone.
- Changed: `FloatingBofoView.java`, `fragment_shell.xml` (button 52 -> 56 dp), `build.gradle` (2.0.50 / 250). New: `BofoMascot.java`, `bg_bofo_fab.xml`. The chat header still uses `MascotView` (unchanged). Not built by Claude (frames were rendered on a desktop JVM to check the drawing).

# v2.0.49 - Android port: better offline strip on Home (JavaFX app stays v1.20.0)
- The dark offline strip now has a small wifi-off icon, the text "You're offline. Showing saved places." and a **Try again** button on the right (reloads the list). Slightly taller (40 dp) so it no longer sits tight on the greeting.
- Changed: `fragment_home.xml`, `strings_home.xml`, `HomeFragment.java`, `build.gradle` (2.0.49 / 249). Not built by Claude.

# v2.0.48 - Android port: no more blank Home when the network is slow or gone (JavaFX app stays v1.20.0)
- Home / Explore / Saved: the skeleton cards stay up while loading, and after 5 s a "Slow network. Still loading..." note appears over them. No network and no saved copy now shows a wifi-off icon, "No internet connection", one line of help and Try again (before: a long "No connection to the server..." line, or nothing). It reloads by itself when the connection comes back.
- Home map: a small note in the middle of the map says "Map can't load without internet" or "Slow network. The map may take a while...".
- `list_offline` reworded everywhere (toasts and messages): "No internet connection. Check your Wi-Fi or mobile data and try again."
- Changed: `ListArea.java`, `HomeFragment.java`, `ExploreFragment.java`, `SavedFragment.java`, `view_list_area.xml`, `fragment_home.xml`, `strings.xml`, `build.gradle` (2.0.48 / 248). New: `strings_net.xml`, `ic_wifi_off.xml`, `bg_map_note.xml`. Not built by Claude.

# v2.0.47 - Android port: Directions card refreshes by itself (JavaFX app stays v1.20.0)
- Place > Directions: if the route cannot be worked out (weak signal, slow server) the card now keeps loading and **tries again by itself** (up to 5 times, 2 s, 4 s, 6 s...) instead of stopping at an empty box with a Retry button. The Retry button only shows after the 5th failure.
- Changed: `DirectionsCardView.java`, `build.gradle` (2.0.47 / 247). Not built by Claude.

# v2.0.46 - Android port: locate button zooms out fast (JavaFX app stays v1.20.0)
- Dashboard map: the round locate button is now a toggle. First tap = fly to you (uses the saved position at once), second tap = zoom back out to all your places. Before, there was no quick way back out.
- Map camera jumps (`moveTo`) now take a fixed 0.5 s instead of the slow default fly time (also used by Home and the Create map).
- Changed: `MapCardView.java`, `DashboardFragment.java`, `build.gradle` (2.0.46 / 246). Not built by Claude.

# v2.0.45 - Android port: nicer owner Dashboard (JavaFX app stays v1.20.0)
- Purple **hero card** (places, boosted, big total of views); stat tiles are now white rounded cards (before they were flat text, so they looked empty); **place cards** have the photo, name, price, an amber "Boosted" tag and chips (views, saved, chats, free beds, rating); amber verification banner; more space at the bottom so the floating tab bar never covers the last card.
- Changed: `DashboardFragment.java`, `fragment_dashboard.xml`, `strings.xml`, `build.gradle` (2.0.45 / 245); new drawables `bg_dash_card`, `bg_dash_hero`, `bg_dash_chip`, `bg_dash_chip_amber`, `bg_dash_chip_green`. Not built by Claude.

# v2.0.44 - Android port: owner Create wizard like the JavaFX one (update 2 of 2; JavaFX app stays v1.20.0)
- **Create / Edit place is now 4 steps** with a segment bar, "STEP n OF 4", and a bottom bar that always shows the errors (with **Copy error details**), "n of 6 required details done" with a progress bar, and Back / Next / **Publish listing**:
  1. **Place & pin:** name, address, a **map with a fixed pin** (move the map, tap **Set pin at map center**), the **walking route and distance from the nearest campus** (e.g. "1.2 km from NORSU (about 15 min walk)"), the map flies to your phone on open.
  2. **Price & rooms:** price, per bed / per room, rooms with **- / +** steppers (Beds, Occupied now; occupied can never pass beds), remove room.
  3. **Amenities & photos:** **Camera** and Gallery buttons, **thumbnails with a small x to remove** a photo, **amenity picker** (search box, popular chips, selected chips you can remove, "Show all", and **Add "text"** to create your own, a leading emoji becomes its icon). The old Free water / electricity / Wi-Fi tick boxes are now amenities (columns stay in sync).
  4. **Contact & review:** phone, Facebook, e-mail, and a review card of what students will see.
- Edit mode uses the same wizard: pin, rooms, contact and **amenities** are filled from the place; saving now also replaces the amenities (only when the catalog was read, so they are never wiped by accident).
- Replaced: typed latitude / longitude + "Use my location" button. The old validation (`BhService.validate`) is unchanged, so the same rules apply.
- Needs the amenities tables: applied to your Supabase in update 1 (005 + 006). Adding your own amenity needs a verified owner (database function).
- Changed: `ListingFormFragment.java` (rewritten), `fragment_listing_form.xml` (rewritten), `MapCardView.java` (+`center()`), `BhService.java` (`Draft.syncAmenities`, amenities saved on edit), `strings.xml`, new `drawable/ic_pin_center.xml`, `build.gradle` (2.0.44 / 244). Not built in Android Studio by Claude (syntax-checked only).

# v2.0.43 - Android port: owner Dashboard like the JavaFX one (update 1 of 2; JavaFX app stays v1.20.0)
- **Dashboard** now has: the verification banner (unverified / pending / rejected, tap = Verification screen), the existing numbers (kept), **"Your places on the map"** (tap a pin = route from the nearest campus with distance and time; locate button), and one tappable card per place (opens the place). Pull down to refresh.
- **Supabase (done with your permission):** migrations `005_amenities` and `006_admin_can_publish` were NOT applied on the live project; they are now (tables `amenities`, `bh_amenities`, view `amenities_v`, function `get_or_create_amenity`, `bh_cards` with amenity columns, 20 starter amenities). Needed by update 2 (amenity picker).
- Changed: `DashboardFragment.java`, `strings.xml` (+ `dash_map_title`), `build.gradle` (2.0.43 / 243). Not built in Android Studio by Claude.

# v2.0.42 - Android port: Student / Owner / Admin force-login buttons are back (JavaFX app stays v1.20.0)
- The login screen has a "Developer: test accounts" row with **Student / Owner / Admin** buttons, like the JavaFX app. Each one does a real Supabase password sign-in (genuine session, so the posted boarding houses, favorites, chat, listings and admin queues all work) and then goes through the normal routing.
- The accounts are the same `dev.student|owner|admin.email` / `.password` keys of the git-ignored `src/main/resources/com/bofo/finder/config.local.properties` that the JavaFX buttons use: nothing to retype. `app/build.gradle` reads that file and puts the values into the **debug** build only; a release APK has them empty, so the buttons are hidden and no test password is inside the APK.
- Changed: `build.gradle` (2.0.42 / 242, dev BuildConfig fields), `AppConfig`, `AuthService.devSignIn`, `LoginFragment`, `fragment_login.xml`, `strings.xml`. Not built or run by Claude.

---

# v2.0.41 - Android port: OpenGL ES map engine (JavaFX app stays v1.20.0)
- MapLibre 13 draws with Vulkan by default (new, some open issues). The app now uses the OpenGL ES build `org.maplibre.gl:android-sdk-opengl:13.0.0` (same API, no code change). Pan / zoom was still choppy on a realme 14 Pro+ after v2.0.37-40.
- Changed: `build.gradle` (2.0.41 / 241). Not built or run by Claude.

---

# v2.0.40 - Android port, lag test switch (JavaFX app stays v1.20.0)
- New `map.mode` in `assets/config.properties`: `vector` (normal), `raster` (one layer of ready-made picture tiles, like the JavaFX app; OpenStreetMap tiles, for TESTING only) or `off` (no map). Used to find whether the lag comes from live vector drawing or from the rest of Home.
- Changed: `MapCardView`, `assets/config.properties`, `build.gradle` (2.0.40 / 240). Not built or run by Claude.

---

# v2.0.39 - Android port, lag fix 5: 120 Hz screens (JavaFX app stays v1.20.0)
- The map is capped at 60 fps (`setMaximumFps(60)`) and the ripple overlay repaints at ~30 fps. A 120 Hz phone (realme 14 Pro+, Snapdragon 7s Gen 3 / Adreno 710) would otherwise draw the map up to 120 times a second while panning.
- Changed: `MapCardView`, `RadarOverlayView`, `build.gradle` (2.0.39 / 239). Not built or run by Claude.

---

# v2.0.38 - Android port, lag fix 4: rounded-corner mask (JavaFX app stays v1.20.0)
- `CornerMaskView` drew one huge inverse-fill shape over the whole map card, and the GPU replayed it every frame once the ripple overlay animated. It now draws four tiny corner pieces. `RadarOverlayView` also repaints only the square around the dot.
- Changed: `CornerMaskView`, `RadarOverlayView`, `build.gradle` (2.0.38 / 238). Not built or run by Claude.

---

# v2.0.37 - Android port, lag fix 3: the map is idle between gestures (JavaFX app stays v1.20.0)
- Cause: the radar ripples were two map layers whose paint properties changed every frame (plus the pop / found ring, 60 per second). Each change makes MapLibre re-render the WHOLE map, so the map never rested, even when nobody touched it.
- Fix (hybrid): the ripples and the "found you" ring are now drawn by the new `RadarOverlayView` (plain Canvas above the map, only a few circles). The dot itself stays a map layer, so it is always on its exact spot; the ripples hide while the camera moves and snap back when the map is idle.
- A pan no longer rebuilds pins / clusters / labels / campus dots (only a zoom, a data change or a selection does). Same houses / campuses again = no redraw. A new or cleared route only updates the route layers.
- First-fix pop repaints the dot at ~30 fps instead of every frame.
- Changed: `MapCardView`, new `RadarOverlayView`, `build.gradle` (2.0.37 / 237, plus a commented OpenGL ES line for an A/B test). Not built or run by Claude.

---

# v2.0.35 - Android port, lag fix 2 (JavaFX app stays v1.20.0)
- Pin / cluster / label rebuild now runs once, 160 ms after the map stops (it ran on every idle and copied every label bitmap from the map each time).
- Lighter drawing: map pixel ratio 1.75 (was 2), max zoom 16 (was 17), buildings only from zoom 15, minor roads from zoom 14, no blur on the location-dot shadow.
- Changed: `MapCardView`, `assets/bofo-map-style.json`, `build.gradle` (2.0.35 / 235). Pins and labels look about 12% smaller on 3x phones. Not built or run by Claude.

# v2.0.34 - Android port, lag fix (JavaFX app stays v1.20.0)
- Cause found in the screen recording (about 17 unique frames/s): the radar ripples redrew the whole map ~30 times a second forever, and 5-6 layer properties each frame.
- Now: ripples run at ~12 fps, update only the 2 ripple layers, and pause while the map is panning/zooming/gliding or the page is scrolling. Changed: `MapCardView`, `build.gradle` (2.0.34 / 234). Not built or run by Claude.

# v2.0.33 - Android port, Step 11: final pass (JavaFX app stays v1.20.0)
- Deleted the old Ask-BOFO bottom sheet (`BofoPanelSheet.java`, `sheet_bofo.xml`; replaced by the floating button + chat overlay in v2.0.21).
- Removed 23 unused strings (old filter sheet, List/Map toggle, price range, old chat/detail text). Checked every layout and drawable: all still used.
- No visual change. Not built or run by Claude. Profile > About shows 2.0.33.

# v2.0.32 - Android port, Step 10: Detail, Chat, Chats (JavaFX app stays v1.20.0)
- **Chats list:** "Chats" + subtitle "Press and hold a chat (or right-click) to delete it"; six pulsing skeleton rows while loading (the blank screen is gone); white rows with a lavender initials avatar, name, time, purple house name, last message; empty state mascot + "No chats yet" + "Open a place and tap Chat with owner."; list has bottom space for the floating tab bar.
- **Chat:** header = back, initials avatar, name, purple "Boarding house owner"; empty state = mascot, "Say hello", "Ask the owner about price, rooms or a visit." and two quick-reply chips ("Is it still available?", "Can I visit this week?": tap sends); composer "Write a message..." with a grey send button that turns purple when there is text; "Today" / "Yesterday" / "Oct 28" chip above each day of messages. Bubbles were already purple with time and tick. Chat logic, polling and outbox untouched.
- **Detail:** pinned title bar "Boarding house" (back + save heart), photo area with "No photos yet", bold name, "P1,550 / month - per bed", chips (Verified owner, "6 beds available", "50.8 km away" only when a distance is known), three tiles (Beds free, Rooms, Rating), amenities, contact, directions card, reviews; "Chat with owner" is a purple pill pinned at the bottom.
- New: `strings_chats.xml`, `strings_chat.xml`, `strings_detail.xml`, `item_chat_skeleton.xml`, drawables `bg_chat_row`, `bg_avatar_tint`, `bg_skeleton_circle`, `bg_day_chip`, `bg_send_off`, `bg_quick_reply`. Changed: `ChatsFragment`, `ChatFragment`, `BhDetailFragment`, `ChatRules` (+ `dayLabel`, test added), `fragment_chats`, `item_conversation`, `fragment_chat`, `item_msg_mine`, `item_msg_theirs`, `fragment_bh_detail`, `build.gradle` (2.0.32 / 232).
- Not built or run by Claude (XML parse-checked only). Profile > About shows 2.0.32.

# v2.0.31 - Android port, Step 9: Explore restyle + Filters sheet (JavaFX app stays v1.20.0)
- **Explore:** "Explore" + subtitle "Find a boarding house near your campus", one white search pill (clear X) and a square filter button with a count badge (ink fill while filters are on), result count and a **List | Map** control. Map shows the results on the Home map card (campus dots, tap a pin then the card opens the place, tap a campus dot = choose it); it only starts when first opened. The New / Hot / Best tabs are gone from Explore (sorting moved into the sheet).
- **Filters sheet:** "Filters" + close; "Maximum price per month" (Any, 1,000, 1,500, 2,000, 3,000 chips + your own number), "Sort by" (Nearest / Lowest price / Most beds), "Amenities" (popular chips + search box with typo matching). Reset only clears the sheet; nothing changes until "Show results". The old min-max range slider is gone (only a maximum now).
- **Amenities could not be loaded:** the error text is gone. If the catalog (`amenities_v`, from `supabase/migrations/005_amenities.sql`) cannot be read, a built-in list (Free Wi-Fi, Free water, Free electricity, Aircon, Own bathroom, Kitchen, Laundry, Parking) is shown and matched by name on the loaded cards (`ExploreRules.has`). No new migration.
- New: `util/ExploreRules` (+ `ExploreRulesTest`), `styles_explore.xml`, `strings_explore.xml`, drawables `bg_ex_*`, `ic_filter`, `ic_x_dark`. Changed: `ExploreFragment`, `FilterSheet`, `fragment_explore.xml`, `sheet_filter.xml`, `BrowseState` (`sort`, `amenityNames`), `build.gradle` (2.0.31 / 231).
- Not built or run by Claude (Android SDK missing).

# v2.0.30 - Android port, Step 8: route on pin select (JavaFX app stays v1.20.0)
- Tapping a pin on Home now also draws the route: start = the chosen campus, else the campus nearest to that house, else your position. Purple line, blue start dot, **red end dot**, a "1.7 km - 21 min" chip above the end (80 m/min on foot up to 3 km, 30 km/h by road beyond).
- An instant straight-line estimate (or a saved road) is drawn first, then swapped for the real road route (`Router.walk` / `Router.drive`); if only a straight line came back it quietly retries up to 4 times (1.5 s, 3 s, ... apart).
- The camera glides to fit the whole route and leaves room for the preview card at the bottom (and the radius pill at the top). Closing the card, tapping empty map, or the house leaving the list removes the route.
- The red end dot also shows on the place screen's Directions map. The Directions card itself (From / To dots, lavender result tile, note, "Show BH -> Campus", "Open in OpenStreetMap", "More info") already matched the spec, so it is unchanged; the route logic (`TripPlanner`, `Router`, `TransitRoutes`) is untouched.
- Changed: `MapCardView` (`placeSelected`, `setRouteLabel`, `fitRouteBounds`, red `route-end`), `HomeFragment` (`showRoute`, `fetchRoute`), `build.gradle` (2.0.30 / 230). No new files.
- Not built or run by Claude (parse-checked only). I could not compare against the JavaFX results on 2 samples without running them: please compare one near and one far house and tell me any difference. Profile > About shows 2.0.30.

# v2.0.29 - Android port, Step 7: chips, section header, rent card, list states (JavaFX app stays v1.20.0)
- **Chips:** New / Hot / Best rated as pills (selected = ink fill, white text) instead of the tab strip.
- **Section header:** bold "Nearby boarding houses" + subtitle: "Closest to you first - 7 places" (Near me), "Within 3.5 km of <campus> - 4 places" (follows the radius pill), "Choose a campus to see what is close" (nothing chosen).
- **Rent card:** white, 18 dp corners, #ECE8FA border. With a campus / Near me: Lowest / Middle (Best rated on that tab) / Highest, bold value + small "/mo", a thin line with one marker per price at its real position. Tap a value or a marker = that house's circle glows on the map and the value turns red; tap again = off. No campus: "Choose a campus to see rent near it" + purple bold "Choose" (opens the dropdown). Reuses `PriceRange`.
- **List:** cards unchanged (`item_bh_card`, 2 columns); batches of 15 with a full-width "Show more (n)" button; skeleton while loading; empty state with the mascot and "Nothing nearby yet".
- New: `RentCardView`, `bg_chip_on`, `bg_chip_off`, `item_show_more.xml`, `strings_home7.xml`. Changed: `HomeFragment`, `HomeSearchBar` (openDropdown), `ListArea` (footer), `fragment_home.xml` (tabs + price_range removed), `strings.xml` (home_empty text), `build.gradle` (2.0.29 / 229).
- Not built or run by Claude (parse-checked only). Profile > About shows 2.0.29.

# v2.0.28 - Android port, Step 6: pins, clusters, campus dots, circle label, preview card (JavaFX app stays v1.20.0)
- **Pins:** zoomed out (below 12.5) small purple dots; dots closer than 30 dp merge into numbered bubbles (tap = glide to a view of the whole group); zoomed in, full pins with the house name under them, labels that would collide are hidden (the selected one always shows). The selected pin grows and turns dark purple. Merging/unmerging is recomputed when the camera stops moving.
- **Campus dots:** every campus is a small orange dot (names from zoom 11); tap = choose that campus. The chosen campus keeps its bigger dot.
- **Radius circle:** "500 m" / "4.5 km" chip on its east edge.
- **Preview card:** house-icon square, name, bold purple "P1,550/mo", star + rating (+ distance), X closes, > opens the place. Tapping a pin glides the camera to it.
- **Price glow:** `MapCardView.setGlow(id)` lights one house's circle (pink #F43F5E); Step 7's rent card will call it.
- The map style has no fonts, so all map text (names, bubble numbers, edge label) is drawn as bitmaps and cached per text.
- Selecting a pin does NOT draw the route yet (Step 8). Red destination pin: Step 8.
- New: `bg_peek_icon`, `strings_pins.xml`. Changed: `MapCardView`, `HomeFragment` (campusTapped, setColleges), `view_map_card.xml`, `build.gradle` (2.0.28 / 228). Not built or run by Claude (parse-checked only). Profile > About shows 2.0.28.

# v2.0.27 - Android port, Step 5: radar dot, location states, overview (JavaFX app stays v1.20.0)
- **Radar dot (LiveDot):** blue (#2F6FED) core in a white ring with a soft shadow; TWO staggered ripple rings grow 0.5 -> 2.7x and fade linearly, looping (2.4 s, ~30 fps, paused when the map is off screen, in the background or with animations off); a soft view cone points the way you are moving (shown after the phone has moved ~8 m); the first fix grows in from 82 %; Locate landing = one bigger "found you" ring + a small pop.
- **Glass pill (top-left):** "Finding your location...", "Your location", "Approximate location" (only coarse permission), "Location is off". Hidden while the radius pill is shown (campus or Near me chosen). The Locate icon pulses while finding; extra taps are ignored.
- **Overview** (no campus / Near me and the search is empty): the map zooms out to fit every pin and you in ~90 % of the card; the Locate button is purple while it is on. Tap = fly to me (overview off); tap again = back to the overview. Clearing the search or the campus brings it back; your first fix joins the overview once.
- New: `bg_glass_pill`, `bg_map_button_on`, `strings_radar.xml`. Changed: `MapCardView`, `HomeFragment`, `HomeSearchBar` (searchChanged hook, isEmpty), `LocationHelper.precise()`, `view_map_card.xml`, `build.gradle` (2.0.27 / 227).
- Not built or run by Claude (parse-checked only). Profile > About shows 2.0.27.

# v2.0.26 - Android port, "Turn on location?" popup (JavaFX app stays v1.20.0)
- Tapping Locate or Near me while the phone's Location switch is off now shows Google's system popup ("For a better experience, turn on device location"); Yes switches it on and the position is read straight away, No shows the "location is turned off" message. The live dot never asks by itself.
- Needs Google Play services (phones and Play emulator images); without them the old message shows. Not built or run by Claude. Profile > About shows 2.0.26.

# v2.0.25 - Android port, current location fix (JavaFX app stays v1.20.0)
- If Google's fused location returns nothing (no Play services, emulator with no mock position, first fix slow), the app now falls back to Android's own LocationManager: last known fix from any provider, else one fresh fix (12 s). Failures are logged with tag `BOFO`.
- New toast when the phone's Location switch is off: "Location is turned off on your phone..." instead of the generic failure message.
- Not built or run by Claude. Profile > About shows 2.0.25.

# v2.0.24 - Android port, map speed: do what the JavaFX map does (JavaFX app stays v1.20.0)
- Why JavaFX is smooth: it paints each OpenFreeMap tile ONCE into a 512 px picture (only land, parks, water, buildings, roads; no labels, no blending), caches it, then pans/zooms just move pictures; zoomed out the pins are small dots, zoom 15+ reuses the zoom 14 tile, and camera moves avoid drawing every zoom level on the way. The Android map re-draws vector geometry every frame at full screen density.
- Now: map capped at 2x pixel density (3x phones drew 2.25x the pixels); style trimmed (no opacity blending, no minor-road outlines, no fade transition); zoom limited to 6-17 like JavaFX; tile prefetch delta 4 -> 1; zoomed out (below 12.5) pins are small dots, full pins only when zoomed in; pins/selection redraw only the pins layer; the map starts 300 ms after the page, not in the same frame as the list.
- Not built or run by Claude. Profile > About shows 2.0.24.

# v2.0.23 - Android port, map lag fix (JavaFX app stays v1.20.0)
- The Home map was slow: texture mode (added in v2.0.22 for the rounded corners) is back to the default fast surface; the rounded corners are drawn by a new `CornerMaskView` on top. Dragging the radius pill now only redraws the circle (not every layer) and moves the camera at most ~6 times a second, with one final move when you let go. Live location and campus dot also update only their own layer.
- Not built or run by Claude. Profile > About shows 2.0.23.

# v2.0.22 - Android port, revert Step 4: inline map card (JavaFX app stays v1.20.0)
- Home is now ONE scrolling page: offline strip, greeting, search, rounded map card, New / Hot / Best rated, rent line, title and the card grid all scroll together (CoordinatorLayout + AppBarLayout; the list recycles as before; pull-to-refresh only at the very top).
- Removed from Home: the List / Map toggle, the full-screen map button and the old radius slider row.
- Map card: 28 dp corners, thin outline, height 42% of the screen (200-440 dp), round white locate button bottom-right, attribution as a small pill bottom-left, radius pill top-centre (Radius, purple slider, chip like "1.0 km"; 0.5 to 5 km, step 0.5). Dragging the pill moves the circle and eases the zoom live; the list reloads 350 ms after it settles.
- The map no longer re-zooms when only the radius changes; it fits when the centre changes (first load, campus picked, Near me).
- MapView now uses texture mode so it clips to the rounded card and the search dropdown floats above it.
- Not built or run by Claude. Profile > About shows 2.0.22.

# v2.0.18 - Android port, login errors are now visible (JavaFX app stays v1.20.0)
- Google sign-in on a real phone returned to the login screen with no message: the error text sits below the buttons, hidden behind the keyboard. Now any login message closes the keyboard and scrolls to it, and failures are also written to Logcat (tag `BOFO`).
- No Supabase, library or other code change. Profile > About and Settings show 2.0.18.

# v2.0.17 - Android port, first successful build (JavaFX app stays v1.20.0)
- First time the Android code was compiled in Android Studio / Gradle: `testDebugUnitTest` passes (BUILD SUCCESSFUL).
- Fixed 3 build errors: duplicate string `cd_mascot` (second one renamed `cd_mascot_ask`, used by `MascotView`); style `Widget.Form.Input` had no parent and Android looked for `Widget.Form` (now `parent=""`); `LoginFragment` caught `GoogleIdTokenParsingException`, which the current Google ID library no longer throws (now `catch (Exception e)`).
- No feature, Supabase or library change. Profile > About and Settings show 2.0.17.

# v2.0.16 - Android port, Stage 9 (release prep; JavaFX app stays v1.20.0)
- Release builds now use R8 (code shrinking + obfuscation) and resource shrinking, with rules in `proguard-rules.pro` (readable crash lines, mascot animation, Fragments kept).
- Release signing from `bofo-android/keystore.properties` (git-ignored; template included) or from environment variables; without a key the release APK is built unsigned.
- New optional GitHub workflow `android-gradle.yml` (tests + debug APK on push; manual run builds the signed release APK + AAB). The old GluonFX workflow is untouched.
- New `bofo-android/docs/RELEASE.md`: how to make a key, build, add the release SHA-1 to Google sign-in, fix R8 problems, and what is still missing before a public release (Boost checkout function, `bofo-chat`, legal text review).
- No app code change, no Supabase change, no new library or test. Profile > About and Settings show 2.0.16. Not built in Android Studio / Gradle by Claude; the R8 rules and the workflow were never run.

# v2.0.15 - Android port, Stage 8h (admin shell; JavaFX app stays v1.20.0)
- Admins now get their own bottom bar: **Queue**, **Reports**, **Payments**, **Profile** (no Ask BOFO button). The role is read when you sign in.
- **Queue:** owner verification requests waiting for review, oldest first. Tap one: ID front, ID back and selfie (private bucket, links valid 2 minutes), then **Approve** (asks first) or **Reject** (a reason is required). The decision uses the existing admin-only `review_verification`. The photos are never kept in the app's image cache and the screen blocks screenshots while it is open.
- **Reports:** the latest 100 reports and blocks (who, about whom, reason, date). Tap one: details and **Take down a place** of the reported user (pick the place, type a reason; uses `admin_takedown`, which also writes the audit log). The database has no "resolved" state for reports, so none is shown.
- **Payments:** the latest 100 boost payments of all owners. Tap one for details; a **Paid** payment can be **Refunded** (reason required; `admin_refund_boost` marks it refunded and ends the boost). **The refund only changes your records: return the money in the PayMongo dashboard.**
- No Supabase change (read-only checks of `owner_verifications`, `blocks_reports`, `users`, `bh_cards`, the three admin functions and their RLS). 4 new unit tests (`AdminRulesTest`, total 122). No new libraries. Profile > About and Settings show 2.0.15. Not built in Android Studio / Gradle by Claude.

# v2.0.14 - Android port, Stage 8g (Boost + Payments; JavaFX app stays v1.20.0)
- **My places > Boost a place** (and **Boost this place** in a place's menu): choose one of your active places and a plan (Starter / Weekly / Monthly, read from `boost_plans`), tap **Pay with GCash**. The payment page opens in a Custom Tab; when you come back the app checks the payment until it says Paid / Failed / Expired (about 2 minutes, then **Check again**).
- **My places > Payments**: your last 30 boost payments (amount, status, place, date); tap one for the reference number.
- **Important - backend not ready:** the live Supabase project has NO `create-boost-checkout` Edge Function (only `delete-account`), so **Pay with GCash will say "Boost payments are not available yet" until that function is deployed** (with the PayMongo secret and a webhook that calls `activate_boost`). Nothing is charged in that case. Claude did not deploy it: it needs your PayMongo keys and cannot be tested from here.
- Safety: the payment link is opened only when it is a plain `https://` link with a real host. Prices come from the server (`boost_plans`), the app never sends an amount.
- New library: `androidx.browser:browser:1.8.0` (Custom Tabs). 6 new unit tests (`BoostRulesTest`, total 118). No Supabase change (read-only checks only). Profile > About and Settings show 2.0.14. Not built in Android Studio / Gradle by Claude.

# v2.0.13 - Android port, Stage 8f (owner dashboard; JavaFX app stays v1.20.0)
- **My places > Dashboard** button: totals over all your places (views, saves, chats, average rating, reviews, free beds) and one card per place, most viewed first, with a star on boosted places. Pull down to refresh.
- The numbers come from the `bh_activity` table (one row per place, kept by your database); boosted places from `boosts` (if that cannot be read the stars are just missing). The desktop `DashboardView` was not available, so the screen follows the database, not the desktop layout.
- 3 new unit tests (`DashboardRulesTest`, total 112). No new libraries. No Supabase change (read-only check of `bh_activity` columns and its read policy).
- Profile > About and Settings show 2.0.13. Not built in Android Studio / Gradle by Claude.

# v2.0.12 - Android port, Stage 8e (edit a place; JavaFX app stays v1.20.0)
- **My places > tap a place > Edit**: the Add-a-place form opens filled with that place (name, address, payment and mode, free water / electricity / Wi-Fi, rooms, contact, location). **Save changes** updates the listing, replaces the rooms and the contact, and adds any NEW photos after the existing ones (8 photos in total at most).
- Not in this version: removing or re-ordering photos, amenities (your Supabase project has no `amenities` / `bh_amenities` tables, so the amenity chips are hidden when editing; Add a place shows none either), the owner Dashboard (stats), map point picker.
- Saving needs a verified owner (database rule). If a save stops halfway the message says which step failed; press Save again (rooms are replaced, so run it to the end).
- No new libraries. No Supabase change (read-only check of RLS on `boarding_houses`, `rooms`, `bh_contacts`, `photos`: the owner may write them). No new unit tests. Profile > About and Settings show 2.0.12. Not built in Android Studio / Gradle by Claude.

# v2.0.11 - Android port, Stage 8d (owner verification; JavaFX app stays v1.20.0)
- **Verification screen** (My places > the banner's **Verification** button, or **Add a place** while not verified): shows where you stand (Not verified / Waiting for review / Verified / Not approved, with the admin's reason) and, when you may send a request, the form: type of ID, ID front, ID back (camera or gallery), a live selfie (camera only), a tick that you agree, **Send for review**.
- Photos are shrunk to 1600 px JPEG on the phone and sent to the private `id-verification` bucket (JPEG, 5 MB; checked in your Supabase). A quick blur check only warns "looks blurry"; it never blocks. After sending, the photos are wiped from memory and the camera copies from the cache.
- **My places** now shows a banner while you are not verified; **Add a place** sends an unverified owner to the Verification screen (a waiting request just says to wait). If the status cannot be read (offline), Add a place still opens and the database decides.
- `VerificationService.submit` now names and types each upload from its bytes (`.jpg` / `image/jpeg`, PNG still works). New `util/VerificationRules` (pure).
- 4 new unit tests (`VerificationRulesTest`, total 109). No new libraries. No Supabase change (read-only check: bucket, RLS policies, `owner_verifications`, `review_verification`; the admin side comes with the admin shell).
- Profile > About and Settings show 2.0.11. Not built in Android Studio / Gradle by Claude.

# v2.0.10 - Android port, Stage 8c (add a place; JavaFX app stays v1.20.0)
- **My places > Add a place**: form with name, address, monthly payment (per bed or per room), rooms (beds and taken), free water / electricity / Wi-Fi, amenities, contact (phone, Facebook, e-mail), location and up to 8 photos. **Publish** checks everything first and lists what is missing; if saving fails halfway the half-made place is removed.
- Location: type latitude / longitude or tap **Use my location** while standing at the place (a map picker comes later). Photos are shrunk to 1600 px JPEG on the phone and uploaded as JPEG (the bucket allows PNG and JPEG, 5 MB each; checked in your Supabase).
- Publishing is refused by the database for owners who are not verified (Verification screen comes later), the form shows the reason.
- 3 new unit tests (`ListingRulesTest`, total 105). No new libraries. No Supabase change. Editing an existing place is not ported yet.
- Profile > About and Settings show 2.0.10. Not built in Android Studio / Gradle by Claude.

# v2.0.9 - Android port, Stage 8b (owner tabs + My places; JavaFX app stays v1.20.0)
- Owners now get their own bottom bar: **My places**, **Chats**, **Profile** (the student tabs are only for students). The role is read when you sign in.
- **My places**: your listings with their status (Active / Unlisted / Taken down). Tap a place: View, Unlist (hide from students) / List again, Delete (asks first). A place taken down by an admin cannot be re-listed by the owner.
- Not yet: creating / editing a place, dashboard, boost, payments, verification, admin screens (8c and later).
- Checked in your Supabase project (read-only): `boarding_houses.status` allows `active`, `unlisted`, `taken_down`; `inbox_v` columns match the chat code. No Supabase change, no new library, no new unit tests.
- Profile > About and Settings show 2.0.9. Not built in Android Studio / Gradle by Claude.

# v2.0.8 - Android port, Stage 8a (BOFO assistant + mascot; JavaFX app stays v1.20.0)
- A small BOFO mascot button floats above the bottom bar on every tab. Tap it: **Ask BOFO** sheet. Your question goes to the `bofo-chat` Edge Function; if you are offline or it refuses, the built-in FAQ answers. The mascot tilts its head while thinking. The talk is not saved.
- `MascotView` (moods: sitting, thinking, running in circles, sleeping; only thinking / sitting are used so far).
- Not done yet: the search filters the assistant can return (`Answer.filters`) are ignored; owner / admin shells are still next (8b).
- No new libraries. No Supabase change. Profile > About and Settings show 2.0.8. Not built in Android Studio / Gradle by Claude; no new unit tests (UI only).

# v2.0.7 - Android port, Stage 7 (chat; JavaFX app stays v1.20.0)
- **Chats** tab (was a placeholder): your conversations, newest first, with the other person's name, the place, the last message, the time and an unread badge. Pull down to refresh, tap to open, long-press to delete a chat from your list.
- **Chat screen**: bubbles (yours on the right), one tick = sent, two blue ticks = read, 30 messages at a time (older ones load when you scroll to the top), long-press a message to copy it or delete it (for me, or for everyone if it is yours). New messages arrive by polling: every 3 s, slower when quiet (never more than 12 s), and only while the chat is on screen.
- **Offline-safe sending**: every message is saved on the phone first (Outbox) and shows a clock; it is sent by itself when the connection returns, even if you left the chat or closed the app. A message the server refuses stays on screen in red: tap it to send again.
- Place screen: **Chat with owner** now works (opens or creates the one conversation for you + this place). On your own place the button is off.
- 9 new unit tests (102 in total). No new libraries. No Supabase change.
- Profile > About and Settings show 2.0.7. Not opened or built in Android Studio / Gradle by Claude.

# v2.0.6 - Android port, Stage 6 (directions + trip planning; JavaFX app stays v1.20.0)
- Place screen: new **Directions** card. Tabs Me -> BH, BH -> Campus, Me -> Campus; a small map with the route line (dashed when it is only an estimate); the ways to go (walk, tricycle, jeepney lines, bus or van when far) with time and fare, the best one marked; More info; Open in OpenStreetMap.
- Trip planner ported from the desktop app (same fares: LTFRB order of 19 March 2026, same limits): roads from OSRM then Valhalla, jeepney lines from OpenStreetMap (Overpass, saved on the phone for 14 days, an old copy is used offline), hand-made lines from `assets/local-transit.json` (empty for now). Never waits more than 12 seconds: then it shows an estimate.
- Location is never asked when the screen opens: if it was already allowed the card follows you (plans again after about 80 m), otherwise the **Use my location** button asks.
- Fixed a Stage 5 compile error in `MapCardView` (view binding inflate call).
- 23 new unit tests (93 in total), including the desktop `TripLogicCheck` as JUnit. No new libraries. No Supabase change.
- Profile > About and Settings show 2.0.6. Not opened or built in Android Studio / Gradle by Claude.

# v2.0.5 - Android port, Stage 5 (map + location; JavaFX app stays v1.20.0)
- Map (MapLibre Native, OpenFreeMap vector tiles, BOFO colours, no labels / POI icons): purple pins (amber star for featured places), dashed radius ring, campus dot, live "you are here" dot, tap a pin for a peek card (price, rating, distance) and View.
- Home: List / Map toggle. The map shows the same places as the list (New / Hot / Best tabs, campus, radius) and has a locate button and an expand button.
- Full-screen map (`MapFragment`): campus picker, radius slider, locate button. Opened from Home (expand) and from a place's detail screen (**Show on map**: centres and selects that place).
- Location (`LocationHelper`, Play Services): permission asked only when you tap Near me or the locate button; honours Settings > Use my location (off = nothing is requested). New **Near me** chip on Home measures distance and radius from your phone instead of a campus.
- Map attribution always visible; MapLibre tile cache capped at 50 MB.
- New libraries: `org.maplibre.gl:android-sdk` 13.0.0, `play-services-location` 21.3.0. 7 new unit tests (`MapGeoTest`). No Supabase change.
- Profile > About and Settings show 2.0.5. Not opened or built in Android Studio / Gradle by Claude.

# v2.0.4 - Android port, Stage 4 (Home, Explore, Saved, place detail; JavaFX app stays v1.20.0)
- Home: campus button + nearest-college chips, New / Hot / Best rated tabs, Lowest / Middle / Highest rent row, radius slider around the chosen campus, card grid with skeleton loading, pull to refresh and an offline banner.
- Explore: search by name or area, Filters sheet (monthly price range + amenities with typo-tolerant search), sort tabs. Saved: the hearted places (works offline from the last copy). Heart on every card.
- Place detail: photo pager, price, rating, beds, free utilities, amenities, call / Facebook / e-mail buttons, reviews. "Chat with owner" is visible but disabled until Stage 7.
- Campus picker sheet saves the choice in `users.default_college_id`. Back from a place returns to the same tab.
- New libraries: recyclerview, swiperefreshlayout, viewpager2. 13 new unit tests (rent range, peso text, price filter, campus order, card texts). No Supabase change.
- Profile > About and Settings show 2.0.4. Not opened or built in Android Studio / Gradle by Claude.

# v2.0.3 - Android port, Stage 3 (login, roles, profile; JavaFX app stays v1.20.0)
- Sign-in: Google (native Credential Manager sheet, browser fallback through `bofo://auth-callback` with PKCE), e-mail code, e-mail + password. Session is restored on launch (splash decides login / role / profile setup / app) and refreshed automatically.
- Role chooser (student / owner) with agreement tick (saved in `user_agreements`), profile form with camera / gallery photo, Profile tab account card, Settings (edit profile, switch student / owner, location switch, terms / privacy / owner agreement, sign out, delete account).
- 9 new unit tests (sign-in link parsing, username / age / phone / e-mail rules). New libraries: Credential Manager + googleid, exifinterface, Glide. No Supabase change was needed.
- Profile > About and Settings show 2.0.3. Not opened or built in Android Studio / Gradle by Claude.

# v2.0.2 - Android port, Stage 2 (data layer; JavaFX app stays v1.20.0)
- Ported the models, the Supabase client (now OkHttp), session store (Android Keystore), offline cache + outbox, error text, geo / fuzzy / validate / PKCE helpers and the boarding-house, chat, boost, AI and verification services. Same class and method names as the desktop app.
- 41 unit tests (run on the JVM). Debug-only screen "Developer: data layer check" under Profile signs in and fetches boarding houses / colleges.
- Profile > About shows 2.0.2. Not opened or built in Android Studio / Gradle by Claude.

# v2.0.1 - Android port, Stage 1 (project skeleton; JavaFX app stays v1.20.0)
- New `bofo-android/` Gradle project (Java 17, Material 3): opens in Android Studio, splash with the mascot, bottom-nav shell (Home, Explore, Saved, Chats, Profile) with placeholder tabs, Profile > About shows the version from `BuildConfig.VERSION_NAME` (2.0.1, versionCode 201).
- BOFO purple theme, button and card styles ported from `bofo.css`; adaptive app icon from the mascot; edge-to-edge screens that move above the keyboard.
- Manifest keeps the `bofo://auth-callback` deep link; CAMERA / storage permissions are intentionally not declared (system camera + photo picker need none).
- Not opened or built in Android Studio by Claude (no Android SDK in the sandbox).

# v2.0.0 - Android port, Stage 0 (audit and plan; JavaFX app stays v1.20.0)
- New Android line (v2.0.N per stage). Added `ANDROID_PORT_PLAN.md` (class map JavaFX -> Android, risks, decisions), the `bofo-android/` folder layout (no Gradle yet), `HANDOFF_ANDROID.md`.
- The release rule for the Android line is now in `MAP_FEATURE_CHANGES.md` and `CLAUDE.md` (section 5). No app code, nothing built or run.

# v1.20.0 - Login inside the app + phone keyboard
- New sign-in screen that never opens a browser tab: type your email, get a 6-digit code, type it in the app (like the Claude app). Also email + password (sign in / create account) and Google.
- Friendly error messages (wrong password, expired code, too many tries, no internet). Resend code with a 45 s timer.
- Phones: the app lifts above the on-screen keyboard and scrolls the field you tap into view (all forms, chat input).
- Test-account buttons on the login screen only appear when `dev.*` accounts are in `config.local.properties`.
- Needs Supabase email templates with the code: `docs/EMAIL_LOGIN_SETUP.md`. App version 1.20.0 (versionCode 80).

# v1.19.15 - Android build: Gluon license step
- v1.19.14 got past the native compile, then `gluonfx:package` stopped asking for a Gluon license (https://gluonhq.com/activate). The workflow now runs `gluonhq/gluon-build-license@v1` with the repo secret `GLUON_LICENSE` before the build. Needs the secret (see HANDOFF.md). App version 1.19.15 (versionCode 79).

# v1.19.14 - Android build fix (APK now builds for real)
- Fix: the GitHub "Build Android APK" run showed green but built nothing. The native build stopped with `Error parsing resource configuration ... Invalid escape character` because the `resourcesList` patterns in `pom.xml` used `\.`. They now use `[.]`.
- Workflow: the build step now fails (red) when Maven fails (`set -o pipefail`), the build log is uploaded as its own artifact `BOFO-build-log`, and `BOFO-android` holds only the `.apk` (the run fails if none is made). Actions bumped to checkout@v5 / upload-artifact@v5 (Node 20 warning).
- `pom.xml`: `packageType` is now `apk` (an `.aab` cannot be installed on a phone directly). App version 1.19.14 (versionCode 78).

# v1.19.13 - Calmer Directions card
- Directions now shows the result, one short line and two small links. The long notes, the data credit and the extra explanations are behind a "More info" link. "Show BH -> Campus" and "Open in OpenStreetMap" are text links instead of a big button.

# v1.19.0 - Home: lowest / middle / highest rent in range
- Under "Nearby boarding houses" Home now shows **Lowest, Middle, Highest** monthly rent of the places in the radius (e.g. `Lowest P1,300/mo   Middle P1,550/mo   Highest P2,050/mo`). Plain text, no tiles.
- Follows the campus and radius slider (and "closest to you" when no campus is chosen). Middle = median; with an even count it is halfway between the two middle prices.
- 1 place, or all the same price: one "Rent" value. 2 places: Lowest + Highest only. 0 places or a load error: the row is hidden (never a dash).
- Same numbers on New, Hot and Best rated: "Best rated" only lists places with 3+ reviews, so that tab reads the range from the unfiltered list (one extra request).
- New `ui/PriceRange` (pure maths), `HomeView.showPriceRange / paintPriceRange / priceCell`. No CSS or Supabase change. Not compiled/run by Claude (no javac/Maven in the sandbox; brackets checked only).

# v1.18.0 - New live location marker + Home / map redesign
- **Blue dot redesigned** (new `ui/LiveDot`): glossy blue-indigo core in a white ring with a soft shadow, two staggered ripple rings, and a fading **view cone** that turns the way you move (replaces the tiny arrow). First fix settles in from 82% size; pressing the locate button ends with a "found you" ring + pop on the dot.
- **Home / map**: softer framed map (28 px corners, white frame), glass **Live / Finding you... / Approximate / Location off** chip on the map (hidden while a campus is chosen, the radius bar uses that spot), 46 px locate button with a clearer "on" state, lighter map colours (land, greens, water, buildings), press feedback on buttons, chips and cards, and a short staggered entrance on the first Home of a login only.
- Motion follows the ui-animation skill: only transform + opacity animate, enter curve `cubic-bezier(0.22,1,0.36,1)`, never from scale 0, interruptible, ripples pause when the map is off screen.

# v1.17.3 - PC: an old pinned spot can no longer hide your real location
- Fix: the spot you pinned by pressing and holding the map was saved on the PC and used on every start instead of your real location (easy to trigger by accident). It is now kept for the current session only, and turning "Use my location" off and on again clears it.
- The console now prints where each location came from, e.g. `[location] wifi service -> 9.37, 122.80` (sources: phone GPS, wifi service, windows, browser, pinned spot, dev.location).

# v1.17.2 - PC location from nearby Wi-Fi (like Google Maps)
- On a PC the app now scans the Wi-Fi networks around you (`netsh wlan`) and asks a Wi-Fi location service where those routers are, the way Chrome / Google Maps do. Usually 20-200 m instead of Windows' internet-address guess. The dot then keeps following you (checked every 15 s, moves under 25 m ignored).
- Service: free beacondb.net by default; for Google's own database set `geolocation.key=<Google Geolocation API key>` in `config.local.properties`.
- If the Wi-Fi service cannot place you (fewer than 2 networks, no coverage), the app falls back to Windows' location as before. Press and hold the map still pins an exact spot.

# v1.17.1 - PC: blue dot follows you live
- On a PC the blue dot used to be set once. Now, after the first Windows location fix, the app keeps Windows' location watcher open and moves the dot whenever the position changes by more than 10 m (checked every 3 s). It stops when "Use my location" is turned off or when you press and hold the map to pin your spot (a pinned spot is never moved).
- A PC has no GPS, so the dot is only as exact as Windows can make it (Wi-Fi). Turn on Windows Settings > Privacy & security > Location. Phones are unchanged (real GPS).

# v1.17.0 - Profile setup on first sign-up + region the map opens on
- New accounts get a "Set up your profile" screen right after choosing Student / Owner: profile picture (camera or gallery), username, mobile number, age, sex and region. Everything is optional ("Skip for now").
- All of it can be edited later in Profile > Personal information (age and sex now load back into the form; before they were saved but shown empty).
- The map (student Home, Explore, owner Dashboard) opens on the saved region every time; the round locate button still flies to you.
- Profile picture shows in the Profile header (Google photo until you change it). Usernames are unique (case-insensitive).
- Supabase: run `supabase/migrations/009_profile_setup.sql` (new columns, `username_available`, public `avatars` bucket). Redeploy the `delete-account` Edge Function (it now also clears avatars).

# v1.16.13 - Owner locate button works like the student one
- Fix: on the owner Dashboard map and the New/Edit listing map the app never asked for your location, so the blue dot was missing and the round locate button had to wait (or did nothing). They now ask once (same "Use your location" question as the student Home) and start tracking, so the button flies to you right away.

# v1.16.12 - Locate button no longer "sometimes does nothing"
- Fix: on a phone, a tap with a tiny finger wobble was cancelled by the button. It now fires on release with some slack, and dragging that starts on the button no longer switches off "follow me".
- Fix: the first time (Android permission dialog) the 12 s timer could run out and throw your tap away; if the position arrives late the map now still flies to you.
- Fix: an old timer could mark a newer location request as failed; only the newest tap waits (no duplicate flights); the pulsing icon stops after ~15 s at most.

# v1.16.11 - Locate button works smoothly
- Fix: tapping the round location button while you were far from the map no longer stutters: the camera flight and the blue dot's glide were both moving the map at once. The dot now waits until the flight is done.
- The button gives feedback: it shrinks slightly when pressed, and its icon pulses while the phone is still finding you.
- Tapping it again while it is still finding you is ignored (before, every tap added another flight and another "Finding your location..." message).

# v1.16.10 - Dashboard loading skeleton
- The owner Dashboard now shows a placeholder for the whole page while it loads: the three Overview tiles, the map and two listing cards pulse in grey, then are replaced by the real content (before, only the tiles had a placeholder and the map and listings popped in later, pushing the page down).

# v1.16.9 - Narrow-screen UI fixes (Profile, My listings)
- Fix: Profile rows no longer break words ("Verificatio / n") - the value (phone, Verified, Pending) is now a grey line under the title, and rows are tighter. "Verified" is green.
- Fix: My listings header showed "My ..." and "+ Add lis..." - the title now gets all free width and "Add listing" is a compact round "+" button.
- Fix: Boost / Unlist / Delete buttons were cut to "Bo... / Un... / Del..." - compact buttons that never clip their label, grouped with their own card. No favourite heart on your own listings.
- Profile: extra bottom space so Log out / Delete can scroll clear of the tab bar and the mascot.

# v1.16.8 - Owner map: dots + number bubbles again; locate button works
- Fix: on the owner Dashboard the pins turned into big full pins after the page was scrolled/pinched over the map (no dots, no number bubble). The map now always uses its real zoom.
- Fix: the round locate button did nothing when your position was already known but the GPS status was not "on". It now flies to your blue dot right away.

# v1.16.7 - Dashboard shows your listings; map names only when zoomed in
- Owner Dashboard now lists every listing you created (card list under the map).
- Campus names on the map only appear when zoomed in (zoom 14.5+), and never overlap.

# v1.16.6 - Preview 4 screen sizes at once (developers)
- New `dev/preview-sizes.ps1` opens the app in a small phone, a normal phone, a big phone and a tablet window side by side. Run: `powershell -ExecutionPolicy Bypass -File dev\preview-sizes.ps1`.
- `--size=WxH@X,Y` can now also set where the window opens.

# v1.16.5 - Map: number bubble really zooms in with one tap
- Fix: tapping a numbered bubble now zooms until its houses are apart (no new smaller bubbles), so one tap shows the pins.
- Houses packed very close together still take two taps: the first goes part way, the second finishes.

# v1.16.4 - Map: number bubble zooms in with one tap
- Tapping a numbered bubble now zooms straight to a view that shows all the boarding houses inside it (before it took up to 3 taps).
- If the houses are packed close together in a small area, it takes two taps: the first goes half way, the second finishes.

# v1.16.3 - Chat header follows the chat
- Fix: the chat header said "Boarding house owner" or "Student" from your account's current role, so it could be wrong (for example after Switch mode). It now follows who is in that chat. Quick replies and the empty-chat hint follow too.
- Testing on PC: a second window can use its own login. In PowerShell: `$env:BOFO_PROFILE="owner"; mvn javafx:run` (any name). Without it the app behaves as before.
- Includes v1.16.1 and v1.16.2 (pop-ups, action sheets, chat delete).

# v1.16.2 - Menus match the pop-ups
- Press-and-hold / right-click on a message, the three dots in a chat, and a chat card in Chats now open a rounded bottom sheet (Copy, Delete, Report / block) instead of the grey Windows-style menu. Tap outside or Esc closes it.
- Includes everything from v1.16.1 (chat delete fix and the new pop-ups).

# v1.16.1 - Chat delete works + new pop-ups
- Fix: "Could not delete the chat". The database part of chat delete had not been installed on Supabase; it is now. Deleting a chat or a message works.
- New look: every question / warning / error box in the app (delete chat, delete message, delete listing, delete account, report, refund, reject, location permission) is now one rounded in-app pop-up with an icon, soft text and pill buttons instead of the grey Windows-style window. Tap outside or press Esc to cancel.

# v1.16.0 - Delete messages and chats
- Press and hold (phone) or right-click (PC) a message: Copy, Delete for me, and for your own messages Delete for everyone. The other person's screen drops a message deleted for everyone within a few seconds.
- Press and hold / right-click a chat card in Chats: Delete chat. It leaves your list and its old messages are hidden for you; the other person keeps their copy. If they write again the chat comes back with only the new messages. The three dots inside a chat also have Delete chat.
- Works for students and owners. **Run `supabase/migrations/008_delete_chat.sql` once in Supabase before using it.**

# v1.15.5 - My listings: card with a photo no longer wider than the screen
- Cause: in a one-column list a card was sized with its own minimum width, which a loaded photo makes large, so it ran past the right edge. `CardGrid` now gives a card exactly the column width.

# v1.15.4 - My listings fits the screen
- Fix: the My listings cards were wider than the screen (photo cut off on the right) and the Boost / Unlist / Delete buttons covered the card text. Now the list is one column sized by the screen width, and the three buttons share the width equally.

# v1.15.3 - Cleanup + faster loading
- Removed code nobody called: `SpriteView`, 6 unused mascot PNGs bundled in the app (about 320 KB), `Geo.pathKm`, `Ui.menuRow`, `FilterSheet.isOpen`, `MapPane.onPinTap/currentCenter`, `Icons.forLabel`, `Switch.setOn`, `AppConfig.devBypass/devRole`, `AuthService.devForce/signedIn`, and the dead `dev.bypassLogin` / `dev.role` settings.
- Network: server answers are gzip-compressed (much less data on slow connections); reads give up after 15 s instead of 30 s; the main list and the featured list now load at the same time.
- Publishing a listing: rooms, amenities and photo rows are saved in one request each, and photos upload side by side. Photos are encoded smaller (1024 px wide, PNG Sub filter).
- Start-up: splash holds 1.2 s (was 2.2 s) and no longer starts its own thread.

# v1.15.2 - Camera and Gallery fix
- PC: Camera and Gallery open a file picker. Phone: errors are caught; storage/media permissions added to the manifest.

# v1.15.1 - New listing polish
- Blue map pin, mascot hidden on the form, slimmer bottom bar, soft card shadows.

# v1.15.0 - Blue Create button + step-by-step New listing
- Create in the bottom bar is a raised blue square.
- New listing is a 4-step form with progress, per-step checks and a review page.

# v1.14.2 - Cleaner two-column card

- The compact card now shows only: photo with heart, name, price and one short line ("★ 4.5 · 1.2 km" or just the distance).
- No tags, no Verified/Hot badges and no "beds left" pill on it (a "Full" pill still shows when there are no free beds). The full card (single column) is unchanged.

# v1.14.1 - Fix: cards in the 2-column list lost their styling

- Cause (most likely): `CardGrid` switched every card between normal and compact (style class, padding, photo height) while measuring and laying out, so the CSS never settled and the cards showed unstyled.
- Now compact is fixed when the card is created (`new BhCard(..., true)` for Home, Explore and Saved). `CardGrid` no longer touches the cards.

# v1.14.0 - Two-column list + ad slots

- Home, Explore and Saved show listings in 2 columns (`CardGrid(10, 150, 2)`). Cards in a row share the same height.
- Compact card in narrow columns: shorter photo, smaller text, "/mo", first two tags only (`BhCard.setCompact`).
- New `AdSlot`: a grid cell that takes the place of one card (or a full row). Off by default (`AdSlot.ENABLED = false`); Home and Explore already insert it at random positions (`AdSlot.spots`) once it is switched on.

# v1.13.2 - Home cleanup

- One location button (the one on the map); the duplicate next to the search bar is gone, so the search bar is full width and its text is no longer cut off.
- Map is shorter (230 px instead of 340) so the first listings show without scrolling.
- "6 places" moved into the list subtitle (the mascot covered the pill).
- Map credit is one small line instead of a two-line box.

# v1.13.1 - Top section: light and clean instead of the purple band

- Removed the gradient band. Every main tab now opens with a big dark title, a quiet subtitle, white round buttons and a white search bar with a soft shadow.

# v1.13.0 - Branded top section on every main tab

- New `Ui.pageHeader`: indigo gradient band with rounded bottom (same family as Profile), white title + subtitle, round translucent buttons.
- Home: avatar, greeting, bell and the single search bar + near-me button now sit together in the band.
- Explore: title, subtitle and search + filter button in the band. Saved and Chats use the band too; owner/admin tabs (Dashboard, My listings, Payments, Verification queue, Reports) too.

# v1.12.9 - Listing card redesign

- Price is now its own big indigo line under the name (no more squeezing the name next to the price).
- Rating and distance share one wrapping row ("No reviews yet" became "New listing").
- Verified / Featured / Hot badges come first, then at most 3 amenity tags.
- Card border tints on hover; slightly more spacing.

# v1.12.8 - Home screen polish

- Greeting follows the time of day (Good morning / afternoon / evening).
- The list title now has a subtitle ("Within 2.0 km of <campus>", "Closest to you first" or a hint to choose a campus) and a "12 places" count pill.
- Thin rounded outline around the map, slightly more even spacing between the map, the New / Hot / Best chips and the list.

# v1.12.7 - Thinking pose cleaned up

- The raised arm no longer sticks out of the body like a lump: it is now a short straight arm with the hand under the chin (still taps).

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
