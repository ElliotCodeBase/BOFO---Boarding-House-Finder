# Fix: map preview card text cut off (v1.14.3)

## What was wrong
On a narrow window the card that slides up over the map showed "Demo ..." (name cut off), put the price next to the name, and
cut the route line ("Bus or van 4 h 4 min ·...").

## What changed (`MapCard.java` only)
- The name now has its own full-width line.
- Price and rating share the second line.
- The route/travel-time line wraps onto a second line instead of being cut off.
- The photo is 68px instead of 76px, which gives the text more room.

## How to apply
```powershell
cd C:\Users\user\bofo-finder
Expand-Archive -Path "$HOME\Downloads\BOFO-mapcard-fix.zip" -DestinationPath . -Force
mvn clean javafx:run
```
Tap a pin on the Home map to see the card.

## If problems occur
- Compile error: `git checkout -- src/main/java/com/bofo/finder/ui/MapCard.java` and send me the error.
- Card too tall: set the photo size back to 76 or 60 in `Ui.photoLoading(b.photoUrl(), 68, 68)`.
- Name still cut: very long names end with "..." on purpose; change `name.setWrapText(false)` to `true` to allow two lines.
