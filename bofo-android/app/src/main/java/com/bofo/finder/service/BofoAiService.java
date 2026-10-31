package com.bofo.finder.service;

import org.json.JSONObject;

/** Calls the bofo-chat Edge Function. The LLM key lives ONLY in the function's secrets. */
public final class BofoAiService {
    public record Answer(String reply, JSONObject filters) {}
    private final SupabaseClient api = SupabaseClient.get();

    public Answer ask(String message, String collegeId) throws Exception {
        JSONObject r = api.function("bofo-chat", new JSONObject().put("message", message).put("college_id", collegeId == null ? JSONObject.NULL : collegeId));
        return new Answer(r.optString("reply", ""), r.optJSONObject("filters"));
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
