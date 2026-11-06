# v2.9.12 - BOFO Admin app: refunds
- Owner can tap a PAID payment in Admin > More > Payments to mark it refunded (reason, two confirmations, fingerprint). It ends the boost; the money is still refunded in PayMongo.
- Migration `043_refund_hardening.sql` (run it): only a paid payment can be refunded, reason required.
- Version 2.9.12 (versionCode 20912). Not compiled or run in the sandbox that made this change.

# v2.9.11 - BOFO Admin app: push notifications
- The admin app now gets its own push (needs migration `042_admin_push.sql`, redeploy `send-push`, `google-services.json` in `app-admin/`). Reports, IDs, daily reminder and a new "approval needs you" go to staff by permission; tap opens the right tab.
- Version 2.9.11 (versionCode 20911). Not compiled or run in the sandbox that made this change.


# v2.9.4 - Groups list first, group tied to its listing
- Opening Groups/Boarding showed one chat (the first group) and hid the rest. Owners (and anyone with 2+ groups) now see the list of all their groups first; tap one to open its chat.
- Migration `036_owner_groups_list.sql` (run it): every listing's group is listed even if its owner row was missing.
- A group chat can only go away by deleting its listing (no separate delete exists).
- Version 2.9.4 (versionCode 20904). Not compiled or run in the sandbox that made this change.


