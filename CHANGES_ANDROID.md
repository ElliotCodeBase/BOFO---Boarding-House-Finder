# v2.4.13 - Admin lists and Reports: filters, search, grouping, clearer states (Admin UI step 4 of 5)
- **Queue, Reports and Payments** now share one look: filter chips **Waiting / Done / All**, a search box, and a status in words on a coloured pill (Waiting, Approved, Rejected, Paid, Failed, ...). Waiting items are shown oldest first, with "Waiting 3 days".
- **Loading and errors.** A skeleton shows while a list loads for the first time. If you are offline (or a refresh fails) you keep the last list and see a banner; with nothing to show you get a plain sentence and a **Try again** button.
- **Reports.** Each row shows the reported place's photo, its name and the start of what was written. Waiting reports about the same person and place are grouped into one row ("2 reports"); the details list the others. When you suspend, ban or dismiss a grouped row, the other waiting reports in it are marked handled too. The title still opens the finer filters (Suspended, Dismissed, Taken down, Banned users).
- **Admin Home.** Cards now open the right list already filtered: IDs > Queue Waiting, Reports > Waiting, Payments > Waiting (pending or failed), Suspended > Reports answered with a suspension, Taken down > Reports answered with a take down.
- How to get there: Admin > Home > any card, or the Queue / Payments / Reports tabs.
- If something goes wrong: pull down to refresh, or tap Try again. A grouped row whose other reports could not be marked handled shows them again after the refresh.
- Version 2.4.13 (versionCode 20413). No server change. Not compiled or run on a device in the sandbox that made this change.

# v2.4.12 - Safer admin decisions, Undo for bans, long-press quick actions (Admin UI step 3 of 5)
- **Confirm only what is serious.** Taking down a place now shows one clear dialog that names the place and says it cannot be undone from the app. Refunding a payment shows one dialog with the amount and the owner. Ban and suspend keep their existing confirmations. Unban is now a single dialog (name + optional note) instead of two.
- **Undo.** After a ban a message with **Undo** stays for 5 seconds. Undo unbans the account (places that were taken down stay down).
- **Long-press a row.** Queue: Open or Reject. Reports: Open, Dismiss or Actions. Payments: Open or Refund. A normal tap works as before.
- How to get there: Admin > Queue / Reports / Payments > press and hold a row.
- If something goes wrong: if Undo does not work (offline) use Reports > filter > Banned users > Unban.
- Version 2.4.12 (versionCode 20412). No server change. Not compiled or run on a device in the sandbox that made this change.

# v2.4.11 - Admin ID review screen (Admin UI step 2 of 5)
- **One photo at a time, one tap to switch.** ID front / ID back / Selfie buttons switch the large photo instantly. Pinch to zoom (up to 5x), drag when zoomed, double-tap to reset.
- **Big buttons at the bottom.** Reject (left) and Approve (right) stay fixed at the bottom, easy to reach with the thumb. Approve still asks you to type the ID number from the photo.
- **Reject with a reason.** Pick Photo is blurry, ID is expired, Face does not match, or Other, and add an optional note (Other needs a note).
- **Who decided.** If the request was already decided (for example by another admin), the screen shows "Approved/Rejected by NAME on DATE" with the reason, and the buttons are hidden.
- How to get there: Admin > Queue > tap a request.
- If something goes wrong: a photo that cannot load shows a message above the photo; the decision line is simply missing if it cannot be read (offline).
- Version 2.4.11 (versionCode 20411). No server change. Not compiled or run on a device in the sandbox that made this change.

# v2.4.10 - Admin Home tab, tab badges, calmer admin top area (Admin UI step 1 of 5)
- **Admin Home.** New first tab for admins. One card each for: IDs waiting for review, open reports, payments to check (pending or failed), unanswered AI questions (last 7 days), suspended users, banned users, listings taken down. Cards with waiting items show "Oldest waiting 3 days". Tap a card to open that list; pull down to refresh. When nothing is waiting it says "All caught up".
- **Badges.** The Queue tab shows how many IDs are waiting; the Reports tab shows open reports.
- **Top area.** Title, today's date and an amber "Admin" tag.
- How to get there: sign in as admin > Home (first tab).
- If something goes wrong: a card shows "-" and "Could not load" when its number cannot be read (offline or no access); pull down to try again.
- Version 2.4.10 (versionCode 20410). No server change. Not compiled or run on a device in the sandbox that made this change.
- Audit of the admin screens: `docs/ADMIN_UI_AUDIT.md`.

