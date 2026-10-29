# BOFO Finder v1.19.0 - Price range in the radius (change log + fix guide)

## What I did
| Area | Change | File |
|---|---|---|
| Home | New row under "Nearby boarding houses": Lowest, Middle, Highest monthly rent of the places in range | `HomeView.java` |
| Maths | Lowest, median (middle) and highest; ignores a price of 0 or less | `PriceRange.java` (new) |
| Version | 1.18.0 -> 1.19.0, versionCode 75 -> 76 | `config.properties`, `pom.xml`, `CHANGES.md`, `HANDOFF.md`, `docs/HANDOFF_ARCHIVE.md` |

## How it behaves
- Follows the campus and radius slider. With no campus chosen it covers every place on the list.
- 3 or more different prices: Lowest, Middle, Highest. 2 places: Lowest and Highest. 1 place or all the same price: one "Rent" value. Nothing to show or a load error: the row is hidden.
- Same numbers on New, Hot and Best rated (Best rated reads the unfiltered list once, because it only lists places with 3+ reviews).

## How to see it
1. Delete older `BOFO-v1.19.0-changes*.zip` files in Downloads, then unzip this one over `C:\Users\user\bofo-finder`.
2. `mvn clean javafx:run`, open Home, scroll to "Nearby boarding houses". Pick a campus and move the radius slider: the three prices change after the list reloads.

## If something goes wrong
| Problem | Fix |
|---|---|
| Build error "cannot find symbol PriceRange" | `PriceRange.java` was not copied. Check `src\main\java\com\bofo\finder\ui\PriceRange.java` exists. |
| Row never appears | Normal when no place is in range or every price is 0. Check the demo data has prices. |
| Numbers differ on Best rated for a moment | It loads one extra list; wait a second. If it stays wrong, the extra request failed (row hides on failure). |
| Want it gone | Delete the `showPriceRange(...)` line in `reload()`; the rest is harmless. Or `git restore .` before committing. |

## Not verified
Not compiled or run on screen: Maven, JavaFX and javac are not available in the sandbox.
