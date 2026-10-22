package com.bofo.finder.app;

import com.bofo.finder.model.Role;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.ui.*;
import com.bofo.finder.util.Async;
import com.gluonhq.attach.statusbar.StatusBarService;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/** App entry. Flow: Splash (restore session) -> Login | Role | Home/Dashboard/Admin. */
public class Main extends Application {
    private static HostServices host;
    public static HostServices host() { return host; }

    @Override public void start(Stage stage) {
        host = getHostServices();
        AuthService auth = AuthService.get();
        auth.init(host);
        auth.onSessionLost(() -> Platform.runLater(() -> showLogin("Your session expired. Please sign in again.")));

        Scene scene = new Scene(Nav.root(), 360, 720);
        scene.getStylesheets().add(getClass().getResource("/com/bofo/finder/bofo.css").toExternalForm());
        stage.setScene(scene); stage.setTitle("BOFO Finder"); stage.show();
        try { StatusBarService.create().ifPresent(s -> s.setColor(Color.web("#4A2A8F"))); } catch (Throwable ignored) {}

        if (com.bofo.finder.config.AppConfig.devBypass()) {   // DEV ONLY: skip login entirely
            try { auth.devLogin(Role.valueOf(com.bofo.finder.config.AppConfig.devRole().toUpperCase())); }
            catch (IllegalArgumentException e) { auth.devLogin(Role.STUDENT); }
            route();
            return;
        }

        SplashView splash = new SplashView();
        Nav.show(splash, false);
        long t0 = System.currentTimeMillis();
        Async.run(auth::restore, result -> {
            long wait = Math.max(0, 1200 - (System.currentTimeMillis() - t0));   // splash shows at least ~1.2 s
            new Thread(() -> { try { Thread.sleep(wait); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> dispatch(result, splash)); }).start();
        }, e -> showLogin(null));
    }

    private static void dispatch(AuthService.Restore r, SplashView splash) {
        switch (r) {
            case NO_SESSION -> showLogin(null);
            case NEEDS_ROLE -> Nav.show(new RoleView(Main::route), false);
            case READY -> route();
            case OFFLINE -> {
                // Offline launch: with a cached profile open the app with an offline banner, else offer retry.
                if (AuthService.get().profile() != null) { State.offline.set(true); route(); }
                else { splash.setStatus("You're offline. Check your connection and reopen the app."); }
            }
        }
    }

    /** Sign-in screen; on success decide between role selection and the correct home. */
    public static void showLogin(String notice) {
        Nav.show(new LoginView(() -> Async.run(() -> AuthService.get().loadProfile(),
                hasProfile -> { if (hasProfile) route(); else Nav.show(new RoleView(Main::route), false); },
                e -> showLogin("Could not load your account.")), notice), false);
    }

    /** Returning users skip sign-in: students -> Home, owners -> Dashboard, admins -> Verification queue. */
    public static void route() {
        var p = AuthService.get().profile();
        if (p == null) { showLogin(null); return; }
        if (p.defaultCollegeId() != null && State.college.get() == null)
            Async.run(() -> new BhService().colleges(), cs -> cs.stream().filter(c -> c.id().equals(p.defaultCollegeId())).findFirst().ifPresent(State.college::set), e -> {});
        Nav.show(home(), true);
    }

    public static Node home() {
        Role r = AuthService.get().profile().role();
        return switch (r) { case OWNER -> new OwnerShell(); case ADMIN -> new AdminShell(); default -> new StudentShell(); };
    }

    public static void signOutToLogin() {
        Async.bg(() -> AuthService.get().signOut());
        State.college.set(null);
        com.bofo.finder.util.Cache.clear();
        showLogin(null);
    }

    public static void main(String[] args) { launch(args); }
}
