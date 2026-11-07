// send-push (v2.3.0, hybrid payload + diagnostics in v2.9.2, admin app targeting in v2.9.11): turns a database event into phone notifications through Firebase Cloud Messaging (FCM HTTP v1).
// Called ONLY by the database triggers of migration 029 (public.push_notify) with the header x-bofo-push-secret.
//   kind "dm"      new message in a student <-> owner chat      -> the OTHER person of the conversation
//   kind "group"   new message / announcement in a boarding group -> every active, un-muted member except the sender (the owner is a member)
//   kind "report"  new user report                              -> ONLY users whose users.role = 'admin' (checked here, at send time)
//   kind "idreview" an owner sent an ID for verification (v2.6.0, migration 033) -> ONLY admins; no name, no ID data
//   kind "stale"   items waiting more than 3 days (v2.6.0; sent once a day by public.push_stale_check()) -> ONLY admins; counts only
//   kind "approval" a moderator asked for an approval (v2.9.11, migration 042) -> staff with approvals_decide, ADMIN APP phones only; type only
//   kind "test"    {"as": "idreview" | "stale" | "report" | "approval"}: a labelled test notification to every phone that would get that kind (run push_notify('test', ...) in the SQL editor)
// v2.9.11: admin kinds go to staff by PERMISSION (public.push_admin_tokens, migration 042): report = reports_act, idreview = id_review, stale = any of
//   id_review / reports_act / payments_view (each person only sees the counts they could act on), approval = approvals_decide. dm / group go to the 'user' app tokens only.
//   If migration 042 is not applied yet this function falls back to the old rule (users.role = 'admin') so nothing breaks; apply 042 BEFORE deploying anyway.
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

type Row = { token: string; user_id: string; app?: string; perms?: string[] };
// rows = the exact phones to use (admin kinds); apps = which app's tokens to read for userIds when rows is not set (default: the user app only)
type Push = { userIds: string[]; data: Record<string, string>; rows?: Row[]; apps?: string[] };

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
const CHANNELS: Record<string, string> = { dm: "bofo_chats", group: "bofo_boarding", report: "bofo_reports", idreview: "bofo_admin", stale: "bofo_admin", approval: "bofo_admin", escalated: "bofo_admin", decided: "bofo_admin", unusual: "bofo_admin", test: "bofo_chats" };
const ADMIN_KINDS = new Set(["report", "idreview", "stale", "approval", "escalated", "decided", "unusual"]);

function shown(d: Record<string, string>) {
  const kind = d.kind ?? "";
  const ann = d.ann === "1";
  const channel = kind === "group" && ann ? "bofo_announcements" : (CHANNELS[kind] ?? "bofo_chats");
  let body = d.body ?? "";
  if (kind === "group") body = ann ? "\u{1F4CC} " + body : (d.sender ? d.sender + ": " + body : body);
  return { title: d.title || "BOFO", body, channel, priv: ADMIN_KINDS.has(kind) };
}

/** "ok" | "dead" (token is gone: delete it) | { fail: why } */
async function sendOne(token: string, data: Record<string, string>, app = "user"): Promise<"ok" | "dead" | { fail: string }> {
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
            channel_id: n.channel, click_action: app === "admin" ? "com.bofo.admin.OPEN_PUSH" : "com.bofo.finder.OPEN_PUSH", notification_priority: "PRIORITY_HIGH", default_sound: true,
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

// ---------------------------------------------------------------- phones
/** Phones of the staff who hold at least one of the permissions (migration 042). Falls back to the old "users.role = admin" rule if 042 is missing. */
async function staffTokens(perms: string[], exclude: string | null): Promise<Row[]> {
  const { data, error } = await admin.rpc("push_admin_tokens", { p_perms: perms, p_exclude: exclude });
  if (!error) return (data ?? []) as Row[];
  console.warn("push_admin_tokens failed (migration 042 not applied?), using the old admin rule:", error.message);
  const { data: admins } = await admin.from("users").select("id").eq("role", "admin");
  const ids = (admins ?? []).map((u: any) => u.id as string).filter((id: string) => id !== exclude);
  if (!ids.length) return [];
  const { data: t } = await admin.from("device_tokens").select("token, user_id").in("user_id", ids);
  return (t ?? []).map((r: any) => ({ token: r.token, user_id: r.user_id, app: "user", perms }));
}

/** Tokens of the given users, only for the given apps. Before migration 042 there is no app column: then every token counts as a user-app token. */
async function tokensFor(userIds: string[], apps: string[]): Promise<Row[]> {
  const r = await admin.from("device_tokens").select("token, user_id, app").in("user_id", userIds).in("app", apps);
  if (!r.error) return (r.data ?? []) as Row[];
  if (!apps.includes("user")) return [];
  const o = await admin.from("device_tokens").select("token, user_id").in("user_id", userIds);
  return (o.data ?? []).map((x: any) => ({ token: x.token, user_id: x.user_id, app: "user" }));
}

const uniq = (rows: Row[]) => [...new Set(rows.map((r) => r.user_id))];

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
  // Staff who may act on reports (reports_act): checked in the database now, not from anything the phone says. The reporter is left out.
  const rows = await staffTokens(["reports_act"], rec.reporter_id ?? null);
  if (!rows.length) return null;
  // Lock-screen safe: the reason label only. No names, no details.
  return { userIds: uniq(rows), rows, data: { kind: "report", id: String(rec.id), title: "New report", body: (REASONS[rec.reason] ?? "A user") + " report is waiting for review", msg: String(rec.id) } };
}

