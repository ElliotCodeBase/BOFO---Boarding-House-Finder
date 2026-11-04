# BOFO AI Announcement Writer - final handoff (Step 7 of 7)

Versions 2.3.3 -> 2.4.3 (versionCode 20303 -> 20403). Step 7 changed **documents only**, so there is **no version bump** (the app stays 2.4.3 / 20403).

## 0. The missing Step 5 files (blocker, now fixed in this zip)
The repo had the Step 6 changes but not 5 files of the Step 5 zip, so Android Studio failed with 31 "cannot find symbol" errors (`AnnounceFormState`, `item_announce_type`, `bg_ann_badge_*`, `ann_row_*`). This zip puts back exactly those 5 files, copied unchanged from your `BOFO-android-v2.4.2-changes.zip`:
- `util/AnnounceFormState.java`, `layout/item_announce_type.xml`, `drawable/bg_ann_badge_urgent.xml`, `drawable/bg_ann_badge_important.xml`, `test/.../util/AnnounceFormStateTest.java`
**Do NOT unzip the full 2.4.2 zip over the repo:** it would overwrite the newer 2.4.3 versions of `build.gradle`, `AnnounceAiSheet.java`, `sheet_announce_ai.xml` and `strings_announce_ai.xml` with older ones. Only this fix zip is safe. No version bump (nothing new, the files belong to 2.4.2 / 2.4.3).
Checked in the sandbox with the files merged: every `R.*` name and class used by the sheet, fragment and draft state now exists (only `design_bottom_sheet`, a Material library id, is outside the project), and `AnnounceFormStateTest` (12) + `AnnounceDraftStateTest` (18) pass together (stand-in JUnit, see section 4). Gradle still has not compiled it: your next build is the real check.

## 1. What was built (7 steps)
| Step | Version | What |
|---|---|---|
| 1 | 2.3.3 | audit + plan + contract (no code) |
| 2 | 2.3.3 | backend: edge function `bofo-announce` + migration 031 (written, not deployed/applied) |
| 3 | 2.4.0 | Java catalogue (`AnnounceType`), `AnnounceRules`, prefs, 211 strings, icons |
| 4 | 2.4.1 | `AnnounceAiService`, `AnnounceDraft`, `AnnounceError`, `AnnounceErrors` |
| 5 | 2.4.2 | the sheet: type list + details form (`AnnounceFormState`, `AnnounceAiSheet`) |
| 6 | 2.4.3 | draft stage, Try again / Shorter / Friendlier / Formal / language / Use this, Write for me button |
| 7 | 2.4.3 | docs + QA review (this) |

## 2. File map
- Backend: `supabase/functions/bofo-announce/{index,catalogue,templates,clean,prompt,llm,generate,limits}.ts`, `announce.test.ts`; `supabase/migrations/031_ai_usage_kind.sql`.
- Android model: `model/{AnnounceType,AnnounceDraft,AnnounceError}.java`; service: `service/{AnnounceAiService,AnnounceErrors}.java`.
- Android util: `util/{AnnounceRules,AnnounceFormState,AnnounceDraftState}.java`, `util/AppPrefs.java` (4 announce prefs).
- Android UI: `ui/AnnounceAiSheet.java`, `ui/AnnounceFragment.java`; layouts `sheet_announce_ai.xml`, `item_announce_type.xml`, `fragment_announce.xml`; drawables `ic_sparkle`, `ic_ann_*` (18), `bg_ann_badge_*`; strings `values/strings_announce_ai.xml`.
- Tests: `app/src/test/.../{model,service,util}/Announce*Test.java` (7 classes), `announce.test.ts`.
- Docs: `docs/ANNOUNCE_AI.md` (owner guide), `docs/ANNOUNCE_AI_TEST_MATRIX.md`, `BOFO-AI-ANNOUNCE-STATUS.md`, `HANDOFF_ANDROID.md`.

## 3. How it works end to end
An owner opens Announce > **Write for me**; the sheet lets them pick one of 26 types, fill up to 4 details and choose language / tone / length / emoji / sign-off. The app validates the details itself, then `AnnounceAiService` calls the Supabase function `bofo-announce` with the user's login. The function checks the login, that the user owns at least one house, that an admin has not switched the AI off, validates the request again, applies 10/min and 40/day limits, asks an LLM (OpenRouter models, same secrets as `bofo-chat`) to write only from the owner's facts, and then cleans the result with code (no links / phones / passwords / invented numbers, length caps, sign-off added by code). If the model fails or breaks a rule it returns a template draft with `degraded:true`. The sheet shows the draft; the owner can adjust or regenerate, and **Use this** copies title and message into the Announce form. The owner still reads, edits and presses **Post**; `postAnnouncement` and `BoardingRules.checkAnnouncement` are unchanged.

