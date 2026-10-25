# Local jeepney / multicab lines (hand-made)

BOFO finds jeepney lines from OpenStreetMap, but volunteers have not mapped every town. For those places you can type a line in by
hand. BOFO then offers it on the Directions card and on the map card, ranked above OpenStreetMap lines, with the fare you checked.

**Nothing is made up for you:** the file that ships with the app is empty (`"lines": []`). Add a line only after you have ridden it or
asked a driver / the terminal, and write the date in `note`.

## Where the file goes

| Place | When to use it |
|---|---|
| `src/main/resources/com/bofo/finder/local-transit.json` | Lines for everybody: commit it and every build ships them. |
| `local-transit.json` in the device's BOFO folder (PC: `C:\Users\<you>\.bofo\`, phone: the app's private storage) | Your own test lines, no rebuild needed. Restart the app after editing. |

Both files are read and combined.

## Format

```json
{
  "lines": [
    {
      "ref": "07",
      "name": "Stop A - Stop B",
      "operator": "optional",
      "fareLow": 13,
      "fareHigh": 15,
      "note": "Checked with a driver, Oct 2026. Ask for 'Stop B'.",
      "bothWays": true,
      "stops": [
        { "name": "Stop A", "lat": 10.0000, "lng": 123.0000 },
        { "name": "Stop B", "lat": 10.0100, "lng": 123.0100 }
      ]
    }
  ]
}
```

(Names and coordinates above are placeholders to show the shape. Use real ones.)

| Field | Needed | Meaning |
|---|---|---|
| `name` or `ref` | one of them | What the passenger looks for on the jeepney. |
| `stops` | this or `path` | Stops in the order the vehicle visits them. Drawn as straight pieces between the stops, so add a stop at every big bend. |
| `path` | this or `stops` | `[[lat,lng], [lat,lng], ...]` following the road exactly (best look). Wins over `stops`. |
| `fareLow`, `fareHigh` | no | Pesos you checked. Left out = the LTFRB formula is used. |
| `note` | no | One short sentence shown under the option. |
| `bothWays` | no | Default `true`: the line also runs the other way. Use `false` for one-way loops. |

## How to get the coordinates

* In OpenStreetMap or Google Maps, long-press / right-click a stop and copy the latitude and longitude.
* Or trace the road in a tool like geojson.io, then paste the points into `path` (swap each pair to `[lat, lng]`: GeoJSON is lng, lat).

## How it is used

A line is offered for a trip only when it passes within 450 m of both ends and reaches the destination after the start in the direction it runs.
A broken entry is skipped and printed to the console as `[LocalTransit] ...`; the rest still work.
