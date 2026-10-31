package com.bofo.finder.config;

import android.content.Context;

import com.bofo.finder.BuildConfig;

import java.io.InputStream;
import java.util.Properties;

/**
 * App settings. Secrets-free values come from BuildConfig (filled by app/build.gradle from the desktop config.properties
 * or local.properties); the optional tuning keys (route.*, transit.*, policy.*) come from assets/config.properties.
 * Same getter names as the JavaFX AppConfig, so ported services need no changes.
 */
public final class AppConfig {
    private static final Properties P = new Properties();
    private AppConfig() {}

    /** Call once from {@link com.bofo.finder.app.BofoApp#onCreate()}. Without it (unit tests) the defaults below are used. */
    public static synchronized void init(Context ctx) {
        try (InputStream in = ctx.getAssets().open("config.properties")) {
            P.clear();
            P.load(in);
        } catch (Exception ignored) { /* defaults apply */ }
    }

    public static String get(String k) { return P.getProperty(k, ""); }
    public static String appVersion() { String v = BuildConfig.VERSION_NAME; return v == null || v.trim().isEmpty() ? "dev" : v; }
    public static String supabaseUrl() { return BuildConfig.SUPABASE_URL; }
    public static String anonKey() { return BuildConfig.SUPABASE_ANON_KEY; }
    public static boolean isAndroid() { return true; }
    /** Google WEB client ID (the one pasted into Supabase). */
    public static String googleWebClientId() { return BuildConfig.GOOGLE_WEB_CLIENT_ID.trim(); }
    public static boolean googleNative() { return !googleWebClientId().isEmpty(); }
    public static String redirectUri() { return BuildConfig.AUTH_REDIRECT; }

    /** DEBUG builds only: test account for a role ("student" | "owner" | "admin") from dev.<role>.email / .password in the desktop
     *  config.local.properties (same accounts as the JavaFX buttons). Release builds have these empty. */
    public static String devEmail(String role) {
        switch (role) {
            case "student": return BuildConfig.DEV_STUDENT_EMAIL.trim();
            case "owner":   return BuildConfig.DEV_OWNER_EMAIL.trim();
            case "admin":   return BuildConfig.DEV_ADMIN_EMAIL.trim();
            default:        return "";
        }
    }
    public static String devPassword(String role) {
        switch (role) {
            case "student": return BuildConfig.DEV_STUDENT_PASSWORD.trim();
            case "owner":   return BuildConfig.DEV_OWNER_PASSWORD.trim();
            case "admin":   return BuildConfig.DEV_ADMIN_PASSWORD.trim();
            default:        return "";
        }
    }
    /** True when at least one test account is configured: the login screen then shows the Student / Owner / Admin buttons. */
    public static boolean hasDevAccounts() {
        return !devEmail("student").isEmpty() || !devEmail("owner").isEmpty() || !devEmail("admin").isEmpty();
    }

    /** Walking-route service (OSRM style). {lng1},{lat1};{lng2},{lat2} are filled in. Override with route.url. */
    public static String routeUrl() { String u = get("route.url").trim(); return u.isEmpty() ? "https://routing.openstreetmap.de/routed-foot/route/v1/foot/{lng1},{lat1};{lng2},{lat2}?overview=full&geometries=geojson" : u; }
    /** route.online=false skips the routing service and always draws a straight line. */
    public static boolean routeOnline() { return !"false".equalsIgnoreCase(get("route.online").trim()); }
    /** Road (car / tricycle / jeepney) route service, same OSRM format. Override with route.drive.url. */
    public static String driveUrl() { String u = get("route.drive.url").trim(); return u.isEmpty() ? "https://routing.openstreetmap.de/routed-car/route/v1/driving/{lng1},{lat1};{lng2},{lat2}?overview=full&geometries=geojson" : u; }
    /** Public transport lines from OpenStreetMap (Overpass API). transit.online=false turns the lookup off. */
    public static String overpassUrl() { String u = get("transit.overpass").trim(); return u.isEmpty() ? "https://overpass-api.de/api/interpreter" : u; }
    public static boolean transitOnline() { return !"false".equalsIgnoreCase(get("transit.online").trim()); }
}
