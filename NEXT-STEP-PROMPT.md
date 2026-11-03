# Step 4 prompt - BOFO AI Announcement Writer: Android service (no UI)

You are continuing the "BOFO AI Announcement Writer" feature. Execute **Step 4 only**. Do not start Step 5.

## Read first (in this order, nothing else unless needed)
1. `BOFO-AI-ANNOUNCE-PROMPT-7-STEPS.md` (master prompt: sections 1-5, Step 4, "Rules that apply to every step"). If the user did not give it to you, say so and use this prompt + the status file.
2. `CLAUDE_ANDROID.md`, then only the top entry of `HANDOFF_ANDROID.md`.
3. `BOFO-AI-ANNOUNCE-STATUS.md` - sections A, B, D, **F (backend contract, error codes)** and **G (Step 3: Java catalogue/rules)**.
4. Only what you touch: `service/BofoAiService.java` (pattern), `service/SupabaseClient.java` (`function(...)`, `ApiException`), `util/Async.java`, `util/Connectivity` (network error check), `util/AnnounceRules.java`, `model/AnnounceType.java`, `bofo-android/app/build.gradle` lines 40-46, one existing service JVM test for style (`BofoAiServiceTest`). **Do not modify `supabase/`.**

## Objective
Add the Android service that calls the Edge Function `bofo-announce` and turns every outcome into a typed result the UI (Steps 5-6) can show. No screen, no layout. The service never posts an announcement (`BhGroupService.postAnnouncement` stays untouched).

## Scope
1. `model/AnnounceDraft.java`: immutable result `{title, message, notes (nullable), degraded}` with a static `fromJson(JSONObject)` that tolerates a missing `notes`, clips defensively to 120 / 1000 (the `post_announcement` limits), and rejects a body without title/message.
2. `service/AnnounceAiService.java`: `generate(AnnounceType, Lang, Tone, Length, emoji, closingOn, closingName, fields, Adjust)` -> builds the body with `AnnounceRules.buildRequest` (validate first, return a typed local error instead of calling the network when the details are invalid), calls `api.function("bofo-announce", body)`, returns a draft. Follow the `BofoAiService` pattern for threading (`Async.run`, main-thread callbacks) and for constructor/test seams (let tests inject a fake `function` call or a fake response; do not hit the network in tests).
3. Error mapping in one pure class (e.g. `AnnounceError` enum + `AnnounceErrors.from(ApiException status, body)`): 401 -> SIGNED_OUT, 403 body `not_owner` -> NOT_OWNER, 403 `ai_disabled` -> AI_DISABLED, 429 -> RATE_LIMITED with `retry_after` seconds (default 30 when absent/bad), 400 -> INVALID (keep the server `field` when present), 405/500/502 and anything >= 500 -> SERVER, network failure (`Connectivity.isNetworkError`) -> OFFLINE, anything else -> GENERIC. Each value maps to an existing string resource name (`announce_err_not_owner|ai_disabled|rate_limited|invalid|offline|generic`; add `announce_err_signed_out` and `announce_err_server` only if you really need them, in `strings_announce_ai.xml`).
4. Behaviour rules: never log field values, passwords or draft text; no retry loop (the owner presses Try again); a degraded draft is a success (`degraded=true`, the UI shows `announce_degraded_note`); ignore a late result if the caller's token changed (provide a cancel/token seam like `AnnounceFragment` uses).
5. Tests (JVM, JUnit 4.13.2 + org.json): `AnnounceDraft.fromJson` (full, no notes, wrong types, over-long, missing fields), every error mapping row above incl. `retry_after` parsing and unknown bodies, request-building path (invalid details never reach the network, valid ones send exactly the backend keys), no-network-in-test seam. Run them the way Step 3 did (JDK compiler API + stand-in runner if Gradle is unavailable; check `which gradle`, `~/.gradle`, real junit jars first) and say exactly what ran.

## Version
Patch bump to **2.4.1 / versionCode 20401** (`bofo-android/app/build.gradle`), replace the old version string everywhere except history, top of `CHANGES_ANDROID.md` and `HANDOFF_ANDROID.md`, PR title.

## Deliverables
- Source + tests + (optional) new strings + version bump.
- Update `BOFO-AI-ANNOUNCE-STATUS.md` (Step 4 row, section H), new top entry in `HANDOFF_ANDROID.md`, `CHANGES_ANDROID.md`, `CHANGED_FILES.txt`, and a new `NEXT-STEP-PROMPT.md` with the complete standalone Step 5 prompt (same structure as this one; Step 5 = sheet stages A and B: type list + details form, `AnnounceAiSheet` as a `BottomSheetDialogFragment`).
- ONE changes-only ZIP (only changed/new files + `CHANGED_FILES.txt`), verified with `unzip -l`, then present it with `present_files`. Include the PowerShell apply block from `CLAUDE_ANDROID.md` section 6.
- Final reply: what was and was not tested, manual commands, and the full next-step prompt as fallback.

## Stop condition
Never deploy, apply a migration, post an announcement, or call the live function without the user's explicit OK. After packaging, **stop and wait**. Do not start Step 5.
