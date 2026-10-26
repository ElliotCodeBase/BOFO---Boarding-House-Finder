# New listing form: UI polish (v1.14.3)

## What changed
| Part | Before | After |
|------|--------|-------|
| Section cards | flat, outlined | white cards, 24px corners, soft violet shadow, more padding, 14px gap |
| Step numbers (1-7) | pale circle | violet gradient circle, white number |
| Text fields | white pill with a grey border | soft tinted fields (16px corners); turn white with a violet border when you type |
| Map in "Pin location" | square corners | rounded corners |
| Buttons "+ Camera", "+ Gallery", "+ Add room", "Set pin at map center" | outlined | soft violet tint, bold violet text |
| Room cards | grey | soft lavender |
| Bottom bar | thin line above it | soft shadow above it |
| Progress | text only ("0 of 6 required details done") | text plus a violet progress bar that fills up and turns green when all 6 are done |

Files: `ui/ListingFormView.java`, `bofo.css` (block "New listing form" at the end; this bofo.css also contains all earlier Home and Chats styling).

## How to apply
```powershell
cd C:\Users\user\bofo-finder
Expand-Archive -Path "$HOME\Downloads\BOFO-listing-form-ui.zip" -DestinationPath . -Force
mvn clean javafx:run
```
Open: Profile / Listings (owner account) -> New listing.

## If problems occur
- Fields look wrong (grey on grey): change `#F6F5FC` in `.form-card > .text-field` in `bofo.css`.
- Map not visible or cut off: remove the `mapBox` clip lines in `ListingFormView.java` (the 3 lines with `mapClip`) and use `map` directly in `cPin`.
- Progress bar too thin or thick: change the two `-fx-padding: 4` values in `.form-progress`.
- Amenities card says "Amenities aren't available right now. Tap to retry": not a UI bug. The amenities table does not exist yet in Supabase. Run `supabase/migrations/005_amenities.sql` in the Supabase SQL Editor, wait a minute, then tap retry.
- Compile error: `git checkout -- src/main/java/com/bofo/finder/ui/ListingFormView.java src/main/resources/com/bofo/finder/bofo.css` and send me the error.
