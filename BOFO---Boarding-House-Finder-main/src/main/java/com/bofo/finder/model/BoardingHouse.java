package com.bofo.finder.model;

import org.json.JSONObject;

public record BoardingHouse(String id, String ownerId, String name, String address, double lat, double lng,
                            double price, int availableBeds, boolean water, boolean electricity, boolean wifi,
                            double avgRating, int reviewCount, String photoUrl, boolean ownerVerified,
                            double distanceKm, boolean featured) {
    public static BoardingHouse from(JSONObject o) {
        return new BoardingHouse(o.getString("id"), o.getString("owner_id"), o.getString("name"), o.optString("address", ""),
                o.getDouble("lat"), o.getDouble("lng"), o.optDouble("price", 0), o.optInt("available_beds", 0),
                o.optBoolean("free_water"), o.optBoolean("free_electricity"), o.optBoolean("free_wifi"),
                o.optDouble("avg_rating", 0), o.optInt("review_count", 0), o.optString("photo_url", ""),
                o.optBoolean("owner_verified"), 0, o.optBoolean("is_featured", false));
    }
    public BoardingHouse withDistance(double km) {
        return new BoardingHouse(id, ownerId, name, address, lat, lng, price, availableBeds, water, electricity, wifi,
                avgRating, reviewCount, photoUrl, ownerVerified, km, featured);
    }
}
