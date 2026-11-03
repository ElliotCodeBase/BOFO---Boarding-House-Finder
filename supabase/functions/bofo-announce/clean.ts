// bofo-announce / clean.ts - validate the request, clean what the model wrote, clip to the limits. Pure TypeScript (no Deno APIs).
// The CODE is the safety net: whatever the model returns is parsed, stripped of markdown / links / phone numbers / passwords the
// owner did not type in THIS request, checked for invented numbers, and clipped at a word boundary.
import { ADJUSTS, LANGS, LENGTHS, MAX_CLOSING_NAME, MAX_FIELD_LEN, TONES, getType } from "./catalogue.ts";
import type { Adjust, Lang, Length, Tone } from "./catalogue.ts";
import { closingText } from "./templates.ts";

export const TITLE_MAX = 100, MSG_MAX = 900, MSG_SHORT_MAX = 250;   // what we aim for
export const TITLE_HARD = 120, MSG_HARD = 1000;                     // never above (app / post_announcement limits)
export const NOTES_MAX = 160;
const MAX_BODY_CHARS = 20_000;

export interface AnnounceRequest {
  type: string; lang: Lang; tone: Tone; length: Length; emoji: boolean;
  closing: { on: boolean; name: string };
  fields: Record<string, string>;
  adjust?: Adjust;
}
export interface AnnounceError { error: string; message: string; field?: string }
export type Validation = { ok: true; value: AnnounceRequest } | { ok: false; error: AnnounceError };
const bad = (error: string, message: string, field?: string): Validation => ({ ok: false, error: field ? { error, message, field } : { error, message } });

const TOP_KEYS = new Set(["type", "lang", "tone", "length", "emoji", "closing", "fields", "adjust"]);
const CONTROL = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F\u200B-\u200F\u202A-\u202E\u2066-\u2069]/g;
const isObj = (x: unknown): x is Record<string, unknown> => !!x && typeof x === "object" && !Array.isArray(x);
/** One line, no control / bidi characters, single spaces. */
export const normalizeValue = (s: string) => s.replace(CONTROL, "").replace(/\s+/g, " ").trim();

