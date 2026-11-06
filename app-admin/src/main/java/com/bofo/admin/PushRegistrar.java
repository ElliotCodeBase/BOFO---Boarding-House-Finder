package com.bofo.admin;

import android.content.Context;
import android.content.SharedPreferences;

import com.bofo.shared.Api;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

/**
 * Ties this phone's FCM token to the signed-in staff account (RPC register_push_token with p_app = 'admin', migration 042) and removes it at sign-out.
 * Without google-services.json Firebase is not set up: every call here does nothing and the app works without push.
 * Blocking network calls: use Async (the Firebase token lookup itself is asynchronous).
 */
final class PushRegistrar {
    private PushRegistrar() {}
    private static final String PREFS = "admin_push", KEY = "token";

    private static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /** Fetch the current token and register it. Call after the role check says the account is active staff. Never throws. */
    static void registerNow(Context c) {
        final Context app = c.getApplicationContext();
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(t -> {
                if (t == null || t.isEmpty()) return;
                new Thread(() -> send(app, t)).start();
            });
        } catch (Throwable ignored) { /* Firebase not configured (no google-services.json) */ }
    }

    /** Called by the messaging service when Firebase rotates the token. */
    static void onNewToken(Context c, String token) {
        if (token == null || token.isEmpty() || AdminApp.access() == null) { if (token != null) prefs(c).edit().putString(KEY, token).apply(); return; }
        final Context app = c.getApplicationContext();
        new Thread(() -> send(app, token)).start();
    }

    private static void send(Context app, String token) {
        try {
            Api.get().rpc("register_push_token", new JSONObject().put("p_token", token).put("p_app_version", BuildConfig.VERSION_NAME).put("p_app", "admin"));
            prefs(app).edit().putString(KEY, token).apply();        // the token is not personal data, but it is only a hint for sign-out
        } catch (Exception ignored) { /* push is optional: the next sign-in tries again */ }
    }

    /** Remove this phone's token from the server (must run BEFORE the session is cleared, off the main thread). Never throws. */
    static void unregister(Context c) {
        try {
            String t = prefs(c).getString(KEY, null);
            if (t == null) return;
            Api.get().rpc("unregister_push_token", new JSONObject().put("p_token", t));
            prefs(c).edit().remove(KEY).apply();
        } catch (Throwable ignored) { }
    }
}
