# Write for me - test matrix (v2.4.3)

Legend: **A** = covered by an automated test that ran (Node tests, run 2026-11-04 in Step 7: 21 of 21 pass). **R** = checked only by reading code, **M** = manual device check (nothing here was run on a device). **L** = needs the live function (not deployed yet).

## 1. 26 types x 4 languages (template path = what the owner gets when the AI model is not used)
Source: `supabase/functions/bofo-announce/announce.test.ts`, test "templates: all 26 types x 4 languages x 4 tones build, are complete and clipped".
For EVERY cell (type x `en|fil|ceb|taglish`, also x 4 tones = 416 builds) it asserts: title >= 3 and message >= 10 characters, no `{`, `}`, `undefined` or `null`, every required detail typed by the owner appears in the message, no `notes`, and after assembly title <= 100 and message <= 900.

| Type | en | fil | ceb | taglish |
|---|---|---|---|---|
| all 26 ids (rent_due ... survey) | A | A | A | A |

What is **not** automated: whether the wording reads naturally (fil / ceb / taglish were written without a native speaker: R/M), the **AI-model path** per language (only one fake-model case in "generate" and "llm" tests, not 104 cells; L), the real model's output quality (L), and emoji / sign-off per language (one `en` check in "templates: tone, length, emoji, closing, urgent safe wording"; the emergency and typhoon BFP wording is asserted for all 4 languages = A).
Other automated server cases: catalogue (26 unique ids, 1-4 fields, hints, oneOf), request validation (bad input -> codes), injection text stays quoted data, cleaning (markdown, links, phones, invented numbers, passwords), clipping 100/900/250 and hard caps 120/1000, 10/min + 40/day limits, fallback to template.
Java side (written in Steps 3-6, run in those steps, **not re-run in Step 7**, see section 5): `AnnounceTypeTest` (drift test Java == `catalogue.ts`), `AnnounceRulesTest`, `AnnounceFormStateTest`, `AnnounceDraftTest`, `AnnounceErrorsTest`, `AnnounceAiServiceTest`, `AnnounceDraftStateTest`.

## 2. Errors (every `AnnounceError` value)
| Error | Trigger | Screen (stage C) | Retry allowed | Countdown | Covered |
|---|---|---|---|---|---|
| SIGNED_OUT | HTTP 401 | message "Your session has ended..." + "write it yourself" line | No | No | A (`AnnounceErrorsTest`, `AnnounceDraftStateTest.errorTableRetryAndRoute`) |
| NOT_OWNER | 403 body `not_owner` | message, no retry | No | No | A |
| AI_DISABLED | 403 body `ai_disabled` (admin switch off; admins are exempt) | message, no retry | No | No | A (mapping), L (real switch) |
| RATE_LIMITED | 429 `{retry_after}` (10/min or 40/day); 30 s when the server sends nothing; max 3600 | message with seconds; Try again disabled until 0 | Yes, after the wait | Yes, 1 s ticks | A |
| INVALID | 400 from the server (`field` may be named) | back to the form, field marked and focused | Yes (after fixing) | No | A (mapping, route) |
| INVALID_DETAILS | the app's own check fails before any network call | back to the form, field marked | Yes (after fixing) | No | A (`AnnounceFormStateTest`, `AnnounceDraftStateTest.formProblemReusesTheStepFiveTexts`) |
| SERVER | 405 or any status >= 500 | message "could not be reached" | Yes | No | A (mapping) |
| OFFLINE | no network / timeout (`Connectivity.isNetworkError`) | message "No internet" | Yes | No | A (mapping), M (airplane mode) |
| GENERIC | 403 with an unknown body, 200 body without title/message, anything else | message + "write it yourself" | Yes | No | A |
Other server codes (400 sub-codes `unknown_param`, `bad_lang`, ...) all arrive as INVALID. "Degraded" (200 with `degraded:true`) is not an error: the draft shows with the note "BOFO AI is busy...": A (model parsing), M (look).

