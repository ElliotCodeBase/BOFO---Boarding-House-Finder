# v2.12.8 - BOFO Admin look: outlined filter chips, real bottom-bar icons with names always shown, role pill + e-mail header, centred empty states
- `Ui.chip` (outlined, selected = tint + bold), `Ui.empty`, 5 new vector icons `ic_nav_*`, hairline above the bottom bar. Used on Reports, IDs, AI needs attention, Unusual activity, Activity. Both apps 2.12.8 / 21208. Not compiled or seen on a phone.

# v2.12.7 - Build fix: `TwoStepScreen.onVisibilityAggregated` must be public (v2.12.6 did not compile). Both apps 2.12.7 / 21207.

# v2.12.6 - BOFO Admin: screenshots allowed except on sign-in, invite code, two-step code / key and the lock screen
- Was: whole app FLAG_SECURE. Now `MainActivity.protect()` turns it on for those screens only. The ID review window (IdScreen) is its own window and stays protected: it shows ID photos and selfies. Both apps 2.12.6 / 21206. Not compiled or run on a phone.

# v2.12.5 - Database fix: Approvals tab error "UPDATE is not allowed in a non-volatile function"
- `admin_approvals_list()` is no longer marked STABLE (migration 047, applied live; 039 corrected). Scanned all live functions: no other stable function writes. Both apps 2.12.5 / 21205.

# v2.12.4 - BOFO Admin: tabs no longer blank out and reload on every tap
- Each tab keeps its screen alive (show / hide); a tab is rebuilt after 3 minutes, when More is reopened, or when a push opens it. Home loads its 9 numbers at the same time instead of one after another. Both apps 2.12.4 / 21204. Not compiled or run on a phone.

# v2.12.3 - BOFO Admin: Google sign-in failure now shows the real reason and the package name (to find a missing Android OAuth client / SHA-1). Both apps 2.12.3 / 21203. Not compiled.

# v2.12.2 - Verification Stage 5: BOFO Admin accessibility (TalkBack, touch size, contrast, large font)
- Admin screens are built in code, so the fixes sit in `Ui` and are reused: headings for TalkBack, every button at least 48 dp tall, tappable cards announced as buttons (`Ui.asButton`), filter chips say "Selected / Not selected" (`Ui.chip`), button rows stack at font scale 1.3+ (`Ui.actions`), the Reports filter chips scroll sideways.
- Each new full screen (login, no access, locked, two-step) sets a pane title so TalkBack says where you are. Saved replies: "Delete this saved reply" is now also a TalkBack action (was long-press only).
- Admin colours: success `#146C43`, error `#B3261E` so text on the tints and on white passes 4.5:1 (admin `colors.xml` only; BOFO Finder unchanged).
- New `docs/SMOKE_TEST_SHEET.md` (phone test per role) and `tools/verify/check_a11y.py`. Both apps 2.12.2 / 21202. Not compiled or run on a device or with TalkBack.

# v2.12.1 - Verification Stage 4: Google nonce, re-auth before delete, release gate for test logins
- S-07: user-app Google sign-in sends a nonce (Google gets the SHA-256, Supabase the raw value), same pattern as the Admin app. New `util/Nonce` (+ test).
- S-14: "Delete my account" now asks you to prove it is you first: Google accounts sign in with Google again, others get an e-mail code. A sign-in for a different account deletes nothing. New `util/ReauthRules` (+ test).
- S-04: `AppConfig.devEmail/devPassword` return empty and `AuthService.devSignIn` refuses unless `BuildConfig.DEBUG`; `tools/verify/check_release_dev.py` checks `build.gradle` too.
- Both apps 2.12.1 / 21201. Not compiled or run on a device. Needs Supabase Google provider "Skip nonce checks" OFF.

# v2.12.0 - BOFO Admin: Stage 4i rebuilt (safety features)
- Admin app: queue claiming ("Being handled by ..."), waiting-time colours (green / amber / red), Escalate (+ Escalated chip, Clear), strikes ladder (warning, 3 d, 7 d, ban), Appeals screen, break-glass (Owner lends one permission for 1 hour), recent sign-ins per person, Unusual activity screen, pushes for escalated / decided / unusual.
- User app: banned and suspended screens get "Appeal this decision" (one appeal per decision, decided by a different team member).
- Migration `044_admin_stage4_safety.sql` (NOT applied) and a `send-push` redeploy are needed. New `shared/QueueRules` (+ tests), `PushRules` has 3 new kinds. Not compiled or run on a device.
