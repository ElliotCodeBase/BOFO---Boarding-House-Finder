// bofo-announce / templates.ts - fallback text (no LLM): TEMPLATES[type][lang], built ONLY from the owner's values.
// A sentence is written only if every {placeholder} in it is filled ("a||b" = use the first alternative whose placeholders are all filled).
// A required field that is empty -> its sentences are dropped and `notes` says "Add the ...". Pure TypeScript (no Deno APIs).
import { getType } from "./catalogue.ts";
import type { Lang, Tone, Length } from "./catalogue.ts";

interface Tpl { t: string; s: string[]; lead?: Partial<Record<Tone, string>> }
type LangTpls = Record<string, Tpl>;

const SAFE: Record<Lang, string> = {
  en: "Please stay safe and follow the advice of your barangay, BFP and PDRRMO.",
  fil: "Mag-ingat po tayong lahat at sundin ang payo ng barangay, BFP at PDRRMO.",
  ceb: "Pag-amping ta tanan ug sunda ang giingon sa barangay, BFP ug PDRRMO.",
  taglish: "Stay safe po tayong lahat and sundin ang advice ng barangay, BFP, at PDRRMO.",
};
export const GREET: Record<Lang, Record<Tone, string>> = {
  en: { friendly: "Hello everyone!", formal: "Good day, everyone.", firm: "Please take note.", short: "" },
  fil: { friendly: "Hello po sa lahat!", formal: "Magandang araw po sa lahat.", firm: "Paalala po sa lahat.", short: "" },
  ceb: { friendly: "Hello sa tanan!", formal: "Maayong adlaw sa tanan.", firm: "Pahinumdom sa tanan.", short: "" },
  taglish: { friendly: "Hi everyone!", formal: "Good day po sa lahat.", firm: "Please take note po.", short: "" },
};
const DETAIL: Record<Lang, string> = {
  en: "Please message me if you have any questions.",
  fil: "Mag-message lang po kayo kung may tanong.",
  ceb: "Palihug pagmensahe kung naay pangutana.",
  taglish: "Message lang po kayo if may questions.",
};
const GENERIC: Record<Lang, string> = {
  en: "Please read this announcement from the owner.",
  fil: "Pakibasa po ang anunsyong ito mula sa may-ari.",
  ceb: "Palihug basaha ang pahibalo gikan sa tag-iya.",
  taglish: "Paki-read po ang announcement na ito from the owner.",
};
const CLOSE: Record<Lang, string> = { en: "Thank you,", fil: "Salamat po,", ceb: "Salamat,", taglish: "Thank you po," };
export const closingText = (lang: Lang, name: string) => `${CLOSE[lang]}\n${name}`;

