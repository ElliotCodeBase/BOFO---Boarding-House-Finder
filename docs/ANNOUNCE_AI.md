# Write for me - owner guide (BOFO AI Announcement Writer)

For boarding-house owners. Plain language. App version 2.4.3 and later.

## What it is
**Write for me** is a helper on the **Announce** screen. You tell it the facts (date, time, amount...), pick a language and a tone, and it writes a draft title and message for you.
It **never posts anything by itself.** You always read the draft, change it if you want, and press **Post** yourself.

## How to open it
1. Open a house group chat and use the 3-dot menu > **Announce** (or open Announce from **My boarding house**).
2. Tap **Write for me** (above the title box). The button is hidden when an admin has switched BOFO AI off.

## The 26 types
Urgency badge: **Urgent** (red-orange) and **Important** (quiet tint) help you find the right type; "-" = no badge. A `*` is a required detail.

| Type id | Title in the app | Badge | Details you type |
|---|---|---|---|
| rent_due | Rent reminder | Important | due_date*, amount*, how_to_pay*, late_fee |
| water_cut | Water interruption | Important | date*, time_range*, reason |
| power_cut | Power interruption | Important | date*, time_range*, source |
| house_rules | House rules | - | rule*, effective_date |
| visitors_curfew | Visitors and curfew | - | rule_time*, what_changes* |
| cleaning_schedule | Cleaning schedule | - | date*, area*, responsible |
| welcome_tenant | Welcome a tenant | - | first_name, room, move_in_info, rules_summary |
| vacancy | Room available | - | room_type*, price*, slots*, how_to_reserve* |
| payment_thanks | Thanks for paying | - | month*, note |
| general | General announcement | - | idea* |
| emergency | Emergency | Urgent | kind*, what_to_do, contact |
| typhoon | Typhoon | Urgent | signal*, house_action, notes |
| move_out | Move-out notice | Important | date*, deposit_rule*, room_check_date |
| deposit_advance | Deposit and advance | Important | amount*, due_date*, receipt_note |
| wifi_update | Wi-Fi update | - | password, outage, schedule, fair_use |
| pest_control | Pest control | Important | date*, rooms*, tenant_action |
| room_inspection | Room inspection | Important | date*, time_window*, reason |
| gate_lock_change | Gate or lock change | Important | new_gate_time, key_code_instructions, effective_date (fill at least one of the first two) |
| meeting | House meeting | - | date*, place*, agenda* |
| holiday | Holiday notice | - | occasion*, owner_away_note |
| price_update | Price update | Important | effective_date*, new_amount*, reason |
| lost_found | Lost and found | - | item*, found_where*, how_to_claim* |
| rule_violation | Rule reminder | Important | rule*, detail |
| thanks | Thank you | - | reason* |
| congrats | Congratulations | - | occasion*, who_group |
| survey | Quick survey | - | question*, how_to_reply* |

Tips: each detail can be up to **200 characters**. The writer only uses what you type: it must not invent dates, amounts, names or phone numbers. If you leave out something it needs, it adds a short hint such as "Add the due date".
Emergency and Typhoon drafts only say to stay safe and follow the barangay, BFP and PDRRMO. They give no medical or rescue advice. Add real contact numbers yourself in the **contact** box.

## The details form: options
- **Language:** English, Filipino, Cebuano or Taglish (this is the language of the draft; the app buttons stay English).
- **Tone:** friendly, formal, firm or short. Each type has a default. Firm is still always polite: no threats.
- **Length:** short (about 250 characters), normal, detailed.
- **Emojis:** off by default; on = at most a couple of fitting emojis.
- **Sign-off name:** a switch plus a name (up to 60 characters, filled from your profile name when known). The app adds "Thank you, <name>" at the end, in the draft language. Switch off = no sign-off.
Your language, tone, length and emoji choices are remembered on this phone for next time (they are cleared when you sign out).

## The draft screen
After **Write for me** you see the draft. You can:
- **Try again** - new wording, same facts.
- **Shorter**, **More friendly**, **More formal** - same facts, adjusted.
- **Language chips** - write the same facts in another language.
- **Edit details** - go back to the form (your typed details are kept).
- **Cancel** while it is writing - go back to the previous draft or to the form.
- **Use this** - copies the title and message into the Announce screen. If the Announce screen already has text, the app asks before replacing it. Title is cut to 120 and message to 1000 characters if needed.
If you edited the draft by hand and then press Try again / a change button, the app asks first, because a new draft replaces your edits.
You then read it, edit it, choose the houses and press **Post** yourself. The normal announcement rules still apply when you post (title 120, message 1000, 1-20 houses, up to 3 pinned announcements per house).

## What the messages mean
| You see | What happened | What to do |
|---|---|---|
| No internet | Phone offline or timeout | Check the connection, tap Try again |
| You have asked for many drafts. Please try again in N seconds. | Limit reached (see Limits). The button stays disabled until the countdown ends | Wait, then tap Try again |
| BOFO AI is turned off right now. You can still write your announcement yourself. | An admin switched the AI off | Write it yourself |
| Your session has ended. Please sign in again. | Signed out / expired | Sign in again |
| Only owners with a boarding house can use this. | Your account has no house | Add a house first |
| BOFO AI could not be reached right now. Please try again in a moment. | Server problem | Try again later |
| Please fill in: ... / at least one of: ... / ... is too long | A detail is missing or over 200 characters (the field is marked) | Fix the field |
| BOFO AI is busy, so this draft was made from a simple template. Please check it. | The AI model was not available; a built-in template was used | Check it carefully, or tap Try again later |

## Privacy
- The details you type, the sign-off name and the draft are **not saved on the phone** and **not written to logs**. Only your language / tone / length / emoji choices are saved on the phone.
- When you tap Write for me, the details are sent to BOFO's server (Supabase function `bofo-announce`) **only to write the draft**, and may be passed to an AI model service to write the text. The server keeps no copy of the text. It only records one usage row (your user id, the kind "announce" and the time) to count your limit. Server logs hold error codes and model names only, never your text.
- Do not type passwords you want to keep secret. If you use the Wi-Fi type, the password goes into the announcement because you typed it; it is only shown to your tenants when you post.

## Limits
- **10 drafts per minute and 40 drafts per day per owner** (counted on the server; the daily limit is only enforced after migration 031 is applied).
- A detail: 200 characters. Sign-off name: 60. Title: 120 (draft titles are about 100 or less). Message: 1000 (drafts are about 900 or less, about 250 for Short).
- Rotating the phone while the sheet is open returns to the details form; the draft is not kept.
