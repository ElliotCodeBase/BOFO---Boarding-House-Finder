package com.bofo.finder.service;

import org.json.JSONArray;
import org.json.JSONObject;

/** GCash boosts. Prices and activation are decided server-side; the app only starts a checkout and watches the status. */
public final class BoostService {
    private final SupabaseClient api = SupabaseClient.get();

    public JSONArray plans() throws Exception { return api.select("boost_plans", "select=*&order=sort_order.asc"); }

    /** @return {checkout_url, payment_id, reference} */
    public JSONObject checkout(String bhId, String planId) throws Exception {
        return api.function("create-boost-checkout", new JSONObject().put("bh_id", bhId).put("plan_id", planId));
    }

    public String status(String paymentId) throws Exception {
        JSONArray a = api.select("payments", "id=eq." + paymentId + "&select=status");
        return a.isEmpty() ? "unknown" : a.getJSONObject(0).getString("status");
    }

    public JSONArray myPayments() throws Exception {
        return api.select("payments", "order=created_at.desc&limit=30&select=id,amount_php,status,plan_id,reference_number,created_at,boarding_house_id");
    }

    /* ---------- admin ---------- */
    public JSONArray allPayments() throws Exception {
        return api.select("payments", "order=created_at.desc&limit=100&select=*");
    }
    public void refund(String paymentId, String reason) throws Exception {
        api.rpc("admin_refund_boost", new JSONObject().put("pid", paymentId).put("reason", reason));
    }
}
