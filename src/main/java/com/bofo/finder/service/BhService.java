package com.bofo.finder.service;

import com.bofo.finder.model.Amenity;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;
import com.bofo.finder.util.Geo;
import org.json.JSONArray;
import org.json.JSONObject;

import com.bofo.finder.util.Async;
import com.bofo.finder.util.Cache;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class BhService {
    public enum Tab { NEW, HOT, BEST }

    public static final class Filter {
        public Double maxPrice;
        /** Amenity ids the place must have (ALL of them). */
        public final Set<String> amenityIds = new LinkedHashSet<>();
        public String text;
    }

    private final SupabaseClient api = SupabaseClient.get();
    /** Only the columns the cards need (smaller payloads than select=*). */
    private static final String COLS = "id,owner_id,name,address,lat,lng,price,price_mode,free_water,free_electricity,free_wifi,created_at,available_beds,avg_rating,review_count,photo_url,owner_verified,hot_score,is_featured";

    /** False once the database turned out not to have 005_amenities.sql yet: lists then load without amenity data instead of failing. */
    private static volatile boolean amenityCols = true;
    private static String cols() { return amenityCols ? COLS + ",amenity_names" : COLS; }

    /** Runs a bh_cards query. If the amenity column is missing (migration not run), retries once without it. */
    private JSONArray cards(String query) throws Exception {
        try { return api.select("bh_cards", query); }
        catch (SupabaseClient.ApiException e) {
            if (e.status == 400 && e.getMessage().contains("amenity") && query.contains("amenity_names")) {
                // No "amenityCols &&" check on purpose: two lists load at once, the first flips the flag and the second
                // (its query already built with amenity_names) must still be allowed to retry instead of showing the error.
                if (amenityCols) System.err.println("[BhService] bh_cards has no amenity columns yet. Run supabase/migrations/005_amenities.sql in Supabase.");
                amenityCols = false; Cache.remove("list:");
                return api.select("bh_cards", query.replace(",amenity_names", "").replaceAll("&amenity_ids=cs\\.%7B[^&]*%7D", ""));
            }
            throw e;
        }
    }

    public List<College> colleges() throws Exception {
        return Cache.get("colleges", 3_600_000, () -> {            // colleges rarely change: cache 1 h
            List<College> l = new ArrayList<>();
            JSONArray a = com.bofo.finder.util.Offline.array("colleges", () -> api.select("colleges", "select=*&order=name.asc"));
            for (int i = 0; i < a.length(); i++) l.add(College.from(a.getJSONObject(i)));
            return l;
        });
    }

    /* ---------- amenities ---------- */
    /** The whole catalog, most-used first (curated "popular" ones break ties, then A-Z). Cached 10 min. */
    public List<Amenity> amenities() throws Exception {
        return Cache.get("amenities", 600_000, () -> {
            List<Amenity> l = new ArrayList<>();
            JSONArray a = api.select("amenities_v", "select=id,name,icon,is_popular,uses");
            for (int i = 0; i < a.length(); i++) l.add(Amenity.from(a.getJSONObject(i)));
            l.sort(Comparator.comparingInt(Amenity::uses).reversed()
                    .thenComparing((Amenity x) -> !x.popular()).thenComparing(Amenity::name, String.CASE_INSENSITIVE_ORDER));
            return l;
        });
    }

    /** Verified owners: finds the amenity with this name (any letter case) or creates it. */
    public Amenity getOrCreateAmenity(String name, String icon) throws Exception {
        JSONObject r = api.rpc("get_or_create_amenity", new JSONObject().put("p_name", name).put("p_icon", icon == null ? JSONObject.NULL : icon));
        Cache.remove("amenities");
        return new Amenity(r.getString("id"), r.getString("name"), r.optString("icon", "\u2728"), false, 0);
    }

    /** Only BHs with free beds and an active listing (also enforced by the bh_cards view + RLS). */
    /** At most this many featured listings are pinned to the top of a list, so ads never drown out real results. */
    public static final int MAX_PINNED = 3;

    public List<BoardingHouse> list(Tab tab, Double refLat, Double refLng, Double radiusKm, Filter f) throws Exception {
        StringBuilder q = new StringBuilder("select=" + cols() + "&available_beds=gt.0");
        if (radiusKm != null && refLat != null && refLng != null) {   // bounding box lets Postgres use the lat/lng index; exact radius is checked below
            double dLat = radiusKm / 111.32, dLng = radiusKm / (111.32 * Math.cos(Math.toRadians(refLat)));
            q.append(String.format(Locale.ROOT, "&lat=gte.%.6f&lat=lte.%.6f&lng=gte.%.6f&lng=lte.%.6f", refLat - dLat, refLat + dLat, refLng - dLng, refLng + dLng));
        }
        List<String> amenityList = new ArrayList<>();
        if (f != null) {
            if (f.maxPrice != null) q.append("&price=lte.").append(f.maxPrice);
            for (String id : f.amenityIds)                       // one "has all of these" filter on the amenity_ids array
                if (id != null && id.matches("[0-9a-fA-F-]{36}")) amenityList.add(id);
            if (!amenityList.isEmpty()) q.append("&amenity_ids=cs.%7B").append(String.join(",", amenityList)).append("%7D");
            if (f.text != null && !f.text.isBlank()) {          // name OR address (so "Dumaguete" or "Daro" finds places in that area)
                String t = f.text.replaceAll("[^\\p{L}\\p{N} ]", "").trim();
                if (!t.isEmpty()) {
                    String enc = java.net.URLEncoder.encode("*" + t + "*", java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                    q.append("&or=(name.ilike.").append(enc).append(",address.ilike.").append(enc).append(")");
                }
            }
        }
        final String base = q.toString();
        String order = switch (tab) {
            case NEW -> "&order=created_at.desc";
            case HOT -> "&order=hot_score.desc";
            case BEST -> "&review_count=gte.3&order=avg_rating.desc";
        };
        final String main = base + order + "&limit=60";
        final String featuredQ = base + "&is_featured=is.true&limit=10";           // featured listings are fetched separately so a top-60 cut never hides them
        JSONArray a = Cache.get("list:" + main, 30_000, () -> com.bofo.finder.util.Offline.array("list_" + Integer.toHexString(main.hashCode()), () -> cards(main)));
        JSONArray fa = Cache.get("list:" + featuredQ, 30_000, () -> com.bofo.finder.util.Offline.array("list_" + Integer.toHexString(featuredQ.hashCode()), () -> cards(featuredQ)));
        Map<String, BoardingHouse> byId = new LinkedHashMap<>();
        for (JSONArray src : new JSONArray[]{fa, a})
            for (int i = 0; i < src.length(); i++) {
                BoardingHouse b = BoardingHouse.from(src.getJSONObject(i));
                if (refLat != null && refLng != null) b = b.withDistance(Geo.km(refLat, refLng, b.lat(), b.lng()));
                if (radiusKm != null && refLat != null && b.distanceKm() > radiusKm) continue;
                byId.putIfAbsent(b.id(), b);
            }
        List<BoardingHouse> out = new ArrayList<>(byId.values());
        Comparator<BoardingHouse> byDistance = Comparator.comparingDouble(BoardingHouse::distanceKm);
        if (refLat != null) out.sort(byDistance);                                   // each tab: nearest first
        // Pin up to MAX_PINNED featured listings (nearest first) at the top; they stay labelled "Featured".
        List<BoardingHouse> pinned = out.stream().filter(BoardingHouse::featured).limit(MAX_PINNED).collect(Collectors.toList());
        out.removeAll(pinned);
        pinned.addAll(out);
        return pinned;
    }

    /** Opens offline too: the last loaded copy of each place is kept on the device. */
    public JSONObject detail(String id) throws Exception { return com.bofo.finder.util.Offline.object("detail_" + id, () -> detailOnline(id)); }

    private JSONObject detailOnline(String id) throws Exception {
        // 4 independent requests run in parallel instead of one after another.
        var pool = Async.pool();
        var card = CompletableFuture.supplyAsync(() -> call(() -> cards("id=eq." + id + "&select=" + cols())), pool);
        var photos = CompletableFuture.supplyAsync(() -> call(() -> api.select("photos", "boarding_house_id=eq." + id + "&order=sort_order.asc&select=url")), pool);
        var rooms = CompletableFuture.supplyAsync(() -> call(() -> api.select("rooms", "boarding_house_id=eq." + id + "&select=beds,occupants")), pool);
        var contact = CompletableFuture.supplyAsync(() -> call(() -> api.select("bh_contacts", "boarding_house_id=eq." + id + "&select=*")), pool);
        var amen = CompletableFuture.supplyAsync(() -> call(() -> api.select("bh_amenities", "boarding_house_id=eq." + id + "&select=amenities(id,name,icon,is_popular)")), pool)
                .exceptionally(ex -> new JSONArray());          // amenities are optional: a place must still open if that table is missing or fails
        try {
            JSONObject bh = card.get().getJSONObject(0);
            bh.put("photos", photos.get()).put("rooms", rooms.get()).put("contact", contact.get()).put("amenity_list", amen.get());
            Async.bg(() -> { try { api.rpc("bump_view", new JSONObject().put("bh", id)); } catch (Exception ignored) {} });   // fire-and-forget
            return bh;
        } catch (java.util.concurrent.ExecutionException e) { throw e.getCause() instanceof Exception ex ? ex : e; }
    }
    private interface Call<T> { T run() throws Exception; }
    private static <T> T call(Call<T> c) { try { return c.run(); } catch (Exception e) { throw new RuntimeException(e.getMessage(), e); } }

    /* ---------- favorites ---------- */
    public Set<String> favoriteIds() throws Exception {
        JSONArray a = api.select("favorites", "select=boarding_house_id");
        Set<String> s = new HashSet<>();
        for (int i = 0; i < a.length(); i++) s.add(a.getJSONObject(i).getString("boarding_house_id"));
        return s;
    }
    public void setFavorite(String userId, String bhId, boolean on) throws Exception {
        if (on) api.insert("favorites", new JSONObject().put("user_id", userId).put("boarding_house_id", bhId));
        else api.delete("favorites", "user_id=eq." + userId + "&boarding_house_id=eq." + bhId);
    }
    public List<BoardingHouse> favorites() throws Exception {
        JSONArray a = com.bofo.finder.util.Offline.array("favorites", () -> {       // the Saved tab works offline from the last copy
            Set<String> ids = favoriteIds();
            return ids.isEmpty() ? new JSONArray() : cards("id=in.(" + String.join(",", ids) + ")&select=" + cols());
        });
        List<BoardingHouse> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) l.add(BoardingHouse.from(a.getJSONObject(i)));
        return l;
    }

    /* ---------- reviews ---------- */
    public JSONArray reviews(String bhId) throws Exception {
        return api.select("reviews", "boarding_house_id=eq." + bhId + "&order=created_at.desc&select=*");
    }
    public void upsertReview(String bhId, String studentId, int rating, String comment) throws Exception {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("Rating must be 1-5");
        api.insert("reviews", new JSONObject().put("boarding_house_id", bhId).put("student_id", studentId)
                .put("rating", rating).put("comment", comment));
    }

    /* ---------- owner: listings ---------- */
    public List<BoardingHouse> myListings(String ownerId) throws Exception {
        JSONArray a = cards("owner_id=eq." + ownerId + "&select=" + cols() + "&order=created_at.desc");
        List<BoardingHouse> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) l.add(BoardingHouse.from(a.getJSONObject(i)));
        return l;
    }

    public static final class Draft {
        public String name, address, phone, facebook, email;
        public Double lat, lng, price;
        public String priceMode = "per_bed"; // or per_room
        public boolean water, electricity, wifi;                 // kept in sync with the "Free water / electricity / Wi-Fi" amenities
        public List<String> amenityIds = new ArrayList<>();
        public List<RoomDraft> rooms = new ArrayList<>();
        public List<byte[]> photos = new ArrayList<>();
    }
    public record RoomDraft(int beds, int occupants) {}

    /** Mirrors the spec: every required field must be present or submission is blocked. */
    public static List<String> validate(Draft d) {
        List<String> e = new ArrayList<>();
        if (d.name == null || d.name.isBlank()) e.add("BH name is required");
        if (d.address == null || d.address.isBlank()) e.add("Address is required");
        if (d.lat == null || d.lng == null) e.add("Drop a pin on the map");
        if (d.price == null || d.price <= 0) e.add("Monthly payment is required");
        if (d.rooms.isEmpty()) e.add("Add at least one room");
        for (RoomDraft r : d.rooms) {
            if (r.beds() < 1) e.add("Each room needs at least 1 bed");
            if (r.occupants() < 0 || r.occupants() > r.beds()) e.add("Occupants cannot exceed beds");
        }
        if (d.photos.isEmpty()) e.add("Upload at least one photo");
        boolean contact = (d.phone != null && !d.phone.isBlank()) || (d.facebook != null && !d.facebook.isBlank()) || (d.email != null && !d.email.isBlank());
        if (!contact) e.add("Add at least one contact (phone, Facebook/Messenger, or email)");
        return e;
    }

    /** Publishing is also blocked server-side by RLS unless users.is_verified = true. */
    public String publish(String ownerId, Draft d) throws Exception {
        JSONObject bh = new JSONObject().put("owner_id", ownerId).put("name", d.name).put("address", d.address)
                .put("lat", d.lat).put("lng", d.lng).put("price", d.price).put("price_mode", d.priceMode)
                .put("free_water", d.water).put("free_electricity", d.electricity).put("free_wifi", d.wifi)
                .put("status", "active");
        String id = api.insert("boarding_houses", bh).getJSONObject(0).getString("id");
        for (RoomDraft r : d.rooms) api.insert("rooms", new JSONObject().put("boarding_house_id", id).put("beds", r.beds()).put("occupants", r.occupants()));
        for (String aid : d.amenityIds) api.insert("bh_amenities", new JSONObject().put("boarding_house_id", id).put("amenity_id", aid));
        api.insert("bh_contacts", new JSONObject().put("boarding_house_id", id).put("phone", d.phone).put("facebook", d.facebook).put("email", d.email));
        int n = 0;
        for (byte[] p : d.photos) {
            String path = ownerId + "/" + id + "/" + (n++) + ".png";
            api.upload("bh-photos", path, p, "image/png");
            api.insert("photos", new JSONObject().put("boarding_house_id", id).put("url", api.publicUrl("bh-photos", path)).put("sort_order", n));
        }
        Cache.remove("list:");
        return id;
    }

    public void setStatus(String bhId, String status) throws Exception {
        api.update("boarding_houses", "id=eq." + bhId, new JSONObject().put("status", status)); Cache.remove("list:");
    }
    public void deleteListing(String bhId) throws Exception { api.delete("boarding_houses", "id=eq." + bhId); }

    public static List<String> names(List<BoardingHouse> l) { return l.stream().map(BoardingHouse::name).collect(Collectors.toList()); }
}