export function validateRequest(body: unknown): Validation {
  if (!isObj(body)) return bad("invalid_body", "The request body must be a JSON object.");
  for (const k of Object.keys(body)) if (!TOP_KEYS.has(k)) return bad("unknown_param", `Unknown parameter "${k}".`, k);

  if (typeof body.type !== "string") return bad("unknown_type", "type is required (a string).", "type");
  const def = getType(body.type);
  if (!def) return bad("unknown_type", `Unknown announcement type "${body.type.slice(0, 40)}".`, "type");

  const lang = body.lang === undefined ? "en" : body.lang;
  if (typeof lang !== "string" || !(LANGS as readonly string[]).includes(lang)) return bad("bad_lang", `lang must be one of: ${LANGS.join(", ")}.`, "lang");
  const tone = body.tone === undefined ? def.tone : body.tone;
  if (typeof tone !== "string" || !(TONES as readonly string[]).includes(tone)) return bad("bad_tone", `tone must be one of: ${TONES.join(", ")}.`, "tone");
  const length = body.length === undefined ? "normal" : body.length;
  if (typeof length !== "string" || !(LENGTHS as readonly string[]).includes(length)) return bad("bad_length", `length must be one of: ${LENGTHS.join(", ")}.`, "length");
  const emoji = body.emoji === undefined ? false : body.emoji;
  if (typeof emoji !== "boolean") return bad("bad_emoji", "emoji must be true or false.", "emoji");

  let adjust: Adjust | undefined;
  if (body.adjust !== undefined) {
    if (typeof body.adjust !== "string" || !(ADJUSTS as readonly string[]).includes(body.adjust)) return bad("bad_adjust", `adjust must be one of: ${ADJUSTS.join(", ")}.`, "adjust");
    adjust = body.adjust as Adjust;
  }

  let closing = { on: false, name: "" };
  if (body.closing !== undefined) {
    const c = body.closing;
    if (!isObj(c)) return bad("bad_closing", "closing must be an object { on, name }.", "closing");
    for (const k of Object.keys(c)) if (k !== "on" && k !== "name") return bad("bad_closing", `Unknown closing key "${k}".`, "closing");
    if (typeof c.on !== "boolean") return bad("bad_closing", "closing.on must be true or false.", "closing");
    if (c.name !== undefined && typeof c.name !== "string") return bad("bad_closing", "closing.name must be a string.", "closing");
    const name = normalizeValue(typeof c.name === "string" ? c.name : "");
    if (name.length > MAX_CLOSING_NAME) return bad("bad_closing", `closing.name can be at most ${MAX_CLOSING_NAME} characters.`, "closing");
    closing = { on: c.on && name.length > 0, name };   // closing on without a name = no sign-off (never invent a name)
  }

  const rawFields = body.fields === undefined ? {} : body.fields;
  if (!isObj(rawFields)) return bad("invalid_body", "fields must be an object of text values.", "fields");
  const allowed = new Map(def.fields.map((f) => [f.key, f] as const));
  const fields: Record<string, string> = {};
  for (const [k, v] of Object.entries(rawFields)) {
    if (!allowed.has(k)) return bad("unknown_field", `"${k}" is not a field of "${def.id}". Allowed: ${def.fields.map((f) => f.key).join(", ")}.`, k);
    if (typeof v !== "string") return bad("bad_value", `Field "${k}" must be text.`, k);
    const val = normalizeValue(v);
    if (val.length > MAX_FIELD_LEN) return bad("field_too_long", `Field "${k}" can be at most ${MAX_FIELD_LEN} characters.`, k);
    if (val) fields[k] = val;                            // empty after trimming = not filled
  }
  for (const f of def.fields) if (f.required && !fields[f.key]) return bad("missing_field", `Please fill in "${f.key}".`, f.key);
  if (def.oneOf && !def.oneOf.some((k) => fields[k])) return bad("missing_field", `Please fill in at least one of: ${def.oneOf.join(", ")}.`, def.oneOf[0]);

  return { ok: true, value: { type: def.id, lang: lang as Lang, tone: tone as Tone, length: length as Length, emoji, closing, fields, ...(adjust ? { adjust } : {}) } };
}
export const MAX_REQUEST_CHARS = MAX_BODY_CHARS;

/** Apply `adjust` on top of the chosen options (the app normally re-sends changed tone/length itself; this is the backstop). */
export function effectiveOptions(r: AnnounceRequest): { tone: Tone; length: Length } {
  let { tone, length } = r;
  if (r.adjust === "shorter" && length !== "short") length = length === "detailed" ? "normal" : "short";
  if (r.adjust === "friendlier" && tone !== "short") tone = "friendly";
  if (r.adjust === "formal") tone = "formal";
  return { tone, length };
}

// ------------------------------------------------------------------ model output
const THINK_BLOCK = /<(think|thinking|reasoning)>[\s\S]*?(<\/\1>|$)/gi;
export const stripThinking = (t: string) => t.replace(THINK_BLOCK, " ").trim();
const LEAK = /^\s*(?:okay|ok|alright|hmm|let me|let's|the user|user (?:wants|is asking)|i (?:need|must|should|have to)|we (?:need|must|should)|my task)\b|\b(?:the user (?:wants|asks|is asking)|as an ai|i cannot|i can't help|system prompt|owner_facts)\b/i;

