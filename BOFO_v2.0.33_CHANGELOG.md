# BOFO Android v2.0.33 - Step 11 (final pass)

## What I did
- Deleted the unused old Ask-BOFO sheet: `BofoPanelSheet.java` and `sheet_bofo.xml`.
- Removed 23 unused strings (old filter sheet, List/Map toggle, price range, old chat/detail text).
- Checked all layouts and drawables: none unused. No look or behaviour change.

## How to access
Nothing new to open. Profile > About shows 2.0.33. The app should look exactly like v2.0.32.

## If a problem occurs
| Problem | Fix |
|---|---|
| "unresolved reference R.string.xxx" | A removed string was still used: re-add it to `res/values/strings.xml` as `<string name="xxx">text</string>`. |
| Build error mentioning `BofoPanelSheet` or `SheetBofoBinding` | Something still uses the deleted sheet: search for it in Android Studio (Ctrl+Shift+F) and remove that line, or restore the file with `git checkout -- <path>`. |
| Delete commands fail | The files are already gone: ignore. |
| Anything else broke | `git revert <commit>` brings back v2.0.32. |
