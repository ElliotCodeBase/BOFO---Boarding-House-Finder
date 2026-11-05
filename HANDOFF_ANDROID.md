# Handoff (Android) - v2.9.5: Settings > Notifications tells the truth + admin-only test
- User: notifications still do not arrive and the Settings button must work. The switch used to stay ON even when nothing was registered (no `google-services.json` in the build, OS permission refused, register RPC failing). Now `PushService.registerNowBlocking` registers immediately (skips the 'registered recently' shortcut) and the switch flips back OFF with a reason if it fails; a refused permission opens the phone's notification settings.
- Admin only: new row 'Send test notification' (uses `send_test_push` / `push_test_result`, migration 037, both `is_admin()`-gated) shows the server's answer. Non-admins see one plain line, never server text.
- Still needs the user: apply `037_push_test_admin.sql`; redeploy `send-push` and check `push_settings` (see `docs/NOTIFICATION_FIX_v2.9.2.md`); make sure `bofo-android/app/google-services.json` exists in the build (Settings says 'not set up in this build' otherwise).
- Nothing compiled or run: `gradlew testDebugUnitTest assembleDebug`, then Settings > Notifications on a phone.
- Version 2.9.5 / 20905.

# Handoff (Android) - v2.9.4: Groups list first, group tied to its listing
- User screenshot: owner with 2 listings opened one group chat and saw no other. Fixed: `BoardingFragment` opened `groups.get(0)`; owners (or anyone with 2+ groups) now get the group LIST first (`OwnerBoardingFragment` as child, tab bar kept). `my_bh_groups()` only read `bh_members`, so a house without its owner row never showed: migration 036 backfills owner rows and lists every owned house.
- Delete rule: there is no delete-group code, RPC or policy; groups cascade from `boarding_houses`, so a group goes only when its listing is deleted.
- Waiting for the user's OK: apply `036_owner_groups_list.sql` (idempotent).
- Nothing compiled or run. Version 2.9.4 / 20904.

# Handoff (Android) - v2.9.3: dashed connector legs on routes
- Directions line "cut through buildings": `Router` drew the raw GPS and house points solid. Fix: dashed connectors (`Router.withEnds`, `MapGeo.routeJson`, `MapCardView.paintRoute`). Test: `RouteConnectorsTest`.
- Open: the road itself can still be wrong where OpenStreetMap lacks a path.
- Version 2.9.3 / 20903.
