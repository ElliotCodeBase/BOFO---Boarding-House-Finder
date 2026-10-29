# v1.19.3 - Cleaner 2-column listing cards (Home / Explore / Saved)

## What changed (ui/BhCard.java only, compact card)
- No-photo listings show a soft purple gradient with a house icon instead of a flat grey slab.
- Price is a white pill on the photo, rating a dark pill; both stay readable on any photo.
- Name is bold on one line; "... near St. Paul University" is split so the place sits on its own grey line (no more "Demo BH 5 near St. Paul University ..." cut-off).
- Distance and beds are two small chips (purple / green) instead of "49.9 km . 4 beds left" text.
- Soft shadow instead of a thin outline. All styles are inline in this file: no CSS change, so it cannot overwrite your other style edits.

## How to open it
Close the app window, then from `C:\Users\user\bofo-finder`:
`Expand-Archive "$HOME\Downloads\BOFO-v1.19.3-changes.zip" -DestinationPath . -Force; mvn clean javafx:run`

## Not run on screen
Only edited here (no Maven/JavaFX in the sandbox). Look at Home, Explore and Saved.

## Fix guide
- Compile error in BhCard.java: you have a different `Ui`/`Icons` than v1.18.x - send me the error.
- Card text still wraps: tell me which screen; Explore may use its own card.
- To undo: restore `BhCard.java` from git (`git checkout -- src/main/java/com/bofo/finder/ui/BhCard.java`).
