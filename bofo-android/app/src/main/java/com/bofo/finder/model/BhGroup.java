package com.bofo.finder.model;

import org.json.JSONException;
import org.json.JSONObject;

/** One group in the caller's list (rpc my_bh_groups): the house a student joined, or each house an owner listed. */
public record BhGroup(String bhId, String bhName, String myRole, int memberCount, int unread, int pinnedCount,
                      String lastMessage, String lastAt, String lastSender, boolean muted) {
    public static BhGroup from(JSONObject o) throws JSONException {
        return new BhGroup(o.getString("bh_id"), o.optString("bh_name", ""), o.optString("my_role", GroupMessage.STUDENT),
                o.optInt("member_count", 0), o.optInt("unread", 0), o.optInt("pinned_count", 0),
                o.isNull("last_message") ? "" : o.optString("last_message"), o.isNull("last_message_at") ? "" : o.optString("last_message_at"),
                o.isNull("last_sender_name") ? "" : o.optString("last_sender_name"), o.optBoolean("muted", false));
    }
}
