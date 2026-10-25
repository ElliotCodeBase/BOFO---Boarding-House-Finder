# Home modernization (v1.14.3) - includes the earlier header polish

## What changed
| Area | Change |
|------|--------|
| Header band | Soft violet-to-white gradient band with rounded bottom corners (avatar, greeting, bell, search sit on it) |
| Avatar / bell / search | Gradient avatar with ring, floating bell, 54px search pill with tinted icon (from the header polish) |
| Map | No outline, rounder corners (22px), sits closer to the header |
| Tabs (New / Hot / Best rated) | Borderless white pills with a soft shadow; selected tab is a violet gradient with glow |
| List title | "Nearby boarding houses" now 20px; the subtitle is smaller and muted |
| Cards (2-column grid) | Borderless, rounder (22px), 14px names, 17px price, soft shadow on the "beds left" pill |

Files: `HomeView.java`, `CampusPicker.java` (shared search bar, so Explore's search bar changes too), `bofo.css` (blocks "v1.14.3" at the end).
This zip replaces `BOFO-home-header.zip`; apply only this one.

## How to apply
```powershell
cd C:\Users\user\bofo-finder
Expand-Archive -Path "$HOME\Downloads\BOFO-home-modern.zip" -DestinationPath . -Force
Remove-Item .\HOME-MODERN-CHANGELOG.md   # optional
mvn clean javafx:run
```
Look at the Home tab. The card, tab and search-bar styles are shared, so also check Explore and Saved.

## If problems occur
- Old look: always use `mvn clean javafx:run`.
- Compile error: `git checkout -- src/main/java/com/bofo/finder/ui/HomeView.java src/main/java/com/bofo/finder/ui/CampusPicker.java` and send me the error text.
- Header band too tall or too strong: edit `.home-header` in `bofo.css` (the padding `22 16 24 16`, or colours `#E7E1FF` / `#F0EDFF`).
- Cards feel too flat: add `-fx-effect: dropshadow(gaussian, rgba(23,20,42,0.06), 8, 0, 0, 2);` to `.card-compact` (costs a bit of scroll smoothness on phones).
- Tabs look wrong: edit `.home-chips` rules.
- Undo all styling: delete everything from the first `v1.14.3` comment to the end of `bofo.css`, then `git checkout` the two .java files.
- If you edited these files since the zip you uploaded, run `git diff` before extracting so nothing gets overwritten.
