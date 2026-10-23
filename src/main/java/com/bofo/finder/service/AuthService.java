package com.bofo.finder.service;

import com.bofo.finder.config.AppConfig;
import com.bofo.finder.model.Profile;
import com.bofo.finder.model.Role;
import com.bofo.finder.util.Pkce;
import com.sun.net.httpserver.HttpServer;
import javafx.application.HostServices;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Google sign-in through Supabase Auth (PKCE), persistent session, auto refresh, sign out. */
public final class AuthService {
    private static final AuthService INSTANCE = new AuthService();
    public static AuthService get() { return INSTANCE; }

    private final SupabaseClient api = SupabaseClient.get();
    private final SessionStore store = new SessionStore();
    private HostServices hostServices;

    private String accessToken, refreshToken, userId;
    private long expiresAtEpoch;
    private Profile profile;
    private String pkceVerifier;
    private volatile CompletableFuture<Void> pendingLogin;
    private volatile Runnable onSessionLost = () -> {};

    private AuthService() { api.setTokenProvider(this::validToken); }

    public void init(HostServices hs) { this.hostServices = hs; }
    public void onSessionLost(Runnable r) { this.onSessionLost = r; }
    public Profile profile() { return profile; }
    /** DEV ONLY: skip Google sign-in and open the app as a fake user of the given role. Remove before release. */
    public void devForce(Role r) { profile = new Profile("dev-user", r, true, "Dev User", "", "", null, "verified"); }
    public String userId() { return userId; }
    public boolean signedIn() { return accessToken != null; }

    /* ---------- restore saved session (called during splash) ---------- */
    public enum Restore { NO_SESSION, NEEDS_ROLE, READY, OFFLINE }

    public Restore restore() {
        Optional<JSONObject> s = store.load();
        if (s.isEmpty()) return Restore.NO_SESSION;
        apply(s.get());
        try {
            if (Instant.now().getEpochSecond() >= expiresAtEpoch - 60) refresh();
            return loadProfile() ? Restore.READY : Restore.NEEDS_ROLE;
        } catch (SupabaseClient.ApiException e) {
            if (e.status == 400 || e.status == 401 || e.status == 403) { clearLocal(); return Restore.NO_SESSION; }
            return Restore.OFFLINE;
        } catch (Exception netDown) {
            return profile != null ? Restore.OFFLINE : Restore.OFFLINE;
        }
    }

    /* ---------- Google OAuth (PKCE) ---------- */
    public CompletableFuture<Void> signInWithGoogle() {
        pkceVerifier = Pkce.verifier();
        String url = AppConfig.supabaseUrl() + "/auth/v1/authorize?provider=google"
                + "&redirect_to=" + enc(AppConfig.redirectUri())
                + "&code_challenge=" + Pkce.challenge(pkceVerifier) + "&code_challenge_method=s256";
        pendingLogin = new CompletableFuture<>();
        if (!AppConfig.isAndroid() && !startLoopback()) return pendingLogin;   // already failed with a clear message; don't open the browser
        hostServices.showDocument(url);
        return pendingLogin;
    }

    /** Android: call this with the bofo://auth-callback?code=... URI delivered by the intent filter. */
    public void handleDeepLink(String uri) {
        try {
            Map<String, String> q = parseQuery(URI.create(uri).getRawQuery());
            String code = q.get("code");
            if (code == null) throw new IllegalStateException(q.getOrDefault("error_description", "No code in callback"));
            JSONObject r = api.authPost("token?grant_type=pkce",
                    new JSONObject().put("auth_code", code).put("code_verifier", pkceVerifier));
            apply(r); store.save(r);
            if (pendingLogin != null) pendingLogin.complete(null);
        } catch (Exception e) { if (pendingLogin != null) pendingLogin.completeExceptionally(e); }
    }

    private HttpServer loopback;     // the single desktop callback listener (port 54321)

