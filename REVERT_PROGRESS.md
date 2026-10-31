# Revert-to-original-UI progress (bofo-android)
## v2.0.26 - Turn on location popup (LocationHelper.ensureSettings).
## v2.0.25 - current location: LocationManager fallback + location-off toast.
## v2.0.24 - map speed (JavaFX-style): 2x pixel cap, lighter style, dots zoomed out, zoom 6-17, delayed map start.
## v2.0.23 - map lag fix: default MapView + CornerMaskView, per-layer updates, throttled radius glide.
## v2.0.22 - Step 4 done: inline map card
- Home is one scrolling page (CoordinatorLayout + AppBarLayout, header scrolls with the list; swipe-refresh only at offset 0; dropdown closes on scroll). List/Map toggle, full-screen map button and the old radius slider row are gone from Home.
- Map card in a 28 dp MaterialCardView (thin outline), height 42% of screen clamped 200-440 dp, texture-mode MapView, round white locate button bottom-right, attribution pill bottom-left. Radius pill top-centre (0.5-5 km, step 0.5, chip "1.0 km"), live circle + eased zoom while dragging, list reloads 350 ms after it settles. Shown when a campus or Near me is the centre. Map fits only when the centre changes.
- Changed: `fragment_home.xml`, `view_map_card.xml`, `HomeFragment`, `MapCardView`, `HomeSearchBar`, `build.gradle` (2.0.22 / 222). New: `bg_radius_pill`, `bg_radius_chip`, `bg_attribution`, `strings_map_card.xml`.
- NOT done in Step 4 (kept for later steps): `bofo-map-style.json` colours were NOT touched (pale lavender land etc.); chips are still the TabLayout (Step 7); rent line is still the plain text (Step 7); radius edge label on the circle (Step 6). MapFragment (full screen, used elsewhere) keeps its own slider.
- Not built or run by Claude. `home_view_list` / `home_view_map` strings are unused now (delete in Step 11).
## v2.0.21 - Step 1b + Step 3 done
- **Ask BOFO button (Step 1b):** `FloatingBofoView` replaces the fixed mascot in `fragment_shell.xml`: drag anywhere (kept above the tab bar), snaps to the nearest side edge with a small bounce, remembers its place while you stay signed in, soft float (bob + tilt) all the time, and after 4-10 s of quiet a random idle move (wiggle, two hops with squash, look around, coin flip, loop-the-loop, think, sleep with "Zz"), never the same twice in a row. Idle stops on touch, while the chat is open, when hidden / app in background, and when system animations are off. Tap = `BofoChatOverlay`: a white card (max 380 x 500 dp, above the tab bar) pops out of the button and shrinks back into it; tap outside, X or Back closes. Same chat logic as the old sheet (bofo-chat Edge Function, FAQ fallback, mascot thinks while waiting, not saved).
- **Home header + search (Step 3):** removed the "Home" title, the campus chip row and the "Campus:" button. Added: dark offline strip, greeting row (46 dp avatar with photo or initials, Good morning / afternoon / evening, bold first name, bell -> toast "No new notifications"), and ONE white search pill ("Search campus or place", clear X, arrow). Dropdown floats over the page (5 rows max, bold match, "Name" + "Campus - City" lines, nearest first, same `BrowseState.colleges`), pick = `BrowseState.setCampus`. X on the campus name clears the campus. Enter picks the best match, or on text that is not a campus opens Explore with it typed in. "Near me" is the first dropdown row (until the Step 5 locate button).
- New: `FloatingBofoView`, `BofoChatOverlay`, `HomeSearchBar`, `util/CampusMatch` (+ `CampusMatchTest`), `util/ExploreHandoff`, layouts `view_bofo_chat`, `item_campus_option`, drawables, `strings_bofo.xml`, `strings_home.xml`. Changed: `ShellFragment`, `fragment_shell.xml`, `HomeFragment`, `fragment_home.xml`, `ExploreFragment` (reads the hand-off once), `build.gradle` (2.0.21 / 221).
- Not built or run by Claude. `BofoPanelSheet` / `sheet_bofo.xml` are unused now (delete in Step 11).
## v2.0.20 - Step 2 done: splash + loading animation
- Splash is now the dark ink screen with the WHITE BOFO mascot jumping (2 jumps per 2.4 s loop, squash/stretch on the soles, hat pops off with spark bursts, ground shadow that shrinks in the air), "BOFO", "Boarding House Finder", three pulsing dots, "Getting things ready".
- New views: `SplashStageView` (mascot, drawn on the 200-unit art grid, clock-driven so it plays even with animations off), `SplashDotsView`.
- Splash shows one full loop (2400 ms, was 1200); status/navigation bar icons turn white on it and back to dark when it leaves. Retry state kept.
- Home list skeleton now pulses like the original (900 ms, 1 -> 0.45, ease in-out). Chats skeleton rows come in Step 10.
- Login, list card and everything else untouched. Not built or run by Claude.
## v2.0.19 - Step 1 done: floating tab bar + mascot button
## Next: Step 5 (radar dot + location states + overview).
**To continue in any AI:** upload your latest project zip, then paste the whole of `REVERT_PROMPT.md` (repo root) and say "Do Step 4".
