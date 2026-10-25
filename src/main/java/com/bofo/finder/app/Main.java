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
    private static Stage window;
    public static HostServices host() { return host; }
    /** Brings the app window back to the front (after signing in through the browser on a PC). */
    public static void focusApp() { if (window != null) { window.setIconified(false); window.toFront(); window.requestFocus(); } }

    @Override public void start(Stage stage) {
        com.bofo.finder.ui.AppStorage.init();                       // device storage first: profile / chats / saved places are kept there for offline use
        host = getHostServices();
        com.bofo.finder.util.Router.warmUp();                       // open the route server connection early so the first walking route is faster
        window = stage;
        AuthService auth = AuthService.get();
        auth.init(host);
        com.bofo.finder.util.Connectivity.OFFLINE.addListener((o, was, now) -> { if (!now) com.bofo.finder.util.Outbox.flushAsync(); });   // back online: send queued chat messages
        com.bofo.finder.util.Outbox.LISTENERS.add(new com.bofo.finder.util.Outbox.Listener() {
            @Override public void sent(String id, com.bofo.finder.model.Message m) {}
            @Override public void waiting(String id) {}
            @Override public void failed(String id, Throwable why) { Platform.runLater(() -> Nav.toast("A message written offline could not be sent")); }
        });
        auth.onSessionLost(() -> Platform.runLater(() -> showLogin("Your session expired. Please sign in again.")));

        double w = 360, h = 720;                                     // phone-sized by default on desktop
        for (String a : getParameters().getRaw())                    // preview other devices: --size=820x1180
            if (a.startsWith("--size=")) try { String[] p = a.substring(7).toLowerCase().split("x"); w = Double.parseDouble(p[0]); h = Double.parseDouble(p[1]); } catch (Exception ignored) {}
        Scene scene = new Scene(Nav.root(), w, h);
        stage.setMinWidth(320); stage.setMinHeight(480);
        scene.getStylesheets().add(getClass().getResource("/com/bofo/finder/bofo.css").toExternalForm());
        stage.setScene(scene); stage.setTitle("BOFO Finder"); stage.show();
        try { StatusBarService.create().ifPresent(s -> s.setColor(Color.web("#17142A"))); } catch (Throwable ignored) {}

        boot();
    }

    /** Splash + session restore. Also what "Try again" runs on the no-internet screen. */
    private static void boot() {
        AuthService auth = AuthService.get();
        SplashView splash = new SplashView();
        Nav.show(splash, false);
        long t0 = System.currentTimeMillis();
        Async.run(auth::restore, result -> {
            long wait = Math.max(0, 2200 - (System.currentTimeMillis() - t0));   // splash shows at least ~2.2 s so the mascot jump is seen
            new Thread(() -> { try { Thread.sleep(wait); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> dispatch(result, splash)); }).start();
        }, e -> {
            if (com.bofo.finder.util.Connectivity.isNetworkError(e)) showNoInternet(e); else showLogin(null);
        });
    }

    /** Full-screen "No internet connection": Try again button, and it reopens the app by itself when the network returns. */
    private static void showNoInternet() { showNoInternet(new java.net.ConnectException("offline")); }
    private static void showNoInternet(Throwable why) {
        Nav.show(new javafx.scene.layout.StackPane(Ui.errorState(why, Main::boot)), false);
    }

    private static void dispatch(AuthService.Restore r, SplashView splash) {
        switch (r) {
            case NO_SESSION -> showLogin(null);
            case NEEDS_ROLE -> Nav.show(new RoleView(Main::route), false);
            case READY -> route();
            case OFFLINE -> {
                // Offline launch: with a cached profile open the app with an offline banner, else offer retry.
                if (AuthService.get().profile() != null) { State.offline.set(true); route(); }
                else showNoInternet(AuthService.get().lastError() != null ? AuthService.get().lastError() : new java.net.ConnectException("offline"));
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
        com.bofo.finder.util.Outbox.flushAsync();                    // messages left in the queue from last time
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