const EN: LangTpls = {
  rent_due: { t: "Rent reminder", lead: { friendly: "Hi everyone! A friendly reminder about this month's rent.", firm: "Please take note: rent is due.", formal: "Final reminder regarding rent payment." },
    s: ["Rent of {amount} is due on {due_date}.", "How to pay: {how_to_pay}.", "Late fee: {late_fee}."] },
  water_cut: { t: "Water interruption", s: ["There will be no water on {date}, {time_range}.", "Reason: {reason}.", "Please store water ahead of time."] },
  power_cut: { t: "Power interruption", s: ["There will be no electricity on {date}, {time_range}.", "Cause: {source}.", "Please charge your devices ahead of time."] },
  house_rules: { t: "House rule reminder", s: ["House rule: {rule}.", "This starts on {effective_date}.", "Thank you for your cooperation."] },
  visitors_curfew: { t: "Visitors and curfew", s: ["New rule: {rule_time}.", "What changes: {what_changes}.", "Thank you for understanding."] },
  cleaning_schedule: { t: "Cleaning schedule", s: ["Cleaning is scheduled on {date} for {area}.", "Responsible: {responsible}.", "Let's keep our boarding house clean."] },
  welcome_tenant: { t: "Welcome to our boarding house", s: ["Welcome, {first_name}!||Welcome to our boarding house!", "Your room is {room}.", "Move-in info: {move_in_info}.", "House rules in short: {rules_summary}."] },
  vacancy: { t: "Room available", s: ["A {room_type} is available at {price}.", "Slots available: {slots}.", "To reserve: {how_to_reserve}."] },
  payment_thanks: { t: "Thank you for your payment", s: ["Thank you to everyone who paid the rent for {month}.", "{note}"] },
  general: { t: "Announcement", s: ["{idea}"] },
  emergency: { t: "Emergency notice", s: ["Notice: {kind}.", "What to do: {what_to_do}.", "Contact: {contact}.", SAFE.en] },
  typhoon: { t: "Typhoon notice", s: ["Typhoon signal: {signal}.", "House action: {house_action}.", "{notes}", SAFE.en] },
  move_out: { t: "Move-out notice", s: ["Move-out date: {date}.", "Deposit: {deposit_rule}.", "Room check: {room_check_date}."] },
  deposit_advance: { t: "Deposit and advance payment", s: ["Please pay {amount} on or before {due_date}.", "Receipt: {receipt_note}."] },
  wifi_update: { t: "Wi-Fi update", s: ["New Wi-Fi password: {password}.", "Outage: {outage}.", "Schedule: {schedule}.", "Fair use: {fair_use}."] },
  pest_control: { t: "Pest control schedule", s: ["Pest control on {date} in {rooms}.", "What you need to do: {tenant_action}."] },
  room_inspection: { t: "Room inspection", s: ["Room inspection on {date}, {time_window}.", "Reason: {reason}.", "Please make sure your room is accessible."] },
  gate_lock_change: { t: "Gate and lock update", s: ["New gate closing time: {new_gate_time}.", "Key and code instructions: {key_code_instructions}.", "Starting {effective_date}."] },
  meeting: { t: "House meeting", s: ["House meeting on {date} at {place}.", "Agenda: {agenda}.", "Please try to attend."] },
  holiday: { t: "Holiday notice", s: ["Greetings for {occasion}!", "Note: {owner_away_note}."] },
  price_update: { t: "Price update", s: ["Starting {effective_date}, the new rate will be {new_amount}.", "Reason: {reason}.", "This is advance notice so you can plan. Please message me with any questions."] },
  lost_found: { t: "Lost and found", s: ["Found: {item}, at {found_where}.", "To claim: {how_to_claim}."] },
  rule_violation: { t: "Reminder: house rules", s: ["Reminder: {rule}.", "Details: {detail}.", "This reminder is for everyone in the house. Thank you for following the rules."] },
  thanks: { t: "Thank you", s: ["Thank you all for {reason}."] },
  congrats: { t: "Congratulations", s: ["Congratulations to {who_group} on {occasion}!||Congratulations on {occasion}!"] },
  survey: { t: "Quick question", s: ["Question: {question}", "Please reply: {how_to_reply}."] },
};

