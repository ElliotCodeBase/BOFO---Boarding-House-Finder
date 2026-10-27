package com.bofo.finder.service;

import com.bofo.finder.config.AppConfig;
import com.bofo.finder.util.Connectivity;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

/** Thin REST client for Supabase (PostgREST, Auth, Storage, Edge Functions). Uses ONLY the anon key + user JWT. */
public final class SupabaseClient {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    private static final SupabaseClient INSTANCE = new SupabaseClient();
    public static SupabaseClient get() { return INSTANCE; }

    /** Returns a valid (auto-refreshed) access token, or null when signed out. Set by AuthService. */
    private Supplier<String> tokenProvider = () -> null;
    public void setTokenProvider(Supplier<String> p) { this.tokenProvider = p; }

    public static class ApiException extends RuntimeException {
        public final int status;
        public final String body;
        public ApiException(int status, String body) { super("HTTP " + status + ": " + body); this.status = status; this.body = body == null ? "" : body; }
    }

    private HttpRequest.Builder base(String path, boolean authed) {
        String bearer = authed ? tokenProvider.get() : null;
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(AppConfig.supabaseUrl() + path))
                .timeout(Duration.ofSeconds(30))
                .header("apikey", AppConfig.anonKey())
                .header("Accept-Encoding", "gzip")                    // JSON lists shrink about 5-10x on the wire (slow mobile data)
                .header("Authorization", "Bearer " + (bearer != null ? bearer : AppConfig.anonKey()));
        return b;
    }

    private String send(HttpRequest req) throws Exception {
        HttpResponse<byte[]> r;
        try { r = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray()); }
        catch (java.io.IOException first) {
            // One quiet retry for reads: a single dropped connection (Wi-Fi hiccup, stale keep-alive) must not flip the whole app offline.
            if (!"GET".equals(req.method())) { Connectivity.report(false); throw first; }
            try { Thread.sleep(500); r = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray()); }
            catch (java.io.IOException noNetwork) { Connectivity.report(false); throw noNetwork; }   // drives the offline banner + screens
        }
        Connectivity.report(true);                                                               // server answered (any status) = online
        String text = decode(r);
        if (r.statusCode() >= 300) throw new ApiException(r.statusCode(), text);
        return text;
    }

    /** Body as text; unzips it when the server sent it gzip-compressed. */
    private static String decode(HttpResponse<byte[]> r) throws java.io.IOException {
        byte[] b = r.body();
        if (b == null || b.length == 0) return "";
        if ("gzip".equalsIgnoreCase(r.headers().firstValue("Content-Encoding").orElse("")))
            try (GZIPInputStream z = new GZIPInputStream(new ByteArrayInputStream(b))) { b = z.readAllBytes(); }
        return new String(b, StandardCharsets.UTF_8);
    }

    /* ---------- PostgREST ---------- */
    public JSONArray select(String table, String query) throws Exception {
        String body = send(base("/rest/v1/" + table + (query == null || query.isEmpty() ? "" : "?" + query), true)
                .timeout(Duration.ofSeconds(15)).header("Accept", "application/json").GET().build());   // reads fail fast; the 30 s limit stays for uploads
        return new JSONArray(body);
    }

    public JSONArray insert(String table, JSONObject row) throws Exception {
        String body = send(base("/rest/v1/" + table, true).header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .POST(HttpRequest.BodyPublishers.ofString(row.toString())).build());
        return new JSONArray(body);
    }

    /** Inserts many rows in ONE request (instead of one request per row). All rows must have the same keys. */
    public JSONArray insert(String table, JSONArray rows) throws Exception {
        String body = send(base("/rest/v1/" + table, true).header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .POST(HttpRequest.BodyPublishers.ofString(rows.toString())).build());
        return new JSONArray(body);
    }

    public JSONArray update(String table, String filter, JSONObject patch) throws Exception {
        String body = send(base("/rest/v1/" + table + "?" + filter, true).header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(patch.toString())).build());
        return new JSONArray(body);
    }

    /** DELETE that returns the rows it removed. PostgREST answers 204 even when RLS hid the row (0 rows), so callers can check. */
    public JSONArray deleteReturning(String table, String filter) throws Exception {
        String body = send(base("/rest/v1/" + table + "?" + filter, true).header("Prefer", "return=representation")
                .header("Accept", "application/json").DELETE().build());
        return body == null || body.isBlank() ? new JSONArray() : new JSONArray(body);
    }

    public void delete(String table, String filter) throws Exception {
        send(base("/rest/v1/" + table + "?" + filter, true).DELETE().build());
    }

    public JSONObject rpc(String fn, JSONObject args) throws Exception {
        String body = send(base("/rest/v1/rpc/" + fn, true).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(args.toString())).build());
        return body == null || body.isBlank() ? new JSONObject() : (body.trim().startsWith("[") ? new JSONObject().put("data", new JSONArray(body)) : new JSONObject(body));
    }

    /* ---------- Auth (unauthenticated endpoints) ---------- */
    public JSONObject authPost(String path, JSONObject body) throws Exception {
        return new JSONObject(send(base("/auth/v1/" + path, false).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()));
    }

    public void authLogout() {
        try { send(base("/auth/v1/logout", true).POST(HttpRequest.BodyPublishers.noBody()).build()); } catch (Exception ignored) {}
    }

    /* ---------- Storage ---------- */
    public void upload(String bucket, String path, byte[] data, String contentType) throws Exception {
        send(base("/storage/v1/object/" + bucket + "/" + path, true).header("Content-Type", contentType)
                .header("x-upsert", "true").POST(HttpRequest.BodyPublishers.ofByteArray(data)).build());
    }

    public String publicUrl(String bucket, String path) {
        return AppConfig.supabaseUrl() + "/storage/v1/object/public/" + bucket + "/" + path;
    }

    /** Time-limited URL for private buckets (admin review). */
    public String signedUrl(String bucket, String path, int seconds) throws Exception {
        String body = send(base("/storage/v1/object/sign/" + bucket + "/" + path, true).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(new JSONObject().put("expiresIn", seconds).toString())).build());
        return AppConfig.supabaseUrl() + "/storage/v1" + new JSONObject(body).getString("signedURL");
    }

    /* ---------- Edge Functions ---------- */
    public JSONObject function(String name, JSONObject payload) throws Exception {
        return new JSONObject(send(base("/functions/v1/" + name, true).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build()));
    }
}
