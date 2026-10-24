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
        @Override protected boolean removeEldestEntry(Map.Entry<String, Route> e) { return size() > 40; }
    });

    public static Route walk(double lat1, double lng1, double lat2, double lng2) {
        String key = String.format(Locale.ROOT, "%.5f,%.5f>%.5f,%.5f", lat1, lng1, lat2, lng2);
        Route cached = CACHE.get(key);
        if (cached != null) return cached;
        if (AppConfig.routeOnline()) {
            String url = AppConfig.routeUrl()
                    .replace("{lat1}", String.format(Locale.ROOT, "%.6f", lat1)).replace("{lng1}", String.format(Locale.ROOT, "%.6f", lng1))
                    .replace("{lat2}", String.format(Locale.ROOT, "%.6f", lat2)).replace("{lng2}", String.format(Locale.ROOT, "%.6f", lng2));
            for (int attempt = 1; attempt <= 2; attempt++) {              // the free server sometimes times out or says "busy": try twice
                try {
                    HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                            .header("User-Agent", "BOFO-Finder/1.2 (boarding house finder app)").GET().build();
                    HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JSONObject o = new JSONObject(resp.body());
                        String code = o.optString("code");
                        if ("Ok".equals(code) && o.getJSONArray("routes").length() > 0) {
                            JSONObject r = o.getJSONArray("routes").getJSONObject(0);
                            JSONArray c = r.getJSONObject("geometry").getJSONArray("coordinates");   // [lng, lat] pairs
                            List<double[]> pts = new ArrayList<>(c.length() + 2);
                            pts.add(new double[]{lat1, lng1});                                       // start exactly on the college / you
                            for (int i = 0; i < c.length(); i++) { JSONArray pt = c.getJSONArray(i); pts.add(new double[]{pt.getDouble(1), pt.getDouble(0)}); }
                            pts.add(new double[]{lat2, lng2});                                       // end exactly on the BH
                            Route route = new Route(thin(pts, 400), Math.max(r.optDouble("distance", 0), Geo.km(lat1, lng1, lat2, lng2) * 1000), false);
                            CACHE.put(key, route);
                            return route;
                        }
                        System.err.println("[Router] server answered '" + code + "' (no walking path between these points)");
                        break;                                                                       // retrying will not help
                    }
                    System.err.println("[Router] HTTP " + resp.statusCode() + " (try " + attempt + ")");
                } catch (Throwable offlineOrBlocked) {
                    System.err.println("[Router] " + offlineOrBlocked + " (try " + attempt + ")");
                }
                try { Thread.sleep(700); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        return new Route(new double[][]{{lat1, lng1}, {lat2, lng2}}, Geo.km(lat1, lng1, lat2, lng2) * 1000, true);
    }

    /** Keeps at most {@code max} points (evenly spaced, first and last always kept) so drawing stays light. */
    private static double[][] thin(List<double[]> p, int max) {
        if (p.size() <= max) return p.toArray(new double[0][]);
        double[][] o = new double[max][];
        for (int i = 0; i < max; i++) o[i] = p.get((int) Math.round(i * (p.size() - 1) / (double) (max - 1)));
        return o;
    }
}