const FIL: LangTpls = {
  rent_due: { t: "Paalala sa upa", lead: { friendly: "Hello po sa lahat! Paalala lang po tungkol sa upa.", firm: "Paalala po: kailangan nang bayaran ang upa.", formal: "Huling paalala po tungkol sa pagbabayad ng upa." },
    s: ["Ang upa na {amount} ay due sa {due_date}.", "Paraan ng pagbabayad: {how_to_pay}.", "Late fee: {late_fee}."] },
  water_cut: { t: "Walang tubig", s: ["Walang tubig sa {date}, {time_range}.", "Dahilan: {reason}.", "Mag-ipon na po ng tubig bago nito."] },
  power_cut: { t: "Walang kuryente", s: ["Walang kuryente sa {date}, {time_range}.", "Dahilan: {source}.", "I-charge na po ang inyong mga gamit bago nito."] },
  house_rules: { t: "Paalala sa house rules", s: ["House rule: {rule}.", "Magsisimula ito sa {effective_date}.", "Salamat po sa pakikiisa."] },
  visitors_curfew: { t: "Bisita at curfew", s: ["Bagong patakaran: {rule_time}.", "Ano ang nagbago: {what_changes}.", "Salamat po sa pang-unawa."] },
  cleaning_schedule: { t: "Iskedyul ng paglilinis", s: ["Maglilinis po sa {date}: {area}.", "Responsable: {responsible}.", "Panatilihin nating malinis ang ating boarding house."] },
  welcome_tenant: { t: "Maligayang pagdating", s: ["Maligayang pagdating, {first_name}!||Maligayang pagdating po sa ating boarding house!", "Ang kuwarto mo ay {room}.", "Impormasyon sa paglipat: {move_in_info}.", "Mga patakaran sa madaling salita: {rules_summary}."] },
  vacancy: { t: "May bakanteng kuwarto", s: ["May bakanteng {room_type} sa halagang {price}.", "Bakanteng slots: {slots}.", "Paano mag-reserve: {how_to_reserve}."] },
  payment_thanks: { t: "Salamat sa bayad", s: ["Salamat po sa lahat ng nagbayad ng upa para sa {month}.", "{note}"] },
  general: { t: "Anunsyo", s: ["{idea}"] },
  emergency: { t: "Abiso sa emergency", s: ["Abiso: {kind}.", "Ano ang gagawin: {what_to_do}.", "Makipag-ugnayan: {contact}.", SAFE.fil] },
  typhoon: { t: "Abiso sa bagyo", s: ["Typhoon signal: {signal}.", "Gagawin ng bahay: {house_action}.", "{notes}", SAFE.fil] },
  move_out: { t: "Abiso sa paglipat", s: ["Petsa ng paglipat: {date}.", "Deposito: {deposit_rule}.", "Pagsusuri ng kuwarto: {room_check_date}."] },
  deposit_advance: { t: "Deposito at advance", s: ["Pakibayaran po ang {amount} bago o sa {due_date}.", "Resibo: {receipt_note}."] },
  wifi_update: { t: "Update sa Wi-Fi", s: ["Bagong Wi-Fi password: {password}.", "Putol: {outage}.", "Iskedyul: {schedule}.", "Patas na paggamit: {fair_use}."] },
  pest_control: { t: "Pest control", s: ["May pest control sa {date} sa {rooms}.", "Ang kailangan ninyong gawin: {tenant_action}."] },
  room_inspection: { t: "Inspeksyon ng kuwarto", s: ["May inspeksyon ng kuwarto sa {date}, {time_window}.", "Dahilan: {reason}.", "Pakisiguro po na mapapasok ang inyong kuwarto."] },
  gate_lock_change: { t: "Update sa gate at susi", s: ["Bagong oras ng pagsara ng gate: {new_gate_time}.", "Susi/code: {key_code_instructions}.", "Simula sa {effective_date}."] },
  meeting: { t: "Pulong ng bahay", s: ["May pulong sa {date} sa {place}.", "Agenda: {agenda}.", "Sana po ay makadalo kayo."] },
  holiday: { t: "Abiso sa holiday", s: ["Pagbati para sa {occasion}!", "Paalala: {owner_away_note}."] },
  price_update: { t: "Pagbabago sa presyo", s: ["Simula sa {effective_date}, ang bagong halaga ay {new_amount}.", "Dahilan: {reason}.", "Paunang abiso po ito para makapaghanda kayo. Mag-message lang po kung may tanong."] },
  lost_found: { t: "Nawawala at nakita", s: ["Nakita ang {item} sa {found_where}.", "Paano kunin: {how_to_claim}."] },
  rule_violation: { t: "Paalala sa patakaran", s: ["Paalala: {rule}.", "Detalye: {detail}.", "Paalala po ito para sa lahat. Salamat sa pagsunod sa mga patakaran."] },
  thanks: { t: "Maraming salamat", s: ["Maraming salamat po sa lahat sa {reason}."] },
  congrats: { t: "Binabati namin kayo", s: ["Binabati namin ang {who_group} sa {occasion}!||Binabati namin kayo sa {occasion}!"] },
  survey: { t: "Maikling tanong", s: ["Tanong: {question}", "Paki-reply po: {how_to_reply}."] },
};

