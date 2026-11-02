package com.bofo.finder.service;

import com.bofo.finder.util.AiUnansweredRules;
import com.bofo.finder.util.AiUnansweredRules.Reason;
import com.bofo.finder.util.AiUnansweredRules.Row;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * v2.1.3 "Needs attention" (admin only, read-only): reads public.ai_unanswered. RLS lets only an admin read it (others get 0 rows) and nobody can
 * write it from the app: the Edge Function bofo-chat and the thumbs-down trigger fill it.
 */
public final class AiUnansweredService {
    private final SupabaseClient api = SupabaseClient.get();

    /** The newest rows (optionally one reason), newest first. */
    public List<Row> latest(Reason reason) throws Exception {
        JSONArray a = api.select("ai_unanswered", AiUnansweredRules.query(reason));
        List<Row> out = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            out.add(new Row(o.optString("id", ""), o.optString("created_at", ""), o.optString("reason", ""),
                    o.isNull("intent") ? "" : o.optString("intent", ""), o.isNull("lang") ? "" : o.optString("lang", ""),
                    o.isNull("message") ? "" : o.optString("message", "")));
        }
        return out;
    }
}
