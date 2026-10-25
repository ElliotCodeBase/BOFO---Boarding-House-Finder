package com.bofo.finder.model;

import org.json.JSONObject;

/** One entry of the shared amenity catalog (owners can add new ones; students filter by them). */
public record Amenity(String id, String name, String icon, boolean popular, int uses) {
    public static Amenity from(JSONObject o) {
        return new Amenity(o.getString("id"), o.getString("name"), o.optString("icon", "\u2728"), o.optBoolean("is_popular"), o.optInt("uses", 0));
    }
    /** "Icon Name" for chips. */
    public String label() { return icon == null || icon.isBlank() ? name : icon + " " + name; }
    @Override public String toString() { return name; }
}
