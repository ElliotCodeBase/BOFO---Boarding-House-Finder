package com.bofo.finder.model;

import org.json.JSONException;
import org.json.JSONObject;

/** A member of a boarding-house group (owner-only list from bh_members_list). */
public record BhMember(String userId, String name, String role, String status, boolean muted, String joinedAt) {
    public boolean isOwner() { return GroupMessage.OWNER.equals(role); }

    public static BhMember from(JSONObject o) throws JSONException {
        return new BhMember(o.getString("user_id"), o.optString("full_name", ""), o.optString("role", GroupMessage.STUDENT),
                o.optString("status", "active"), o.optBoolean("muted", false), o.optString("joined_at", ""));
    }
}
