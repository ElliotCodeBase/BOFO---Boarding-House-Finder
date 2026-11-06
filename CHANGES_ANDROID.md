# v2.9.11 - BOFO Admin app: push notifications
- The admin app now gets its own push (needs migration `042_admin_push.sql`, redeploy `send-push`, `google-services.json` in `app-admin/`). Reports, IDs, daily reminder and a new "approval needs you" go to staff by permission; tap opens the right tab.
- Version 2.9.11 (versionCode 20911). Not compiled or run in the sandbox that made this change.

# v2.9.4 - Groups list first, group tied to its listing
- Opening Groups/Boarding showed one chat (the first group) and hid the rest. Owners (and anyone with 2+ groups) now see the list of all their groups first; tap one to open its chat.
- Migration `036_owner_groups_list.sql` (run it): every listing's group is listed even if its owner row was missing.
- A group chat can only go away by deleting its listing (no separate delete exists).
- Version 2.9.4 (versionCode 20904). Not compiled or run in the sandbox that made this change.


# v2.9.3 - Route line no longer looks like it cuts through buildings
- The route used to be one solid line from your exact dot to the exact pin. The first and last stretch (your dot to the nearest road, the nearest road to the house) cross open ground and buildings, but looked like a real path. Now those two connector legs are drawn **dashed**; only the road part is solid.
- `Router`: only the road part is simplified now, so the first and last segment are always exactly the two connectors. `MapGeo.routeJson` draws them dashed (under 5 m stays solid); estimates and short paths are drawn as before. `MapCardView` uses it everywhere a route is shown.
- Not changed: which road is chosen (that comes from the OpenStreetMap routing server and its map data). A long dashed leg means OpenStreetMap has no walkable path near that spot.
- Version 2.9.3 (versionCode 20903). Not compiled or run in the sandbox that made this change. New test: `RouteConnectorsTest`.

