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
    /** 17 -> "17 min", 65 -> "1 h 5 min", 1266 -> "21 h 6 min". Never "1266 min". */
    public static String fmtTime(int min) {
        if (min < 60) return Math.max(1, min) + " min";
        int h = min / 60, m = min % 60;
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }
    /** "850 m · 11 min walk" up to 3 km; farther than that just "12 km away" (nobody walks it, so we do not print hours of walking). */
    public static String walkText(double km) {
        return km <= 3 ? fmt(km) + " \u00B7 " + fmtTime(Math.max(1, walkMinutes(km))) + " walk" : fmt(km) + " away";
    }
    /** Length of a {lat,lng} path in km. */
    public static double pathKm(double[][] pts) {
        double km = 0;
        for (int i = 1; i < pts.length; i++) km += km(pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1]);
        return km;
    }
    public static String fmt(double km) { return km < 1 ? Math.round(km * 1000) + " m" : String.format("%.1f km", km); }

    /** Compass direction from point 1 to point 2 in degrees (0 = north, 90 = east). Used for the "which way am I walking" arrow. */
    public static double bearing(double lat1, double lng1, double lat2, double lng2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2), dl = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    /** Pixel position of a point on the whole-world map at zoom z (256 px tiles, Web Mercator). */
    private static double[] worldPx(double lat, double lng, int z) {
        double world = 256.0 * (1 << z);
        double s = Math.sin(Math.toRadians(Math.max(-85, Math.min(85, lat))));
        return new double[]{(lng + 180) / 360 * world, (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * world};
    }

    /** Highest zoom (between minZ and maxZ) at which the box fits inside w x h pixels. */
    public static int fitZoom(double minLat, double minLng, double maxLat, double maxLng, double w, double h, int minZ, int maxZ) {
        for (int z = maxZ; z > minZ; z--) {
            double[] a = worldPx(maxLat, minLng, z), b = worldPx(minLat, maxLng, z);
            if (Math.abs(b[0] - a[0]) <= w && Math.abs(b[1] - a[1]) <= h) return z;
        }
        return minZ;
    }
}
