// send-push (v2.3.0, hybrid payload + diagnostics in v2.9.2): turns a database event into phone notifications through Firebase Cloud Messaging (FCM HTTP v1).
// Called ONLY by the database triggers of migration 029 (public.push_notify) with the header x-bofo-push-secret.
//   kind "dm"      new message in a student <-> owner chat      -> the OTHER person of the conversation
//   kind "group"   new message / announcement in a boarding group -> every active, un-muted member except the sender (the owner is a member)
//   kind "report"  new user report                              -> ONLY users whose users.role = 'admin' (checked here, at send time)
//   kind "idreview" an owner sent an ID for verification (v2.6.0, migration 033) -> ONLY admins; no name, no ID data
//   kind "stale"   items waiting more than 3 days (v2.6.0; sent once a day by public.push_stale_check()) -> ONLY admins; counts only
//   kind "test"    {"as": "idreview" | "stale" | "report"}: a labelled test notification to every admin phone (run push_notify('test', ...) in the SQL editor)
// Messages are DATA-ONLY: the app builds the notification itself (channels, grouping, tap target).
// Secrets (never commit): PUSH_WEBHOOK_SECRET = long random string (same value as push_settings.secret),
//                         FCM_SERVICE_ACCOUNT = the whole Firebase service-account JSON (one line).
// Deploy: supabase functions deploy send-push --no-verify-jwt     (SUPABASE_URL / SERVICE_ROLE are provided automatically)
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const WEBHOOK_SECRET = Deno.env.get("PUSH_WEBHOOK_SECRET") ?? "";
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

const REASONS: Record<string, string> = {
  scam: "Scam", fake_listing: "Fake listing", harassment: "Harassment",
  inappropriate: "Inappropriate content", wrong_info: "Wrong information", other: "Other",
};

type Push = { userIds: string[]; data: Record<string, string> };

function sameSecret(a: string, b: string): boolean {
  if (!a || a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
}

const clip = (s: unknown, n: number) => { const t = String(s ?? "").replace(/\s+/g, " ").trim(); return t.length > n ? t.slice(0, n - 1) + "…" : t; };

// ---------------------------------------------------------------- FCM auth (service account -> OAuth access token)
// v2.9.2: also accepts a key pasted with real line breaks inside the private_key (a common copy/paste slip).
const SA = (() => {
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT") ?? "{}";
  try { return JSON.parse(raw); } catch { /* try again below */ }
  try { return JSON.parse(raw.replace(/\r?\n/g, "\\n")); } catch { return {}; }
})();
let cached: { token: string; exp: number } | null = null;

const b64url = (v: string | ArrayBuffer) => {
  const bytes = typeof v === "string" ? new TextEncoder().encode(v) : new Uint8Array(v);
  let s = ""; for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};
const pemToDer = (pem: string) => {
  const raw = atob(pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, ""));
  const out = new Uint8Array(raw.length); for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out.buffer;
};

async function accessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.exp - 60 > now) return cached.token;
  if (!SA.client_email || !SA.private_key || !SA.project_id) throw new Error("FCM_SERVICE_ACCOUNT is not set");
  const unsigned = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" })) + "." + b64url(JSON.stringify({
    iss: SA.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600,
  }));
  const key = await crypto.subtle.importKey("pkcs8", pemToDer(SA.private_key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: unsigned + "." + b64url(sig) }),
  });
  const j = await r.json();
  if (!r.ok || !j.access_token) throw new Error("FCM auth failed: " + JSON.stringify(j));
  cached = { token: j.access_token, exp: now + (j.expires_in ?? 3600) };
  return cached.token;
}

// v2.9.2: HYBRID message = data + a ready-made notification. The phone's system draws the notification by itself, so it still appears when
// the app is closed or our own code never gets to run (battery savers, a dropped role check, a crash). While the app is open Firebase hands the
// message to BofoMessagingService instead, which draws the richer in-app version (chat stack, "already looking at this chat" check).
const CHANNELS: Record<string, string> = { dm: "bofo_chats", group: "bofo_boarding", report: "bofo_reports", idreview: "bofo_admin", stale: "bofo_admin", test: "bofo_chats" };
const ADMIN_KINDS = new Set(["report", "idreview", "stale"]);

function shown(d: Record<string, string>) {
  const kind = d.kind ?? "";
  const ann = d.ann === "1";
  const channel = kind === "group" && ann ? "bofo_announcements" : (CHANNELS[kind] ?? "bofo_chats");
  let body = d.body ?? "";
  if (kind === "group") body = ann ? "\u{1F4CC} " + body : (d.sender ? d.sender + ": " + body : body);
  return { title: d.title || "BOFO", body, channel, priv: ADMIN_KINDS.has(kind) };
}

