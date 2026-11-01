package com.bofo.finder.service;

import com.bofo.finder.util.AiChatRules;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * v2.0.91 AI Chats monitor (admin only): the only class that talks to Supabase for it. Reading ai_chat_log works only for an admin (RLS), the app can never
 * write or delete a single row; "delete all" goes through the admin-only SQL function ai_chat_log_wipe. Delete this file with the rest of the feature (REMOVE_AI_CHATS.md).
 */
public final class AiChatLogService {
    private final SupabaseClient api = SupabaseClient.get();

    /** The on/off switch (table app_flags). A missing row counts as off. Throws when the server cannot be reached; callers treat that as "hidden". */
    public boolean enabled() throws Exception {
        JSONArray a = api.select("app_flags", "key=eq." + AiChatRules.FLAG + "&select=value");
        return a.length() > 0 && a.getJSONObject(0).optBoolean("value", false);
    }

    /** One page for a query built by {@link AiChatRules#query}: PAGE + 1 rows at most. */
    public List<AiChatRules.Row> page(String query) throws Exception {
        JSONArray a = api.select("ai_chat_log", query);
        List<AiChatRules.Row> out = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            out.add(new AiChatRules.Row(o.optString("id", ""), o.optString("user_id", ""), o.optString("user_name", ""), o.optString("user_email", ""),
                    o.optString("question", ""), o.optString("answer", ""), o.optString("created_at", "")));
        }
        return out;
    }

    /** Deletes every stored AI chat on the server (admin only, written to audit_logs). Returns how many rows were deleted. */
    public int wipeAll() throws Exception {
        return api.rpc("ai_chat_log_wipe", new JSONObject()).optInt("deleted", 0);
    }
}
