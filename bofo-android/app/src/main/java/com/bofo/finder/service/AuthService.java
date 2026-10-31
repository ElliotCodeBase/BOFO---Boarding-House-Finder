package com.bofo.finder.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.bofo.finder.config.AppConfig;
import com.bofo.finder.util.AuthLinks;
import com.bofo.finder.util.Cache;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.DiskCache;
import com.bofo.finder.util.Outbox;
import com.bofo.finder.util.Pkce;
import com.bofo.finder.util.Url;

import org.json.JSONObject;

import java.util.Map;
import java.util.Optional;

/**
 * Sign-in state for the whole app. Blocking calls: run them through Async.
 * Supports: email code (OTP), email + password, Google native id token (Credential Manager) and Google in the browser (PKCE,
 * returns through bofo://auth-callback). The session (access + refresh token) is kept encrypted by {@link SessionStore}.
 * It also feeds {@link SupabaseClient} (token provider, auto refresh) and {@link Outbox#userId}.
 */
public final class AuthService {
    private static volatile AuthService instance;

    /** Call once from BofoApp.onCreate(). */
    public static synchronized void init(Context app) {
        if (instance == null) instance = new AuthService(app.getApplicationContext());
    }
    public static AuthService get() {
        AuthService a = instance;
        if (a == null) throw new IllegalStateException("AuthService.init(context) was not called");
        return a;
    }

    /** Thrown when sign-up worked but the project wants the e-mail confirmed before a session exists. */
    public static class ConfirmEmailException extends Exception {
        public ConfirmEmailException() { super("Check your e-mail and open the confirmation link, then sign in."); }
    }

    private static final String PREFS = "bofo_auth";
    private static final String K_VERIFIER = "pkce_verifier";
    private static final String K_PROVIDER = "provider";

    private final SessionStore store;
    private final SharedPreferences prefs;

    private volatile String access, refresh, userId, email, provider = "email", metaName, metaPhoto;
    private volatile long expiresAt;          // epoch seconds

    private AuthService(Context ctx) {
        store = new SessionStore(ctx);
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SupabaseClient.get().setTokenProvider(this::token);
        Outbox.userId = this::userId;
        Optional<JSONObject> s = store.load();
        if (s.isPresent()) apply(s.get(), false);
    }

    /* ---------- state ---------- */
    public boolean hasSession() { return refresh != null && userId != null; }
    public String userId() { return userId; }
    public String email() { return email == null ? "" : email; }
    /** "google" or "email": goes into users.auth_provider. */
    public String provider() { return provider == null ? "email" : provider; }
    /** Name / photo that Google (or the sign-up form) put into the account: used to prefill the profile form. */
    public String metaName() { return metaName == null ? "" : metaName; }
    public String metaPhoto() { return metaPhoto == null ? "" : metaPhoto; }

    /** Valid access token (refreshed when it is about to expire), or null when signed out. May block for one refresh call. */
    public String token() {
        if (access == null) return null;
        if (expiresAt - now() < 60 && refresh != null) {
            try { refreshNow(); } catch (Exception ignored) { /* offline: keep using the old token, the server will say 401 if it is really dead */ }
        }
        return access;
    }

    private static long now() { return System.currentTimeMillis() / 1000; }

    /* ---------- restore / refresh ---------- */
    /**
     * Splash: true when a usable session exists. Offline with a saved session still counts as signed in.
     * A refresh token the server refuses (revoked, user deleted) signs the user out.
     */
    public synchronized boolean restore() {
        if (!hasSession()) return false;
        if (expiresAt - now() >= 60) return true;
        try { refreshNow(); return true; }
        catch (SupabaseClient.ApiException e) { if (e.status == 400 || e.status == 401 || e.status == 403) { clearLocal(); return false; } return true; }
        catch (Exception e) { return true; }
    }

    private synchronized void refreshNow() throws Exception {
        if (expiresAt - now() >= 60) return;      // another thread did it while we waited for the lock
        JSONObject r = SupabaseClient.get().authPost("token?grant_type=refresh_token", new JSONObject().put("refresh_token", refresh));
        apply(r, true);
    }

    /* ---------- sign in ---------- */
    public void sendEmailCode(String mail) throws Exception {
        SupabaseClient.get().authPost("otp", new JSONObject().put("email", mail.trim()).put("create_user", true));
    }

    public void verifyEmailCode(String mail, String code) throws Exception {
        JSONObject r = SupabaseClient.get().authPost("verify", new JSONObject().put("type", "email").put("email", mail.trim()).put("token", code.trim()));
        provider = "email";
        apply(r, true);
    }

    public void signInPassword(String mail, String password) throws Exception {
        JSONObject r = SupabaseClient.get().authPost("token?grant_type=password", new JSONObject().put("email", mail.trim()).put("password", password));
        provider = "email";
        apply(r, true);
    }

