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
    public static String redirectUri() { return isAndroid() ? get("auth.redirect.android") : get("auth.redirect.desktop"); }
}
