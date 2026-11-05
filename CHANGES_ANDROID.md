# v2.9.2 - Notifications now show while the app is closed
- **Fix (server, works at once after a redeploy):** `send-push` now sends a HYBRID message (data + a ready-made notification with the right channel and tap action). The phone's system draws it by itself, so a message still pops when the app is closed, even if the app's own code never runs. While the app is open nothing changes (the app draws the richer chat stack).
- **Fix (app):** a signed-in phone whose saved role was not written yet no longer drops every push silently; taps on system-drawn notifications open the right chat (`PushRouter`, new `OPEN_PUSH` intent-filter); every dropped push is now logged (Logcat filter `BOFO`: "push not shown: ...").
- **Diagnostics:** `send-push` answers with a reason (`no_device_token`, `nobody_to_notify`, `failed` + `why`, or `error: ...`), visible in `net._http_response`. New test: `select public.push_notify('test', jsonb_build_object('as','me','user_id','<uuid>'));` sends a test to one account's phones, any role.
- Also: a private key pasted with real line breaks in `FCM_SERVICE_ACCOUNT` is now accepted; notification accent colour set to the app purple. No migration. Full guide: `docs/NOTIFICATION_FIX_v2.9.2.md`.
- Version 2.9.2 (versionCode 20902). Not compiled or run in the sandbox that made this change (the edge function was type-checked only).

# v2.9.1 - Build fix
- `AdminRowAdapter.isEmpty()` is now `public` with `@Override` (a weaker access does not compile: the build failed on it). This was the v2.5.0 fix that never reached the repo.
- Version 2.9.1 (versionCode 20901).

# v2.9.0 - Auto-lock for admins (Admin next steps, step 5 of 5, part 1)
- **Auto-lock.** After 5 minutes or more in the background, the admin tools are covered and the 6-digit code is asked again. What you were typing is kept (the screens are only hidden). Skipped when the account has no two-step sign-in. If you lost the authenticator, "Sign out instead" is on the lock sheet. A rotation or an app restart does not unlock.
- **Settings (admin only):** switch "Lock after 5 minutes away", default on, with a one-line hint.
- **Not done: moderator role split (item 13).** It needs migration `035_admin_level.sql` (column `admin_level`, server checks in the ban / unban / suspend / take down / refund functions, self-assign guard). Waiting for the owner's OK.
- Docs: `ADMIN_GUIDE.md` and `ADMIN_UI_AUDIT.md` updated. No server change.
- Version 2.9.0 (versionCode 20900). Not compiled or run in the sandbox that made this change.

