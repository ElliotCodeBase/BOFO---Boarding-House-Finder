# BOFO Finder v1.19.0 - Home redesign (change log + fix guide)

## What I did
| Area | Change | File |
|---|---|---|
| Header | Removed the violet gradient band and the glow shadows. Greeting + campus search sit on the page background. Search is a flat 16 px field with a 1 px line. | `HomeView.java`, `bofo.css` |
| Map | Corners 16 px, 1 px outline, plain white "Live" chip. Blue dot (`LiveDot`) and map logic untouched. | `HomeView.java`, `bofo.css` |
| Motion | Removed the first-Home entrance stagger. Press feedback and the photo-loading pulse stay. | `HomeView.java` |
| Cards | No house icon, no amber price pill. Missing photo = neutral block. Price is ink text. Meta line: `350 m · 4 beds left`, rating first when reviews exist. | `BhCard.java`, `bofo.css` |
| Copy | "Boarding houses near you", "Within 1.5 km of <campus> · 6 with free beds", "No free beds in range". | `HomeView.java` |
| Campus dropdown | All rows 56 px, text centred, max 5 rows, a third line is cut. Fixes the misaligned selected row and uneven gaps. | `CampusPicker.java`, `bofo.css` |
| Theme | `bofo.css` rewritten as one cascade (674 -> 443 lines). Tokens in `.root`. Dead override blocks deleted. | `bofo.css` |
| Version | 1.18.0 -> 1.19.0, versionCode 75 -> 76 | `config.properties`, `pom.xml`, `CHANGES.md`, `HANDOFF.md`, `docs/HANDOFF_ARCHIVE.md` |

## Numbers (bofo.css, your commands)
| Metric | Before | After |
|---|---|---|
| Gradients | 16 | 0 (one remains in Java: radius-slider fill in `HomeView.paintTrack`) |
| Shadow declarations | 46 | 9, two tiers: 0.14 / blur 12 and 0.24 / blur 24 |
| Radius values | 18 different | 4 (16, 8, 999, 3) |
| Pill (999) usages | 111 | 45 |
| Bold / lines with font-weight | 93 / 96 | 5 / 12 |
| Distinct hex colours | 77 | 16 |

## How to see it
1. Unzip `BOFO-v1.19.0-changes.zip` over `C:\Users\user\bofo-finder` (overwrite).
2. Build and run the desktop preview the way you normally do (for example `mvn clean javafx:run`).
3. Open the Home tab. Tap the search bar to see the campus list. Switch to Explore, Saved, Chats, Profile to check they match.

## If something goes wrong
| Problem | Fix |
|---|---|
| Build error "cannot find symbol" in `HomeView` or `BhCard` | Make sure all changed files were overwritten, not only some of them. Re-unzip the whole zip. |
| Looks unchanged | `mvn clean` first, so the old `bofo.css` in `target/` is not reused. |
| A control lost its styling (no border, wrong colour) | Search the old CSS (`git show HEAD:src/main/resources/com/bofo/finder/bofo.css`) for the class name and copy that rule back. Only `.home-header`, `.pill-green`, `.pill-grey` were intentionally removed. |
| Shadow or colour looks wrong somewhere | Colours live only in the `.root` block at the top of `bofo.css`. Change the token, not the rule. |
| Dropdown row still clips text | Raise `ROW` in `CampusPicker.java` (56 -> 64). |
| Want everything back | `git checkout -- .` before committing, or revert the PR. |

## Not verified
Maven, JavaFX and `javac` are not available in the sandbox, so nothing was compiled or run on screen. Java edits were only checked for balanced brackets. The screenshots you sent show a purple hero and stat tiles that are not in this v1.18.0 zip, so those exact bugs (clipped tile captions, hero shadow) could not be reproduced here.
