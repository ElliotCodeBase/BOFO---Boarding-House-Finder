# Fix: walking route looks like it goes where there is no road (v1.14.3)

## Cause
The walking route is real: it comes from OpenStreetMap routing, which also uses alleys, service roads and footpaths (very common
between houses). But the map drawing code deliberately skipped those ("clutter + slow"), so the purple line seemed to cross empty
blocks. The route was not wrong, the map was hiding the paths it follows.

## What changed
`VectorTileRetriever.java`: alleys, service roads, tracks and footpaths are now drawn (thin, slightly softer white) when the map is
zoomed in to level 15 or closer. Bigger streets are unchanged. Zoomed out, they stay hidden so the map stays fast and clean.

## How to apply
```powershell
cd C:\Users\user\bofo-finder
Expand-Archive -Path "$HOME\Downloads\BOFO-route-paths-fix.zip" -DestinationPath . -Force
mvn clean javafx:run
```
Open a boarding house on the map with a campus chosen and look at the purple line: it should now sit on a visible path or street.
Tiles are cached, so the first look at an area may need a few seconds to redraw. If old tiles stay, delete the tile cache folder in `C:\Users\<you>\.bofo\` (ask me if you cannot find it).

## If problems occur
- Map feels slow or cluttered: in `VectorTileRetriever.java` change `z < 15` to `z < 16` (paths only when very close).
- Paths still not visible next to the route: zoom in one step; paths only show from zoom 15.
- Route still goes through a place that is clearly a wall or private land: that is a mistake in OpenStreetMap data (or the free routing server); fix it on openstreetmap.org, or tell me and I can add a "roads only" option that routes along streets.
- Compile error: `git checkout -- src/main/java/com/bofo/finder/util/VectorTileRetriever.java` and send me the error.