// v2.6.0: an owner sent an ID. Admins only. Lock-screen safe: the type only. The row must still be waiting (a banned ID is rejected right after the insert).
async function planIdReview(rec: any, skipCheck = false): Promise<Push | null> {
  if (!skipCheck) {
    const { data: v } = await admin.from("owner_verifications").select("status").eq("id", rec.id).maybeSingle();
    if (!v || v.status !== "pending") return null;
  }
  const rows = await staffTokens(["id_review"], rec.owner_id ?? null);
  if (!rows.length) return null;
  return { userIds: uniq(rows), rows, data: { kind: "idreview", id: String(rec.id), title: "New ID to review", body: "An ID is waiting for review", msg: String(rec.id) } };
}

// v2.6.0: the daily reminder. Counts only. "place" tells the app which tab to open: the group with the most waiting items (ties: IDs, reports, payments).
async function planStale(rec: any): Promise<Push[] | null> {
  const n = (v: unknown) => Math.max(0, Math.min(9999, Math.floor(Number(v) || 0)));
  const all = { id_review: n(rec.ids), reports_act: n(rec.reports), payments_view: n(rec.payments) };
  if (all.id_review + all.reports_act + all.payments_view <= 0) return null;
  const rows = await staffTokens(Object.keys(all), null);
  if (!rows.length) return null;
  // Each person is told only about the counts they could act on (their permissions); people with the same set share one message.
  const groups = new Map<string, Row[]>();
  for (const r of rows) { const k = [...(r.perms ?? Object.keys(all))].sort().join(","); groups.set(k, [...(groups.get(k) ?? []), r]); }
  const out: Push[] = [];
  for (const [k, g] of groups) {
    const has = new Set(k.split(","));
    const ids = has.has("id_review") ? all.id_review : 0, reports = has.has("reports_act") ? all.reports_act : 0, payments = has.has("payments_view") ? all.payments_view : 0;
    const total = ids + reports + payments;
    if (total <= 0) continue;
    // "place" tells the app which tab to open: the group with the most waiting items (ties: IDs, reports, payments).
    const target = ids >= reports && ids >= payments ? "queue" : reports >= payments ? "reports" : "payments";
    out.push({ userIds: uniq(g), rows: g, data: { kind: "stale", id: "stale", title: "Items are waiting", body: `${total} ${total === 1 ? "item has" : "items have"} been waiting more than 3 days`, place: target, msg: "stale" } });
  }
  return out.length ? out : null;
}

// v2.9.11: a moderator asked for an approval. Staff with approvals_decide, ADMIN APP phones only (the user app has no approvals screen). Lock-screen safe: the type only.
const APPROVAL_LABEL: Record<string, string> = { report_ban: "A ban", report_suspend: "A suspension", report_takedown: "A listing take-down" };
async function planApproval(rec: any, skipCheck = false): Promise<Push | null> {
  let label = "An approval", by: string | null = rec?.requested_by ?? null;
  if (!skipCheck) {
    const { data: a } = await admin.from("admin_approvals").select("status, kind, requested_by").eq("id", rec.id).maybeSingle();
    if (!a || a.status !== "pending") return null;
    label = APPROVAL_LABEL[a.kind] ?? label; by = a.requested_by;
  }
  const rows = (await staffTokens(["approvals_decide"], by)).filter((r) => r.app === "admin");
  if (!rows.length) return null;
  return { userIds: uniq(rows), rows, data: { kind: "approval", id: String(rec.id), title: "Approval needed", body: `${label} is waiting for your decision`, msg: String(rec.id) } };
}

// v2.12.0 (migration 044): a report was escalated. Staff with approvals_decide, ADMIN APP phones only, the person who escalated is left out. Type only, no names.
async function planEscalated(rec: any): Promise<Push | null> {
  const rows = (await staffTokens(["approvals_decide"], rec?.by ?? null)).filter((r) => r.app === "admin");
  if (!rows.length) return null;
  return { userIds: uniq(rows), rows, data: { kind: "escalated", id: String(rec.id), title: "Report escalated", body: "A report needs a senior look", msg: String(rec.id) } };
}

