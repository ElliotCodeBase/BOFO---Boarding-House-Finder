package com.bofo.admin;

import com.bofo.shared.Api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Stage 4i: who has an item open ("Being handled by ..."), and which reports are escalated. All blocking calls: run them inside Async.run.
 * A claim is a heads-up only; the server still lets either person decide. If migration 044 is missing the list is simply empty.
 */
final class Marks {
    private Marks() {}

    /** item id -> row of admin_item_marks_list (only items with an active claim or an open escalation). Never throws. */
    static Map<String, JSONObject> list(String kind) {
        Map<String, JSONObject> m = new HashMap<>();
        try {
            JSONArray a = new JSONArray(Api.get().rpc("admin_item_marks_list", Ui.json("kind_", kind)));
            for (int i = 0; i < a.length(); i++) { JSONObject o = a.optJSONObject(i); if (o != null) m.put(o.optString("item_id"), o); }
        } catch (Exception ignored) { /* 044 not applied yet, or no permission: show no marks */ }
        return m;
    }

    /** {"mine":true} or {"mine":false,"by":"Name","since":"..."}. */
    static JSONObject claim(String kind, String id) throws Exception {
        return new JSONObject(Api.get().rpc("admin_claim_item", Ui.json("kind_", kind, "item", id)));
    }

    static void release(String kind, String id) {
        try { Api.get().rpc("admin_release_item", Ui.json("kind_", kind, "item", id)); } catch (Exception ignored) { }
    }

    /** org.json turns a JSON null into the text "null"; this returns "" instead. */
    static String str(JSONObject o, String k) { return o == null || o.isNull(k) ? "" : o.optString(k, ""); }

    static boolean escalated(JSONObject mark) { return mark != null && mark.optBoolean("escalated", false); }

    /** Someone ELSE has it open right now. */
    static boolean claimedByOther(JSONObject mark) { return mark != null && !str(mark, "claimed_by_name").isEmpty() && !mark.optBoolean("claimed_mine", false); }
}
