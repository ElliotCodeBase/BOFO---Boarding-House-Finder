package com.bofo.finder.model;

import org.json.JSONObject;

public record Profile(String id, Role role, boolean verified, String fullName, String photoUrl,
                      String phone, String defaultCollegeId, String verificationStatus) {
    public static Profile from(JSONObject o) {
        return new Profile(o.getString("id"), Role.of(o.optString("role", "student")),
                o.optBoolean("is_verified", false), o.optString("full_name", ""),
                o.optString("photo_url", ""), o.optString("phone", ""),
                o.isNull("default_college_id") ? null : o.optString("default_college_id"),
                o.optString("verification_status", "unverified"));
    }
}
