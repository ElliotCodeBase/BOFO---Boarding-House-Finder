package com.bofo.finder.service;

import com.bofo.finder.model.BofoAnswer;
import com.bofo.finder.util.BrowseState;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Calls the bofo-chat Edge Function. The LLM key lives ONLY in the function's secrets (never in the app). */
public final class BofoAiService {
    /** Old shape (reply + filters), kept so older callers still compile. */
    public record Answer(String reply, JSONObject filters) {}

    /** One chat turn kept in memory by the overlay. resultIds = the ids of the cards shown with that answer (so "the second one" works). */
    public record Turn(boolean mine, String text, List<String> resultIds) {}

    private final SupabaseClient api = SupabaseClient.get();

    public Answer ask(String message, String collegeId) throws Exception {
        BofoAnswer a = ask(message, List.of(), collegeId);
        return new Answer(a.reply(), a.filters());
    }

    /** Full answer: reply + real listings + compare table + actions. Sends the campus / Near me position and the last 6 turns. */
    public BofoAnswer ask(String message, List<Turn> history, String collegeId) throws Exception {
        JSONObject body = new JSONObject().put("message", message).put("college_id", collegeId == null ? JSONObject.NULL : collegeId);
        boolean near = BrowseState.nearMe && BrowseState.userLat != null && BrowseState.userLng != null;
        body.put("near_me", near).put("radius_km", BrowseState.radiusKm);
        if (near) body.put("lat", BrowseState.userLat).put("lng", BrowseState.userLng);
        JSONArray h = new JSONArray();
        List<String> lastIds = List.of();
        int from = Math.max(0, history.size() - 6);
        for (int i = from; i < history.size(); i++) {
            Turn t = history.get(i);
            JSONObject o = new JSONObject().put("role", t.mine() ? "user" : "bot").put("text", t.text());
            if (t.resultIds() != null && !t.resultIds().isEmpty()) { o.put("result_ids", new JSONArray(t.resultIds())); lastIds = t.resultIds(); }
            h.put(o);
        }
        body.put("history", h).put("last_result_ids", new JSONArray(lastIds));
        return BofoAnswer.parse(api.function("bofo-chat", body));
    }

    /** Offline / rate-limited fallback. */
    public static String faq(String q) {
        String s = q.toLowerCase();
        if (s.contains("verif")) return "Owners: log in, accept the consent, take photos of your ID (front/back) and a live selfie. An admin reviews it; status shows in the Verification tab.";
        if (s.contains("delete")) return "Go to Profile > Delete my account and data.";
        if (s.contains("chat")) return "Open a boarding house and tap 'Chat with owner'. All conversations are in the Chats tab.";
        return "BOFO is offline right now. Try: how to verify, how to chat, how to delete my account.";
    }
}
