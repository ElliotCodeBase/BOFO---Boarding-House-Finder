# Fix: map preview card covered the map (v1.14.3)

Replaces `BOFO-mapcard-fix.zip`. Apply only this one.

## What changed
- `MapCard.java`: the card is now a slim strip (about 64px tall instead of about 140px): 48px photo, name, then price + rating.
  The route line and the amenity tags are gone from the card, because the purple badge on the map already shows distance and
  travel time and the tags are on the details page. X (close) and chevron (open details) sit side by side on the right.
- `MapPane.java`: the space reserved for the card is 70px (was 104) and its bottom margin 12px (was 20), so the map and the
  pin move up less and more of the map stays visible.

## How to apply
```powershell
cd C:\Users\user\bofo-finder
Expand-Archive -Path "$HOME\Downloads\BOFO-mapcard-slim.zip" -DestinationPath . -Force
mvn clean javafx:run
```
Tap a pin on the Home map to see the card.

## If problems occur
- Compile error: `git checkout -- src/main/java/com/bofo/finder/ui/MapCard.java src/main/java/com/bofo/finder/ui/MapPane.java` and send me the error text.
- Card overlaps the pin: raise `CARD_SPACE` in `MapPane.java` (try 80).
- Want the tags or route line back: they were removed from the `VBox text = new VBox(2, name, priceRow)` line in `MapCard.java`; add `info` and a `FlowPane` of tags to it.
- Want no card at all: tell me and I can open the details page directly when a pin is tapped.
