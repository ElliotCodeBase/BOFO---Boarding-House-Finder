package com.bofo.finder.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pure-Java helpers for the sign-in return link (bofo://auth-callback?code=...). Kept free of android.net.Uri so JUnit can run it. */
public final class AuthLinks {
    private AuthLinks() {}

    /** True when the link is the sign-in return link of this app. */
    public static boolean isCallback(String uri) {
        return uri != null && uri.regionMatches(true, 0, "bofo://auth-callback", 0, "bofo://auth-callback".length());
    }

    /** Query AND fragment parameters (Supabase puts errors in either), decoded. */
    public static Map<String, String> params(String uri) {
        Map<String, String> m = new LinkedHashMap<>();
        if (uri == null) return m;
        int q = uri.indexOf('?');
        int h = uri.indexOf('#');
        if (q >= 0) add(m, uri.substring(q + 1, h > q ? h : uri.length()));
        if (h >= 0) add(m, uri.substring(h + 1));
        return m;
    }

    private static void add(Map<String, String> m, String s) {
        for (String pair : s.split("&")) {
            if (pair.isEmpty()) continue;
            int i = pair.indexOf('=');
            String k = dec(i < 0 ? pair : pair.substring(0, i));
            String v = i < 0 ? "" : dec(pair.substring(i + 1));
            if (!m.containsKey(k)) m.put(k, v);
        }
    }

    private static String dec(String s) {
        try { return URLDecoder.decode(s, "UTF-8"); } catch (UnsupportedEncodingException | IllegalArgumentException e) { return s; }
    }
}
