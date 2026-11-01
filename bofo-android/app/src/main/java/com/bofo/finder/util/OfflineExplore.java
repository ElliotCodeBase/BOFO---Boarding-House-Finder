package com.bofo.finder.util;

import com.bofo.finder.model.BoardingHouse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * v2.0.83: the last Explore result set, kept on the device so the map still shows the houses of the last search with no
 * network. Saved after each successful reload (id, name, lat, lng, price, photo url, beds, verified and the few other
 * card fields), read back when a reload fails with a network error. Photos are not cached here (Glide keeps its own).
 * The file is named {@code user_*}, so sign-out wipes it with the other copies ({@link DiskCache#deletePrefix}).
 */
public final class OfflineExplore {
    private OfflineExplore() {}

    static final String FILE = "user_explore_last";
    static final int MAX = 200;

    public static void save(List<BoardingHouse> list) {
        if (list == null || list.isEmpty()) return;                 // an empty search must not wipe the last good one
        DiskCache.write(FILE, toJson(list).toString());
    }

    /** The saved houses (empty when nothing was saved or the file is damaged). Never throws. */
    public static List<BoardingHouse> load() {
        String s = DiskCache.read(FILE);
        return s == null ? new ArrayList<>() : fromJson(s);
    }

    /** True when a saved search exists. */
    public static boolean has() { return DiskCache.ageMs(FILE) != Long.MAX_VALUE; }

    static JSONArray toJson(List<BoardingHouse> list) {
        JSONArray a = new JSONArray();
        int n = 0;
        for (BoardingHouse h : list) {
            if (n++ >= MAX) break;
            try {
                JSONObject o = new JSONObject();
                o.put("id", h.id()); o.put("owner_id", h.ownerId() == null ? "" : h.ownerId());
                o.put("name", h.name()); o.put("address", h.address() == null ? "" : h.address());
                o.put("lat", h.lat()); o.put("lng", h.lng()); o.put("price", h.price());
                o.put("available_beds", h.availableBeds());
                o.put("free_water", h.water()); o.put("free_electricity", h.electricity()); o.put("free_wifi", h.wifi());
                o.put("avg_rating", h.avgRating()); o.put("review_count", h.reviewCount());
                o.put("photo_url", h.photoUrl() == null ? "" : h.photoUrl());
                o.put("owner_verified", h.ownerVerified()); o.put("is_featured", h.featured());
                JSONArray am = new JSONArray();
                if (h.amenities() != null) for (String x : h.amenities()) am.put(x);
                o.put("amenity_names", am);
                a.put(o);
            } catch (Exception skip) { /* one bad house never loses the rest */ }
        }
        return a;
    }

    static List<BoardingHouse> fromJson(String text) {
        List<BoardingHouse> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(text);
            for (int i = 0; i < a.length(); i++) {
                try { out.add(BoardingHouse.from(a.getJSONObject(i))); } catch (Exception skip) { /* damaged entry */ }
            }
        } catch (Exception bad) { /* damaged file: behave as "nothing saved" */ }
        return out;
    }

    /**
     * What the screen would show for these filters, worked out on the phone: search text (name / address), price range,
     * amenity names, Verified / Has beds, sort, and the distance from the campus when there is one. The same rules as the
     * online path in {@code ExploreFragment.reload}. {@code box} = {south, west, north, east} after "Search this area" (or null).
     */
    public static List<BoardingHouse> apply(List<BoardingHouse> in, String text, Double min, Double max, Collection<String> names,
                                            boolean verified, boolean beds, int sort, Double refLat, Double refLng, double[] box) {
        List<BoardingHouse> l = new ArrayList<>();
        String q = text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
        for (BoardingHouse h : in) {
            if (!q.isEmpty() && !(h.name().toLowerCase(java.util.Locale.ROOT).contains(q)
                    || (h.address() != null && h.address().toLowerCase(java.util.Locale.ROOT).contains(q)))) continue;
            l.add(refLat != null && refLng != null ? h.withDistance(Geo.km(refLat, refLng, h.lat(), h.lng())) : h);
        }
        l = ListRules.inPriceRange(l, min, max);
        l = ExploreRules.withAll(l, names);
        l = ExploreRules.quick(l, verified, beds);
        if (sort == ExploreRules.SORT_NEAREST && refLat != null && refLng != null) l.sort(java.util.Comparator.comparingDouble(BoardingHouse::distanceKm));
        else l = ExploreRules.sort(l, sort);
        if (box != null) {
            List<BoardingHouse> inside = new ArrayList<>();
            for (BoardingHouse h : l) if (h.lat() >= box[0] && h.lat() <= box[2] && h.lng() >= box[1] && h.lng() <= box[3]) inside.add(h);
            l = inside;
        }
        return l;
    }
}
