package com.bofo.finder.ui;

import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.model.Profile;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.ProfileService;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.ErrorText;

import java.util.function.Consumer;

/** Decides where the user goes: login, role chooser, profile setup or the app. Used by the splash and after every sign-in. */
public final class AuthRouter {
    private AuthRouter() {}

    /** Role of the signed-in user, set by {@link #route} (the shell picks its tabs from it). */
    public static volatile com.bofo.finder.model.Role role = com.bofo.finder.model.Role.STUDENT;

    /** v2.0.120: the role the server gave this account. {@link #role} is what the screens show; an admin can view the app as student or owner (Settings), this never changes. */
    public static volatile com.bofo.finder.model.Role realRole = com.bofo.finder.model.Role.STUDENT;

    /** v2.1.8: set by {@link #route} when the account is suspended (shown on the suspended screen). */
    public static volatile String suspendedUntil = "";
    public static volatile String suspendedReason = "";

    /** v2.1.9: set by {@link #route} when the account is banned (shown on the banned screen). */
    public static volatile String bannedReason = "";

    /** v2.0.126: forget the previous account's role (sign out, delete, no session), so the next account never opens with the old owner / admin tabs. */
    public static void reset() {
        role = com.bofo.finder.model.Role.STUDENT;
        realRole = com.bofo.finder.model.Role.STUDENT;
        mfaPrompt = false;
        suspendedUntil = "";
        suspendedReason = "";
        bannedReason = "";
    }

    /** v2.0.119: true when the shell should open the admin two-step code sheet once. */
    private static volatile boolean mfaPrompt;
    static boolean takeMfaPrompt() { boolean p = mfaPrompt; mfaPrompt = false; return p; }

    /** A bofo://auth-callback link that arrived before the screens were ready (cold start). The splash picks it up. */
    private static volatile String pendingLink;
    public static void setPendingLink(String uri) { pendingLink = uri; }

    /** Signed out -> login, no profile row -> role, no name -> setup, else the shell. A dead session (401) signs out. */
    public static void route(NavController nav, Consumer<Throwable> onFail) {
        Async.run(() -> {
            AuthService a = AuthService.get();
            if (!a.restore()) { reset(); return R.id.action_global_login; }
            try {
                Profile p = new ProfileService().fetchMe();
                if (p == null) return R.id.action_global_role;
                role = p.role();
                realRole = role;
                // v2.1.8: a suspended account sees only the suspended screen until the end time passes. Fails open: if the check itself
                // fails (offline, migration 022 not applied) the user is routed as before; the database still blocks writes.
                if (role != com.bofo.finder.model.Role.ADMIN) {
                    try {
                        org.json.JSONObject sus = SupabaseClient.get().rpc("my_suspension", new org.json.JSONObject());
                        // v2.1.9: a banned account sees only the banned screen (sign out is the one thing it can do)
                        if (sus.optBoolean("banned", false)) {
                            bannedReason = sus.optString("reason", "");
                            return R.id.action_global_banned;
                        }
                        if (sus.optBoolean("suspended", false)) {
                            suspendedUntil = sus.optString("until", "");
                            suspendedReason = sus.optString("reason", "");
                            return R.id.action_global_suspended;
                        }
                    } catch (Exception ex) {
                        if (ex instanceof SupabaseClient.ApiException api && api.status == 401) throw api;
                    }
                }
                // v2.0.119: an admin with an authenticator who has not typed the code in this sign-in gets the code sheet when the shell opens
                if (role == com.bofo.finder.model.Role.ADMIN) {
                    try { mfaPrompt = !a.mfaVerifiedFactor().isEmpty() && !"aal2".equals(a.aal()); } catch (Exception ignored) { mfaPrompt = false; }
                }
                if (!ProfileService.isComplete(p)) return R.id.action_global_setup;
                return R.id.action_global_shell;
            } catch (SupabaseClient.ApiException e) {
                if (e.status == 401) { a.signOut(); reset(); return R.id.action_global_login; }
                throw e;
            }
        }, id -> go(nav, id), onFail);
    }

    /** If a sign-in link is waiting, finishes it (exchange the code, then route). Returns false when there was none. */
    public static boolean consumeLink(Context ctx, NavController nav, Consumer<Throwable> onFail) {
        final String link = pendingLink;
        if (link == null) return false;
        pendingLink = null;
        Async.run(() -> { AuthService.get().handleCallback(link); return Boolean.TRUE; },
                ok -> route(nav, onFail),
                e -> {
                    Toast.makeText(ctx, ErrorText.full("Google sign-in failed", e), Toast.LENGTH_LONG).show();
                    if (onFail != null && isSplash(nav)) onFail.accept(e);
                });
        return true;
    }

    public static NavController navOf(AppCompatActivity act) {
        NavHostFragment host = (NavHostFragment) act.getSupportFragmentManager().findFragmentById(R.id.nav_host);
        return host == null ? null : host.getNavController();
    }

    public static boolean isSplash(NavController nav) {
        NavDestination d = nav.getCurrentDestination();
        return d != null && d.getId() == R.id.splashFragment;
    }

    /** Navigates with a global action; every global action clears the back stack (Back must never return to login or setup). */
    public static void go(NavController nav, int actionId) {
        if (nav.getCurrentDestination() == null) return;
        nav.navigate(actionId);
    }
}
