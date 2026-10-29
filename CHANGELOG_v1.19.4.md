# v1.19.4 - Price row restyled and tap-able

- Home: the Lowest / Middle / Highest row is one rounded tinted strip (small caps labels, bold prices, Middle in purple).
- Tap a price to open that place (lowest = cheapest listing, middle = the median one, highest = the dearest). Hand cursor on hover.
- `ui/PriceRange.java` now also keeps the place behind each price. `ui/HomeView.java` is patched in place by `apply-price-row.ps1` (4 methods touched: paintPriceRange, priceCell and its calls).
- Not run on screen. Undo: `git checkout -- src/main/java/com/bofo/finder/ui/HomeView.java src/main/java/com/bofo/finder/ui/PriceRange.java`
