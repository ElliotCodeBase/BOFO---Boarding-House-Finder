package com.bofo.finder.util;

import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Small pure rules shared by the list screens (cards, Explore filters, campus ordering). Easy to unit test. */
public final class ListRules {
    private ListRules() {}

    /** Colleges ordered by distance from a point (A-Z when the point is unknown). Does not change the input list. */
    public static List<College> nearestFirst(List<College> all, Double lat, Double lng) {
        List<College> l = new ArrayList<>(all);
        if (lat == null || lng == null) l.sort(Comparator.comparing((College c) -> c.name().toLowerCase(Locale.ROOT)));
        else l.sort(Comparator.comparingDouble((College c) -> Geo.km(lat, lng, c.lat(), c.lng())));
        return l;
    }

    /** min / max are optional (null = no limit). */
    public static boolean priceOk(double price, Double min, Double max) {
        return (min == null || price >= min) && (max == null || price <= max);
    }

    public static List<BoardingHouse> inPriceRange(List<BoardingHouse> in, Double min, Double max) {
        if (min == null && max == null) return in;
        List<BoardingHouse> out = new ArrayList<>();
        for (BoardingHouse b : in) if (priceOk(b.price(), min, max)) out.add(b);
        return out;
    }

    public static List<Double> prices(List<BoardingHouse> l) {
        List<Double> p = new ArrayList<>();
        for (BoardingHouse b : l) p.add(b.price());
        return p;
    }

    /** "4.5 (12)" or "New" when nobody rated it yet. */
    public static String ratingText(double avg, int reviews) {
        return reviews <= 0 ? "New" : String.format(Locale.US, "%.1f (%d)", avg, reviews);
    }

    public static String bedsText(int left) { return left == 1 ? "1 bed left" : left + " beds left"; }

    /** "Free water, electricity, Wi-Fi" or "" when nothing is free. */
    public static String freeText(boolean water, boolean electricity, boolean wifi) {
        List<String> f = new ArrayList<>();
        if (water) f.add("water");
        if (electricity) f.add("electricity");
        if (wifi) f.add("Wi-Fi");
        return f.isEmpty() ? "" : "Free " + String.join(", ", f);
    }

    /** Beds still free across rooms (never below 0). */
    public static int bedsLeft(int[] beds, int[] occupants) {
        int left = 0;
        for (int i = 0; i < beds.length; i++) left += Math.max(0, beds[i] - (i < occupants.length ? occupants[i] : 0));
        return left;
    }

    /** "2026-10-30T05:27:00+00:00" -> "2026-10-30". */
    public static String shortDate(String iso) { return iso == null ? "" : iso.length() >= 10 ? iso.substring(0, 10) : iso; }

    /** 4 -> "★★★★☆". */
    public static String stars(int n) {
        int k = Math.max(0, Math.min(5, n));
        return String.join("", Collections.nCopies(k, "\u2605")) + String.join("", Collections.nCopies(5 - k, "\u2606"));
    }

    /** First non-empty text of the given keys' values (for JSON rows whose column names may differ). */
    public static String firstText(String... values) {
        for (String v : values) if (v != null && !v.trim().isEmpty() && !"null".equals(v)) return v.trim();
        return "";
    }

    /** v2.0.99: the place for a card from the free-text address: the last two parts ("Daro, Dumaguete City"), without "Philippines". "" when there is no address. */
    public static String locationText(String address) {
        if (address == null || address.isBlank()) return "";
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (String p : address.split(",")) {
            String t = p.trim();
            if (!t.isEmpty() && !t.equalsIgnoreCase("philippines") && !t.equalsIgnoreCase("ph")) parts.add(t);
        }
        if (parts.isEmpty()) return "";
        if (parts.size() == 1) return parts.get(0);
        return parts.get(parts.size() - 2) + ", " + parts.get(parts.size() - 1);
    }
}