const CEB: LangTpls = {
  rent_due: { t: "Pahinumdom sa abang", lead: { friendly: "Hello sa tanan! Pahinumdom lang bahin sa abang.", firm: "Pahinumdom: kinahanglan na bayran ang abang.", formal: "Katapusang pahinumdom bahin sa pagbayad sa abang." },
    s: ["Ang abang nga {amount} kay due sa {due_date}.", "Unsaon pagbayad: {how_to_pay}.", "Late fee: {late_fee}."] },
  water_cut: { t: "Walay tubig", s: ["Walay tubig sa {date}, {time_range}.", "Rason: {reason}.", "Palihug tipigi na og tubig daan."] },
  power_cut: { t: "Walay kuryente", s: ["Walay kuryente sa {date}, {time_range}.", "Rason: {source}.", "Palihug i-charge na ang inyong mga gamit daan."] },
  house_rules: { t: "Pahinumdom sa balaod sa balay", s: ["Balaod sa balay: {rule}.", "Magsugod kini sa {effective_date}.", "Salamat sa inyong kooperasyon."] },
  visitors_curfew: { t: "Bisita ug curfew", s: ["Bag-ong lagda: {rule_time}.", "Unsay nausab: {what_changes}.", "Salamat sa pagsabot."] },
  cleaning_schedule: { t: "Iskedyul sa paglimpyo", s: ["Maglimpyo sa {date}: {area}.", "Responsable: {responsible}.", "Atong pabiling limpyo ang atong boarding house."] },
  welcome_tenant: { t: "Malipayong pag-abot", s: ["Malipayong pag-abot, {first_name}!||Malipayong pag-abot sa atong boarding house!", "Ang imong kwarto kay {room}.", "Impormasyon sa paglipat: {move_in_info}.", "Mubo nga mga lagda: {rules_summary}."] },
  vacancy: { t: "Naay bakanteng kwarto", s: ["Naay bakanteng {room_type} sa {price}.", "Bakanteng slots: {slots}.", "Unsaon pag-reserve: {how_to_reserve}."] },
  payment_thanks: { t: "Salamat sa bayad", s: ["Salamat sa tanan nga nagbayad sa abang para sa {month}.", "{note}"] },
  general: { t: "Pahibalo", s: ["{idea}"] },
  emergency: { t: "Pahibalo sa emergency", s: ["Pahibalo: {kind}.", "Unsay buhaton: {what_to_do}.", "Kontak: {contact}.", SAFE.ceb] },
  typhoon: { t: "Pahibalo sa bagyo", s: ["Typhoon signal: {signal}.", "Buhaton sa balay: {house_action}.", "{notes}", SAFE.ceb] },
  move_out: { t: "Pahibalo sa paglipat", s: ["Petsa sa paglipat: {date}.", "Deposito: {deposit_rule}.", "Pagsusi sa kwarto: {room_check_date}."] },
  deposit_advance: { t: "Deposito ug advance", s: ["Palihug bayri ang {amount} hangtod sa {due_date}.", "Resibo: {receipt_note}."] },
  wifi_update: { t: "Update sa Wi-Fi", s: ["Bag-ong Wi-Fi password: {password}.", "Pagkaputol: {outage}.", "Iskedyul: {schedule}.", "Patas nga paggamit: {fair_use}."] },
  pest_control: { t: "Pest control", s: ["Naay pest control sa {date} sa {rooms}.", "Unsay inyong buhaton: {tenant_action}."] },
  room_inspection: { t: "Inspeksyon sa kwarto", s: ["Naay inspeksyon sa kwarto sa {date}, {time_window}.", "Rason: {reason}.", "Palihug siguroha nga mabuksan ang inyong kwarto."] },
  gate_lock_change: { t: "Update sa gate ug yawi", s: ["Bag-ong oras sa pagsira sa gate: {new_gate_time}.", "Yawi/code: {key_code_instructions}.", "Magsugod sa {effective_date}."] },
  meeting: { t: "Tigom sa balay", s: ["Naay tigom sa {date} sa {place}.", "Agenda: {agenda}.", "Palihug pag-attend."] },
  holiday: { t: "Pahibalo sa holiday", s: ["Pagpangumusta para sa {occasion}!", "Hinumdumi: {owner_away_note}."] },
  price_update: { t: "Pagbag-o sa presyo", s: ["Sugod sa {effective_date}, ang bag-ong presyo kay {new_amount}.", "Rason: {reason}.", "Daan nga pahibalo kini aron makapangandam mo. Mensahe lang kung naay pangutana."] },
  lost_found: { t: "Nawala ug nakit-an", s: ["Nakit-an ang {item} sa {found_where}.", "Unsaon pagkuha: {how_to_claim}."] },
  rule_violation: { t: "Pahinumdom sa lagda", s: ["Pahinumdom: {rule}.", "Detalye: {detail}.", "Pahinumdom kini alang sa tanan. Salamat sa pagsunod sa mga lagda."] },
  thanks: { t: "Daghang salamat", s: ["Daghang salamat sa tanan sa {reason}."] },
  congrats: { t: "Mga pagbati", s: ["Pagbati sa {who_group} sa {occasion}!||Pagbati sa {occasion}!"] },
  survey: { t: "Mubo nga pangutana", s: ["Pangutana: {question}", "Palihug tubaga: {how_to_reply}."] },
};

