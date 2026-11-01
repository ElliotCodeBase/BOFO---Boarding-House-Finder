package com.bofo.finder.util;

import com.bofo.finder.model.BoardingHouse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Step 9: pure rules for Explore (sort order and the built-in amenity fallback). No Android classes, easy to unit test. */
public final class ExploreRules {
    private ExploreRules() {}

    public static final int SORT_NEAREST = 0, SORT_PRICE = 1, SORT_BEDS = 2;

    /** Shown when the amenity catalog cannot be loaded (no network, or 005_amenities.sql not run yet). Filtered by name on the loaded cards. */
    public static final String[] POPULAR = {"Free Wi-Fi", "Free water", "Free electricity", "Aircon", "Own bathroom", "Kitchen", "Laundry", "Parking"};

    /** Nearest = the order BhService already returns (nearest first when a centre is known). Other sorts are stable. */
    public static List<BoardingHouse> sort(List<BoardingHouse> in, int sort) {
        List<BoardingHouse> l = new ArrayList<>(in);
        if (sort == SORT_PRICE) l.sort(Comparator.comparingDouble(BoardingHouse::price));
        else if (sort == SORT_BEDS) l.sort(Comparator.comparingInt(BoardingHouse::availableBeds).reversed());
        return l;
    }

    private static String norm(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", ""); }

    /** Does this house offer the named amenity? Uses the free water / electricity / Wi-Fi flags, then the amenity names. */
    public static boolean has(BoardingHouse b, String name) {
        String k = norm(name);
        if (k.isEmpty()) return true;
        if (k.contains("wifi") && b.wifi()) return true;
        if (k.contains("water") && b.water()) return true;
        if (k.contains("electric") && b.electricity()) return true;
        for (String a : b.amenities()) {
            String n = norm(a);
            if (n.equals(k) || Fuzzy.score(name, a) >= 80) return true;
        }
        return false;
    }

    /** Keeps the houses that have ALL of the names (empty = keep everything). */
    public static List<BoardingHouse> withAll(List<BoardingHouse> in, Collection<String> names) {
        if (names == null || names.isEmpty()) return in;
        List<BoardingHouse> out = new ArrayList<>();
        outer:
        for (BoardingHouse b : in) {
            for (String n : names) if (!has(b, n)) continue outer;
            out.add(b);
        }
        return out;
    }

    /** v2.0.82: quick chips. Keeps verified owners and / or houses with at least one free bed (false = no rule). */
    public static List<BoardingHouse> quick(List<BoardingHouse> in, boolean verified, boolean beds) {
        if (!verified && !beds) return in;
        List<BoardingHouse> out = new ArrayList<>();
        for (BoardingHouse b : in) { if (verified && !b.ownerVerified()) continue; if (beds && b.availableBeds() <= 0) continue; out.add(b); }
        return out;
    }

    /** "1500" -> 1500.0, blank -> null (no limit), anything else (0, negative, text) -> NaN = invalid. */
    public static Double parseMax(String text) {
        String t = text == null ? "" : text.trim().replace(",", "");
        if (t.isEmpty()) return null;
        try { double v = Double.parseDouble(t); return v > 0 ? v : Double.NaN; }
        catch (NumberFormatException e) { return Double.NaN; }
    }
}
