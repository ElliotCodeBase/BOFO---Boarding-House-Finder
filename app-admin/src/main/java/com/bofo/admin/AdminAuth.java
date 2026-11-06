package com.bofo.admin;

import android.content.Context;
import android.util.Base64;

import com.bofo.shared.Api;
import com.bofo.shared.SessionStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Sign-in state of the admin app. Google only. The session lives encrypted (Android Keystore, AES-GCM) in SessionStore. Blocking calls: use Async. */
public final class AdminAuth {
    private static volatile AdminAuth I;
    public static synchronized void init(Context c) { if (I == null) I = new AdminAuth(c.getApplicationContext()); }
    public static AdminAuth get() { return I; }

    private final SessionStore store;
    private volatile String access, refresh, userId, email;
    private volatile long expiresAt;

    private AdminAuth(Context c) {
        appCtx = c;
        store = new SessionStore(c);
        Api.get().setTokenProvider(this::token);
        Optional<JSONObject> s = store.load();
        if (s.isPresent()) { access = s.get().optString("a", null); refresh = s.get().optString("r", null); userId = s.get().optString("u", null);
            email = s.get().optString("e", ""); expiresAt = s.get().optLong("x", 0); }
    }

    public boolean hasSession() { return refresh != null && userId != null; }
    public String userId() { return userId; }
    public String email() { return email == null ? "" : email; }

    public String token() {
        if (access == null) return null;
        if (expiresAt - System.currentTimeMillis() / 1000 < 60 && refresh != null) { try { refreshNow(); } catch (Exception ignored) { } }
        return access;
    }

    private synchronized void refreshNow() throws Exception {
        JSONObject r = Api.get().authPost("token?grant_type=refresh_token", new JSONObject().put("refresh_token", refresh));
        apply(r);
    }

    private void apply(JSONObject s) throws org.json.JSONException {
        access = s.getString("access_token"); refresh = s.getString("refresh_token");
        expiresAt = System.currentTimeMillis() / 1000 + s.optLong("expires_in", 3600);
        JSONObject u = s.optJSONObject("user");
        if (u != null) { userId = u.optString("id", userId); email = u.optString("email", email); }
        try { store.save(new JSONObject().put("a", access).put("r", refresh).put("u", userId).put("e", email).put("x", expiresAt)); } catch (Exception ignored) { }
    }

    /** Google id token + the RAW nonce (Google got its SHA-256, Supabase checks it). */
    public void signInGoogle(String idToken, String rawNonce) throws Exception {
        apply(Api.get().authPost("token?grant_type=id_token", new JSONObject().put("provider", "google").put("id_token", idToken).put("nonce", rawNonce)));
    }

    private volatile Context appCtx;

    public void signOut() {
        AdminApp.setAccess(null);
        if (appCtx != null && access != null) PushRegistrar.unregister(appCtx);   // while the session still works; does nothing on the main thread (caught)
        try { Api.get().logout(); } catch (Exception ignored) { }
        access = refresh = userId = null; email = null; expiresAt = 0; store.clear();
    }

    /** "aal2" once the two-step code was entered in this session. Reads the JWT claim; the server still checks it. */
    public boolean isAal2() {
        try {
            String p = access.split("\\.")[1];
            return "aal2".equals(new JSONObject(new String(Base64.decode(p, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP), StandardCharsets.UTF_8)).optString("aal"));
        } catch (Exception e) { return false; }
    }

    /** Id of the verified authenticator-app factor, or null when this account has none. */
    public String totpFactorId() throws Exception {
        JSONArray f = new JSONObject(Api.get().authCall("GET", "user", null)).optJSONArray("factors");
        if (f != null) for (int i = 0; i < f.length(); i++) {
            JSONObject o = f.optJSONObject(i);
            if (o != null && "totp".equals(o.optString("factor_type")) && "verified".equals(o.optString("status"))) return o.optString("id");
        }
        return null;
    }

    public void verifyTotp(String factorId, String code) throws Exception {
        String ch = new JSONObject(Api.get().authCall("POST", "factors/" + factorId + "/challenge", new JSONObject())).getString("id");
        apply(new JSONObject(Api.get().authCall("POST", "factors/" + factorId + "/verify", new JSONObject().put("challenge_id", ch).put("code", code))));
    }
}
