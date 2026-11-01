package com.bofo.finder.util;

import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v2.0.100: search by PLACE. A house whose address does not say "Dumaguete" is still in Dumaguete, so a typed city also
 * matches every house within RADIUS_KM of that city's centre. Centres come from the campus list (the city in brackets,
 * "Silliman University (Dumaguete)", averaged over its campuses), then from a small built-in list. Pure Java.
 */
public final class PlaceSearch {
    private PlaceSearch() {}

    public static final double RADIUS_KM = 8;

    public record Place(String name, double lat, double lng) {}

    private static final Object[][] BUILT_IN = {
            {"Dumaguete", 9.3068, 123.3054}, {"Bayawan", 9.3640, 122.8050}, {"Bais", 9.5900, 123.1200},
            {"Tanjay", 9.5170, 123.1580}, {"Guihulngan", 10.1200, 123.2700}, {"Canlaon", 10.3870, 123.2000},
            {"Sibulan", 9.3600, 123.2850}, {"Bacong", 9.2500, 123.2900}, {"Siquijor", 9.2100, 123.5100},
            {"Bacolod", 10.6765, 122.9509}, {"Bago", 10.5370, 122.8350}, {"Kabankalan", 9.9900, 122.8100},
            {"Himamaylan", 10.1000, 122.8700}, {"Sipalay", 9.7500, 122.4000}, {"Sagay", 10.8960, 123.4210},
            {"Silay", 10.8000, 122.9740}, {"Talisay", 10.7380, 122.9670}, {"Victorias", 10.9000, 123.0740},
            {"Cadiz", 10.9500, 123.3000}, {"San Carlos", 10.4930, 123.4170}, {"La Carlota", 10.4230, 122.9200},
            {"Cebu City", 10.3157, 123.8854}, {"Mandaue", 10.3236, 123.9223}, {"Lapu-Lapu", 10.3103, 123.9494},
            {"Iloilo", 10.7202, 122.5621}, {"Tagbilaran", 9.6500, 123.8550}, {"Manila", 14.5995, 120.9842},
            {"Davao", 7.0731, 125.6128}, {"Cagayan de Oro", 8.4542, 124.6319}};

    /** The place the text names, or null when it is not a known place (then only the address / name text is searched). */
    public static Place find(List<College> colleges, String text) {
        String q = norm(text);
        if (q.length() < 3) return null;
        Map<String, double[]> sums = new LinkedHashMap<>();           // city -> {sumLat, sumLng, n}
        Map<String, String> shown = new LinkedHashMap<>();
        if (colleges != null) for (College c : colleges) {
            String city = bracket(c.name());
            if (city.isEmpty()) continue;
            String k = norm(city);
            double[] s = sums.computeIfAbsent(k, x -> new double[3]);
            s[0] += c.lat(); s[1] += c.lng(); s[2]++;
            shown.putIfAbsent(k, city);
        }
        List<Place> all = new ArrayList<>();
        for (Map.Entry<String, double[]> e : sums.entrySet()) all.add(new Place(shown.get(e.getKey()), e.getValue()[0] / e.getValue()[2], e.getValue()[1] / e.getValue()[2]));
        for (Object[] p : BUILT_IN) {
            boolean have = false;
            for (Place a : all) if (norm(a.name()).equals(norm((String) p[0]))) { have = true; break; }
            if (!have) all.add(new Place((String) p[0], (Double) p[1], (Double) p[2]));
        }
        String qq = q.replaceAll(" city$", "");                       // "dumaguete city" = "dumaguete"
        Place best = null; int bestScore = 0;
        for (Place p : all) {
            String n = norm(p.name()).replaceAll(" city$", "");
            int score = n.equals(qq) ? 3 : n.startsWith(qq) ? 2 : n.contains(qq) ? 1 : 0;
            if (score > bestScore) { best = p; bestScore = score; }
        }
        return best;
    }

    /** Houses whose name / address has the text, or that stand within RADIUS_KM of the place. */
    public static List<BoardingHouse> keep(List<BoardingHouse> in, String text, Place place) {
        String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        List<BoardingHouse> out = new ArrayList<>();
        for (BoardingHouse h : in) {
            boolean t = !q.isEmpty() && (h.name().toLowerCase(Locale.ROOT).contains(q)
                    || (h.address() != null && h.address().toLowerCase(Locale.ROOT).contains(q)));
            boolean near = place != null && Geo.km(place.lat(), place.lng(), h.lat(), h.lng()) <= RADIUS_KM;
            if (t || near) out.add(h);
        }
        return out;
    }

    private static String bracket(String name) {
        String n = name == null ? "" : name.trim();
        int par = n.lastIndexOf('(');
        return par >= 0 && n.endsWith(")") ? n.substring(par + 1, n.length() - 1).trim() : "";
    }

    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
}
