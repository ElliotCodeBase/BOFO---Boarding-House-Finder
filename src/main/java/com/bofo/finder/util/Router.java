package com.bofo.finder.util;

import com.bofo.finder.config.AppConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Walking route between two points (follows real roads and paths). Asks an OSRM-style service (see AppConfig.routeUrl);
 * if the service cannot be reached the answer is a straight line flagged {@code approximate}, so the map always has
 * something to draw. Blocking: call it from {@link Async}, never from the FX thread. Never throws.
 */
public final class Router {
    private Router() {}

    /** pts = {lat,lng} pairs from start to end; meters = walking distance; approximate = straight line fallback. */
    public record Route(double[][] pts, double meters, boolean approximate) {
        /** {minLat, minLng, maxLat, maxLng} of the whole path. */
        public double[] bounds() {
            double a = 90, b = 180, c = -90, d = -180;
            for (double[] p : pts) { a = Math.min(a, p[0]); c = Math.max(c, p[0]); b = Math.min(b, p[1]); d = Math.max(d, p[1]); }
            return new double[]{a, b, c, d};
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Map<String, Route> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Route> e) { return size() > 300; }
    });

    /** Same request already running (two taps, map + card)? Both callers wait for ONE server call. */
    private static final Map<String, CompletableFuture<Route>> INFLIGHT = new ConcurrentHashMap<>();

    private static String key(double lat1, double lng1, double lat2, double lng2) { return key("foot", lat1, lng1, lat2, lng2); }
    private static String key(String profile, double lat1, double lng1, double lat2, double lng2) {
        return String.format(Locale.ROOT, "%s:%.5f,%.5f>%.5f,%.5f", profile, lat1, lng1, lat2, lng2);
    }

    /** Beyond this straight distance nobody walks, and the free foot server only returns odd paths: skip it. */
    public static final double MAX_WALK_KM = 30;

    /** Already-known real route (no network, no waiting), or null. Call on the FX thread to draw it instantly. */
    public static Route peek(double lat1, double lng1, double lat2, double lng2) {
        Route r = CACHE.get(key(lat1, lng1, lat2, lng2));
        if (r != null) return r;
        Route rev = CACHE.get(key(lat2, lng2, lat1, lng1));            // A->B walked the other way is the same road
        if (rev == null) return null;
        double[][] back = new double[rev.pts().length][];
        for (int i = 0; i < back.length; i++) back[i] = rev.pts()[back.length - 1 - i];
        return new Route(back, rev.meters(), rev.approximate());
    }

    /** Instant offline guess: straight line, with distance x1.3 to allow for bends. No network, so it is ready right away. */
    public static Route estimate(double lat1, double lng1, double lat2, double lng2) {
        return new Route(new double[][]{{lat1, lng1}, {lat2, lng2}}, Geo.km(lat1, lng1, lat2, lng2) * 1300, true);
    }

    /** Opens the connection to the route server early (TLS handshake takes a good part of the first route's wait). Fire and forget. */
    public static void warmUp() {
        Async.bg(() -> {
            try {
                String u = AppConfig.routeUrl();
                int i = u.indexOf('/', u.indexOf("://") + 3);
                HTTP.send(HttpRequest.newBuilder(URI.create(i > 0 ? u.substring(0, i) + "/" : u)).timeout(Duration.ofSeconds(5)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.discarding());
            } catch (Throwable ignored) { }
        });
    }

    public static Route walk(double lat1, double lng1, double lat2, double lng2) { return route("foot", lat1, lng1, lat2, lng2); }

    /** Road route for vehicles (tricycle, jeepney, van): follows one-way streets, so it can be longer than the walk. */
    public static Route drive(double lat1, double lng1, double lat2, double lng2) { return route("drive", lat1, lng1, lat2, lng2); }

    private static Route route(String profile, double lat1, double lng1, double lat2, double lng2) {
        boolean foot = profile.equals("foot");
        if (foot && Geo.km(lat1, lng1, lat2, lng2) > MAX_WALK_KM) return estimate(lat1, lng1, lat2, lng2);
        Route known = foot ? peek(lat1, lng1, lat2, lng2) : CACHE.get(key(profile, lat1, lng1, lat2, lng2));
        if (known != null) return known;
        String key = key(profile, lat1, lng1, lat2, lng2);
        CompletableFuture<Route> mine = new CompletableFuture<>();
        CompletableFuture<Route> other = INFLIGHT.putIfAbsent(key, mine);
        if (other != null) { try { return other.get(); } catch (Exception e) { return estimate(lat1, lng1, lat2, lng2); } }
        try { Route r = fetch(key, foot, lat1, lng1, lat2, lng2); mine.complete(r); return r; }
        finally { INFLIGHT.remove(key); mine.complete(estimate(lat1, lng1, lat2, lng2)); }   // no-op if already completed
    }

    /** Why the last route failed (shown on the map card so a dotted/straight line can be diagnosed). */
    private static volatile String lastError = "";
    public static String lastError() { return lastError; }

    private static final String VALHALLA = "https://valhalla1.openstreetmap.de/route?json=";

    private static Route fetch(String key, boolean foot, double lat1, double lng1, double lat2, double lng2) {
        if (AppConfig.routeOnline()) {
            lastError = "";
            // 1) OSRM foot server (configurable), 2) Valhalla pedestrian server: two independent free services, so one being busy or blocked does not lose the road path
            for (int provider = 0; provider < 2; provider++) {
                for (int attempt = 1; attempt <= 2; attempt++) {
                    try {
                        Route r = provider == 0 ? osrm(foot, lat1, lng1, lat2, lng2) : valhalla(foot, lat1, lng1, lat2, lng2);
                        if (r != null) { CACHE.put(key, r); lastError = ""; return r; }
                        break;                                                                       // answered "no path": retrying will not help
                    } catch (Throwable e) {
                        lastError = (provider == 0 ? "OSRM: " : "Valhalla: ") + e;
                        System.err.println("[Router] " + lastError + " (try " + attempt + ")");
                    }
                    try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return estimate(lat1, lng1, lat2, lng2); }
                }
            }
        }
        Route fallback = estimate(lat1, lng1, lat2, lng2);
        return new Route(fallback.pts(), Geo.km(lat1, lng1, lat2, lng2) * 1000, true);               // straight line only when both servers failed
    }

    private static HttpResponse<String> get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                .header("User-Agent", "BOFO-Finder/" + AppConfig.appVersion() + " (boarding house finder app)").GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new java.io.IOException("HTTP " + resp.statusCode());
        return resp;
    }

    /** OSRM-style answer (GeoJSON line). Returns null when the server says there is no walking path. */
    private static Route osrm(boolean foot, double lat1, double lng1, double lat2, double lng2) throws Exception {
        String url = (foot ? AppConfig.routeUrl() : AppConfig.driveUrl())
                .replace("{lat1}", String.format(Locale.ROOT, "%.6f", lat1)).replace("{lng1}", String.format(Locale.ROOT, "%.6f", lng1))
                .replace("{lat2}", String.format(Locale.ROOT, "%.6f", lat2)).replace("{lng2}", String.format(Locale.ROOT, "%.6f", lng2));
        JSONObject o = new JSONObject(get(url).body());
        String code = o.optString("code");
        if (!"Ok".equals(code) || o.getJSONArray("routes").length() == 0) { lastError = "OSRM: " + code; System.err.println("[Router] OSRM answered '" + code + "'"); return null; }
        JSONObject r = o.getJSONArray("routes").getJSONObject(0);
        JSONArray c = r.getJSONObject("geometry").getJSONArray("coordinates");                      // [lng, lat] pairs
        List<double[]> pts = new ArrayList<>(c.length() + 2);
        pts.add(new double[]{lat1, lng1});                                                           // start exactly on the college / you
        for (int i = 0; i < c.length(); i++) { JSONArray pt = c.getJSONArray(i); pts.add(new double[]{pt.getDouble(1), pt.getDouble(0)}); }
        pts.add(new double[]{lat2, lng2});                                                           // end exactly on the BH
        return new Route(thin(pts, 400), Math.max(r.optDouble("distance", 0), Geo.km(lat1, lng1, lat2, lng2) * 1000), false);
    }

    /** Valhalla pedestrian route (shape is an encoded polyline with 6 decimals). */
    private static Route valhalla(boolean foot, double lat1, double lng1, double lat2, double lng2) throws Exception {
        JSONObject q = new JSONObject().put("costing", foot ? "pedestrian" : "auto").put("locations", new JSONArray()
                .put(new JSONObject().put("lat", lat1).put("lon", lng1)).put(new JSONObject().put("lat", lat2).put("lon", lng2)));
        String url = VALHALLA + java.net.URLEncoder.encode(q.toString(), java.nio.charset.StandardCharsets.UTF_8);
        JSONObject trip = new JSONObject(get(url).body()).getJSONObject("trip");
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{lat1, lng1});
        JSONArray legs = trip.getJSONArray("legs");
        for (int l = 0; l < legs.length(); l++) pts.addAll(decodePolyline6(legs.getJSONObject(l).getString("shape")));
        pts.add(new double[]{lat2, lng2});
        double meters = trip.getJSONObject("summary").optDouble("length", 0) * 1000;                 // Valhalla reports km
        return new Route(thin(pts, 400), Math.max(meters, Geo.km(lat1, lng1, lat2, lng2) * 1000), false);
    }

    /** Standard encoded-polyline decoder, precision 6 (Valhalla). Returns {lat,lng} pairs. */
    static List<double[]> decodePolyline6(String s) {
        List<double[]> out = new ArrayList<>();
        int idx = 0, lat = 0, lng = 0;
        while (idx < s.length()) {
            for (int k = 0; k < 2; k++) {
                int shift = 0, result = 0, b;
                do { b = s.charAt(idx++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20 && idx < s.length());
                int d = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
                if (k == 0) lat += d; else lng += d;
            }
            out.add(new double[]{lat / 1e6, lng / 1e6});
        }
        return out;
    }

    /** Keeps at most {@code max} points (evenly spaced, first and last always kept) so drawing stays light. */
    private static double[][] thin(List<double[]> p, int max) {
        if (p.size() <= max) return p.toArray(new double[0][]);
        double[][] o = new double[max][];
        for (int i = 0; i < max; i++) o[i] = p.get((int) Math.round(i * (p.size() - 1) / (double) (max - 1)));
        return o;
    }
}
