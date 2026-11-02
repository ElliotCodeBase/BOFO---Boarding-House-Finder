package com.bofo.finder.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Answer of the bofo-chat Edge Function (contract: see BOFO-AI-STEPS.md). Every field is optional: an old or partial answer
 * (only "reply" + "filters") parses fine, and nothing here throws.
 */
public record BofoAnswer(String reply, String intent, List<Result> results, Compare compare, List<Action> actions,
                         JSONObject filters, List<String> suggestions, boolean degraded,
                         String clarify, List<String> quickReplies, String lang, String logId) {

    /** v2.1.2: new optional fields. clarify = one short question (null = none), quickReplies = up to 4 tappable answers,
     *  lang = en | tl | ceb | taglish (null = unknown), logId = uuid of the chat log row (null = none, used by rate()). */
    public BofoAnswer {
        if (reply == null) reply = "";
        if (intent == null) intent = "unknown";
        if (results == null) results = new ArrayList<>();
        if (actions == null) actions = new ArrayList<>();
        if (suggestions == null) suggestions = new ArrayList<>();
        if (quickReplies == null) quickReplies = new ArrayList<>();
    }

    /** The old 8-field shape, so every existing caller keeps compiling. */
    public BofoAnswer(String reply, String intent, List<Result> results, Compare compare, List<Action> actions,
                      JSONObject filters, List<String> suggestions, boolean degraded) {
        this(reply, intent, results, compare, actions, filters, suggestions, degraded, null, new ArrayList<>(), null, null);
    }

    /** A copy with other chips (used by the offline planner to offer the second request). */
    public BofoAnswer withSuggestions(List<String> s) {
        return new BofoAnswer(reply, intent, results, compare, actions, filters, s, degraded, clarify, quickReplies, lang, logId);
    }

    /** One real listing. distanceKm / walkMin are null when the server had no campus or phone position. */
    public record Result(String id, String name, String address, double price, String priceMode, Double distanceKm, Integer walkMin,
                         double rating, int reviewCount, int availableBeds, boolean water, boolean electricity, boolean wifi,
                         double lat, double lng, String photoUrl, boolean verified) {}

    /** best = index of the best cell, -1 = none. */
    public record Row(String label, List<String> values, int best) {}

    public record Compare(List<String> ids, List<Row> rows) {}

    /** type: show_on_map | open_detail | directions | apply_filters. id for one place, ids for several. */
    public record Action(String type, String id, List<String> ids) {}

    public static BofoAnswer text(String reply) {
        return new BofoAnswer(reply == null ? "" : reply, "unknown", new ArrayList<>(), null, new ArrayList<>(), null, new ArrayList<>(), false);
    }

    public List<String> resultIds() {
        List<String> l = new ArrayList<>();
        for (Result r : results) l.add(r.id());
        return l;
    }

    public Result result(String id) {
        if (id != null) for (Result r : results) if (id.equals(r.id())) return r;
        return null;
    }

    public static BofoAnswer parse(JSONObject o) {
        if (o == null) return text("");
        List<Result> rs = new ArrayList<>();
        JSONArray ra = o.optJSONArray("results");
        if (ra != null) for (int i = 0; i < ra.length(); i++) {
            JSONObject r = ra.optJSONObject(i);
            if (r == null || r.optString("id", "").isEmpty()) continue;
            rs.add(new Result(r.optString("id"), r.optString("name", ""), r.optString("address", ""), r.optDouble("price", 0),
                    r.isNull("price_mode") ? "" : r.optString("price_mode", ""),
                    r.isNull("distance_km") || !r.has("distance_km") ? null : r.optDouble("distance_km"),
                    r.isNull("walk_min") || !r.has("walk_min") ? null : r.optInt("walk_min"),
                    r.optDouble("rating", 0), r.optInt("review_count", 0), r.optInt("available_beds", 0),
                    r.optBoolean("free_water"), r.optBoolean("free_electricity"), r.optBoolean("free_wifi"),
                    r.optDouble("lat", 0), r.optDouble("lng", 0), r.isNull("photo_url") ? "" : r.optString("photo_url", ""),
                    r.optBoolean("verified")));
        }
        Compare cmp = null;
        JSONObject co = o.optJSONObject("compare");
        if (co != null) {
            List<Row> rows = new ArrayList<>();
            JSONArray rowsA = co.optJSONArray("rows");
            if (rowsA != null) for (int i = 0; i < rowsA.length(); i++) {
                JSONObject r = rowsA.optJSONObject(i);
                if (r != null) rows.add(new Row(r.optString("label", ""), strings(r.optJSONArray("values")), r.optInt("best", -1)));
            }
            if (!rows.isEmpty()) cmp = new Compare(strings(co.optJSONArray("ids")), rows);
        }
        List<Action> acts = new ArrayList<>();
        JSONArray aa = o.optJSONArray("actions");
        if (aa != null) for (int i = 0; i < aa.length(); i++) {
            JSONObject a = aa.optJSONObject(i);
            if (a != null && !a.optString("type", "").isEmpty())
                acts.add(new Action(a.optString("type"), a.isNull("id") ? null : a.optString("id", null), strings(a.optJSONArray("ids"))));
        }
        String clarify = o.isNull("clarify") ? null : o.optString("clarify", "").trim();
        if (clarify != null && clarify.isEmpty()) clarify = null;
        List<String> quick = new ArrayList<>();
        for (String q : strings(o.optJSONArray("quick_replies"))) if (!q.trim().isEmpty() && quick.size() < 4) quick.add(q.trim());
        String lang = o.isNull("lang") ? null : o.optString("lang", "");
        if (lang != null && !(lang.equals("en") || lang.equals("tl") || lang.equals("ceb") || lang.equals("taglish"))) lang = null;
        String logId = o.isNull("log_id") ? null : o.optString("log_id", "").trim();
        if (logId != null && logId.isEmpty()) logId = null;
        return new BofoAnswer(o.optString("reply", ""), o.optString("intent", "unknown"), rs, cmp, acts,
                o.optJSONObject("filters"), strings(o.optJSONArray("suggestions")), o.optBoolean("degraded"),
                clarify, quick, lang, logId);
    }

    private static List<String> strings(JSONArray a) {
        List<String> l = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) l.add(a.optString(i, ""));
        return l;
    }
}
