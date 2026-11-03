// bofo-announce / catalogue.ts - the canonical list of announcement types (26), their fields and defaults.
// Pure TypeScript (no Deno APIs) so it can be tested with node. Step 3 mirrors this table in Java (model/AnnounceType.java).
// Urgency: N = normal, I = important, U = urgent. Field values: strings, max 200 chars each. `*` in the docs = required.

export const LANGS = ["en", "fil", "ceb", "taglish"] as const;
export const TONES = ["friendly", "formal", "firm", "short"] as const;
export const LENGTHS = ["short", "normal", "detailed"] as const;
export const ADJUSTS = ["again", "shorter", "friendlier", "formal"] as const;
export type Lang = typeof LANGS[number];
export type Tone = typeof TONES[number];
export type Length = typeof LENGTHS[number];
export type Adjust = typeof ADJUSTS[number];
export type Urgency = "N" | "I" | "U";

export const MAX_FIELD_LEN = 200;
export const MAX_CLOSING_NAME = 60;

export interface FieldDef { key: string; required: boolean; hint: string }
export interface TypeDef {
  id: string;
  urgency: Urgency;
  tone: Tone;            // default tone
  emoji: string;         // used only when the owner turns emoji on
  fields: FieldDef[];    // 1-4 fields
  oneOf?: string[];      // at least one of these keys must be filled (even if none is individually required)
  guard: string;         // extra rule for the model, specific to this type
}

const r = (key: string, hint: string): FieldDef => ({ key, required: true, hint });
const o = (key: string, hint: string): FieldDef => ({ key, required: false, hint });

