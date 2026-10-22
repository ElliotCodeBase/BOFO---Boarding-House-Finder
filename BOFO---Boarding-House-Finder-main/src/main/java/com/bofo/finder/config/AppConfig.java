package com.bofo.finder.config;

import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {
    private static final Properties P = new Properties();
    static {
        try (InputStream in = AppConfig.class.getResourceAsStream("/com/bofo/finder/config.properties")) {
            if (in != null) P.load(in);
        } catch (Exception e) { throw new IllegalStateException("config.properties missing", e); }
    }
    private AppConfig() {}
    public static String get(String k) { return P.getProperty(k, ""); }
    public static String supabaseUrl() { return get("supabase.url"); }
    public static String anonKey() { return get("supabase.anonKey"); }
    public static boolean isAndroid() { return "android".equalsIgnoreCase(System.getProperty("javafx.platform")); }
    public static String redirectUri() { return isAndroid() ? get("auth.redirect.android") : get("auth.redirect.desktop"); }
}
