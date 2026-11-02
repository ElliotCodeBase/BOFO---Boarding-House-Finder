package com.bofo.finder.service;

import com.bofo.finder.model.BofoAnswer;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BofoMemory;
import com.bofo.finder.util.BrowseState;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.regex.Pattern;

/** Calls the bofo-chat Edge Function. The LLM key lives ONLY in the function's secrets (never in the app). */
public final class BofoAiService {
    /** Old shape (reply + filters), kept so older callers still compile. */
    public record Answer(String reply, JSONObject filters) {}

    /** One chat turn kept in memory by the overlay. resultIds = the ids of the cards shown with that answer (so "the second one" works). */
    public record Turn(boolean mine, String text, List<String> resultIds) {}

    /** v2.1.2: what the server gets as memory: the last 10 turns, 300 characters each. */
    public static final int HISTORY_TURNS = 10, HISTORY_CHARS = 300;
    private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final SupabaseClient api = SupabaseClient.get();

    public Answer ask(String message, String collegeId) throws Exception {
        BofoAnswer a = ask(message, List.of(), collegeId);
        return new Answer(a.reply(), a.filters());
    }

    /** Full answer: reply + real listings + compare table + actions + optional clarify / quick_replies / lang / log_id.
     *  Sends the campus / Near me position, the last 10 turns (300 characters each) and {@code session} (filters + what the student said). */
    public BofoAnswer ask(String message, List<Turn> history, String collegeId) throws Exception {
        JSONObject body = new JSONObject().put("message", message).put("college_id", collegeId == null ? JSONObject.NULL : collegeId);
        // The phone's position is sent automatically when known (Near me fix first, else the one read when the chat opened).
        Double lat = BrowseState.nearMe && BrowseState.userLat != null ? BrowseState.userLat : BrowseState.deviceLat;
        Double lng = BrowseState.nearMe && BrowseState.userLng != null ? BrowseState.userLng : BrowseState.deviceLng;
        boolean near = lat != null && lng != null;
        body.put("near_me", near).put("radius_km", BrowseState.radiusKm);
        if (near) body.put("lat", lat).put("lng", lng);
        JSONArray h = new JSONArray();
        List<String> lastIds = List.of();
        int from = Math.max(0, history.size() - HISTORY_TURNS);
        for (int i = from; i < history.size(); i++) {
            Turn t = history.get(i);
            JSONObject o = new JSONObject().put("role", t.mine() ? "user" : "bot").put("text", BofoMemory.clip(t.text(), HISTORY_CHARS));
            if (t.resultIds() != null && !t.resultIds().isEmpty()) { o.put("result_ids", new JSONArray(t.resultIds())); lastIds = t.resultIds(); }
            h.put(o);
        }
        body.put("history", h).put("last_result_ids", new JSONArray(lastIds));
        body.put("session", session(message));
        return BofoAnswer.parse(api.function("bofo-chat", body));
    }

    /** The app filters (price slider, free wifi / water / electricity) merged with what the student said in this chat. Never throws. */
    static JSONObject session(String message) {
        try {
            BofoMemory.observe(message);
            Double ui = BrowseState.maxPrice != null && BrowseState.maxPrice > 0 && BrowseState.maxPrice < BrowseState.PRICE_MAX ? BrowseState.maxPrice : null;
            return BofoMemory.mergeSession(ui, BrowseState.hasAmenityNamed(BrowseState.AMEN_WIFI),
                    BrowseState.hasAmenityNamed("Free Water"), BrowseState.hasAmenityNamed("Free Electricity"));
        } catch (Throwable t) { return new JSONObject(); }
    }

    /** Thumbs up (1) / thumbs down (-1) for an answer, saved in ai_feedback (RLS: own rows). Runs off the UI thread, ignores every failure,
     *  does nothing when {@code logId} is null / not a uuid or the rating is not -1 or 1. */
    public void rate(String logId, int rating) {
        if (logId == null || !UUID.matcher(logId.trim()).matches() || (rating != -1 && rating != 1)) return;
        final String id = logId.trim();
        try {
            Async.pool().execute(() -> {
                try { api.insert("ai_feedback", new JSONObject().put("chat_log_id", id).put("rating", rating)); }
                catch (Throwable ignored) { }
            });
        } catch (Throwable ignored) { }
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
