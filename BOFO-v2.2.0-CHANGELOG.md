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

## Step 4 of 5: owner Boarding groups + Announce form
- **Profile > Boarding groups** (owners): list of the owner's houses with members, pinned count, last message and unread badge; tap opens the group chat.
- Owner tools in the chat: Pin / Unpin / Edit / Delete on his announcements, Delete on any message (with confirm), menu > New announcement.
- **Announce form**: title, message, tick houses (one or many, Select all). Posted announcements are pinned in each chosen house.
- New: `OwnerBoardingFragment`, `AnnounceFragment`, 2 layouts, `ic_set_boarding`. Changed: `BoardingChatFragment`, `BoardingRules` (+ tests), `ProfileFragment`, `fragment_profile.xml`, `nav_graph.xml`, `strings_boarding.xml`.
- Not built or run by Claude (no Android SDK). 18 rules tests pass on a plain JVM; screens syntax-checked with `javac`; resources / view ids cross-checked by script. Needs migration 024 live. No Supabase change in this step.
- Apply steps 2 and 3 first.

## Step 5 of 5: invite code, members, announcement notice (series finished)
- **Group info** (owner, chat menu > Group info and invite code): invite code with Copy / Share / New code (confirm first), and the member list with Mute / Unmute and Remove (confirm first). Loading, empty and error + Try again states.
- **Students:** unread count on the Boarding tab, and a notice bar above the tab bar for owner announcements not yet answered with Got it (once per announcement per app run, Open goes to the Boarding tab).
- The owner's empty chat now says to share the invite code.
- New: `GroupInfoFragment`, `fragment_group_info.xml`, `item_group_member.xml`. Changed: `BoardingChatFragment`, `ShellFragment`, `DockBarView`, `BhGroupService`, `BoardingRules` (+ tests), `nav_graph.xml`, `strings_boarding.xml`.
- Not built or run by Claude (no Android SDK). 22 rules tests pass on a plain JVM (org.json and BhGroupService stubbed); the 3 touched screens were checked with `javac` (only missing-Android-class errors remain); strings, view ids and binding fields cross-checked by script. Needs migration 024 live (apply 023 first). No Supabase change in this step.
- Known limits: owner has no unread badge; no brute-force limit on code guesses; a removed student cannot be restored; the notice bar appears when the main screen resumes.
- Apply steps 2-4 first.

## Polish: announcement card + pinned bar
- Announcement card redone as a plain white card (no amber, no speaker icon, no tick). Footer: Got it button, "You acknowledged this", or "Seen by N members" for the owner.
- The top bar now only shows announcements that still wait for your Got it, and disappears after pressing it. The 3-dot menu has **Pinned announcements (N)** with the full list.
- Changed: `item_group_msg.xml`, `fragment_boarding_chat.xml`, `bg_pinned_banner.xml`, `strings_boarding.xml`, `BoardingChatFragment`, `BoardingRules` (+ test); new `bg_ann_card.xml`. `ic_announce` is no longer used by the chat. Not built or run (no Android SDK); 23 rules tests pass on a plain JVM.
