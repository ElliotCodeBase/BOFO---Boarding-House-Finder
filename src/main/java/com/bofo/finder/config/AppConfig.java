package com.bofo.finder.config;

import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {
    private static final Properties P = new Properties();
    static {
        try (InputStream in = AppConfig.class.getResourceAsStream("/com/bofo/finder/config.properties")) {
            if (in != null) P.load(in);
        } catch (Exception e) { throw new IllegalStateException("config.properties missing", e); }
        // Optional, git-ignored overrides (dev test-account passwords etc.). Never commit this file.
        try (InputStream in = AppConfig.class.getResourceAsStream("/com/bofo/finder/config.local.properties")) {
            if (in != null) P.load(in);
        } catch (Exception ignored) { }
    }
    private AppConfig() {}
    public static String get(String k) { return P.getProperty(k, ""); }
    public static String supabaseUrl() { return get("supabase.url"); }
    public static String anonKey() { return get("supabase.anonKey"); }
    public static boolean devBypass() { return "true".equalsIgnoreCase(get("dev.bypassLogin").trim()); }
    public static String devRole() { String r = get("dev.role").trim(); return r.isEmpty() ? "student" : r; }
    /** DEV: email / password of the real Supabase test account for a role ("student" | "owner" | "admin"), or "" if not configured. */
    public static String devEmail(String role) { return get("dev." + role + ".email").trim(); }
    public static String devPassword(String role) { return get("dev." + role + ".password").trim(); }
    public static boolean isAndroid() { return "android".equalsIgnoreCase(System.getProperty("javafx.platform")); }
    /** DEV (PC only): pretend the phone is here, e.g. dev.location=10.3157,123.8854 in config.local.properties. */
    public static double[] devLocation() {
        String v = get("dev.location").trim();
        if (v.isEmpty()) return null;
        try { String[] p = v.split(","); return new double[]{Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim())}; }
        catch (Exception bad) { return null; }
    }
    /** Google WEB client ID (the one pasted into Supabase). Empty = keep the browser sign-in. */
    public static String googleWebClientId() { return get("google.webClientId").trim(); }
    /** True when Android should use the in-app Google account picker instead of the browser. */
    public static boolean googleNative() { return !googleWebClientId().isEmpty() && !"false".equalsIgnoreCase(get("google.native").trim()); }
    public static String tileUrl() { String u = get("map.tiles.url").trim(); return u.isEmpty() ? "https://basemaps.cartocdn.com/rastertiles/light_nolabels/{z}/{x}/{y}.png" : u; }
    public static String tileKey() { return get("map.tiles.key").trim(); }
    /** "vector" (default: OpenFreeMap vector tiles drawn by VectorTileRetriever) or "raster" (picture tiles, MinimalTileRetriever). */
    public static String mapRenderer() { return "raster".equalsIgnoreCase(get("map.renderer").trim()) ? "raster" : "vector"; }
    public static String vectorTileJson() { String u = get("map.vector.tilejson").trim(); return u.isEmpty() ? "https://tiles.openfreemap.org/planet" : u; }
    public static String mapAttribution() {
        if ("vector".equals(mapRenderer())) {
            String v = get("map.attribution.vector").trim();
            return v.isEmpty() ? "OpenFreeMap \u00A9 OpenMapTiles, data from OpenStreetMap" : v;
        }
        String a = get("map.attribution").trim();
        return a.isEmpty() ? "\u00A9 OpenStreetMap contributors \u00A9 CARTO" : a;
    }
    /** Walking-route service (OSRM style). {lng1},{lat1};{lng2},{lat2} are filled in. Override with route.url in config.properties. */
    public static String routeUrl() { String u = get("route.url").trim(); return u.isEmpty() ? "https://routing.openstreetmap.de/routed-foot/route/v1/foot/{lng1},{lat1};{lng2},{lat2}?overview=full&geometries=geojson" : u; }
    /** route.online=false skips the routing service and always draws a straight line. */
    public static boolean routeOnline() { return !"false".equalsIgnoreCase(get("route.online").trim()); }
    public static String redirectUri() { return isAndroid() ? get("auth.redirect.android") : get("auth.redirect.desktop"); }
}
