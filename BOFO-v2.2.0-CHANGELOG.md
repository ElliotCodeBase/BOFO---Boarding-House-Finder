# v2.2.0 - Boarding series, step 1 of 5: database + models + services

## What changed
- `supabase/migrations/024_boarding_groups.sql` (**not applied live yet**): group chat per boarding house, invite codes, members, announcements with pinning (max 3), "Got it" acknowledgments, mute / remove, backfill for existing houses, rules that stop muted, banned and suspended users from posting.
- `model/GroupMessage.java`, `model/BhMember.java`, `model/BhGroup.java`, `service/BhGroupService.java`: ready for the screens of steps 2-5.
- `app/build.gradle`: 2.2.0 (versionCode 20200).

## What you will notice
Nothing yet: no screen uses the new code. The Boarding tab arrives in step 2.

## Not run
Not built or run by Claude. Java checked with `javac` against stubs only; the SQL was not executed.

## Step 2 of 5: student Boarding screen
- New **Boarding** tab (centre of the student bar): join with the owner's code, then the group chat of your house with sender names, Owner / Student badges, times, offline sending and a Leave group option.
- **Saved** moved to Profile > Saved places.
- New files: `BoardingFragment`, `BoardingChatFragment`, `BoardingRules` (+ test), 3 layouts, dock icon, role badge drawables / colours, `strings_boarding.xml`. Changed: `Outbox` (queues group messages), `ShellFragment`, `ProfileFragment`, `SavedFragment`, `nav_graph.xml`, `fragment_saved.xml`, `fragment_profile.xml`, `ids_dock.xml`.
- Needs migration 024 applied before the Boarding tab loads. Not built or run by Claude (no Android SDK); Java syntax-checked with `javac`, rules tests run on a plain JVM.

## Step 3 of 5: announcements, pinned banner, report a message
- Announcement card in the group chat with a **Got it** button (done state "You got it"); the owner sees "Seen by N members".
- **Pinned strip** under the header (max 3 pinned, unanswered first, Got it inside, tap to read all). Refreshes about every 15 s.
- Long press somebody else's message > **Report message**: reason list, optional details, the message text is quoted for the admin. Uses table `reports`; no database change.
- Changed: `BoardingChatFragment`, `ReportDialog`, `BoardingRules` (+ tests), `item_group_msg.xml`, `fragment_boarding_chat.xml`, `strings_boarding.xml`. New: `ic_announce`, `bg_announcement`, `bg_pinned_banner`.
- Not built or run by Claude (no Android SDK). The rules tests (15) pass on a plain JVM; the two Java screens were only syntax-checked with `javac`; layouts / strings / ids cross-checked by script.
- Apply step 2 (its PR) first: step 3 changes files that step 2 created.
