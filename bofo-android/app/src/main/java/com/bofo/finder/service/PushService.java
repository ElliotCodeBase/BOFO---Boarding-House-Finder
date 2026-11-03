package com.bofo.finder.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import com.bofo.finder.BuildConfig;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.AppPrefs;
import com.bofo.finder.util.PushRules;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

/**
 * v2.3.0: ties this phone's Firebase (FCM) token to the signed-in account on the server (RPC register_push_token), and removes it again at
 * sign-out or when Settings > Notifications is switched off. Blocking calls (the *Async ones are safe from any thread).
 * No google-services.json = Firebase is not set up = every method here quietly does nothing and the app works as before.
 * Admin-only reports: the server decides by users.role; the role kept here ("role") is only the phone's second lock (see PushRules.allowedFor).
 */
public final class PushService {
    private PushService() {}

    // Own file (NOT AppPrefs): the token and role must survive AppPrefs.clear(), which sign-out calls after we already cleaned up.
    private static final String PREFS = "bofo_push";
    private static final String K_TOKEN = "token", K_UID = "uid", K_AT = "at", K_ROLE = "role", K_ASKED = "asked_permission";

    private static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static boolean firebaseReady(Context c) {
        try { return !FirebaseApp.getApps(c.getApplicationContext()).isEmpty(); } catch (Throwable t) { return false; }
    }

    /** Role of the account signed in on this phone as the SERVER knows it ("student", "owner", "admin"), or "" when nobody / not yet synced. */
    public static String role(Context c) { return sp(c).getString(K_ROLE, ""); }

    /** The system permission prompt (Android 13+) is shown once per install. */
    public static boolean askedPermission(Context c) { return sp(c).getBoolean(K_ASKED, false); }
    public static void markAskedPermission(Context c) { sp(c).edit().putBoolean(K_ASKED, true).apply(); }

    /**
     * After sign-in / when the shell opens / when the switch goes on / when the system gives a new token: make sure the server knows this phone.
     * realRole = users.role of the signed-in account (an admin who views the app as a student is still "admin").
     */
    public static void syncAsync(Context ctx, String realRole) {
        final Context app = ctx.getApplicationContext();
        Async.bg(() -> {
            try { sync(app, realRole); } catch (Throwable t) { Log.w("BOFO", "push sync failed", t); }
        });
    }

    private static void sync(Context app, String realRole) throws Exception {
        if (!firebaseReady(app) || !AuthService.get().hasSession() || !AppPrefs.pushEnabled(app)) return;
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return;       // permission refused: nothing could be shown anyway
        String uid = AuthService.get().userId();
        String token = Tasks.await(FirebaseMessaging.getInstance().getToken());
        SharedPreferences p = sp(app);
        // role first: a push that arrives while we register is judged with the right role
        if (realRole != null && !realRole.isEmpty()) p.edit().putString(K_ROLE, realRole).apply();
        long now = System.currentTimeMillis();
        if (!PushRules.needsRegister(p.getString(K_UID, ""), p.getString(K_TOKEN, ""), p.getLong(K_AT, 0), uid, token, now)) return;
        SupabaseClient.get().rpc("register_push_token", new JSONObject().put("p_token", token).put("p_app_version", BuildConfig.VERSION_NAME));
        p.edit().putString(K_UID, uid).putString(K_TOKEN, token).putLong(K_AT, now).apply();
    }

    /** FirebaseMessagingService.onNewToken: the system rotated the token. Only matters while someone is signed in and the switch is on. */
    public static void onNewToken(Context ctx, String token) {
        final Context app = ctx.getApplicationContext();
        Async.bg(() -> {
            try {
                if (token == null || token.isEmpty() || !AuthService.get().hasSession() || !AppPrefs.pushEnabled(app)) return;
                String role = role(app);
                if (role.isEmpty()) return;                                              // not synced yet: the shell does it when it opens
                SupabaseClient.get().rpc("register_push_token", new JSONObject().put("p_token", token).put("p_app_version", BuildConfig.VERSION_NAME));
                sp(app).edit().putString(K_UID, AuthService.get().userId()).putString(K_TOKEN, token).putLong(K_AT, System.currentTimeMillis()).apply();
            } catch (Throwable t) { Log.w("BOFO", "push token update failed", t); }
        });
    }

    /** Settings > Notifications switched off. */
    public static void disableAsync(Context ctx) {
        final Context app = ctx.getApplicationContext();
        Async.bg(() -> unregisterBlocking(app));
    }

    /**
     * Sign-out: tell the server to forget this phone (needs the session, so call it BEFORE the session is cleared), then forget it here.
     * Even if the server call fails (offline), forgetLocal() deletes the FCM token on the phone, so the old token can never deliver again.
     */
    public static void unregisterBlocking(Context ctx) {
        final Context app = ctx.getApplicationContext();
        String token = sp(app).getString(K_TOKEN, "");
        try {
            if (!token.isEmpty() && AuthService.get().hasSession())
                SupabaseClient.get().rpc("unregister_push_token", new JSONObject().put("p_token", token));
        } catch (Throwable t) { Log.w("BOFO", "push unregister failed (token is still deleted on this phone)", t); }
        forgetLocal(app);
    }

    /** Wipes the saved token / role, drops the FCM token on this phone and removes the notifications already shown. Never throws. */
    public static void forgetLocal(Context ctx) {
        final Context app = ctx.getApplicationContext();
        try { sp(app).edit().remove(K_TOKEN).remove(K_UID).remove(K_AT).remove(K_ROLE).apply(); } catch (Throwable ignored) { }
        try { NotificationManagerCompat.from(app).cancelAll(); } catch (Throwable ignored) { }
        try { if (firebaseReady(app)) FirebaseMessaging.getInstance().deleteToken(); } catch (Throwable ignored) { }
    }
}