const TAG: LangTpls = {
  rent_due: { t: "Rent reminder", lead: { friendly: "Hi everyone! Friendly reminder po about this month's rent.", firm: "Please take note po: due na ang rent.", formal: "Final reminder po regarding rent payment." },
    s: ["Ang rent na {amount} ay due sa {due_date}.", "Paano magbayad: {how_to_pay}.", "Late fee: {late_fee}."] },
  water_cut: { t: "Water interruption", s: ["Walang tubig sa {date}, {time_range}.", "Reason: {reason}.", "Mag-ipon na po ng tubig ahead of time."] },
  power_cut: { t: "Power interruption", s: ["Walang kuryente sa {date}, {time_range}.", "Reason: {source}.", "Paki-charge na po ang devices ahead of time."] },
  house_rules: { t: "House rule reminder", s: ["House rule: {rule}.", "Effective po ito sa {effective_date}.", "Thank you po sa cooperation."] },
  visitors_curfew: { t: "Visitors and curfew", s: ["Bagong rule: {rule_time}.", "Ano ang nagbago: {what_changes}.", "Thank you po sa pang-unawa."] },
  cleaning_schedule: { t: "Cleaning schedule", s: ["Cleaning po sa {date} para sa {area}.", "Responsible: {responsible}.", "Keep natin na malinis ang ating boarding house."] },
  welcome_tenant: { t: "Welcome to our boarding house", s: ["Welcome po, {first_name}!||Welcome po sa ating boarding house!", "Ang room mo ay {room}.", "Move-in info: {move_in_info}.", "House rules in short: {rules_summary}."] },
  vacancy: { t: "Room available", s: ["May available na {room_type} for {price}.", "Slots: {slots}.", "Paano mag-reserve: {how_to_reserve}."] },
  payment_thanks: { t: "Thank you for your payment", s: ["Thank you po sa lahat ng nagbayad ng rent para sa {month}.", "{note}"] },
  general: { t: "Announcement", s: ["{idea}"] },
  emergency: { t: "Emergency notice", s: ["Notice: {kind}.", "Ano ang gagawin: {what_to_do}.", "Contact: {contact}.", SAFE.taglish] },
  typhoon: { t: "Typhoon notice", s: ["Typhoon signal: {signal}.", "House action: {house_action}.", "{notes}", SAFE.taglish] },
  move_out: { t: "Move-out notice", s: ["Move-out date: {date}.", "Deposit: {deposit_rule}.", "Room check: {room_check_date}."] },
  deposit_advance: { t: "Deposit and advance", s: ["Paki-bayad po ang {amount} on or before {due_date}.", "Receipt: {receipt_note}."] },
  wifi_update: { t: "Wi-Fi update", s: ["Bagong Wi-Fi password: {password}.", "Outage: {outage}.", "Schedule: {schedule}.", "Fair use: {fair_use}."] },
  pest_control: { t: "Pest control schedule", s: ["May pest control sa {date} sa {rooms}.", "Ang kailangan ninyong gawin: {tenant_action}."] },
  room_inspection: { t: "Room inspection", s: ["May room inspection sa {date}, {time_window}.", "Reason: {reason}.", "Paki-make sure po na accessible ang room ninyo."] },
  gate_lock_change: { t: "Gate and lock update", s: ["Bagong gate closing time: {new_gate_time}.", "Key/code instructions: {key_code_instructions}.", "Effective sa {effective_date}."] },
  meeting: { t: "House meeting", s: ["May house meeting sa {date} sa {place}.", "Agenda: {agenda}.", "Sana po makapunta kayo."] },
  holiday: { t: "Holiday notice", s: ["Greetings po para sa {occasion}!", "Note: {owner_away_note}."] },
  price_update: { t: "Price update", s: ["Starting {effective_date}, ang bagong rate ay {new_amount}.", "Reason: {reason}.", "Advance notice po ito para makapag-plan kayo. Message lang po kung may questions."] },
  lost_found: { t: "Lost and found", s: ["Nakita ang {item} sa {found_where}.", "Paano i-claim: {how_to_claim}."] },
  rule_violation: { t: "Reminder: house rules", s: ["Reminder: {rule}.", "Details: {detail}.", "Reminder po ito para sa lahat. Thank you po sa pagsunod sa rules."] },
  thanks: { t: "Thank you", s: ["Thank you po sa lahat para sa {reason}."] },
  congrats: { t: "Congratulations", s: ["Congratulations sa {who_group} para sa {occasion}!||Congratulations po sa {occasion}!"] },
  survey: { t: "Quick question", s: ["Question: {question}", "Paki-reply po: {how_to_reply}."] },
};

