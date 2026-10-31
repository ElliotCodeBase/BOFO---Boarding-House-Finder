package com.bofo.finder.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Philippine regions the user can pick (Profile > Personal information). The map opens on the saved region. */
public final class Regions {
    private Regions() {}
    /** @param key short code saved in users.region; zoom is the map zoom that frames the whole region. */
    public record Region(String key, String name, double lat, double lng, double zoom) {}

    public static final List<Region> ALL = Collections.unmodifiableList(Arrays.asList(
            new Region("NIR",  "Negros Island Region (NIR)",           10.20, 123.05, 8.6),
            new Region("VII",  "Region VII - Central Visayas",         10.00, 123.90, 8.4),
            new Region("VI",   "Region VI - Western Visayas",          11.10, 122.50, 8.4),
            new Region("VIII", "Region VIII - Eastern Visayas",        11.50, 125.00, 8.3),
            new Region("NCR",  "NCR - Metro Manila",                   14.60, 121.00, 10.5),
            new Region("CAR",  "CAR - Cordillera",                     17.10, 121.00, 8.6),
            new Region("I",    "Region I - Ilocos",                    17.00, 120.50, 8.4),
            new Region("II",   "Region II - Cagayan Valley",           17.40, 121.80, 8.0),
            new Region("III",  "Region III - Central Luzon",           15.50, 120.80, 8.5),
            new Region("IV-A", "Region IV-A - CALABARZON",             14.10, 121.50, 8.7),
            new Region("IV-B", "Region IV-B - MIMAROPA",               12.50, 120.80, 7.6),
            new Region("V",    "Region V - Bicol",                     13.30, 123.50, 8.3),
            new Region("IX",   "Region IX - Zamboanga Peninsula",       8.00, 123.20, 8.4),
            new Region("X",    "Region X - Northern Mindanao",          8.30, 124.60, 8.5),
            new Region("XI",   "Region XI - Davao",                     7.20, 125.90, 8.5),
            new Region("XII",  "Region XII - SOCCSKSARGEN",             6.50, 124.80, 8.5),
            new Region("XIII", "Region XIII - Caraga",                  8.90, 125.90, 8.5),
            new Region("BARMM","BARMM",                                  7.20, 124.30, 8.2)));

    public static Region byKey(String key) {
        if (key == null || key.trim().isEmpty()) return null;
        for (Region r : ALL) if (r.key().equals(key)) return r;
        return null;
    }
    public static Region byName(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        for (Region r : ALL) if (r.name().equals(name)) return r;
        return null;
    }
}