# v2.4.9 - Floating Announce button, Messages tab, Listings moved into the Dashboard (owner)
- **Announce button.** In your house group chat the small megaphone next to the message box is gone. There is now a floating **Announce** button at the bottom right of the chat (owners only). Tap it to open the Announce form; **Write for me** is still inside the form.
- **Messages tab.** For owners the bottom bar is now Dashboard, **Messages**, Create, Groups, Profile. Messages is your direct-chat list with students (with its unread badge). The Groups tab badge now counts only group messages. The messages icon next to Announce in the Groups header is removed.
- **Listings moved to the Dashboard.** The Listings tab is gone. The Dashboard now starts with **My places**: **Add a place**, **Boost** and **Payments**. Tap any place card to view, edit, boost, unlist / list again or delete it; unlisted or taken-down places show a grey tag.
- How to get there: owner > Dashboard > Add a place / tap a place; owner > Messages tab; owner > Groups > a house > floating Announce button.
- If something goes wrong:
  - Announce button missing: you are not the owner of this house, or the chat has not finished loading.
  - Can't find My places: it is at the top of the Dashboard.
  - Place tags missing: the status could not be read (offline); pull down to refresh.
- Version 2.4.9 (versionCode 20409). No server change. Not compiled or run on a device in the sandbox that made this change: build and test it (`gradlew testDebugUnitTest`, `gradlew assembleDebug`) before releasing.

# v2.4.8 - Calmer Announce button, notice-style announcement card, Boarding groups in the bottom bar (owner)
- **Announce button.** The big floating orange button and its two stacked choices are gone. In your house group chat there is now a small round megaphone button right beside the message box (owners only). Tap it to open the Announce form. If BOFO AI is on, **Write for me** is inside that form, so AI is an option and no longer the first thing you see.
- **Announcement card.** In the chat an announcement is now a plain white notice: a small grey "Announcement" label, **Pinned** as plain text, the title, the message, then "Seen by N" for you or a text **Got it** for members. No purple border, no icon, no pills.
- **Boarding groups moved.** For owners the bottom bar is now Dashboard, Listings, Create, **Groups**, Profile. Groups replaces Chats and shows the unread count of your groups plus your direct chats. Your direct chats with students open from the messages icon at the top of the Groups screen. Profile no longer has a Boarding groups row. Students are unchanged.
- How to get there: owner > **Groups** tab > open a house > megaphone button next to the message box.
- If something goes wrong:
  - Megaphone missing: you are not the owner of this house, or the chat has not finished loading.
  - Can't find your chats with students: Groups tab > messages icon (top right).
  - A notification for a student message opens the chat on top of the current tab; Back returns to it.
- Version 2.4.8 (versionCode 20408). No server change. Not compiled or run on a device in the sandbox that made this change: build and test it (`gradlew testDebugUnitTest`, `gradlew assembleDebug`) before releasing.

# v2.4.7 - Floating Announce button in the group chat + searchable announcement type
- **Floating Announce button (owner only).** In your house group chat there is now an **Announce** button at the bottom right, just above the message box. Tap it: two choices slide up, **Write with AI** (BOFO AI writes the draft) and **Write it myself** (type your own title and message). Tap the dimmed chat, the X or Back to close it. It shrinks to an icon while you scroll down and hides while you type. If an admin switched BOFO AI off, the button opens the manual Announce screen directly.
- The 3-dot menu of the group chat no longer has "Write announcement with AI" and "New announcement" (they are the new button now). Pinned announcements, Group info and invite code, and Leave group stay.
- **Announcement type is now a searchable dropdown** in the Write-with-AI sheet. The sheet opens straight on the form with one **Announcement type** field; tap it, type a word (for example "water") and pick. The first time, the dropdown opens by itself. Changing the type clears the details you typed but keeps language, tone, length and emoji. The old full list of types is gone.
- How to get there: owner > house group chat > floating **Announce** button > **Write with AI** > **Announcement type**.
- If something goes wrong:
  - Button missing: you are not signed in as the owner of this house, or the chat is still loading / offline / the keyboard is open. (Missing **Write with AI** only: the admin switch **BOFO AI for users** is OFF.)
  - Dropdown looks empty: the search box still has text. Tap the x in the search box to clear it.
  - Choices or dimmed screen stuck: press Back or tap the dimmed area; leaving the chat also closes it.
- Version 2.4.7 (versionCode 20407). No server change. Not compiled or run on a device in the sandbox that made this change: build and test it (`gradlew testDebugUnitTest`, `gradlew assembleDebug`) before releasing.


_Older entries: see docs/CHANGES_ANDROID_ARCHIVE.md_
