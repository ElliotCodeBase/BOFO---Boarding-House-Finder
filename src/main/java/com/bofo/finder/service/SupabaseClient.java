package com.bofo.finder.service;

import com.bofo.finder.config.AppConfig;
import com.bofo.finder.util.Connectivity;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

/** Thin REST client for Supabase (PostgREST, Auth, Storage, Edge Functions). Uses ONLY the anon key + user JWT. */
public final class SupabaseClient {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final SupabaseClient INSTANCE = new SupabaseClient();
    public static SupabaseClient get() { return INSTANCE; }

    /** Returns a valid (auto-refreshed) access token, or null when signed out. Set by AuthService. */
    private Supplier<String> tokenProvider = () -> null;
    public void setTokenProvider(Supplier<String> p) { this.tokenProvider = p; }

    public static class ApiException extends RuntimeException {
        public final int status;
        public ApiException(int status, String body) { super("HTTP " + status + ": " + body); this.status = status; }
    }

    private HttpRequest.Builder base(String path, boolean authed) {
        String bearer = authed ? tokenProvider.get() : null;
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(AppConfig.supabaseUrl() + path))
                .timeout(Duration.ofSeconds(30))
                .header("apikey", AppConfig.anonKey())
                .header("Authorization", "Bearer " + (bearer != null ? bearer : AppConfig.anonKey()));
        return b;
    }

    private String send(HttpRequest req) throws Exception {
        HttpResponse<String> r;
        try { r = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)); }
        catch (java.io.IOException noNetwork) { Connectivity.report(false); throw noNetwork; }   // drives the offline banner + screens
        Connectivity.report(true);                                                               // server answered (any status) = online
        if (r.statusCode() >= 300) throw new ApiException(r.statusCode(), r.body());
        return r.body();
    }

    /* ---------- PostgREST ---------- */
    public JSONArray select(String table, String query) throws Exception {
        String body = send(base("/rest/v1/" + table + (query == null || query.isEmpty() ? "" : "?" + query), true)
                .header("Accept", "application/json").GET().build());
        return new JSONArray(body);
    }

    public JSONArray insert(String table, JSONObject row) throws Exception {
        String body = send(base("/rest/v1/" + table, true).header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .POST(HttpRequest.BodyPublishers.ofString(row.toString())).build());
        return new JSONArray(body);
    }

    public JSONArray update(String table, String filter, JSONObject patch) throws Exception {
        String body = send(base("/rest/v1/" + table + "?" + filter, true).header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(patch.toString())).build());
        return new JSONArray(body);
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
