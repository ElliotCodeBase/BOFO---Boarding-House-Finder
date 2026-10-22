// PayMongo webhook -> activates the boost. Deploy WITHOUT JWT verification (PayMongo is not a Supabase user):
//   supabase functions deploy paymongo-webhook --no-verify-jwt
// Secrets: PAYMONGO_WEBHOOK_SECRET (whsk_...). Register the function URL in PayMongo for event checkout_session.payment.paid
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

async function hmacHex(secret: string, msg: string) {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(msg));
  return [...new Uint8Array(sig)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
function safeEq(a: string, b: string) { if (a.length !== b.length) return false; let d = 0; for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i); return d === 0; }

Deno.serve(async (req) => {
  const raw = await req.text();
  // 1) Verify the signature header:  Paymongo-Signature: t=<ts>,te=<test sig>,li=<live sig>
  const header = req.headers.get("Paymongo-Signature") ?? "";
  const parts = Object.fromEntries(header.split(",").map((p) => p.split("=") as [string, string]));
  if (!parts.t) return new Response("bad signature", { status: 401 });
  if (Math.abs(Date.now() / 1000 - Number(parts.t)) > 600) return new Response("stale", { status: 401 });   // replay protection
  const expected = await hmacHex(Deno.env.get("PAYMONGO_WEBHOOK_SECRET")!, `${parts.t}.${raw}`);
  if (!safeEq(expected, parts.te ?? "") && !safeEq(expected, parts.li ?? "")) return new Response("bad signature", { status: 401 });

  const event = JSON.parse(raw);
  const type = event?.data?.attributes?.type;
  if (type !== "checkout_session.payment.paid") return new Response("ignored", { status: 200 });

  const session = event.data.attributes.data;            // the checkout session object
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data: pay } = await admin.from("payments").select("id,amount_php,status").eq("provider_session_id", session.id).single();
  if (!pay) return new Response("unknown session", { status: 200 });
  if (pay.status === "paid") return new Response("already", { status: 200 });                // idempotent

  // 2) Amount check: what was actually paid must match what we charged.
  const paidCentavos = session?.attributes?.payments?.[0]?.attributes?.amount ?? session?.attributes?.line_items?.[0]?.amount;
  if (Number(paidCentavos) !== Math.round(Number(pay.amount_php) * 100)) {
    await admin.from("audit_logs").insert({ action: "payment_amount_mismatch", target_type: "payment", target_id: pay.id, details: { paidCentavos } });
    return new Response("amount mismatch", { status: 200 });
  }
  const { error } = await admin.rpc("activate_boost", { pid: pay.id });
  return new Response(error ? "error" : "ok", { status: error ? 500 : 200 });     // 500 makes PayMongo retry
});
