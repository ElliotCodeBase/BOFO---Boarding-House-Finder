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

# v2.9.2 - Notifications now show while the app is closed
- **Fix (server, works at once after a redeploy):** `send-push` now sends a HYBRID message (data + a ready-made notification with the right channel and tap action). The phone's system draws it by itself, so a message still pops when the app is closed, even if the app's own code never runs. While the app is open nothing changes (the app draws the richer chat stack).
- **Fix (app):** a signed-in phone whose saved role was not written yet no longer drops every push silently; taps on system-drawn notifications open the right chat (`PushRouter`, new `OPEN_PUSH` intent-filter); every dropped push is now logged (Logcat filter `BOFO`: "push not shown: ...").
- **Diagnostics:** `send-push` answers with a reason (`no_device_token`, `nobody_to_notify`, `failed` + `why`, or `error: ...`), visible in `net._http_response`. New test: `select public.push_notify('test', jsonb_build_object('as','me','user_id','<uuid>'));` sends a test to one account's phones, any role.
- Also: a private key pasted with real line breaks in `FCM_SERVICE_ACCOUNT` is now accepted; notification accent colour set to the app purple. No migration. Full guide: `docs/NOTIFICATION_FIX_v2.9.2.md`.
- Version 2.9.2 (versionCode 20902). Not compiled or run in the sandbox that made this change (the edge function was type-checked only).
