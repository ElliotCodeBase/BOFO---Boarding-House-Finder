package com.bofo.finder.util;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Plain-text HTTP for the route and transit services (replaces java.net.http.HttpClient, which Android does not have).
 * Blocking: call it from {@link Async}, never from the main thread. Anything but HTTP 200 is an {@link IOException}.
 */
public final class HttpText {
    private HttpText() {}

    public static String get(String url, int callSeconds, String userAgent) throws IOException {
        Request req = new Request.Builder().url(url).header("User-Agent", userAgent).get().build();
        return send(req, callSeconds);
    }

    /** POST of one form field (the Overpass API wants {@code data=<query>}). */
    public static String postForm(String url, String field, String value, int callSeconds, String userAgent) throws IOException {
        Request req = new Request.Builder().url(url).header("User-Agent", userAgent)
                .post(new FormBody.Builder().add(field, value).build()).build();
        return send(req, callSeconds);
    }

    /** Opens the connection early (the TLS handshake is a good part of the first route's wait). Result ignored. */
    public static void head(String url, int callSeconds) throws IOException {
        Request req = new Request.Builder().url(url).head().build();
        try (Response ignored = Http.client(callSeconds).newCall(req).execute()) { /* only the handshake matters */ }
    }

    private static String send(Request req, int callSeconds) throws IOException {
        try (Response r = Http.client(callSeconds).newCall(req).execute()) {
            if (r.code() != 200) throw new IOException("HTTP " + r.code());
            ResponseBody body = r.body();
            return body == null ? "" : body.string();
        }
    }
}