export const TEMPLATES: Record<string, Record<Lang, Tpl>> = (() => {
  const out: Record<string, Record<Lang, Tpl>> = {};
  for (const id of Object.keys(EN)) out[id] = { en: EN[id], fil: FIL[id], ceb: CEB[id], taglish: TAG[id] };
  return out;
})();

export interface TemplateInput { type: string; lang: Lang; tone: Tone; length: Length; emoji: boolean; fields: Record<string, string> }
export interface TemplateOutput { title: string; message: string; notes?: string }

const PLACEHOLDER = /\{([a-z_]+)\}/g;
/** Fill one sentence (or "a||b" alternatives); null if no alternative has all its placeholders filled. */
function fillSentence(spec: string, f: Record<string, string>): string | null {
  for (const alt of spec.split("||")) {
    let ok = true;
    const text = alt.replace(PLACEHOLDER, (_m, k: string) => { const v = (f[k] ?? "").trim(); if (!v) ok = false; return v; });
    if (ok) return tidy(text);
  }
  return null;
}
/** The owner may type "Nov 5." -> avoid "Nov 5..": collapse a doubled end mark. */
function tidy(s: string): string { return s.replace(/([.!?:;,])\s*\.(?=\s|$)/g, "$1").replace(/\s+/g, " ").trim(); }

export function buildTemplate(inp: TemplateInput): TemplateOutput {
  const def = getType(inp.type);
  const tpl = TEMPLATES[inp.type]?.[inp.lang];
  if (!def || !tpl) throw new Error("unknown template");
  const sentences: string[] = [];
  for (const spec of tpl.s) { const s = fillSentence(spec, inp.fields); if (s) sentences.push(s); }
  if (!sentences.length) sentences.push(GENERIC[inp.lang]);

  const lead = inp.length === "short" ? "" : (tpl.lead?.[inp.tone] ?? GREET[inp.lang][inp.tone]);
  let body = sentences;
  if (inp.length === "short") {                       // keep whole sentences while they fit in 250 chars
    const keep: string[] = []; let n = 0;
    for (const s of sentences) { if (n + s.length + 1 > 240 && keep.length) break; keep.push(s); n += s.length + 1; }
    body = keep;
  }
  const parts = [lead, body.join(" ")];
  if (inp.length === "detailed") parts.push(DETAIL[inp.lang]);
  const message = parts.filter(Boolean).join("\n\n");

  const missing = def.fields.filter((x) => x.required && !(inp.fields[x.key] ?? "").trim()).map((x) => x.key.replace(/_/g, " "));
  if (def.oneOf && !def.oneOf.some((k) => (inp.fields[k] ?? "").trim())) missing.push(def.oneOf.map((k) => k.replace(/_/g, " ")).join(" or "));
  const out: TemplateOutput = { title: (inp.emoji ? def.emoji + " " : "") + tpl.t, message };
  if (missing.length) out.notes = "Add the " + missing.join(", ") + ".";
  return out;
}
