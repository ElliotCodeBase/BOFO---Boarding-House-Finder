# v2.9.5 - Settings > Notifications works and says why when it cannot
- The Notifications switch now registers this phone immediately and only stays ON when that worked. If not, it flips back OFF and tells you why (not set up in this build, phone permission refused, signed out, server error). A refused permission opens the phone's notification settings.
- Admin only: 'Send test notification' row in Settings shows the push server's answer. Regular users never see server text or logs.
- Migration `037_push_test_admin.sql` (run it, admin-gated). Version 2.9.5 (versionCode 20905). Not compiled or run in the sandbox that made this change.

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