// v2.12.0: an approval request was decided -> only the person who asked, ADMIN APP phone only. Status word only.
async function planDecided(rec: any): Promise<Push | null> {
  const { data: a } = await admin.from("admin_approvals").select("status, requested_by").eq("id", rec.id).maybeSingle();
  if (!a || !a.requested_by || (a.status !== "approved" && a.status !== "denied")) return null;     // read from the table, not from what the caller sent
  const rows = await tokensFor([a.requested_by], ["admin"]);
  if (!rows.length) return null;
  return { userIds: [a.requested_by], rows, data: { kind: "decided", id: String(rec.id), title: "Request " + a.status, body: "Your approval request was " + a.status, msg: String(rec.id) } };
}

// v2.12.0: a non-owner reached 10 risky actions in an hour (trigger in 044). Staff with audit_view_all (owners), ADMIN APP phones only. No names.
async function planUnusual(_rec: any): Promise<Push | null> {
  const rows = (await staffTokens(["audit_view_all"], null)).filter((r) => r.app === "admin");
  if (!rows.length) return null;
  return { userIds: uniq(rows), rows, data: { kind: "unusual", id: "unusual-" + Math.floor(Date.now() / 3600000), title: "Unusual activity", body: "A team member did many sensitive actions in one hour", msg: "unusual" } };
}

async function planTest(rec: any): Promise<Push | Push[] | null> {
  const as = String(rec?.as ?? "idreview");
  const stamp = String(Date.now());
  if (as === "me") {          // v2.9.2: a plain test for ONE account (any role): select public.push_notify('test', '{"as":"me","user_id":"<uuid>"}'::jsonb);
    const uid = String(rec?.user_id ?? "");
    if (!uid) return null;
    return { userIds: [uid], apps: ["user", "admin"], data: { kind: "test", id: "test-" + stamp, title: "BOFO test notification", body: "If you can read this, phone notifications work.", place: "", msg: stamp, sender: "BOFO" } };
  }
  const plan = as === "stale" ? await planStale({ ids: 1, reports: 1, payments: 1 })
    : as === "report" ? await planReport({ id: "test-" + stamp, reason: "other", reporter_id: null })
    : as === "approval" ? await planApproval({ id: "test-" + stamp, requested_by: null }, true)
    : await planIdReview({ id: "test-" + stamp, owner_id: null }, true);
  for (const p of plan ? (Array.isArray(plan) ? plan : [plan]) : []) p.data.title = "Test: " + p.data.title;
  return plan;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("method not allowed", { status: 405 });
  if (!sameSecret(WEBHOOK_SECRET, req.headers.get("x-bofo-push-secret") ?? "")) return new Response("unauthorized", { status: 401 });
  try {
    const { kind, record } = await req.json();
    const planned = kind === "dm" ? await planDm(record) : kind === "group" ? await planGroup(record) : kind === "report" ? await planReport(record)
      : kind === "idreview" ? await planIdReview(record) : kind === "stale" ? await planStale(record) : kind === "approval" ? await planApproval(record)
      : kind === "escalated" ? await planEscalated(record) : kind === "decided" ? await planDecided(record) : kind === "unusual" ? await planUnusual(record)
      : kind === "test" ? await planTest(record) : null;
    const plans: Push[] = planned ? (Array.isArray(planned) ? planned : [planned]) : [];
    if (!plans.length) return Response.json({ sent: 0, reason: "nobody_to_notify" });       // e.g. blocked sender, unknown conversation, empty group

    let tokens = 0, sent = 0; const dead: string[] = []; const fails: string[] = [];
    for (const plan of plans) {
      const rows = plan.rows ?? await tokensFor(plan.userIds, plan.apps ?? ["user"]);
      tokens += rows.length;
      await Promise.all(rows.map(async (t) => {
        const res = await sendOne(t.token, plan.data, t.app ?? "user");
        if (res === "ok") sent++; else if (res === "dead") dead.push(t.token); else fails.push(res.fail);
      }));
    }
    if (!tokens) { console.warn("send-push", kind, "no device token for", plans.reduce((a, p) => a + p.userIds.length, 0), "user(s)"); return Response.json({ sent: 0, reason: "no_device_token" }); }
    if (dead.length) await admin.from("device_tokens").delete().in("token", dead);
    console.log("send-push", kind, "tokens", tokens, "sent", sent, "dead", dead.length, "failed", fails.length);
    return Response.json({ sent, removed: dead.length, tokens, ...(fails.length ? { failed: fails.length, why: fails[0] } : {}) });
  } catch (e) {
    console.error("send-push failed", e);
    return new Response("error: " + String((e as Error)?.message ?? e).slice(0, 300), { status: 500 });      // visible in net._http_response (e.g. "FCM_SERVICE_ACCOUNT is not set")
  }
});
