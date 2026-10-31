package com.bofo.finder.util;

/** Pure input rules of the listing form (unit tested): money, coordinates, small integers, photo limit. */
public final class ListingRules {
    private ListingRules() {}

    public static final int MAX_PHOTOS = 8;

    /** "1,500", "P 1500.50", "1500" -> number; empty, zero, negative or garbage -> null. */
    public static Double money(String s) {
        if (s == null) return null;
        String t = s.replaceAll("[^0-9.]", "");
        if (t.isEmpty() || t.indexOf('.') != t.lastIndexOf('.')) return null;
        try { double v = Double.parseDouble(t); return v > 0 && v < 10_000_000 ? v : null; }
        catch (NumberFormatException e) { return null; }
    }

    /** {lat, lng} from two texts, or null when one is missing, not a number or out of range. */
    public static double[] coords(String lat, String lng) {
        try {
            if (lat == null || lng == null || lat.trim().isEmpty() || lng.trim().isEmpty()) return null;
            double a = Double.parseDouble(lat.trim().replace(',', '.')), b = Double.parseDouble(lng.trim().replace(',', '.'));
            return a >= -90 && a <= 90 && b >= -180 && b <= 180 ? new double[]{a, b} : null;
        } catch (NumberFormatException e) { return null; }
    }

    /** Whole number >= 0 from text, or {@code fallback} when empty / not a number. */
    public static int intOr(String s, int fallback) {
        try { int v = Integer.parseInt(s.trim()); return v < 0 ? fallback : v; }
        catch (RuntimeException e) { return fallback; }
    }

    /** How many more photos may be added. */
    public static int photoRoom(int have) { return Math.max(0, MAX_PHOTOS - have); }
}
