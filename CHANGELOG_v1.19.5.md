# v1.19.5 - Tap a price to show that place on the map

- Home: tapping **Lowest / Middle / Highest** now picks that boarding house on the map, the same as tapping its pin: the pin is highlighted, the preview card opens, the route is drawn and the camera moves to it. (Replaces v1.19.4, where a tap opened the detail page; the card's own "details" button still does that.)
- The price row is one rounded tinted strip with small caps labels, bold prices, Middle in purple.
- `ui/PriceRange.java` keeps the place behind each price. `HomeView.java` and `MapPane.java` are patched in place by `apply-price-highlight.ps1` (MapPane gets one new method, `highlight(BoardingHouse)`).
- Not run on screen. Undo: `git checkout -- src/main/java/com/bofo/finder/ui/HomeView.java src/main/java/com/bofo/finder/ui/MapPane.java src/main/java/com/bofo/finder/ui/PriceRange.java`