## 4. What was tested and what was NOT
Tested (ran):
- Step 7: backend Node tests **21 of 21 pass** (catalogue, validation, injection, cleaning, clipping, 26 types x 4 languages x 4 tones templates, generate/LLM fallback, limits).
- Steps 4-6 (earlier sessions): Step 4 = 44 JVM tests and Step 5 = 56 JVM tests, both with real JUnit 4.13.2; Step 6 re-ran 51 model/util tests with a stand-in JUnit and mutation-checked them. The two service test classes (23 tests) were last run in Step 5.
Not tested:
- Gradle / the real Android compile: not run. JVM tests were re-run only partly, with a stand-in JUnit and org.json: `AnnounceFormStateTest` 12 and `AnnounceDraftStateTest` 18 pass. `AnnounceRulesTest`, `AnnounceTypeTest` (drift test), `AnnounceDraftTest` and the 2 service tests were NOT re-run (they need the real org.json); run them in Android Studio.
- Nothing was compiled against the real Android SDK (the sheet, the fragment, `FragmentAnnounceBinding.writeForMe`, Material `MaterialSwitch`/`Chip`/`ComponentDialog`), no layout was seen on a device, no Gradle build, no lint, no instrumented tests (none exist).
- The function was never deployed, never called live, migration 031 never applied, Deno never run, no real model answer reviewed.

## 5. Review result (Step 7)
No logic bug found, so no code was changed. Checked by reading/grep: the AI never posts; no retry loop; no `Log.` / `System.out` / `printStackTrace` / prefs writes of content in any announce class; server `console.*` prints codes and model ids only; owner, `ai_enabled`, limit order in `index.ts`; prompt-injection handling (values quoted, delimiters stripped, sign-off by code); Java ids == backend ids (26/26); no hard-coded UI text in the two announce layouts; ViewBinding name `write_for_me` -> `writeForMe` matches. Findings left as they are (no app-code change to avoid a bump without a compile):
1. **Missing Step 5 files** (section 0): fixed by this zip.
2. `announce_sheet_title` in `strings_announce_ai.xml` is unused (harmless; delete when you next touch the file).
3. `AnnounceAiSheet.java` lines ~537-538 put the title and message into the fragment-result Bundle for `Use this`. That Bundle lives in memory and is consumed at once, but Android can keep pending fragment results in saved instance state until consumed. Low risk (same process, not written to disk by the app); noted because the rule says never store the draft.
4. The Write for me button hides for admins too when the AI is off (the sheet itself still exempts admins like the server). Intentional, mild inconsistency.
5. Accessibility: `ann_write`, `ann_res_retry`, `ann_use` and `post` do not set `minHeight` in XML; they use the app button styles, check on a device that they are at least 48dp. Progress bars have no description.
6. The master prompt `BOFO-AI-ANNOUNCE-PROMPT-7-STEPS.md` was **not** given to this session either, so the review used the Step 7 prompt, the status file and `CLAUDE_ANDROID.md`. If you have it, give it to any AI doing another review.

## 6. Known limits
- A phone rotation returns to the details form; the draft is not kept (by design, never stored).
- The Filipino, Cebuano and Taglish templates were not reviewed by a native speaker.
- UI labels are English only (no localisation exists in the app).
- Count-then-insert rate limit is not atomic (same as `bofo-chat`); before 031 the daily limit is not enforced; after 031 `bofo-chat`'s 10/min check also counts announce rows.
- No instrumented tests; the sheet and fragment have never been compiled.
- `bofo-chat` still ignores the `ai_enabled` switch (not part of this feature).

## 7. What YOU must still do (in this order)
a) **Apply this fix zip** (section 0), then rebuild.
b) **Apply migration 031** (Supabase SQL editor, project `zfxldzcoirqolflkkhrh`; safe to run twice): paste the file `supabase/migrations/031_ai_usage_kind.sql`, or `supabase db push` if you use migrations. Verify: `select column_name from information_schema.columns where table_name='ai_usage' and column_name='kind';` returns one row.
c) **Deploy the function:** `supabase functions deploy bofo-announce --project-ref zfxldzcoirqolflkkhrh`. The LLM path needs the same secrets as `bofo-chat`: `OPENROUTER_API_KEY` and `LLM_MODELS` (check with `supabase secrets list`). Without them the function still works and returns template drafts (`degraded:true`).
d) **Build in Android Studio** (open `C:\Users\user\bofo-finder\bofo-android`): Build > Make Project and fix any compile error; likely places: `AnnounceAiSheet` (Material/AndroidX symbols), `FragmentAnnounceBinding`. Run the unit tests: `./gradlew testDebugUnitTest --tests "com.bofo.finder.*Announce*"` (or right-click the `test` folder > Run).
e) **Live smoke test with an owner account:** one type in each of the 4 languages, an 11th draft within a minute (rate limit message), an admin switching **BOFO AI for users** off (button gone / "turned off"), airplane mode ("No internet"), and a non-owner account (expect the not-owner message).
f) **Native-speaker review** of the Filipino, Cebuano and Taglish templates (`supabase/functions/bofo-announce/templates.ts`).
g) **Device checks** from `docs/ANNOUNCE_AI_TEST_MATRIX.md` section 4 (dark / orange theme, 360dp, keyboard, rotation, TalkBack, Cancel, Use this with and without text).
h) Push: the PR title should be `v2.4.3 Android: AI Announcement Writer (docs + QA)` with the changelog line already in `CHANGES_ANDROID.md`.
