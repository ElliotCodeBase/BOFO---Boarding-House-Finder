// Supabase Edge Function: bofo-announce  (AI Announcement Writer, backend only - it NEVER posts anything)
// Deploy (NOT run automatically):  supabase functions deploy bofo-announce
// Secrets (already set for bofo-chat, shared): OPENROUTER_API_KEY, LLM_MODELS. Needs migration 031 for the 40/day limit (works without it, see below).
// Request:  POST { type, lang, tone, length, emoji, closing:{on,name}, fields:{...}, adjust? }   (see catalogue.ts)
// Response: 200 { title, message, notes?, degraded } | 400 {error,message,field?} | 401 | 403 {error:"not_owner"|"ai_disabled"} | 429 {error:"rate_limited",retry_after} | 502
// Order: JWT -> owner of >=1 house -> ai_enabled -> validate -> rate limit (kind='announce') -> LLM -> code cleans -> template if anything fails.
// Privacy: no announcement text, field value, password or model output is logged or stored. The counter row holds only user_id, kind, time.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";
import { MAX_REQUEST_CHARS, validateRequest } from "./clean.ts";
import { generate, templateResult } from "./generate.ts";
import { decide, isMissingKindColumn } from "./limits.ts";
import { makeLlm, readLlmEnv } from "./llm.ts";

const cors = { "Access-Control-Allow-Headers": "authorization, content-type, apikey, x-client-info, x-bofo-app-version" };
const json = (b: unknown, s = 200, extra: Record<string, string> = {}) => new Response(JSON.stringify(b), { status: s, headers: { ...cors, ...extra, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  try {
    const url = Deno.env.get("SUPABASE_URL")!;
    const userClient = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, { global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } } });
    const { data: u } = await userClient.auth.getUser();
    if (!u?.user) return json({ error: "unauthorized" }, 401);
    const uid = u.user.id;
    const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

    // 1) owner of at least one house (is_bh_owner needs a house id, so query boarding_houses directly)
    const own = await admin.from("boarding_houses").select("id").eq("owner_id", uid).limit(1);
    if (own.error) { console.error("bofo-announce: owner check failed", own.error.code); return json({ error: "server_error" }, 500); }   // fail closed
    if (!own.data?.length) return json({ error: "not_owner" }, 403);

    // 2) admin switch (missing row or read error = on); admins are exempt from "off"
    const flag = await admin.from("app_flags").select("value").eq("key", "ai_enabled").maybeSingle();
    if (flag.error) console.error("bofo-announce: ai_enabled read failed", flag.error.code);
    if (!flag.error && flag.data && flag.data.value === false) {
      const me = await admin.from("users").select("role").eq("id", uid).maybeSingle();
      if (me.data?.role !== "admin") return json({ error: "ai_disabled" }, 403);
    }

    // 3) validate (cheap, before counting)
    let body: unknown;
    try { const raw = await req.text(); if (raw.length > MAX_REQUEST_CHARS) return json({ error: "invalid_body", message: "Request too large." }, 400); body = JSON.parse(raw); }
    catch { return json({ error: "invalid_body", message: "The request body must be valid JSON." }, 400); }
    const v = validateRequest(body);
    if (!v.ok) return json(v.error, 400);

    // 4) rate limit: 10/min and 40/day per user, announce rows only. Not atomic (count, then insert) - same trade-off as bofo-chat.
    const now = Date.now();
    let stamps: number[] = [], enforceDay = true;
    const q = await admin.from("ai_usage").select("created_at").eq("user_id", uid).eq("kind", "announce")
      .gte("created_at", new Date(now - 86_400_000).toISOString()).order("created_at", { ascending: false }).limit(40);
    if (q.error && isMissingKindColumn(q.error)) {
      // Migration 031 not applied yet: FAIL OPEN for the daily limit, keep the minute limit by counting ALL of this user's rows (chat + announce).
      console.error("bofo-announce: ai_usage.kind missing (migration 031 not applied) -> minute limit only");
      enforceDay = false;
      const q2 = await admin.from("ai_usage").select("created_at").eq("user_id", uid).gte("created_at", new Date(now - 60_000).toISOString()).order("created_at", { ascending: false }).limit(10);
      stamps = (q2.data ?? []).map((r: { created_at: string }) => Date.parse(r.created_at));
    } else if (q.error) {
      console.error("bofo-announce: usage read failed", q.error.code);   // other DB error: do not block the owner
    } else stamps = (q.data ?? []).map((r: { created_at: string }) => Date.parse(r.created_at));
    const d = decide(stamps, now, enforceDay);
    if (!d.ok) return json({ error: "rate_limited", retry_after: d.retry_after }, 429, { "Retry-After": String(d.retry_after) });
    let ins = await admin.from("ai_usage").insert({ user_id: uid, kind: "announce" });
    if (ins.error && isMissingKindColumn(ins.error)) ins = await admin.from("ai_usage").insert({ user_id: uid });
    if (ins.error) console.error("bofo-announce: usage insert failed", ins.error.code);

    // 5) write (LLM -> code cleans -> template fallback)
    try {
      const llm = makeLlm(readLlmEnv((k) => Deno.env.get(k)));
      return json(await generate(v.value, llm));
    } catch (_e) {
      try { return json(templateResult(v.value)); } catch { return json({ error: "generation_failed" }, 502); }
    }
  } catch (e) {
    console.error("bofo-announce: unexpected", (e as Error)?.name);   // name only: never message text
    return json({ error: "server_error" }, 500);
  }
});
