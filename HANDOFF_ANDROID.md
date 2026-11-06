# Handoff (Android) - v2.9.11: admin app push (Stage 4 part 4)
- See `STATE_STAGE4d.md`. Migration 042 written, NOT applied (waiting for the user's OK); `send-push` must be redeployed; admin app needs its own `google-services.json`.
- Nothing compiled or run: `gradlew :shared:testDebugUnitTest :app-admin:assembleDebug`.
- Next: strikes / escalate / saved replies (migrations), or Stage 5. Version 2.9.11 / 20911.

# Handoff (Android) - v2.9.4: Groups list first, group tied to its listing
- User screenshot: owner with 2 listings opened one group chat (MOLAS BOARDING, 1 member) and saw no other. Two causes fixed: (1) `BoardingFragment` opened `groups.get(0)`; owners (or anyone with 2+ groups) now get the group LIST first (`OwnerBoardingFragment` as child, tab bar kept). (2) `my_bh_groups()` only read `bh_members`, so a house without its owner row never showed: migration 036 backfills owner rows and lists every owned house.
- Delete rule: there is no delete-group code, RPC or policy; groups cascade from `boarding_houses`, so a group goes only when its listing is deleted. Nothing to add.
- Waiting for the user's OK: apply `036_owner_groups_list.sql` (idempotent). Migration 035 (moderator split) is still the earlier open item.
- Nothing compiled or run: `gradlew testDebugUnitTest assembleDebug`, then Groups tab with 2 listings.
- Version 2.9.4 / 20904.


# Handoff (Android) - v2.9.3: dashed connector legs on routes
- User screenshot (Directions, Me -> BH): the line "cuts through buildings". Cause: `Router` prepended the raw GPS point and appended the raw house point to the road geometry, and drew them solid. Fix: dashed connectors (`Router.withEnds`, `MapGeo.routeJson`, `MapCardView.paintRoute`). Test: `RouteConnectorsTest`.
- Open: the road itself can still be wrong where OpenStreetMap lacks a path; a better snap would need a self-hosted router or a place-snap step. Dash style is the same as the estimate dash (layer `route-dash`).
- Nothing compiled or run: `gradlew testDebugUnitTest assembleDebug`, then open Directions > Me -> BH on a phone.
- Version 2.9.3 / 20903.

