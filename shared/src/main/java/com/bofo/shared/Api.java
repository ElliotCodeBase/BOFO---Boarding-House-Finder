package com.bofo.shared;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.function.Supplier;

import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Thin Supabase REST client shared by the apps (anon key + the signed-in user's JWT only; never a service_role key).
 * Blocking: call it through {@link Async}. Every request carries x-bofo-client so the server can tell the admin app from the user app.
 */
public final class Api {
    private static final Api I = new Api();
    public static Api get() { return I; }

    public static class ApiException extends RuntimeException {
        public final int status; public final String body;
        public ApiException(int status, String body) { super("HTTP " + status); this.status = status; this.body = body == null ? "" : body; }
    }

    private volatile String url = "", anon = "", version = "0", client = "user";
    private volatile Supplier<String> token = () -> null;

    public void init(String supabaseUrl, String anonKey, String appVersion, String clientName) {
        url = supabaseUrl == null ? "" : supabaseUrl; anon = anonKey == null ? "" : anonKey; version = appVersion; client = clientName;
    }
    public void setTokenProvider(Supplier<String> p) { token = p; }

    private Request.Builder base(String path, boolean authed) {
        if (url.isEmpty()) throw new IllegalStateException("supabase.url is missing (bofo-android/local.properties)");
        String t = authed ? token.get() : null;
        return new Request.Builder().url(url + path).header("apikey", anon)
                .header("x-bofo-app-version", version).header("x-bofo-client", client)
                .header("Authorization", "Bearer " + (t != null ? t : anon));
    }

    private String send(Request r, int timeout) throws Exception {
        try (Response resp = Http.client(timeout).newCall(r).execute()) {
            String text = resp.body() == null ? "" : resp.body().string();
            if (resp.code() >= 300) throw new ApiException(resp.code(), text);
            return text;
        }
    }

    public JSONArray select(String table, String query) throws Exception {
        return new JSONArray(send(base("/rest/v1/" + table + (query == null || query.isEmpty() ? "" : "?" + query), true)
                .header("Accept", "application/json").get().build(), 15));
    }

    /** Calls a database function. Returns the JSON text ("" when the function returns nothing). */
    public String rpc(String fn, JSONObject args) throws Exception {
        return send(base("/rest/v1/rpc/" + fn, true).post(RequestBody.create(Http.JSON, args.toString())).build(), 30);
    }
    public JSONObject rpcObject(String fn, JSONObject args) throws Exception {
        String b = rpc(fn, args);
        return b == null || b.trim().isEmpty() || !b.trim().startsWith("{") ? new JSONObject() : new JSONObject(b);
    }

    public JSONObject authPost(String path, JSONObject body) throws Exception {
        return new JSONObject(send(base("/auth/v1/" + path, false).post(RequestBody.create(Http.JSON, body.toString())).build(), 30));
    }
    /** Auth endpoints that need the signed-in user (factors, challenge, verify). */
    public String authCall(String method, String path, JSONObject body) throws Exception {
        Request.Builder rb = base("/auth/v1/" + path, true);
        if ("GET".equals(method)) rb.get(); else rb.post(RequestBody.create(Http.JSON, body == null ? "{}" : body.toString()));
        return send(rb.build(), 30);
    }
    public void logout() {
        try { send(base("/auth/v1/logout", true).post(RequestBody.create(null, new byte[0])).build(), 10); } catch (Exception ignored) { }
    }
    /** Time-limited link for a private bucket (ID photos). */
    public String signedUrl(String bucket, String path, int seconds) throws Exception {
        String b = send(base("/storage/v1/object/sign/" + bucket + "/" + path, true)
                .post(RequestBody.create(Http.JSON, new JSONObject().put("expiresIn", seconds).toString())).build(), 15);
        return url + "/storage/v1" + new JSONObject(b).getString("signedURL");
    }
    /** Downloads bytes with the user's JWT (used for signed links, which carry their own token). */
    public byte[] fetchBytes(String fullUrl) throws Exception {
        try (Response r = Http.client(30).newCall(new Request.Builder().url(fullUrl).build()).execute()) {
            if (r.code() >= 300 || r.body() == null) throw new ApiException(r.code(), "");
            return r.body().bytes();
        }
    }
}