export const TYPES: TypeDef[] = [
  { id: "rent_due", urgency: "I", tone: "firm", emoji: "💰",
    fields: [r("due_date", "e.g. Nov 5"), r("amount", "e.g. 1500 per bed"), r("how_to_pay", "e.g. GCash to the owner or cash at the office"), o("late_fee", "only if you charge one")],
    guard: "Rent reminder. Tone friendly = polite reminder, firm = clear reminder, formal = final reminder. NEVER threaten (no eviction, no police, no locking out, no penalties). Mention a late fee only if the owner gave one." },
  { id: "water_cut", urgency: "I", tone: "short", emoji: "💧",
    fields: [r("date", "e.g. Nov 7"), r("time_range", "e.g. 8 AM to 3 PM"), o("reason", "e.g. pipe repair")],
    guard: "Water interruption notice. You may remind tenants to store water, nothing more." },
  { id: "power_cut", urgency: "I", tone: "short", emoji: "💡",
    fields: [r("date", "e.g. Nov 7"), r("time_range", "e.g. 9 AM to 4 PM"), o("source", "e.g. utility maintenance or house wiring repair")],
    guard: "Power interruption notice. You may remind tenants to charge devices, nothing more." },
  { id: "house_rules", urgency: "N", tone: "friendly", emoji: "📋",
    fields: [r("rule", "e.g. Quiet hours start at 10 PM"), o("effective_date", "e.g. Nov 10")],
    guard: "Announce or remind about one house rule. Do not add extra rules." },
  { id: "visitors_curfew", urgency: "N", tone: "formal", emoji: "🚪",
    fields: [r("rule_time", "e.g. Visitors must leave by 9 PM"), r("what_changes", "e.g. Gate closes at 10 PM instead of 11 PM")],
    guard: "Visitor or curfew rule change. Do not add penalties." },
  { id: "cleaning_schedule", urgency: "N", tone: "friendly", emoji: "🧹",
    fields: [r("date", "e.g. Saturday, Nov 8"), r("area", "e.g. kitchen and hallway"), o("responsible", "e.g. Rooms 1 to 3")],
    guard: "Cleaning schedule. Name a responsible group only if the owner typed it (rooms or floors, never people)." },
  { id: "welcome_tenant", urgency: "N", tone: "friendly", emoji: "👋",
    fields: [o("first_name", "first name only"), o("room", "e.g. Room 4"), o("move_in_info", "e.g. Keys at the office, 2 PM"), o("rules_summary", "e.g. Quiet hours 10 PM, no smoking inside")],
    guard: "Welcome a new tenant. You may use the first name only if the owner typed it. Do not add facts about the house." },
  { id: "vacancy", urgency: "N", tone: "friendly", emoji: "🏠",
    fields: [r("room_type", "e.g. single room with own CR"), r("price", "e.g. 3000 per month"), r("slots", "e.g. 2"), r("how_to_reserve", "e.g. Message the owner on BOFO")],
    guard: "Vacancy notice. Use ONLY the room type, price, slots and way to reserve typed by the owner. Never invent amenities, prices or places." },
  { id: "payment_thanks", urgency: "N", tone: "friendly", emoji: "🙏",
    fields: [r("month", "e.g. November"), o("note", "e.g. Thank you for paying on time")],
    guard: "Thank everyone who paid. NEVER name who paid or who did not, and never mention anyone's payment details." },
  { id: "general", urgency: "N", tone: "friendly", emoji: "📣",
    fields: [r("idea", "one sentence about what you want to say")],
    guard: "General announcement written from the owner's one-sentence idea. Do not add facts beyond it." },
  { id: "emergency", urgency: "U", tone: "short", emoji: "🚨",
    fields: [r("kind", "fire, flood, typhoon, earthquake or lockdown"), o("what_to_do", "what the owner wants tenants to do"), o("contact", "phone or place, only if you want to share it")],
    guard: "Emergency notice. Use this safe wording: stay safe and follow the advice of the barangay, BFP and PDRRMO. Give NO medical advice and NO rescue instructions. Include what_to_do and contact only exactly as the owner typed them." },
  { id: "typhoon", urgency: "U", tone: "short", emoji: "🌀",
    fields: [r("signal", "e.g. Signal No. 2"), o("house_action", "e.g. Gate closes at 6 PM"), o("notes", "anything else")],
    guard: "Typhoon notice. Use this safe wording: stay safe and follow the advice of the barangay, BFP and PDRRMO. Give NO medical advice and NO rescue instructions. Never predict the weather." },
  { id: "move_out", urgency: "I", tone: "formal", emoji: "📦",
    fields: [r("date", "e.g. Nov 30"), r("deposit_rule", "e.g. Deposit returned within 7 days after the room check"), o("room_check_date", "e.g. Nov 29")],
    guard: "Move-out notice. State the deposit rule exactly as typed; never add legal claims or penalties." },
  { id: "deposit_advance", urgency: "I", tone: "formal", emoji: "🧾",
    fields: [r("amount", "e.g. 2 months: 6000"), r("due_date", "e.g. Nov 15"), o("receipt_note", "e.g. Receipt will be given on payment")],
    guard: "Deposit and advance payment notice. Use only the amount and date typed." },
  { id: "wifi_update", urgency: "N", tone: "friendly", emoji: "📶",
    fields: [o("password", "the new Wi-Fi password"), o("outage", "e.g. Down on Nov 6 for 2 hours"), o("schedule", "e.g. Router restarts at midnight"), o("fair_use", "e.g. No large downloads at night")],
    guard: "Wi-Fi update. If a password is given, write it exactly as typed; if none is given, NEVER write or guess a password." },
  { id: "pest_control", urgency: "I", tone: "friendly", emoji: "🪲",
    fields: [r("date", "e.g. Nov 12"), r("rooms", "e.g. all rooms and kitchen"), o("tenant_action", "e.g. Keep food covered and be out from 9 to 11 AM")],
    guard: "Pest control schedule. Do not name chemicals or health claims." },
  { id: "room_inspection", urgency: "I", tone: "formal", emoji: "🔍",
    fields: [r("date", "e.g. Nov 14"), r("time_window", "e.g. 2 PM to 5 PM"), o("reason", "e.g. regular maintenance check")],
    guard: "Room inspection notice. Be respectful of privacy; do not add threats." },
  { id: "gate_lock_change", urgency: "I", tone: "formal", emoji: "🔑",
    fields: [o("new_gate_time", "e.g. Gate closes at 10 PM"), o("key_code_instructions", "e.g. Get the new key from the owner"), o("effective_date", "e.g. Nov 10")],
    oneOf: ["new_gate_time", "key_code_instructions"],
    guard: "Gate or lock change. If a key code or instruction is given, write it exactly as typed; never invent a code." },
  { id: "meeting", urgency: "N", tone: "friendly", emoji: "🗓️",
    fields: [r("date", "e.g. Nov 9, 7 PM"), r("place", "e.g. the common area"), r("agenda", "e.g. Cleaning rotation and Wi-Fi")],
    guard: "House meeting invitation." },
  { id: "holiday", urgency: "N", tone: "friendly", emoji: "🎉",
    fields: [r("occasion", "Christmas, New Year, Fiesta, Holy Week, Sinulog or other"), o("owner_away_note", "e.g. I will be away Dec 24 to 26")],
    guard: "Holiday greeting and notice. Mention the owner being away only if typed." },
  { id: "price_update", urgency: "I", tone: "formal", emoji: "📈",
    fields: [r("effective_date", "e.g. Jan 1"), r("new_amount", "e.g. 3300 per month"), o("reason", "e.g. higher electricity cost")],
    guard: "Price update. Present it as advance notice so tenants can plan; do NOT state a number of days and make NO legal claims." },
  { id: "lost_found", urgency: "N", tone: "friendly", emoji: "🔎",
    fields: [r("item", "e.g. a black umbrella"), r("found_where", "e.g. near the gate"), r("how_to_claim", "e.g. Ask the owner")],
    guard: "Lost and found notice. Do not guess whose item it is." },
  { id: "rule_violation", urgency: "I", tone: "firm", emoji: "⚠️",
    fields: [r("rule", "e.g. No loud music after 10 PM"), o("detail", "general detail, no names")],
    guard: "Group reminder about a broken rule. NEVER name or point at an individual or a room. Keep it generic and respectful. No threats, no penalties unless the owner typed them." },
  { id: "thanks", urgency: "N", tone: "friendly", emoji: "💛",
    fields: [r("reason", "e.g. keeping the house clean this month")],
    guard: "Thank-you message to all tenants." },
  { id: "congrats", urgency: "N", tone: "friendly", emoji: "🎓",
    fields: [r("occasion", "birthday, graduation or other"), o("who_group", "generic, e.g. our graduates")],
    guard: "Congratulations message. Use a group name only if typed; do not name individuals." },
  { id: "survey", urgency: "N", tone: "friendly", emoji: "📝",
    fields: [r("question", "e.g. Should we change the cleaning day?"), r("how_to_reply", "e.g. Reply in the group chat by Nov 10")],
    guard: "Short survey question for tenants." },
];

export const TYPE_IDS: string[] = TYPES.map((t) => t.id);
const BY_ID = new Map(TYPES.map((t) => [t.id, t] as const));
export function getType(id: string): TypeDef | undefined { return BY_ID.get(id); }
