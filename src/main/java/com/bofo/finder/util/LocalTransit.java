package com.bofo.finder.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Jeepney / multicab / bus lines typed in by hand (the BOFO team, or you) for places where OpenStreetMap has none.
 * Two files are read and combined:
 * <ul>
 *   <li>{@code local-transit.json} inside the app (src/main/resources/com/bofo/finder/): ships with every build;</li>
 *   <li>{@code local-transit.json} in the device's BOFO folder ({@link DiskCache#root()}): edit it without rebuilding.</li>
 * </ul>
 * Format and how to trace a route: docs/LOCAL_TRANSIT.md. A line is drawn from {@code path} (dense points) or, if only
 * {@code stops} are given, from straight pieces between the stops. Lines run both ways unless {@code "bothWays": false}.
 * A broken file is ignored (printed to the console), never a crash.
 */
public final class LocalTransit {
    private LocalTransit() {}

    private static volatile List<TransitMatch.Line> cached;

    /** All hand-made lines (read once per session). */
    public static List<TransitMatch.Line> lines() {
        List<TransitMatch.Line> c = cached;
        if (c != null) return c;
        List<TransitMatch.Line> all = new ArrayList<>();
        try (InputStream in = LocalTransit.class.getResourceAsStream("/com/bofo/finder/local-transit.json")) {
            if (in != null) all.addAll(parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (Throwable t) { System.err.println("[LocalTransit] built-in file: " + t); }
        try {
            File f = new File(DiskCache.root(), "local-transit.json");
            if (f.isFile()) all.addAll(parse(Files.readString(f.toPath(), StandardCharsets.UTF_8)));
        } catch (Throwable t) { System.err.println("[LocalTransit] device file: " + t); }
        return cached = all;
    }

    /** Forget what was read (after the device file was edited). */
    public static void reload() { cached = null; }

    /** JSON text to lines. Entries that are not usable are skipped one by one; an unreadable file gives an empty list. */
    public static List<TransitMatch.Line> parse(String json) {
        List<TransitMatch.Line> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONObject(json).optJSONArray("lines");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                try {
                    JSONObject o = arr.getJSONObject(i);
                    double[][] path = points(o.optJSONArray("path"), false);
                    if (path.length < 2) path = densify(points(o.optJSONArray("stops"), true), 100);
                    if (path.length < 2) { System.err.println("[LocalTransit] line " + i + " has no path or stops, skipped"); continue; }
                    String name = o.optString("name", ""), ref = o.optString("ref", "");
                    if (name.isBlank() && ref.isBlank()) { System.err.println("[LocalTransit] line " + i + " has no name or ref, skipped"); continue; }
                    int lo = o.optInt("fareLow", -1), hi = o.optInt("fareHigh", lo);
                    String note = o.optString("note", "");
                    out.add(new TransitMatch.Line(ref, name, o.optString("operator", ""), "local", path, note, lo, hi));
                    if (o.optBoolean("bothWays", true)) {
                        double[][] back = new double[path.length][];
                        for (int k = 0; k < path.length; k++) back[k] = path[path.length - 1 - k];
                        out.add(new TransitMatch.Line(ref, name, o.optString("operator", ""), "local", back, note, lo, hi));
                    }
                } catch (Throwable bad) { System.err.println("[LocalTransit] line " + i + " skipped: " + bad); }
            }
        } catch (Throwable t) { System.err.println("[LocalTransit] could not read: " + t); }
        return out;
    }

    /** [[lat,lng],...] or, for stops, [{"lat":..,"lng":..,"name":..},...]. Out-of-range points make the whole list empty. */
    private static double[][] points(JSONArray a, boolean objects) {
        if (a == null) return new double[0][];
        double[][] p = new double[a.length()][];
        for (int i = 0; i < a.length(); i++) {
            double lat, lng;
            if (objects) { JSONObject o = a.getJSONObject(i); lat = o.getDouble("lat"); lng = o.getDouble("lng"); }
            else { JSONArray c = a.getJSONArray(i); lat = c.getDouble(0); lng = c.getDouble(1); }
            if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return new double[0][];
            p[i] = new double[]{lat, lng};
        }
        return p;
    }

    /** Adds points so no two neighbours are more than {@code maxM} metres apart (matching needs a dense line). */
    static double[][] densify(double[][] p, double maxM) {
        if (p.length < 2) return p;
        List<double[]> out = new ArrayList<>();
        out.add(p[0]);
        for (int i = 1; i < p.length; i++) {
            double d = TransitMatch.m(p[i - 1], p[i]);
            int n = (int) Math.ceil(d / maxM);
            for (int k = 1; k < n; k++) out.add(new double[]{p[i - 1][0] + (p[i][0] - p[i - 1][0]) * k / n, p[i - 1][1] + (p[i][1] - p[i - 1][1]) * k / n});
            out.add(p[i]);
        }
        return out.toArray(new double[0][]);
    }
}
