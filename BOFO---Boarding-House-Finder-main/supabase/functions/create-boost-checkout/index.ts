// Creates a PayMongo Checkout Session limited to GCash and records a pending payment.
// Deploy: supabase functions deploy create-boost-checkout
// Secrets: PAYMONGO_SECRET_KEY (sk_test_... / sk_live_...), BOOST_SUCCESS_URL, BOOST_CANCEL_URL (https pages you host)
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, apikey, x-client-info" };
const json = (b: unknown, s = 200) => new Response(JSON.stringify(b), { status: s, headers: { ...cors, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  try {
    const url = Deno.env.get("SUPABASE_URL")!;
    const userClient = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, { global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } } });
    const { data: u } = await userClient.auth.getUser();
    if (!u?.user) return json({ error: "unauthorized" }, 401);
    const { bh_id, plan_id } = await req.json();

    const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
    // Server-side checks: never trust the client for price, ownership or verification.
    const { data: me } = await admin.from("users").select("role,is_verified").eq("id", u.user.id).single();
    if (!me || me.role !== "owner" || !me.is_verified) return json({ error: "Only verified owners can boost." }, 403);
    const { data: bh } = await admin.from("boarding_houses").select("id,name,owner_id,status").eq("id", bh_id).single();
    if (!bh || bh.owner_id !== u.user.id || bh.status !== "active") return json({ error: "Listing must be yours and active." }, 400);
    const { data: plan } = await admin.from("boost_plans").select("*").eq("id", plan_id).eq("active", true).single();
    if (!plan) return json({ error: "Unknown plan." }, 400);

    // Throttle: max 5 pending checkouts per owner per hour.
    const since = new Date(Date.now() - 3600_000).toISOString();
    const { count } = await admin.from("payments").select("id", { count: "exact", head: true }).eq("owner_id", u.user.id).eq("status", "pending").gte("created_at", since);
    if ((count ?? 0) >= 5) return json({ error: "Too many pending payments. Try again later." }, 429);

    const ref = `BOFO-${Date.now().toString(36).toUpperCase()}-${crypto.randomUUID().slice(0, 6).toUpperCase()}`;
    const { data: pay, error: perr } = await admin.from("payments").insert({
      owner_id: u.user.id, boarding_house_id: bh.id, plan_id: plan.id, amount_php: plan.price_php, reference_number: ref,
    }).select("id").single();
    if (perr || !pay) return json({ error: "Could not create payment." }, 500);

    const r = await fetch("https://api.paymongo.com/v1/checkout_sessions", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: "Basic " + btoa(Deno.env.get("PAYMONGO_SECRET_KEY")! + ":") },
      body: JSON.stringify({ data: { attributes: {
        line_items: [{ currency: "PHP", amount: Math.round(Number(plan.price_php) * 100), name: `BOFO Featured: ${plan.name}`, quantity: 1, description: bh.name }],
        payment_method_types: ["gcash"],
        reference_number: ref,
        description: `Boost for ${bh.name}`,
        success_url: Deno.env.get("BOOST_SUCCESS_URL")!,
        cancel_url: Deno.env.get("BOOST_CANCEL_URL")!,
        metadata: { payment_id: pay.id },
      } } }),
    });
    const out = await r.json();
    if (!r.ok) { await admin.from("payments").update({ status: "failed" }).eq("id", pay.id); return json({ error: "Payment provider error." }, 502); }
    await admin.from("payments").update({ provider_session_id: out.data.id }).eq("id", pay.id);
    return json({ checkout_url: out.data.attributes.checkout_url, payment_id: pay.id, reference: ref });
  } catch (_e) { return json({ error: "server_error" }, 500); }
});