/** "ok" | "dead" (token is gone: delete it) | { fail: why } */
async function sendOne(token: string, data: Record<string, string>): Promise<"ok" | "dead" | { fail: string }> {
  const n = shown(data);
  const r = await fetch(`https://fcm.googleapis.com/v1/projects/${SA.project_id}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${await accessToken()}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      message: {
        token,
        data,
        notification: { title: n.title, body: n.body },
        android: {
          priority: "HIGH", ttl: "86400s",
          notification: {
            channel_id: n.channel, click_action: "com.bofo.finder.OPEN_PUSH", notification_priority: "PRIORITY_HIGH", default_sound: true,
            ...(n.priv ? { visibility: "PRIVATE" } : {}),     // admin pushes: contents hidden on a locked screen
          },
        },
      },
    }),
  });
  if (r.ok) return "ok";
  const t = await r.text();
  if (r.status === 404 || t.includes("UNREGISTERED")) return "dead";
  console.error("FCM refused", r.status, t.slice(0, 300));
  return { fail: `FCM ${r.status}: ${t.slice(0, 160)}` };
}

// ---------------------------------------------------------------- who gets what
async function names(ids: string[]): Promise<Record<string, string>> {
  const { data } = await admin.from("users").select("id, full_name").in("id", ids);
  const m: Record<string, string> = {};
  for (const u of data ?? []) m[u.id] = u.full_name || "Someone";
  return m;
}

async function planDm(rec: any): Promise<Push | null> {
  const { data: c } = await admin.from("conversations").select("student_id, owner_id, boarding_house_id").eq("id", rec.conversation_id).maybeSingle();
  if (!c) return null;
  const to = c.student_id === rec.sender_id ? c.owner_id : c.student_id;
  if (!to || to === rec.sender_id) return null;
  // the receiver blocked the sender: no sound, no banner
  const { data: blk } = await admin.from("blocks_reports").select("id").eq("reporter_id", to).eq("reported_user_id", rec.sender_id).eq("is_block", true).limit(1);
  if ((blk ?? []).length) return null;
  const { data: bh } = await admin.from("boarding_houses").select("name").eq("id", c.boarding_house_id).maybeSingle();
  const who = (await names([rec.sender_id]))[rec.sender_id] ?? "New message";
  return { userIds: [to], data: { kind: "dm", id: String(rec.conversation_id), title: who, place: bh?.name ?? "", body: clip(rec.body, 140), msg: String(rec.id), sender: who } };
}

async function planGroup(rec: any): Promise<Push | null> {
  const { data: members } = await admin.from("bh_members").select("user_id, muted").eq("bh_id", rec.bh_id).eq("status", "active");
  const to = (members ?? []).filter((m: any) => m.user_id !== rec.sender_id && !m.muted).map((m: any) => m.user_id as string);
  if (!to.length) return null;
  const { data: bh } = await admin.from("boarding_houses").select("name").eq("id", rec.bh_id).maybeSingle();
  const who = (await names([rec.sender_id]))[rec.sender_id] ?? "Someone";
  const ann = rec.is_announcement === true;
  return {
    userIds: to,
    data: {
      kind: "group", id: String(rec.bh_id), title: bh?.name ?? "Boarding group", place: bh?.name ?? "",
      body: ann ? clip(rec.title || rec.body, 140) : clip(rec.body, 140), msg: String(rec.id), sender: who, ann: ann ? "1" : "0",
    },
  };
}

async function planReport(rec: any): Promise<Push | null> {
  // ONLY admin accounts: the role is read from the database now, not from anything the phone says.
  const { data: admins } = await admin.from("users").select("id").eq("role", "admin");
  const to = (admins ?? []).map((u: any) => u.id as string).filter((id: string) => id !== rec.reporter_id);
  if (!to.length) return null;
  // Lock-screen safe: the reason label only. No names, no details.
  return { userIds: to, data: { kind: "report", id: String(rec.id), title: "New report", body: (REASONS[rec.reason] ?? "A user") + " report is waiting for review", msg: String(rec.id) } };
}

// v2.6.0: an owner sent an ID. Admins only. Lock-screen safe: the type only. The row must still be waiting (a banned ID is rejected right after the insert).
async function planIdReview(rec: any, skipCheck = false): Promise<Push | null> {
  if (!skipCheck) {
    const { data: v } = await admin.from("owner_verifications").select("status").eq("id", rec.id).maybeSingle();
    if (!v || v.status !== "pending") return null;
  }
  const { data: admins } = await admin.from("users").select("id").eq("role", "admin");
  const to = (admins ?? []).map((u: any) => u.id as string).filter((id: string) => id !== rec.owner_id);
  if (!to.length) return null;
  return { userIds: to, data: { kind: "idreview", id: String(rec.id), title: "New ID to review", body: "An ID is waiting for review", msg: String(rec.id) } };
}

// v2.6.0: the daily reminder. Counts only. "place" tells the app which tab to open: the group with the most waiting items (ties: IDs, reports, payments).
async function planStale(rec: any): Promise<Push | null> {
  const n = (v: unknown) => Math.max(0, Math.min(9999, Math.floor(Number(v) || 0)));
  const ids = n(rec.ids), reports = n(rec.reports), payments = n(rec.payments), total = ids + reports + payments;
  if (total <= 0) return null;
  const { data: admins } = await admin.from("users").select("id").eq("role", "admin");
  const to = (admins ?? []).map((u: any) => u.id as string);
  if (!to.length) return null;
  const target = ids >= reports && ids >= payments ? "queue" : reports >= payments ? "reports" : "payments";
  return { userIds: to, data: { kind: "stale", id: "stale", title: "Items are waiting", body: `${total} ${total === 1 ? "item has" : "items have"} been waiting more than 3 days`, place: target, msg: "stale" } };
}

async function planTest(rec: any): Promise<Push | null> {
  const as = String(rec?.as ?? "idreview");
  const stamp = String(Date.now());
  if (as === "me") {          // v2.9.2: a plain test for ONE account (any role): select public.push_notify('test', '{"as":"me","user_id":"<uuid>"}'::jsonb);
    const uid = String(rec?.user_id ?? "");
    if (!uid) return null;
    return { userIds: [uid], data: { kind: "test", id: "test-" + stamp, title: "BOFO test notification", body: "If you can read this, phone notifications work.", place: "", msg: stamp, sender: "BOFO" } };
  }
  const plan = as === "stale" ? await planStale({ ids: 1, reports: 1, payments: 0 })
    : as === "report" ? await planReport({ id: "test-" + stamp, reason: "other", reporter_id: null })
    : await planIdReview({ id: "test-" + stamp, owner_id: null }, true);
  if (plan) plan.data.title = "Test: " + plan.data.title;
  return plan;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("method not allowed", { status: 405 });
  if (!sameSecret(WEBHOOK_SECRET, req.headers.get("x-bofo-push-secret") ?? "")) return new Response("unauthorized", { status: 401 });
  try {
    const { kind, record } = await req.json();
    const plan = kind === "dm" ? await planDm(record) : kind === "group" ? await planGroup(record) : kind === "report" ? await planReport(record)
      : kind === "idreview" ? await planIdReview(record) : kind === "stale" ? await planStale(record) : kind === "test" ? await planTest(record) : null;
    if (!plan) return Response.json({ sent: 0, reason: "nobody_to_notify" });       // e.g. blocked sender, unknown conversation, empty group

    const { data: rows } = await admin.from("device_tokens").select("token, user_id").in("user_id", plan.userIds);
    if (!(rows ?? []).length) { console.warn("send-push", kind, "no device token for", plan.userIds.length, "user(s)"); return Response.json({ sent: 0, reason: "no_device_token", users: plan.userIds.length }); }
    let sent = 0; const dead: string[] = []; const fails: string[] = [];
    await Promise.all((rows ?? []).map(async (t: any) => {
      const res = await sendOne(t.token, plan.data);
      if (res === "ok") sent++; else if (res === "dead") dead.push(t.token); else fails.push(res.fail);
    }));
    if (dead.length) await admin.from("device_tokens").delete().in("token", dead);
    console.log("send-push", kind, "tokens", (rows ?? []).length, "sent", sent, "dead", dead.length, "failed", fails.length);
    return Response.json({ sent, removed: dead.length, tokens: (rows ?? []).length, ...(fails.length ? { failed: fails.length, why: fails[0] } : {}) });
  } catch (e) {
    console.error("send-push failed", e);
    return new Response("error: " + String((e as Error)?.message ?? e).slice(0, 300), { status: 500 });      // visible in net._http_response (e.g. "FCM_SERVICE_ACCOUNT is not set")
  }
});