/** Parse the model's JSON: fences stripped, first {...} taken, ONE bounded local repair (trailing commas, raw newlines in strings, missing closing quote/brace). */
export function parseModelJson(text: string): Record<string, unknown> | null {
  if (typeof text !== "string") return null;
  const s = text.replace(/```(?:json)?/gi, "").trim();
  const a = s.indexOf("{");
  if (a < 0) return null;
  const b = s.lastIndexOf("}");
  const tryParse = (x: string) => { try { const v = JSON.parse(x); return isObj(v) ? v : null; } catch { return null; } };
  if (b > a) { const v = tryParse(s.slice(a, b + 1)); if (v) return v; }
  return tryParse(repairJson(b > a ? s.slice(a, b + 1) : s.slice(a)));
}
function repairJson(s: string): string {
  let out = "", inStr = false, esc = false;
  for (const ch of s) {
    if (inStr) {
      if (esc) { out += ch; esc = false; continue; }
      if (ch === "\\") { out += ch; esc = true; continue; }
      if (ch === '"') { inStr = false; out += ch; continue; }
      if (ch === "\n") { out += "\\n"; continue; }
      if (ch === "\r") continue;
      if (ch === "\t") { out += "\\t"; continue; }
      out += ch;
    } else { if (ch === '"') inStr = true; out += ch; }
  }
  if (inStr) out += '"';                                    // truncated inside a string
  out = out.replace(/,\s*([}\]])/g, "$1");
  if (!/}\s*$/.test(out)) out = out.replace(/,\s*$/, "") + "}";   // truncated before the closing brace
  return out;
}

export function stripMarkdown(t: string): string {
  return t
    .replace(/```[\s\S]*?```/g, " ").replace(/`([^`]*)`/g, "$1")
    .replace(/!\[[^\]]*\]\([^)]*\)/g, "").replace(/\[([^\]]+)\]\(([^)]*)\)/g, "$1")
    .replace(/^\s{0,3}#{1,6}\s+/gm, "").replace(/^\s{0,3}>\s?/gm, "")
    .replace(/^\s*(?:[-*•]|\d{1,2}[.)])\s+/gm, "")
    .replace(/(\*\*|__)(.+?)\1/g, "$2").replace(/(^|[\s(])[*_]([^*_\n]+?)[*_](?=[\s).,!?:;]|$)/g, "$1$2")
    .replace(/[*_]{2,}/g, "").replace(/^\s*[-=_*]{3,}\s*$/gm, "");
}

const URL_RE = /\b(?:https?:\/\/|www\.)\S+/gi;
const EMAIL_RE = /[\w.+-]+@[\w-]+(?:\.[\w-]+)+/g;
const DOMAIN_RE = /\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|net|org|ph|io|me|ly|gov|edu|co|info|app|xyz|site|link)\b(?:\/\S*)?/gi;
const PHONE_RE = /\+?\d[\d\s().-]{5,}\d/g;
const DATE_LIKE = /^\d{1,2}[/-]\d{1,2}[/-]\d{2,4}$|^\d{4}[/-]\d{1,2}[/-]\d{1,2}$/;
const SECRET_RE = /\b(password|passcode|passphrase|pin|wi-?fi key|key ?code)\b(\s*(?:is|na|ay|:|=|-|—)?\s*)["'“‘]?([^\s"'”’,;]{3,})/gi;
const EMOJI_RE = /[\p{Extended_Pictographic}\uFE0F\u200D]/gu;
const digitsOf = (s: string) => s.replace(/\D/g, "");

/** Remove links / emails / phone-like numbers / password-like tokens that the owner did not type in this request. */
export function removeUnsafe(text: string, ownerValues: string[]): string {
  const typed = ownerValues.join("\n").toLowerCase();
  const typedDigits = ownerValues.map(digitsOf).filter((d) => d.length >= 7);
  const keepIfTyped = (m: string) => (typed.includes(m.toLowerCase().replace(/[.,;:!?)]+$/, "")) ? m : "");
  let t = text.replace(URL_RE, keepIfTyped).replace(EMAIL_RE, keepIfTyped).replace(DOMAIN_RE, keepIfTyped);
  t = t.replace(PHONE_RE, (m) => {
    const d = digitsOf(m);
    if (d.length < 7 || DATE_LIKE.test(m.trim())) return m;
    return typedDigits.some((x) => x.includes(d) || d.includes(x)) ? m : "";
  });
  // password-like = the token has a digit/symbol or mixed case ("please" or "ask" are kept)
  t = t.replace(SECRET_RE, (m, w: string, sep: string, tok: string) => {
    if (typed.includes(tok.toLowerCase())) return m;
    const secretish = /\d|[^A-Za-z]/.test(tok) || (/[a-z]/.test(tok) && /[A-Z]/.test(tok.slice(1)));
    return secretish ? `${w}${sep}`.trimEnd() : m;
  });
  return t.replace(/[ \t]{2,}/g, " ").replace(/ +([,.;:!?])/g, "$1").replace(/\(\s*\)/g, "").replace(/[ \t]+\n/g, "\n").trim();
}