## 3. Rules to verify (review against the feature rules)
| Rule | Result |
|---|---|
| AI never posts; owner reviews first | R: `AnnounceFragment` only fills the form; `postAnnouncement` is called only from the Post button path. `BoardingRules.checkAnnouncement` still runs. |
| No retry loop | R + A: one request per tap (`noSecondRequestWhileLoadingOrWithoutADraft`, `retryResendsTheSameRequestAndKeepsTheFacts`). |
| Late result after the sheet closed is ignored | A (`lateOrEmptyResultsAreIgnored`), R (`cancelAll()` in `onDestroyView`). |
| Nothing logged or stored (fields, wifi password, sign-off name, draft) | R (grep, Step 7): no `Log.`, `System.out`, `printStackTrace` in any announce class; `AppPrefs` writes only language, tone, length, emoji; server `console.*` prints error codes and model ids only. Only in-memory: fragment result Bundle (see FINAL-HANDOFF, known limits). |
| Prompt injection | A (test "injection text in a field stays quoted data"); R (`prompt.ts` quotes values and strips the delimiters; code, not the model, adds the sign-off). |
| Owner-only and `ai_enabled` | R (`index.ts` order: JWT, owner, ai_enabled, validate, limit); L to prove. |
| Daily limit + `ai_usage.kind` (migration 031) | A (`limits`), L (needs 031 applied). |
| Java catalogue == backend catalogue | A in Step 3 (drift test); R in Step 7: same 26 ids in both files. |

## 4. Manual device checklist (owner account, v2.4.3 built in Android Studio)
Do each on a real phone or emulator and tick it.
- [ ] Announce screen shows **Write for me** above the title; it is not covered by the keyboard.
- [ ] Open the sheet: type list shows 26 rows, urgent rows have the red-orange badge, important rows the quiet one; search "water" finds Water interruption.
- [ ] Pick a type: required fields are marked `*`; leave one empty and tap Write for me: the field is marked and focused.
- [ ] **Dark theme** and **orange theme** (orange is admin-only in Settings): text readable, badges and chips visible, buttons readable (`bofo_on_accent`).
- [ ] **360dp width** (small phone or Display size larger): no cut-off, chip rows scroll sideways, bottom buttons reachable.
- [ ] **Keyboard open** on the form and on the draft screen: sheet scrolls, Write for me / Use this still reachable.
- [ ] **Rotation** in each stage: list, form (typed details kept), loading (returns to the form), draft (returns to the form; draft not kept), error (returns to the form). Nothing crashes.
- [ ] **TalkBack:** back and close buttons are announced ("Back", "Close"), each type row reads title, urgency and description, switches and chips have labels, the draft text is readable, the countdown is announced or at least the button state changes.
- [ ] **Cancel while writing:** goes back to the previous draft (options restored) or to the form; no late draft appears afterwards.
- [ ] **Use this with an empty form** fills title and message directly, no question. **With text already in the form:** the app asks before replacing; No keeps the old text.
- [ ] Use this clips a very long draft to 120 / 1000 without breaking an emoji; Post still validates and nothing is posted automatically.
- [ ] Edit the draft by hand, then tap Try again: the app asks first.
- [ ] Shorter / More friendly / More formal / language chip each produce a new draft from the same facts.
- [ ] **AI switched off by an admin** (Settings > BOFO AI for users): the Write for me button disappears (after returning to the screen); an open sheet shows the "turned off" message.
- [ ] **Airplane mode:** "No internet" message, Try again works after turning it off.
- [ ] Rate limit: tap Try again 11 times within a minute: message with seconds, button disabled until the countdown ends (needs the deployed function).
- [ ] Sign out and sign in: language / tone / length / emoji choices are reset (by design).
- [ ] Type one type in each of the 4 languages; a native speaker reads the Filipino, Cebuano and Taglish drafts.
- [ ] Rows 48dp high, no overlapping text with font size set to Large.

## 5. Step 7 run log (what actually ran)
- `node --experimental-strip-types --test supabase/functions/bofo-announce/announce.test.ts` -> 21 tests, 21 pass, 0 fail (Node v22.23.4).
- JVM tests, stand-in JUnit + org.json (sandbox has no real jars / Gradle): `AnnounceFormStateTest` 12 of 12 and `AnnounceDraftStateTest` 18 of 18 pass with the restored Step 5 files. NOT re-run: `AnnounceRulesTest`, `AnnounceTypeTest`, `AnnounceDraftTest`, `AnnounceErrorsTest`, `AnnounceAiServiceTest` (need real org.json); earlier steps ran them (Step 4: 44, Step 5: 56 with real JUnit).
- Static checks run: grep for logging / storage / hard-coded UI text; string, id, drawable, layout and class references of the 3 UI/state classes against `res` and the source tree; Java ids == `catalogue.ts` ids (26/26); accessibility attributes of the two layouts.
- Not run: Gradle, Android lint, the app, the live function, Deno.
