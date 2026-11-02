package com.bofo.finder.model;

import org.json.JSONException;
import org.json.JSONObject;

/** One row of the Boarding group chat (view bh_group_feed_v): a chat message or an announcement. */
public record GroupMessage(String id, String bhId, String senderId, String senderName, String senderRole,
                           String body, String createdAt, boolean announcement, String title,
                           boolean pinned, String pinnedAt, String editedAt, boolean iAcked, int ackCount) {
    public static final String OWNER = "owner", STUDENT = "student";

    public boolean fromOwner() { return OWNER.equals(senderRole); }

    /** "Owner" or "Student": the badge shown next to the sender name. */
    public String badge() { return fromOwner() ? "Owner" : "Student"; }

    public static GroupMessage from(JSONObject o) throws JSONException {
        return new GroupMessage(o.getString("id"), o.getString("bh_id"), o.getString("sender_id"),
                o.optString("sender_name", ""), o.optString("sender_role", STUDENT),
                o.getString("body"), o.getString("created_at"), o.optBoolean("is_announcement", false),
                o.isNull("title") ? null : o.optString("title"),
                o.optBoolean("pinned", false), o.isNull("pinned_at") ? null : o.optString("pinned_at"),
                o.isNull("edited_at") ? null : o.optString("edited_at"),
                o.optBoolean("i_acked", false), o.isNull("ack_count") ? 0 : o.optInt("ack_count", 0));
    }
}
