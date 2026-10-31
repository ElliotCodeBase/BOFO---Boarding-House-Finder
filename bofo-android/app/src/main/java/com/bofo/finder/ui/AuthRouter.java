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

    /** A bofo://auth-callback link that arrived before the screens were ready (cold start). The splash picks it up. */
    private static volatile String pendingLink;
    public static void setPendingLink(String uri) { pendingLink = uri; }

    /** Signed out -> login, no profile row -> role, no name -> setup, else the shell. A dead session (401) signs out. */
    public static void route(NavController nav, Consumer<Throwable> onFail) {
        Async.run(() -> {
            AuthService a = AuthService.get();
            if (!a.restore()) return R.id.action_global_login;
            try {
                Profile p = new ProfileService().fetchMe();
                if (p == null) return R.id.action_global_role;
                role = p.role();
                if (!ProfileService.isComplete(p)) return R.id.action_global_setup;
                return R.id.action_global_shell;
            } catch (SupabaseClient.ApiException e) {
                if (e.status == 401) { a.signOut(); return R.id.action_global_login; }
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