    /** Starts (or restarts) the desktop callback listener. Any listener left over from an earlier click is stopped first,
     *  so a second "Continue with Google" never fails with "address already in use". Returns false if it could not start. */
    private synchronized boolean startLoopback() {
        if (loopback != null) { try { loopback.stop(0); } catch (Exception ignored) { } loopback = null; }
        try {
            HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 54321), 0);
            srv.createContext("/callback", ex -> {
                handleDeepLink("http://localhost/callback?" + ex.getRequestURI().getRawQuery());
                byte[] b = "<html><body style='font-family:sans-serif'><h3>Signed in. Return to BOFO.</h3></body></html>".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "text/html");
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b); ex.close();
                srv.stop(1);
                synchronized (AuthService.this) { if (loopback == srv) loopback = null; }
            });
            srv.start();
            loopback = srv;
            return true;
        } catch (Exception e) {
            if (pendingLogin != null) pendingLogin.completeExceptionally(new IllegalStateException("Port 54321 is busy. Close other BOFO windows and try again.", e));
            return false;
        }
    }

    /* ---------- DEV ONLY: login bypass ---------- */
    /** Pretends a user is signed in (no Google, no token). Requests go out with the anon key, so RLS-protected writes may fail. */
    public void devLogin(Role role) {
        userId = "00000000-0000-0000-0000-0000000de000";
        profile = new Profile(userId, role, true, "Dev Tester (" + role.db() + ")", "", "", null, "verified");
    }

    /** DEV ONLY: real Supabase sign-in with a test account (email + password from config.local.properties).
     *  Unlike {@link #devLogin}, this produces a genuine JWT, so RLS, chat, favorites, listings and storage all work over the internet. */
    public void devSignIn(Role role) throws Exception {
        String email = AppConfig.devEmail(role.db()), pw = AppConfig.devPassword(role.db());
        if (email.isEmpty() || pw.isEmpty())
            throw new IllegalStateException("No dev." + role.db() + ".email / .password in config.local.properties");
        JSONObject r = api.authPost("token?grant_type=password", new JSONObject().put("email", email).put("password", pw));
        apply(r); store.save(r);
        if (!loadProfile()) throw new IllegalStateException("Test account has no row in public.users");
    }

    /* ---------- profile / role ---------- */
    /** @return true when a users row exists (role already chosen). */
    public boolean loadProfile() throws Exception {
        JSONArray rows = api.select("profiles_v", "id=eq." + userId + "&select=*");
        if (rows.isEmpty()) { profile = null; return false; }
        profile = Profile.from(rows.getJSONObject(0));
        return true;
    }

    public void createProfile(Role role, String fullName, String photoUrl, String termsV, String privV) throws Exception {
        if (role == Role.ADMIN) throw new IllegalArgumentException("Admin cannot be self-assigned");
        api.insert("users", new JSONObject().put("id", userId).put("role", role.db())
                .put("auth_provider", "google").put("full_name", fullName).put("photo_url", photoUrl));
        api.insert("user_agreements", new JSONObject().put("user_id", userId).put("document_type", "terms").put("document_version", termsV));
        api.insert("user_agreements", new JSONObject().put("user_id", userId).put("document_type", "privacy").put("document_version", privV));
        loadProfile();
    }

    public String googleName() { return meta("full_name", meta("name", "")); }
    public String googlePhoto() { return meta("avatar_url", meta("picture", "")); }
    private JSONObject user;
    private String meta(String k, String d) { return user == null ? d : user.optJSONObject("user_metadata") == null ? d : user.getJSONObject("user_metadata").optString(k, d); }

    /* ---------- tokens ---------- */
    private void apply(JSONObject s) {
        accessToken = s.getString("access_token");
        refreshToken = s.getString("refresh_token");
        expiresAtEpoch = s.has("expires_at") ? s.getLong("expires_at") : Instant.now().getEpochSecond() + s.optLong("expires_in", 3600);
        s.put("expires_at", expiresAtEpoch);
        if (s.has("user")) { user = s.getJSONObject("user"); userId = user.getString("id"); }
        else if (userId == null) userId = s.optString("user_id", null);
    }

    private synchronized void refresh() throws Exception {
        JSONObject r = api.authPost("token?grant_type=refresh_token", new JSONObject().put("refresh_token", refreshToken));
        apply(r); store.save(r);
    }

    private synchronized String validToken() {
        if (accessToken == null) return null;
        try {
            if (Instant.now().getEpochSecond() >= expiresAtEpoch - 60) refresh();
        } catch (SupabaseClient.ApiException e) {
            if (e.status == 400 || e.status == 401 || e.status == 403) { clearLocal(); onSessionLost.run(); return null; }
        } catch (Exception offline) { /* keep stale token; calls will fail gracefully */ }
        return accessToken;
    }

    public void signOut() { api.authLogout(); clearLocal(); }
    public void clearLocal() { store.clear(); accessToken = refreshToken = userId = null; profile = null; user = null; }

    /* ---------- helpers ---------- */
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static Map<String, String> parseQuery(String q) {
        Map<String, String> m = new HashMap<>();
        if (q == null) return m;
        for (String p : q.split("&")) { int i = p.indexOf('='); if (i > 0) m.put(p.substring(0, i), URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8)); }
        return m;
    }
}
