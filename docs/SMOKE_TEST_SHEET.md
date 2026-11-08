# BOFO Admin: phone test sheet (v2.12.2)
Tick **P** (pass) or **F** (fail) and write a note. Send me every F row. Needs the release build installed, test accounts with the roles below, and SQL 038-046 applied. Base list: `RELEASE_CHECKLIST.md` sections 8 and 9.
Roles in the code: Owner, Admin, Moderator, Support (there is no separate "ID reviewer": IDs belong to Owner, Admin, Moderator).

## A. TalkBack and display (do once, any role; Settings > Accessibility > TalkBack on)
| # | Step | Expected | P/F | Notes |
|---|------|----------|-----|-------|
| A1 | Swipe through the Home screen | Each stat is read as label then number; "Needs a look" is read when shown | | |
| A2 | Swipe the bottom bar | Home, IDs, Reports, Approvals, More are read by name, selected one says selected | | |
| A3 | On Reports, swipe over the filter chips | Each says its name and "Selected" or "Not selected" | | |
| A4 | Swipe to a report card | Read as "button"; double-tap opens it | | |
| A5 | Headings: TalkBack menu > Headings, swipe down | Jumps from title to title on each screen | | |
| A6 | Settings > Display > Font size and Display size to the largest; open IDs, Reports, an ID review, Approvals, Team | No button cut off; ID review buttons stack; Reports chips scroll sideways; no text overlaps | | |
| A7 | Lock the phone for over 5 minutes, open the app | "BOFO Admin is locked" is read; Unlock asks for fingerprint / PIN | | |
| A8 | Approve or reject one ID | "Approved." / "Rejected." (toast text) is spoken | | |
| A9 | Saved reply in ID review: swipe to a "Mine:" button, open TalkBack actions | "Delete this saved reply" is offered and asks to confirm | | |

## B. Every role (repeat for Owner, Admin, Moderator, Support)
| # | Step | Expected | P/F | Notes |
|---|------|----------|-----|-------|
| B1 | Sign in with Google | Role label and e-mail shown on top; only that role's tabs exist | | |
| B2 | Two-step enabled: sign in | Asks for the 6-digit code; a wrong code says "That code did not work"; right code enters | | |
| B3 | Home | Numbers load; no raw error text | | |
| B4 | Turn on flight mode, pull a list | Plain sentence "No connection. Check the internet and try again."; no crash | | |
| B5 | Leave the app 5+ min, return | Locked screen; unlock works; sign out works | | |
| B6 | Phone has no screen lock | Asked to set one; app signs out for safety | | |
| B7 | Admin account in BOFO Finder (student app) | Looks like a student app; no admin push arrives there | | |
| B8 | Open the audit view (Activity) | Shows only the person's own actions (Owner: everyone's) | | |
| B9 | Take a push (queue item, escalation, decision) | Tap opens the matching tab (only if the role has it) | | |

## C. Owner
| # | Step | Expected | P/F | Notes |
|---|------|----------|-----|-------|
| C1 | More | Team, Payments, Banned, Appeals, Unusual activity, AI screens, Export CSV, 2-step all listed | | |
| C2 | Team > Invite someone | Code shown once; invited person can use it only with the invited e-mail | | |
| C3 | Team > person > suspend, then re-activate | Status pill changes; person is refused at the next call while suspended | | |
| C4 | Team > lend a permission (1 hour), then "Lent right now" > end it | Borrower gets it, loses it at the end | | |
| C5 | Payments > a paid row > refund with reason under 5 characters | Refused with a message; a valid reason marks refunded | | |
| C6 | Export CSV | Asks for fingerprint / PIN first; file can be shared | | |
| C7 | Appeals > accept one (ban appeal) | Ban lifted, person notified | | |
| C8 | Banned > Unban with a reason | Asks for a reason; person removed from the list | | |
| C9 | AI settings > turn off / on | Asks to confirm; pill changes ON / OFF | | |
| C10 | AI settings > Delete all | Must type DELETE; anything else is refused | | |

## D. Admin
| # | Step | Expected | P/F | Notes |
|---|------|----------|-----|-------|
| D1 | IDs > open one | "Being handled by" claim set; photos load; closing releases it | | |
| D2 | IDs > Reject with a saved reply chip | Reason filled; needs 5+ characters | | |
| D3 | Reports > suspend for 3 days; then for 30 days | 3 days works; 30 days works (has suspend_long) | | |
| D4 | Reports > strike | Message shows the step (warning, 3 d, 7 d, ban) | | |
| D5 | Reports > ban | Becomes a request for approval (no direct ban) | | |
| D6 | More | NO Team, Payments, Export CSV | | |
| D7 | Banned list | Visible, no Unban button | | |
| D8 | Appeals about a decision this admin made | Cannot decide it (message says so) | | |
| D9 | Approvals | Can approve a request from someone else; cannot approve own | | |

## E. Moderator
| # | Step | Expected | P/F | Notes |
|---|------|----------|-----|-------|
| E1 | Tabs | Home, IDs, Reports, Approvals; More shows only allowed screens | | |
| E2 | Reports > short suspension (up to 7 days) | Works | | |
| E3 | Reports > longer suspension, ban, take down | Sent as an approval request; "Waiting for an approver" shows | | |
| E4 | An Admin approves / denies it | Moderator gets a push; status shows approved / denied | | |
| E5 | Open the same item on two phones | Second phone shows "Being handled by ..." | | |
| E6 | Escalate with a 2-word note | Refused; 5+ characters works; "Escalated" chip shows it | | |

## F. Support (read only)
| # | Step | Expected | P/F | Notes |
|---|------|----------|-----|-------|
| F1 | Tabs | Home, Reports; no IDs, no Approvals | | |
| F2 | Reports | Can read; every action button hidden or answers "Your role cannot do this." | | |
| F3 | More | Banned (read), AI needs attention, own audit only | | |
| F4 | Try to open Team, Payments, Export | Not listed | | |

## G. No access and expired
| # | Step | Expected | P/F | Notes |
|---|------|----------|-----|-------|
| G1 | Google account with no role | "No access" screen, no data | | |
| G2 | Same account with an invite code for another e-mail | Refused with the "not valid for this account" sentence | | |
| G3 | Owner suspends a team member who is signed in | Their next action says "Your role cannot do this." or signs them out | | |
| G4 | Session expired (sign out on the Supabase side) | "Your session ended. Sign in again." and back to login | | |
| G5 | Release build | No student / owner / admin test-login buttons anywhere | | |
