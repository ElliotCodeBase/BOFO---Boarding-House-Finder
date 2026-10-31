package com.bofo.finder.model;

import org.json.JSONException;
import org.json.JSONObject;

public record Message(String id, String conversationId, String senderId, String body, String createdAt, String readAt) {
    public static Message from(JSONObject o) throws JSONException {
        return new Message(o.getString("id"), o.getString("conversation_id"), o.getString("sender_id"),
                o.getString("body"), o.getString("created_at"), o.isNull("read_at") ? null : o.optString("read_at"));
    }
}
