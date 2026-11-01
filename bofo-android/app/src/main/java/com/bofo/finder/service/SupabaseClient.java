package com.bofo.finder.service;

import com.bofo.finder.config.AppConfig;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.Http;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.function.Supplier;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Thin REST client for Supabase (PostgREST, Auth, Storage, Edge Functions). Uses ONLY the anon key + user JWT. Blocking: call it off the main thread (Async). */
public final class SupabaseClient {
    private static final SupabaseClient INSTANCE = new SupabaseClient();
    public static SupabaseClient get() { return INSTANCE; }

    /** Returns a valid (auto-refreshed) access token, or null when signed out. Set by AuthService. */
    private volatile Supplier<String> tokenProvider = () -> null;
    public void setTokenProvider(Supplier<String> p) { this.tokenProvider = p; }

    public static class ApiException extends RuntimeException {
        public final int status;
        public final String body;
        public ApiException(int status, String body) { super("HTTP " + status + ": " + body); this.status = status; this.body = body == null ? "" : body; }
    }

    private static RequestBody json(String text) { return RequestBody.create(Http.JSON, text); }

    private Request.Builder base(String path, boolean authed) {
        String url = AppConfig.supabaseUrl();
        if (url == null || url.isEmpty()) throw new IllegalStateException("Supabase URL is missing: check supabase.url in config.properties / local.properties");
        String bearer = authed ? tokenProvider.get() : null;
        return new Request.Builder().url(url + path)
                .header("apikey", AppConfig.anonKey())
                .header("x-bofo-app-version", com.bofo.finder.BuildConfig.VERSION_NAME)      // v2.0.120: the server (migration 018) can refuse admin access to old app versions
                .header("Authorization", "Bearer " + (bearer != null ? bearer : AppConfig.anonKey()));
    }

    private String send(Request req) throws Exception { return send(req, Http.client()); }

    private String send(Request req, OkHttpClient client) throws Exception {
        Response r;
        try { r = client.newCall(req).execute(); }
        catch (IOException first) {
            // One quiet retry for reads: a single dropped connection (Wi-Fi hiccup, stale keep-alive) must not flip the whole app offline.
            if (!"GET".equals(req.method())) { Connectivity.report(false); throw first; }
            try { Thread.sleep(500); r = client.newCall(req).execute(); }
            catch (IOException noNetwork) { Connectivity.report(false); throw noNetwork; }   // drives the offline banner + screens
        }
        try (Response resp = r) {
            Connectivity.report(true);                                                     // server answered (any status) = online
            ResponseBody b = resp.body();
            String text = b == null ? "" : b.string();                                      // OkHttp already unzipped gzip
            if (resp.code() >= 300) throw new ApiException(resp.code(), text);
            return text;
        }
    }

    /* ---------- PostgREST ---------- */
    public JSONArray select(String table, String query) throws Exception {
        String body = send(base("/rest/v1/" + table + (query == null || query.isEmpty() ? "" : "?" + query), true)
                .header("Accept", "application/json").get().build(), Http.client(15));      // reads fail fast; the 40 s limit stays for uploads
        return new JSONArray(body);
    }

    public JSONArray insert(String table, JSONObject row) throws Exception {
        return new JSONArray(send(base("/rest/v1/" + table, true)
                .header("Prefer", "return=representation").post(json(row.toString())).build()));
    }

    /** Insert, or update the existing row when {@code onConflict} (comma-separated unique columns) already matches one. */
    public JSONArray upsert(String table, String onConflict, JSONObject row) throws Exception {
        return new JSONArray(send(base("/rest/v1/" + table + "?on_conflict=" + onConflict, true)
                .header("Prefer", "resolution=merge-duplicates,return=representation").post(json(row.toString())).build()));
    }

    /** Inserts many rows in ONE request (instead of one request per row). All rows must have the same keys. */
    public JSONArray insert(String table, JSONArray rows) throws Exception {
        return new JSONArray(send(base("/rest/v1/" + table, true)
                .header("Prefer", "return=representation").post(json(rows.toString())).build()));
    }

    public JSONArray update(String table, String filter, JSONObject patch) throws Exception {
        return new JSONArray(send(base("/rest/v1/" + table + "?" + filter, true)
                .header("Prefer", "return=representation").patch(json(patch.toString())).build()));
    }

    /** DELETE that returns the rows it removed. PostgREST answers 204 even when RLS hid the row (0 rows), so callers can check. */
    public JSONArray deleteReturning(String table, String filter) throws Exception {
        String body = send(base("/rest/v1/" + table + "?" + filter, true).header("Prefer", "return=representation")
                .header("Accept", "application/json").delete().build());
        return body == null || body.trim().isEmpty() ? new JSONArray() : new JSONArray(body);
    }

    public void delete(String table, String filter) throws Exception {
        send(base("/rest/v1/" + table + "?" + filter, true).delete().build());
    }

    public JSONObject rpc(String fn, JSONObject args) throws Exception {
        String body = send(base("/rest/v1/rpc/" + fn, true).post(json(args.toString())).build());
        return body == null || body.trim().isEmpty() ? new JSONObject() : (body.trim().startsWith("[") ? new JSONObject().put("data", new JSONArray(body)) : new JSONObject(body));
    }

    /* ---------- Auth (unauthenticated endpoints) ---------- */
    public JSONObject authPost(String path, JSONObject body) throws Exception {
        return new JSONObject(send(base("/auth/v1/" + path, false).post(json(body.toString())).build()));
    }

    /** v2.0.119: auth endpoints that need the signed-in user (two-step verification: /user, /factors...). method = GET, POST or DELETE. */
    public String authCall(String method, String path, JSONObject body) throws Exception {
        Request.Builder rb = base("/auth/v1/" + path, true);
        if ("GET".equals(method)) rb.get();
        else if ("DELETE".equals(method)) rb.delete();
        else rb.post(json(body == null ? "{}" : body.toString()));
        return send(rb.build());
    }

    public void authLogout() {
        try { send(base("/auth/v1/logout", true).post(RequestBody.create(null, new byte[0])).build()); } catch (Exception ignored) {}
    }

    /* ---------- Storage ---------- */
    public void upload(String bucket, String path, byte[] data, String contentType) throws Exception {
        send(base("/storage/v1/object/" + bucket + "/" + path, true)
                .header("x-upsert", "true").post(RequestBody.create(okhttp3.MediaType.parse(contentType), data)).build());
    }

    public String publicUrl(String bucket, String path) {
        return AppConfig.supabaseUrl() + "/storage/v1/object/public/" + bucket + "/" + path;
    }

    /** Time-limited URL for private buckets (admin review). */
    public String signedUrl(String bucket, String path, int seconds) throws Exception {
        String body = send(base("/storage/v1/object/sign/" + bucket + "/" + path, true)
                .post(json(new JSONObject().put("expiresIn", seconds).toString())).build());
        return AppConfig.supabaseUrl() + "/storage/v1" + new JSONObject(body).getString("signedURL");
    }

    /* ---------- Edge Functions ---------- */
    public JSONObject function(String name, JSONObject payload) throws Exception {
        return new JSONObject(send(base("/functions/v1/" + name, true).post(json(payload.toString())).build()));
    }
}