/** Every number the model writes must come from the owner's values (no invented dates / amounts / counts). */
export function hasInventedNumbers(text: string, ownerValues: string[]): boolean {
  const pools = ownerValues.map(digitsOf);
  for (const tok of text.match(/\d+/g) ?? []) {
    if (/^0+$/.test(tok)) continue;                          // ".00", "00" in "8:00"
    if (!pools.some((p) => p.includes(tok))) return true;
  }
  return false;
}

/** Cut at a sentence end or word boundary; never mid-word; never above `max`. */
export function clip(text: string, max: number): string {
  const t = text.replace(/\n{3,}/g, "\n\n").trim();
  if (t.length <= max) return t;
  const cut = t.slice(0, max);
  let end = -1;
  for (const m of cut.matchAll(/[.!?](?=\s|$)/g)) end = (m.index ?? 0) + 1;
  if (end >= max * 0.5) return cut.slice(0, end).trim();
  const sp = Math.max(cut.lastIndexOf(" "), cut.lastIndexOf("\n"));
  if (sp >= max * 0.5) { const w = cut.slice(0, Math.min(sp, max - 1)).replace(/[\s,;:\-–—(]+$/, ""); return w + "…"; }
  return cut.slice(0, max - 1).trimEnd() + "…";
}

export const messageMax = (length: Length) => (length === "short" ? MSG_SHORT_MAX : MSG_MAX);
export interface Final { title: string; message: string; notes?: string }

/** Title + message + sign-off, clipped. The sign-off is added by the code (never by the model) and always fits. */
export function assemble(title: string, message: string, req: AnnounceRequest, length: Length, notes?: string): Final {
  const sign = req.closing.on && req.closing.name ? closingText(req.lang, req.closing.name) : "";
  const max = messageMax(length);
  let body = message.trim();
  if (sign) {
    const base = body.toLowerCase().includes(req.closing.name.toLowerCase()) ? body : clip(body, max - sign.length - 2);
    body = base.toLowerCase().includes(req.closing.name.toLowerCase()) ? base : base + "\n\n" + sign;
  } else body = clip(body, max);
  const out: Final = { title: clip(title.replace(/\s+/g, " "), TITLE_MAX), message: clip(body, Math.min(max, MSG_HARD)) };
  if (out.title.length > TITLE_HARD) out.title = out.title.slice(0, TITLE_HARD);
  if (notes) out.notes = clip(notes, NOTES_MAX);
  return out;
}

const tidyText = (s: string) => s.replace(/\r/g, "").replace(CONTROL, "").replace(/[ \t]{2,}/g, " ").replace(/\n{3,}/g, "\n\n").trim();

/** Raw model text -> a safe, clipped result, or null when it is unusable (the caller then uses the template). */
export function finalizeModelOutput(text: string, req: AnnounceRequest, length: Length): Final | null {
  const obj = parseModelJson(text);
  if (!obj || typeof obj.title !== "string" || typeof obj.message !== "string") return null;
  const owner = Object.values(req.fields);
  const scrub = (s: string) => {
    let t = removeUnsafe(stripMarkdown(s), owner);
    if (!req.emoji) t = t.replace(EMOJI_RE, "");
    return tidyText(t);
  };
  const title = scrub(obj.title).replace(/\n+/g, " ").trim();
  const message = scrub(obj.message);
  const notes = typeof obj.notes === "string" ? scrub(obj.notes).replace(/\n+/g, " ") : "";
  if (title.length < 3 || message.length < 10) return null;
  if (LEAK.test(title) || LEAK.test(message)) return null;
  if (hasInventedNumbers(title + "\n" + message, owner)) return null;
  const out = assemble(title, message, req, length, notes || undefined);
  return out.title.length >= 3 && out.message.length >= 10 ? out : null;
}
