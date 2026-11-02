package com.bofo.finder.service;

import com.bofo.finder.util.BanRules;
import com.bofo.finder.util.BanRules.Row;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** v2.1.9 (admin only): the banned accounts and Unban. Both go through SECURITY DEFINER functions that check is_admin() on the server. */
public final class BannedUsersService {
    private final SupabaseClient api = SupabaseClient.get();

    /** Newest ban first (admin_banned_list). */
    public List<Row> list() throws Exception {
        JSONArray a = api.rpc("admin_banned_list", new JSONObject()).optJSONArray("data");
        List<Row> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            out.add(new Row(o.optString("user_id", ""), o.optString("full_name", ""), o.optString("email", ""), o.optString("ban_reason", ""),
                    o.optString("banned_at", ""), o.optString("banned_by_name", ""), o.optBoolean("id_banned", false)));
        }
        return out;
    }

    public void unban(String userId, String note) throws Exception {
        JSONObject args = new JSONObject().put("uid", userId);
        String n = BanRules.reason(note);
        if (!n.isEmpty()) args.put("note", n);
        api.rpc("admin_unban", args);
    }
}
