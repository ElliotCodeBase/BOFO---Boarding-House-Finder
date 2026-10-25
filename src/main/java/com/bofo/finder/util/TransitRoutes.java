package com.bofo.finder.util;

import com.bofo.finder.config.AppConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Public commuter data: the jeepney / multicab / bus lines that volunteers have mapped in OpenStreetMap, downloaded
 * on demand from the Overpass API (open data, ODbL licence: show "(c) OpenStreetMap contributors"). Only lines that
 * touch the box around the trip are asked for, and the answer is remembered for the session. How much exists depends
 * on how well the town is mapped: where nothing is mapped the answer is simply an empty list and
 * {@link TripPlanner} falls back to fare formulas. Blocking: call from {@link Async}. Never throws.
 */
public final class TransitRoutes {
    private TransitRoutes() {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final String MIRROR = "https://overpass.kumi.systems/api/interpreter";
    private static final Map<String, List<TransitMatch.Line>> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<TransitMatch.Line>> e) { return size() > 40; }
    });
    private static volatile String lastError = "";
    public static String lastError() { return lastError; }

    /** Where the last answer came from: "online", "saved" (fresh copy on the device), "old" (offline, copy older than {@link #FRESH_DAYS} days) or "none". */
    private static volatile String lastSource = "none";
    public static String lastSource() { return lastSource; }
    public static final int FRESH_DAYS = 14;
    private static final long FRESH_MS = FRESH_DAYS * 24L * 3600_000L;
    private static final String FILE_PREFIX = "transit-";

    /** Trips longer than this (straight line) do not look for local lines: the download would be huge and the answer is "take a bus". */
    public static final double MAX_TRIP_KM = 15;

    /** Lines mapped around the trip from a to b ({lat,lng}); empty when none, offline, or the trip is too long. */
    public static List<TransitMatch.Line> around(double[] a, double[] b) {
        if (!AppConfig.transitOnline() || Geo.km(a[0], a[1], b[0], b[1]) > MAX_TRIP_KM) return List.of();
        double pad = 0.006;                                                                    // about 650 m around the two points
        double s = Math.min(a[0], b[0]) - pad, n = Math.max(a[0], b[0]) + pad, w = Math.min(a[1], b[1]) - pad, e = Math.max(a[1], b[1]) + pad;
        String key = String.format(Locale.ROOT, "%.3f,%.3f,%.3f,%.3f", s, w, n, e);
        List<TransitMatch.Line> known = CACHE.get(key);
        if (known != null) return known;
        String file = FILE_PREFIX + key + ".json";
        if (DiskCache.ageMs(file) < FRESH_MS) {                                               // saved on the device recently: no download needed
            List<TransitMatch.Line> saved = fromJson(DiskCache.read(file));
            if (saved != null) { CACHE.put(key, saved); lastSource = "saved"; return saved; }
        }
        // route=bus / share_taxi / minibus: jeepneys and multicabs are mapped under one of these depending on the mapper
        String q = String.format(Locale.ROOT, "[out:json][timeout:20][maxsize:30000000];relation[\"type\"=\"route\"][\"route\"~\"^(bus|share_taxi|minibus|trolleybus)$\"](%.5f,%.5f,%.5f,%.5f);out geom;", s, w, n, e);
        String[] servers = {AppConfig.overpassUrl(), MIRROR};
        for (String server : servers) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(server)).timeout(Duration.ofSeconds(25))
                        .header("User-Agent", "BOFO-Finder/" + AppConfig.appVersion() + " (boarding house finder app)")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("data=" + URLEncoder.encode(q, StandardCharsets.UTF_8))).build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) throw new java.io.IOException("HTTP " + resp.statusCode());
                List<TransitMatch.Line> lines = parse(resp.body());
                CACHE.put(key, lines); lastError = ""; lastSource = "online";
                DiskCache.write(file, toJson(lines)); DiskCache.trim(FILE_PREFIX, 30);       // keep it for next time (and for offline)
                return lines;
            } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return List.of(); }
            catch (Throwable ex) { lastError = "Overpass: " + ex; System.err.println("[TransitRoutes] " + lastError); }
        }
        List<TransitMatch.Line> old = fromJson(DiskCache.read(file));                       // offline: an old copy is still better than nothing
        if (old != null) { lastSource = "old"; return old; }
        lastSource = "none";
        return List.of();                                                                       // not cached in memory: try again next time
    }

    /** Lines to JSON text for the device cache (5 decimals = about 1 m). */
    static String toJson(List<TransitMatch.Line> lines) {
        JSONArray arr = new JSONArray();
        for (TransitMatch.Line l : lines) {
            JSONArray pts = new JSONArray();
            for (double[] p : l.path()) pts.put(new JSONArray().put(Math.round(p[0] * 1e5) / 1e5).put(Math.round(p[1] * 1e5) / 1e5));
            arr.put(new JSONObject().put("ref", l.ref()).put("name", l.name()).put("operator", l.operator()).put("kind", l.kind()).put("path", pts));
        }
        return new JSONObject().put("lines", arr).toString();
    }

    /** Device cache text back to lines; null when missing or unreadable. */
    static List<TransitMatch.Line> fromJson(String text) {
        if (text == null) return null;
        try {
            JSONArray arr = new JSONObject(text).getJSONArray("lines");
            List<TransitMatch.Line> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                JSONArray pts = o.getJSONArray("path");
                double[][] path = new double[pts.length()][];
                for (int k = 0; k < path.length; k++) path[k] = new double[]{pts.getJSONArray(k).getDouble(0), pts.getJSONArray(k).getDouble(1)};
                out.add(new TransitMatch.Line(o.optString("ref"), o.optString("name"), o.optString("operator"), o.optString("kind"), path));
            }
            return out;
        } catch (Throwable t) { return null; }
    }

    /** Overpass "out geom" answer to lines. Skips relations without road geometry. */
    static List<TransitMatch.Line> parse(String body) {
        List<TransitMatch.Line> out = new ArrayList<>();
        JSONArray els = new JSONObject(body).optJSONArray("elements");
        if (els == null) return out;
        for (int i = 0; i < els.length(); i++) {
            JSONObject el = els.getJSONObject(i);
            if (!"relation".equals(el.optString("type"))) continue;
            JSONObject tags = el.optJSONObject("tags");
            JSONArray members = el.optJSONArray("members");
            if (tags == null || members == null) continue;
            List<double[][]> pieces = new ArrayList<>();
            for (int k = 0; k < members.length(); k++) {
                JSONObject m = members.getJSONObject(k);
                String role = m.optString("role", "");
                if (!"way".equals(m.optString("type")) || role.contains("platform") || role.contains("stop")) continue;
                JSONArray g = m.optJSONArray("geometry");
                if (g == null || g.length() < 2) continue;
                double[][] pts = new double[g.length()][];
                boolean ok = true;
                for (int j = 0; j < g.length(); j++) {
                    JSONObject pt = g.optJSONObject(j);                                         // null entries = nodes outside the box
                    if (pt == null) { ok = false; break; }
                    pts[j] = new double[]{pt.getDouble("lat"), pt.getDouble("lon")};
                }
                if (ok) pieces.add(pts);
            }
            double[][] path = TransitMatch.chain(pieces);
            if (path.length >= 2) out.add(new TransitMatch.Line(tags.optString("ref"), tags.optString("name"), tags.optString("operator"), tags.optString("route"), path));
        }
        return out;
    }
}
