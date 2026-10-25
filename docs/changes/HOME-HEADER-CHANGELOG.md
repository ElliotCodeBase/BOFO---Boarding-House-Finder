# Home header redesign (v1.14.3 polish)

## What changed
| Part | Before | After |
|------|--------|-------|
| Avatar | flat pale circle | 46px violet gradient circle, white initials, white ring and soft glow |
| Greeting | "Good morning," small grey | "Good morning" (no comma), 13px, above the bold 26px name |
| Bell | flat white circle with outline | white circle, no outline, soft shadow, tint on hover |
| Search bar | bold prompt text, bare icon, heavy tinted arrow | 54px pill, icon in a tinted circle, normal-weight light prompt, quiet chevron that tints on hover |
| Spacing | 16px between rows | 18px |

Files: `HomeView.java`, `CampusPicker.java` (the search bar is shared, so Explore gets the same search bar), `bofo.css` (new block at the end, v1.14.3).

## How to apply
```powershell
cd C:\Users\user\bofo-finder
Expand-Archive -Path "$HOME\Downloads\BOFO-home-header.zip" -DestinationPath . -Force
mvn clean javafx:run
```
Look at: Home tab (top), and the Explore search bar.

## If problems occur
- Old look still showing: use `mvn clean javafx:run` (clean deletes cached resources).
- Compile error in `CampusPicker.java` or `HomeView.java`: restore with `git checkout -- src/main/java/com/bofo/finder/ui/CampusPicker.java src/main/java/com/bofo/finder/ui/HomeView.java` and send me the error.
- Search bar looks too tall/short: change `-fx-min-height: 54` / `-fx-pref-height: 54` in the v1.14.3 block of `bofo.css`.
- Avatar colours: edit the two hex values in `.hdr-avatar` (`#7C5CFF`, `#5B3BF0`).
- Undo only the styling: delete the block under `/* ---- v1.14.3 Home header polish`, and `git checkout` the two .java files.
- These files are based on the zip you uploaded (current `main`). If you changed them locally since, `git diff` first so nothing is overwritten.
