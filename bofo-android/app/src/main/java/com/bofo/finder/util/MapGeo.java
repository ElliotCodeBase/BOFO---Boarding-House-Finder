package com.bofo.finder.util;

import com.bofo.finder.model.BoardingHouse;

import java.util.List;
import java.util.Locale;

/**
 * Pure helpers for the map: GeoJSON text for the marker, radius-ring and dot sources, and bounds.
 * No Android and no org.json (plain strings), so it runs in unit tests. GeoJSON order is [lng, lat].
 */
public final class MapGeo {
    private MapGeo() {}

    public static final double DEFAULT_LAT = 9.3068, DEFAULT_LNG = 123.3054;   // Dumaguete, used when there is nothing else to show
    public static final String EMPTY = "{\"type\":\"FeatureCollection\",\"features\":[]}";

    /** One point per place; properties: id, icon ("pin" or "pin-star" for featured places) and sel (1 for the selected place). */
    public static String placesJson(List<BoardingHouse> places, String selectedId) {
        if (places == null || places.isEmpty()) return EMPTY;
        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        boolean first = true;
        for (BoardingHouse p : places) {
            if (Double.isNaN(p.lat()) || Double.isNaN(p.lng())) continue;
            if (!first) sb.append(',');
            first = false;
            boolean sel = p.id() != null && p.id().equals(selectedId);
            sb.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
              .append(num(p.lng())).append(',').append(num(p.lat())).append("]},\"properties\":{\"id\":\"")
              .append(esc(p.id())).append("\",\"icon\":\"").append(p.featured() ? "pin-star" : "pin")
              .append("\",\"sel\":").append(sel ? 1 : 0).append("}}");
        }
        return sb.append("]}").toString();
    }

    /** A single point (campus or the live dot). Null coordinates give an empty collection. */
    public static String pointJson(Double lat, Double lng) {
        if (lat == null || lng == null) return EMPTY;
        return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":["
                + num(lng) + "," + num(lat) + "]},\"properties\":{}}]}";
    }

    /** A closed ring of {@code steps} points around the centre, {@code km} away. Null centre or km <= 0 gives an empty collection. */
    public static String circleJson(Double lat, Double lng, double km, int steps) {
        if (lat == null || lng == null || km <= 0 || steps < 8) return EMPTY;
        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[");
        for (int i = 0; i <= steps; i++) {
            double[] p = destination(lat, lng, km, 360.0 * (i % steps) / steps);
            if (i > 0) sb.append(',');
            sb.append('[').append(num(p[1])).append(',').append(num(p[0])).append(']');
        }
        return sb.append("]]},\"properties\":{}}]}").toString();
    }

    /** {lat, lng} reached from the start going {@code km} on a bearing (degrees, 0 = north). */
    public static double[] destination(double lat, double lng, double km, double bearingDeg) {
        double r = 6371.0088, d = km / r, br = Math.toRadians(bearingDeg);
        double la1 = Math.toRadians(lat), lo1 = Math.toRadians(lng);
        double la2 = Math.asin(Math.sin(la1) * Math.cos(d) + Math.cos(la1) * Math.sin(d) * Math.cos(br));
        double lo2 = lo1 + Math.atan2(Math.sin(br) * Math.sin(d) * Math.cos(la1), Math.cos(d) - Math.sin(la1) * Math.sin(la2));
        return new double[]{Math.toDegrees(la2), ((Math.toDegrees(lo2) + 540) % 360) - 180};
    }

    /** {minLat, minLng, maxLat, maxLng} of the places, or null when there are none. */
    public static double[] bounds(List<BoardingHouse> places) {
        if (places == null || places.isEmpty()) return null;
        double minLat = 90, minLng = 180, maxLat = -90, maxLng = -180;
        for (BoardingHouse p : places) {
            minLat = Math.min(minLat, p.lat()); maxLat = Math.max(maxLat, p.lat());
            minLng = Math.min(minLng, p.lng()); maxLng = Math.max(maxLng, p.lng());
        }
        return new double[]{minLat, minLng, maxLat, maxLng};
    }

    /** Bounds of the ring around a centre: {minLat, minLng, maxLat, maxLng}. */
    public static double[] ringBounds(double lat, double lng, double km) {
        double dLat = km / 111.32, dLng = km / (111.32 * Math.cos(Math.toRadians(lat)));
        return new double[]{lat - dLat, lng - dLng, lat + dLat, lng + dLng};
    }

    /** A route line (GeoJSON LineString) with the property approx = 1 for a straight-line estimate. Fewer than 2 points gives an empty collection. */
    public static String lineJson(double[][] pts, boolean approx) {
        if (pts == null || pts.length < 2) return EMPTY;
        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");
        for (int i = 0; i < pts.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('[').append(num(pts[i][1])).append(',').append(num(pts[i][0])).append(']');
        }
        return sb.append("]},\"properties\":{\"approx\":").append(approx ? 1 : 0).append("}}]}").toString();
    }

    /** {minLat, minLng, maxLat, maxLng} of a {lat,lng} path, or null when it is empty. */
    public static double[] pathBounds(double[][] pts) {
        if (pts == null || pts.length == 0) return null;
        double minLat = 90, minLng = 180, maxLat = -90, maxLng = -180;
        for (double[] p : pts) {
            minLat = Math.min(minLat, p[0]); maxLat = Math.max(maxLat, p[0]);
            minLng = Math.min(minLng, p[1]); maxLng = Math.max(maxLng, p[1]);
        }
        return new double[]{minLat, minLng, maxLat, maxLng};
    }

    private static String num(double v) { return String.format(Locale.ROOT, "%.6f", v); }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20) sb.append(' ');
            else sb.append(c);
        }
        return sb.toString();
    }
}
