# Home - all changes in one zip (v1.14.3)

This zip contains EVERY Home change so far. If you apply it, you do not need `BOFO-home-header.zip`, `BOFO-home-modern.zip`
or `BOFO-mapcard-slim.zip`. (The Supabase "Something went wrong" fix is separate: `BOFO-amenity-fix.zip`.)

## What changed
| Area | Change | File |
|------|--------|------|
| Map size | Home map is taller: 360px (was 230px) | `HomeView.java` |
| Pin card | Slim strip (about 64px) with photo, name, price, rating; no tags or route line | `MapCard.java` |
| Pin card space | Less room reserved for the card (70px), so the map keeps more visible area | `MapPane.java` |
| Header | Violet gradient band, gradient avatar with ring, floating bell | `HomeView.java`, `bofo.css` |
| Search bar | 54px pill, icon in a tinted circle, light prompt text, quiet chevron (Explore gets it too) | `CampusPicker.java`, `bofo.css` |
| Tabs | White pills with shadow, selected tab is a violet gradient with glow | `HomeView.java`, `bofo.css` |
| Map look | No outline, rounder corners | `HomeView.java`, `bofo.css` |
| List title + cards | 20px title, muted subtitle, borderless rounder cards | `HomeView.java`, `bofo.css` |

## How to apply
```powershell
cd C:\Users\user\bofo-finder
Expand-Archive -Path "$HOME\Downloads\BOFO-home-all.zip" -DestinationPath . -Force
Remove-Item .\HOME-ALL-CHANGELOG.md   # optional
mvn clean javafx:run
```
If you edited `HomeView.java`, `CampusPicker.java`, `MapCard.java`, `MapPane.java` or `bofo.css` yourself since the repo zip you uploaded, run `git diff` first.

## If problems occur
- Map too big or too small: change the number in `new MapPane(360)` in `HomeView.java` (300 to 420 works well).
- Pin card still covers the pin: raise `CARD_SPACE` in `MapPane.java` (70 -> 85).
- Old look showing: always run `mvn clean javafx:run`.
- Compile error: `git checkout -- src` to go back to the last commit, then send me the error text.
- Undo only the styling: delete everything from the first `v1.14.3` comment to the end of `bofo.css`.
