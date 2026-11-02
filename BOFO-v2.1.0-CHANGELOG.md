# BOFO Android v2.1.0 - Changelog

## What you asked for
Improve and redesign the Directions result (the "3 h 31 min by bus or van / 123.0 km / Show BH -> Campus / Open in OpenStreetMap / More info" part), and move to the next version.

## What I did
| # | Change | Where |
|---|---|---|
| 1 | Result panel: soft tinted card, round drawn icon (walk / tricycle / bus-van), big time, then distance, fare and "Estimate" as small pills (the grey "123.0 km" line is gone) | `view_directions_card.xml`, `bg_dir_hero.xml`, `bg_dir_hero_icon.xml`, `bg_dir_chip.xml`, `ic_dir_*.xml`, `styles.xml` |
| 2 | Too far to walk: calm rounded note; "Show BH -> Campus" is a full-width button instead of a small link | `view_directions_card.xml`, `bg_dir_note.xml` |
| 3 | "Open in OpenStreetMap" and "More info": two equal outlined buttons side by side | `view_directions_card.xml`, `styles.xml` |
| 4 | Thin "exact roads" bar now has a caption so people know why it is there | `view_directions_card.xml` (`dir_refresh`) |
| 5 | The result eases in (fade + small rise) the first time; the later swap to the exact road does not replay it | `DirectionsCardView.java` |
| 6 | Pill text helpers + 3 test lines (old `heroMeta` text unchanged) | `DirectionsRules.java`, `Stage6LogicTest.java` |
| - | Version 2.1.0 (versionCode 20100). New feature (visible redesign) = MINOR bump: 2.0.131 -> 2.1.0 (MAJOR.MINOR.PATCH) | `app/build.gradle`, `CHANGES_ANDROID.md`, `HANDOFF_ANDROID.md` |

## How to see it
Open a boarding house > Directions > BH -> Campus. A far trip shows the icon panel with pills, the note, the full-width "Show BH -> Campus" (on the other tabs) and the two outlined buttons. Profile > About shows 2.1.0.

## If something goes wrong
| Problem | Fix |
|---|---|
| Build error `heroMeta` not found | Copy `view_directions_card.xml` and `DirectionsCardView.java` together: `hero_meta` was removed on purpose. |
| Build error `chipDist` / `heroPanel` | Same: the layout and the Java must both be v2.1.0. Build > Clean Project. |
| Build error `Text.Bofo.DirChip` / `Widget.Bofo.Button.DirOutline` | Copy `res/values/styles.xml` too. |
| Build error `dir_estimate` | Copy `res/values/strings.xml` too. |
| Icon looks wrong | `ic_dir_walk.xml`, `ic_dir_bus.xml`, `ic_dir_ride.xml` are plain vector files; swap the path data. |
| Undo | `git checkout -- bofo-android CHANGES_ANDROID.md HANDOFF_ANDROID.md MAP_FEATURE_CHANGES.md` |

Also needs the earlier v2.0.125 to v2.0.131 files applied.
