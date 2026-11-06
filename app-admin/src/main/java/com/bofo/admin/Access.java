package com.bofo.admin;

import com.bofo.shared.PermissionRules;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** The server's answer to my_admin_access(): who this is and what they may do. Used to decide what to SHOW; the server still checks every call. */
public final class Access {
    public final boolean active;
    public final String role, label;
    public final Set<String> perms = new HashSet<>();

    public Access(JSONObject j) {
        active = j.optBoolean("active", false);
        role = j.optString("role", "");
        label = j.optString("label", "");
        JSONArray p = j.optJSONArray("permissions");
        if (p != null) for (int i = 0; i < p.length(); i++) if (PermissionRules.ALL.contains(p.optString(i))) perms.add(p.optString(i));   // unknown keys ignored
    }
    public boolean can(String key) { return active && perms.contains(key); }
}
