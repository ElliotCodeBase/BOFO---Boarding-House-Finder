# v2.12.9 - BOFO Admin UX pass 2: More redesigned, tappable Home cards, loading and retry, press feedback, back button
- `MoreScreen`: scrolls (last rows were cut off), grouped (Your account / Moderation / Money / AI / Team and data), icon + one-line help + arrow per row, `< More` bar instead of a text button, app version at the bottom. Phone back button inside More goes up one level instead of leaving the app (`MainActivity` back callback + `MoreScreen.up`).
- Home: ID and Report cards open their tab (`MainActivity.goTab`); a card that needs a look gets a red outline plus the words. Reports / IDs / Home / Approvals show a spinner on first load and a plain-words message with Try again if it fails (`Ui.loading`, `Ui.fail`). Approvals: pull to refresh, centred empty state. Tappable cards get a ripple (`Ui.ripple`). Both apps 2.12.9 / 21209. Not compiled or seen on a phone.

# v2.12.8 - BOFO Admin look: outlined filter chips, real bottom-bar icons with names always shown, role pill + e-mail header, centred empty states
- `Ui.chip` (outlined, selected = tint + bold), `Ui.empty`, 5 new vector icons `ic_nav_*`, hairline above the bottom bar. Used on Reports, IDs, AI needs attention, Unusual activity, Activity. (v2.12.8 was unzipped into `bofo-android/` by mistake and never reached `app-admin/`; v2.12.9 carries it.)

# v2.12.7 - Build fix: `TwoStepScreen.onVisibilityAggregated` must be public (v2.12.6 did not compile). Both apps 2.12.7 / 21207.
