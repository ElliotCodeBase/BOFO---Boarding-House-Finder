# v2.9.6 - Send test notification shows its result where you tap
- The test result used to appear at the top of Settings (off-screen). It is now a pop-up; the row shows 'Sending...' while it runs. Notifications switch messages also show a short Toast.
- Version 2.9.6 (versionCode 20906). Not compiled or run in the sandbox that made this change.

# v2.9.5 - Settings > Notifications works and says why when it cannot
- The Notifications switch now registers this phone immediately and only stays ON when that worked. If not, it flips back OFF and tells you why (not set up in this build, phone permission refused, signed out, server error). A refused permission opens the phone's notification settings.
- Admin only: 'Send test notification' row in Settings shows the push server's answer. Regular users never see server text or logs.
- Migration `037_push_test_admin.sql` (run it, admin-gated). Version 2.9.5 (versionCode 20905). Not compiled or run in the sandbox that made this change.
