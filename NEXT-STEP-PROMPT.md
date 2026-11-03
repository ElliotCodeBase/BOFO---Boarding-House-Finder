# Step 3 prompt - BOFO AI Announcement Writer: Android catalogue + rules (no UI, no network)

You are continuing the "BOFO AI Announcement Writer" feature. Execute **Step 3 only**. Do not start Step 4.

## Read first (in this order, nothing else unless needed)
1. `BOFO-AI-ANNOUNCE-PROMPT-7-STEPS.md` (master prompt: sections 1-5, Step 3, "Rules that apply to every step"). It was NOT available in Step 2: if the user did not give it to you, say so and use this prompt + the status file.
2. `CLAUDE_ANDROID.md`, then only the top entry of `HANDOFF_ANDROID.md`.
3. `BOFO-AI-ANNOUNCE-STATUS.md` - sections A, B, D and **F (Step 2 result: the canonical contract)**.
4. `supabase/functions/bofo-announce/catalogue.ts` (canonical ids, field keys, required flags, hints, urgency, default tone, emoji, `oneOf`) and `clean.ts` lines 1-70 (validation rules). **Do not modify any file under `supabase/`.**
5. Only what you touch: `bofo-android/app/build.gradle` lines 40-46, `util/AppPrefs.java`, `util/BoardingRules.java` (style reference), one existing JVM test for style.

## Objective
Mirror the backend catalogue in Java and add the pure rules the UI will need: types, fields, validation, option enums, remembered options. No UI, no network, no service (Step 4), no layouts (Step 5).

## Scope
1. `model/AnnounceType.java`: the 26 types (id, urgency N/I/U, default tone, emoji, ordered fields with key/required/hint, `oneOf`). IDs and field keys must equal `catalogue.ts` exactly. Also small enums/constants for `lang` (`en|fil|ceb|taglish`), `tone`, `length`, `adjust` with their wire strings, `MAX_FIELD_LEN = 200`, `MAX_CLOSING_NAME = 60`.
2. `util/AnnounceRules.java`: `validateFields(type, Map<String,String>)` returning the first problem (missing required, too long, `oneOf`) with the field key; `normalize(value)` (trim, collapse whitespace, strip control chars, same as `normalizeValue` in `clean.ts`); `buildRequest(...)` -> `JSONObject` with exactly the keys the backend accepts (unknown top-level keys are rejected with 400; skip empty fields); `defaultTone(type)`.
3. Strings: `res/values/strings_announce_ai.xml` (English only: type titles, one-line descriptions, field labels, hints from `catalogue.ts`, urgency badge words, tone/length/language labels, error messages for 403 `not_owner`/`ai_disabled`, 429, 400, offline, generic). No fil/ceb UI localisation exists: keep English.
4. `AppPrefs`: last language, tone, length, emoji (new static getters/setters in the same style). Decide whether `clear()` keeps them like the theme and write the decision in the status file.
5. Icons: `ic_sparkle` plus one line icon per type, reusing existing `ic_*` where suitable (93 exist; grep first). Vector drawables, theme-aware tint, no new colors.
6. Tests (JVM, JUnit 4.13.2 + org.json, no Android classes): catalogue completeness (26 entries, ids equal the list in status section B, 1-4 fields, unique keys, hints non-empty, every string resource referenced exists), validation (required, 200 boundary, `oneOf`, normalization), `buildRequest` JSON shape (keys only from the allowed set), default tones. Better: a test that parses `supabase/functions/bofo-announce/catalogue.ts` with a regex and compares ids + field keys + required flags with the Java catalogue (fails when the two drift). Sandbox has no Android SDK: run the pure classes + tests with `javac` + JUnit jars if available (check `~/.gradle`, `find / -name "junit*.jar"`), otherwise parse-check and say exactly what was not run.

## Version
New feature: bump to **2.4.0 / versionCode 20400** (`bofo-android/app/build.gradle`), replace the old version string everywhere except history, put 2.4.0 at the top of `CHANGES_ANDROID.md` and `HANDOFF_ANDROID.md`, and in the PR title.

## Deliverables
- Source + tests + strings + icons + version bump.
- Update `BOFO-AI-ANNOUNCE-STATUS.md` (Step 3 row, any change to field keys), new top entry in `HANDOFF_ANDROID.md`, `CHANGES_ANDROID.md`, `CHANGED_FILES.txt`, and a new `NEXT-STEP-PROMPT.md` with the complete standalone Step 4 prompt (same structure as this one).
- ONE changes-only ZIP (only changed/new files + `CHANGED_FILES.txt`), verified with `unzip -l`, with the PowerShell apply block from `CLAUDE_ANDROID.md` section 6.
- Final reply: what was and was not tested, manual commands, and the full next-step prompt as fallback.

## Stop condition
Never deploy, apply a migration, post an announcement, or call the live function without the user's explicit OK. After packaging, **stop and wait**. Do not start Step 4.
