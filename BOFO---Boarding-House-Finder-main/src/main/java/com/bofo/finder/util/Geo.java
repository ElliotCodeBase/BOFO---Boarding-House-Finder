package com.bofo.finder.util;

public final class Geo {
    private Geo() {}
    public static double km(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
    /** Rough walking time (5 km/h) in minutes. Use a routing API for real travel time. */
    public static int walkMinutes(double km) { return (int) Math.round(km / 5.0 * 60); }
    public static String fmt(double km) { return km < 1 ? Math.round(km * 1000) + " m" : String.format("%.1f km", km); }
}
