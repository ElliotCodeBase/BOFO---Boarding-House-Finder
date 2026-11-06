# Handoff (Android) - v2.9.13: admin app AI chats + settings (Stage 4 part 6)
- See `STATE_STAGE4f.md`. No migration. Migrations 042 (push) and 043 (refunds) may still be unapplied.
- Nothing compiled or run: from `bofo-android`: `.\gradlew :shared:testDebugUnitTest :app-admin:assembleDebug`.
- Next: strikes / escalate / saved replies (migrations), or two-step enrolment in the admin app, then Stage 5. Version 2.9.13 / 20913.

# Handoff (Android) - v2.9.12: admin app refunds (Stage 4 part 5)
- See `STATE_STAGE4e.md`. Migration 043 written, NOT applied (waiting for the user's OK). Migration 042 (push) may also still be pending.
- Nothing compiled or run: `gradlew :shared:testDebugUnitTest :app-admin:assembleDebug`.
- Next: AI chat log + AI settings in the admin app, or strikes / escalate / saved replies (migrations). Version 2.9.12 / 20912.


# Handoff (Android) - v2.9.11: admin app push (Stage 4 part 4)
- See `STATE_STAGE4d.md`. Migration 042 written, NOT applied (waiting for the user's OK); `send-push` must be redeployed; admin app needs its own `google-services.json`.
- Nothing compiled or run: `gradlew :shared:testDebugUnitTest :app-admin:assembleDebug`.
- Next: strikes / escalate / saved replies (migrations), or Stage 5. Version 2.9.11 / 20911.


