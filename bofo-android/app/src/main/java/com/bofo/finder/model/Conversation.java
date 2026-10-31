package com.bofo.finder.model;

import org.json.JSONException;
import org.json.JSONObject;

public record Conversation(String id, String studentId, String ownerId, String bhId, String bhName,
                           String otherName, String lastMessage, String lastAt, int unread) {
    public static Conversation from(JSONObject o) throws JSONException {
        return new Conversation(o.getString("id"), o.getString("student_id"), o.getString("owner_id"),
                o.getString("boarding_house_id"), o.optString("bh_name", ""), o.optString("other_name", ""),
                o.optString("last_message", ""), o.optString("last_message_at", ""), o.optInt("unread", 0));
    }
}