    /** DEBUG builds only (the "force login" buttons): real password sign-in with the test account of a role. It is a genuine session
     *  (real JWT), so RLS, favorites, chat, listings and storage all work, exactly like the JavaFX dev buttons. */
    public void devSignIn(com.bofo.finder.model.Role role) throws Exception {
        String r = role.db(), mail = AppConfig.devEmail(r), pw = AppConfig.devPassword(r);
        if (mail.isEmpty() || pw.isEmpty())
            throw new IllegalStateException("No dev." + r + ".email / dev." + r + ".password in src/main/resources/com/bofo/finder/config.local.properties (then Sync Gradle and run a DEBUG build).");
        signInPassword(mail, pw);
    }

    /** Creates the account. Throws {@link ConfirmEmailException} when the project requires e-mail confirmation first. */
    public void signUpPassword(String mail, String password) throws Exception {
        JSONObject r = SupabaseClient.get().authPost("signup", new JSONObject().put("email", mail.trim()).put("password", password));
        if (!r.has("access_token")) throw new ConfirmEmailException();
        provider = "email";
        apply(r, true);
    }

    /** Google ID token from the Credential Manager (native sheet). */
    public void signInGoogleIdToken(String idToken) throws Exception {
        JSONObject r = SupabaseClient.get().authPost("token?grant_type=id_token", new JSONObject().put("provider", "google").put("id_token", idToken));
        provider = "google";
        apply(r, true);
    }

    /** Google in the browser: returns the URL to open and remembers the PKCE verifier (it survives the app being killed meanwhile). */
    public String beginBrowserGoogle() {
        String verifier = Pkce.verifier();
        prefs.edit().putString(K_VERIFIER, verifier).apply();
        return AppConfig.supabaseUrl() + "/auth/v1/authorize?provider=google"
                + "&redirect_to=" + Url.encode(AppConfig.redirectUri())
                + "&code_challenge=" + Url.encode(Pkce.challenge(verifier))
                + "&code_challenge_method=s256";
    }

    /** Finishes the browser sign-in from bofo://auth-callback?code=... Throws with a readable message when the link carries an error. */
    public void handleCallback(String uri) throws Exception {
        Map<String, String> p = AuthLinks.params(uri);
        String err = p.containsKey("error_description") ? p.get("error_description") : p.get("error");
        if (err != null && !err.isEmpty()) throw new IllegalStateException(err);
        String code = p.get("code");
        String verifier = prefs.getString(K_VERIFIER, null);
        if (code == null || code.isEmpty()) throw new IllegalStateException("The sign-in link had no code. Start the sign-in again.");
        if (verifier == null) throw new IllegalStateException("The sign-in was started on another run of the app. Start it again.");
        JSONObject r = SupabaseClient.get().authPost("token?grant_type=pkce", new JSONObject().put("auth_code", code).put("code_verifier", verifier));
        prefs.edit().remove(K_VERIFIER).apply();
        provider = "google";
        apply(r, true);
    }

    /* ---------- sign out / delete ---------- */
    public void signOut() {
        if (access != null) SupabaseClient.get().authLogout();
        clearLocal();
    }

    /** Deletes the account on the server (RPC delete_my_account removes the auth user, the rest cascades), then clears the device. */
    public void deleteAccount() throws Exception {
        SupabaseClient.get().rpc("delete_my_account", new JSONObject());
        clearLocal();
    }

    private synchronized void clearLocal() {
        access = null; refresh = null; userId = null; email = null; metaName = null; metaPhoto = null; expiresAt = 0;
        store.clear();
        prefs.edit().clear().apply();
        Cache.clear();
        DiskCache.deletePrefix("user_");          // saved offline copies belong to the previous user
    }

    /* ---------- internals ---------- */
    private synchronized void apply(JSONObject r, boolean persist) {
        String a = r.optString("access_token", "");
        String rf = r.optString("refresh_token", "");
        if (a.isEmpty() || rf.isEmpty()) throw new IllegalStateException("The server sent no session.");
        access = a; refresh = rf;
        long exp = r.optLong("expires_at", 0);
        expiresAt = exp > 0 ? exp : now() + r.optLong("expires_in", 3600);
        JSONObject u = r.optJSONObject("user");
        if (u != null) {
            userId = u.optString("id", userId);
            email = u.optString("email", email == null ? "" : email);
            JSONObject md = u.optJSONObject("user_metadata");
            if (md != null) {
                String n = md.optString("full_name", md.optString("name", ""));
                metaName = n;
                metaPhoto = md.optString("avatar_url", md.optString("picture", ""));
            }
            JSONObject am = u.optJSONObject("app_metadata");
            if (am != null && !am.optString("provider", "").isEmpty()) provider = am.optString("provider").equals("google") ? "google" : "email";
        } else if (r.has("id")) {                  // a refresh answer without "user" keeps the old values
            userId = r.optString("id");
        }
        if (persist) {
            try {
                JSONObject s = new JSONObject(r.toString());
                s.put("expires_at", expiresAt);
                if (u == null) s.put("user", new JSONObject().put("id", userId).put("email", email()));
                store.save(s);
            } catch (Exception e) { /* the session still works until the app closes */ }
        }
        Connectivity.report(true);
    }
}
