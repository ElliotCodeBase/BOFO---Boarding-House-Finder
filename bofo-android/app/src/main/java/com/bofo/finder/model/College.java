package com.bofo.finder.model;

import org.json.JSONException;
import org.json.JSONObject;

public record College(String id, String name, double lat, double lng) {
    public static College from(JSONObject o) throws JSONException { return new College(o.getString("id"), o.getString("name"), o.getDouble("lat"), o.getDouble("lng")); }
    @Override public String toString() { return name; }
}
