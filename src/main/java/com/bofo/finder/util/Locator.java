package com.bofo.finder.util;

import com.bofo.finder.config.AppConfig;
import com.bofo.finder.ui.Nav;
import com.bofo.finder.ui.State;
import com.gluonhq.attach.position.Position;
import com.gluonhq.attach.position.PositionService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.util.Duration;

import com.gluonhq.attach.storage.StorageService;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Properties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * "Where am I?" for the whole app. start() asks for location permission (Android shows the system dialog) and begins
 * tracking; the newest fix is kept in {@link State#gps}. If nothing arrives in 12 s (permission denied, GPS off, or a
 * PC without location) the status becomes UNAVAILABLE so screens can show a hint instead of waiting forever.
 * On a PC you can pretend to be somewhere: add dev.location=10.3157,123.8854 to config.local.properties.
 * Call from the JavaFX thread.
 */
public final class Locator {
    public enum Status { IDLE, REQUESTING, ON, UNAVAILABLE }

    private static final Locator INSTANCE = new Locator();
    public static Locator get() { return INSTANCE; }

    public final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.IDLE);
    private final List<Consumer<double[]>> waiting = new ArrayList<>();
    /** The user's choice (Settings switch). Saved on the device. When false the app never asks the phone for a position. */
    public final BooleanProperty enabled = new SimpleBooleanProperty(true);
    private boolean asked;                       // has the "Allow location?" question been shown yet
    private PositionService service;
    private final File prefFile;
    private PauseTransition timeout;
    private boolean listening;
    private double[] manual;                     // "I am here" spot the user set by pressing and holding the map (wins over GPS until location is switched off and on)
    private static boolean approxHintShown;
    private boolean nextFixApprox;               // the fix that is about to arrive comes from a PC, not from phone GPS

    private Locator() {
        File base = StorageService.create().flatMap(StorageService::getPrivateStorage)
                .orElse(new File(System.getProperty("user.home"), ".bofo"));
        prefFile = new File(base, "location.properties");
        try (InputStream in = Files.newInputStream(prefFile.toPath())) {
            Properties p = new Properties(); p.load(in);
            enabled.set(!"false".equals(p.getProperty("enabled")));
            asked = "true".equals(p.getProperty("asked"));
            try { manual = new double[]{Double.parseDouble(p.getProperty("manualLat")), Double.parseDouble(p.getProperty("manualLng"))}; } catch (Exception none) { manual = null; }
        } catch (Exception firstRun) { /* defaults */ }
    }

    private void save() {
        try {
            prefFile.getParentFile().mkdirs();
            Properties p = new Properties();
            p.setProperty("enabled", String.valueOf(enabled.get()));
            p.setProperty("asked", String.valueOf(asked));
            if (manual != null) { p.setProperty("manualLat", String.valueOf(manual[0])); p.setProperty("manualLng", String.valueOf(manual[1])); }
            try (OutputStream out = Files.newOutputStream(prefFile.toPath())) { p.store(out, "BOFO location choice"); }
        } catch (Exception ignored) { }
    }

    private boolean prompting;

    /** Shows a yes/no dialog without blocking (showAndWait can throw while a screen is still being laid out). */
    private void ask(String title, String header, String text, String yesLabel, String noLabel, Consumer<Boolean> answer) {
        Platform.runLater(() -> {
            try { com.bofo.finder.ui.Popup.ask(com.bofo.finder.ui.Popup.Kind.QUESTION, header, text, yesLabel, noLabel, answer); }
            catch (Throwable dialogFailed) { answer.accept(true); }
        });
    }

    /** First time after sign-in: explains why BOFO wants the location and lets the user say yes or no.
     *  Afterwards just starts tracking if the Settings switch is on. */
    public void startWithPrompt() {
        if (!Platform.isFxApplicationThread()) { Platform.runLater(this::startWithPrompt); return; }
        if (asked) { start(); return; }
        if (prompting) return;
        prompting = true;
        ask("Allow location?", "Use your location",
                "BOFO uses your location to show boarding houses near you and to place the blue dot on the map. "
                + "It is only used while the app is open. You can change this any time in Settings.",
                "Allow", "Not now", yes -> { prompting = false; setEnabled(yes); });
    }

    /** The Settings switch. Off = stop tracking and forget the position. On = ask the phone and start again. */
    public void setEnabled(boolean on) {
        enabled.set(on);
        asked = true;
        if (on) manual = null;                               // switching location on again goes back to the automatic position
        save();
        if (on) { if (status.get() == Status.UNAVAILABLE) status.set(Status.IDLE); start(); }
        else stop();
    }

    private void stop() {
        if (timeout != null) timeout.stop();
        stopGeo();
        waiting.clear();
        try { if (service != null) service.stop(); } catch (Throwable ignored) { }
        State.gps.set(null);
        status.set(Status.IDLE);
    }

    /** Asks for permission and starts tracking. Safe to call many times. */
    public void start() {
        if (!Platform.isFxApplicationThread()) { Platform.runLater(this::start); return; }
        if (!enabled.get()) return;
        if (status.get() == Status.ON || status.get() == Status.REQUESTING) return;
        if (manual != null) { nextFixApprox = false; fix(manual); return; }
        double[] dev = AppConfig.devLocation();
        if (dev != null) { nextFixApprox = true; fix(dev); return; }
        status.set(Status.REQUESTING);
        if (timeout != null) timeout.stop();                 // an old timer must never fail a newer request
        try {
            Optional<PositionService> svc = PositionService.create();
            if (svc.isEmpty()) { nextFixApprox = true; desktopFix(); return; }        // PC: ask Windows, else the browser (coarse: Wi-Fi / IP based)
            nextFixApprox = false;
            PositionService ps = svc.get();
            service = ps;
            if (!listening) {
                listening = true;
                ps.positionProperty().addListener((o, a, p) -> { if (p != null && manual == null) fix(new double[]{p.getLatitude(), p.getLongitude()}); });
            }
            ps.start();
            Position now = ps.positionProperty().get();
            if (now != null) fix(new double[]{now.getLatitude(), now.getLongitude()});
            else {
                timeout = new PauseTransition(Duration.seconds(12));
                timeout.setOnFinished(e -> { if (status.get() == Status.REQUESTING) fail(); });
                timeout.play();
            }
        } catch (Throwable unsupportedOrDenied) { fail(); }
    }

    /* ---------- PC: ask Windows' own location service (the same one the browser uses) ---------- */
    private static final String WIN_SCRIPT =
            "Add-Type -AssemblyName System.Device; $w = New-Object System.Device.Location.GeoCoordinateWatcher([System.Device.Location.GeoPositionAccuracy]::High); $w.Start(); "
            + "for ($i = 0; $i -lt 30; $i++) { $q = $w.Position.Location; if (-not $q.IsUnknown -and $q.HorizontalAccuracy -le 150) { break }; Start-Sleep -Milliseconds 500 }; "
            + "$l = $w.Position.Location; if (-not $l.IsUnknown) { [string]::Format([cultureinfo]::InvariantCulture, '{0},{1}', $l.Latitude, $l.Longitude) }";

    private void desktopFix() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) { browserFix(); return; }
        Thread t = new Thread(() -> {
            double[] got = null;
            try {
                Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", WIN_SCRIPT)
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
                String[] a = out.split(",");
                if (a.length == 2) got = new double[]{Double.parseDouble(a[0].trim()), Double.parseDouble(a[1].trim())};
            } catch (Throwable ignored) { }
            double[] result = got;
            Platform.runLater(() -> { if (result != null) fix(result); else browserFix(); });   // Windows could not tell: try the browser
        }, "bofo-win-geo");
        t.setDaemon(true); t.start();
    }

    /* ---------- PC: the same Geolocation feature Google Maps uses (the browser asks Windows / Wi-Fi) ---------- */
    private HttpServer geo;
    private volatile String why;      // reason shown in the "can't find you" message

    private static final String GEO_PAGE = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>BOFO location</title></head>"
            + "<body style='font-family:sans-serif;text-align:center;padding:64px 24px;color:#17142A'>"
            + "<h2 id='t' style='color:#5B3BF0'>Finding your location...</h2><p id='p'>Click <b>Allow</b> when your browser asks.</p>"
            + "<script>navigator.geolocation.getCurrentPosition(function(p){"
            + "fetch('/fix?lat='+p.coords.latitude+'&lng='+p.coords.longitude).then(function(){"
            + "document.getElementById('t').textContent='Got it!';document.getElementById('p').textContent='You can close this tab and go back to BOFO.';"
            + "setTimeout(function(){window.close()},900);});},"
            + "function(e){fetch('/fail?code='+e.code);document.getElementById('t').textContent='Could not get your location';"
            + "document.getElementById('p').textContent='Allow location for this page in your browser (and in Windows Settings > Privacy > Location), then tap the location button in BOFO again.';},"
            + "{enableHighAccuracy:true,timeout:30000,maximumAge:60000});</script></body></html>";

    /** Opens a one-time page in the default browser that calls navigator.geolocation and sends the result back to a tiny local server. */
    private void browserFix() {
        if (timeout != null) timeout.stop();
        timeout = new PauseTransition(Duration.seconds(60));          // the user needs time to click Allow
        timeout.setOnFinished(e -> { if (status.get() == Status.REQUESTING) { stopGeo(); why = "The browser tab never answered. Click Allow in the tab that opened, then try again."; fail(); } });
        timeout.play();
        Thread t = new Thread(() -> {
            try {
                stopGeo();
                HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                srv.createContext("/", ex -> {
                    byte[] b = GEO_PAGE.getBytes(StandardCharsets.UTF_8);
                    ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                    ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b); ex.close();
                });
                srv.createContext("/fix", ex -> {
                    String q = ex.getRequestURI().getRawQuery() == null ? "" : ex.getRequestURI().getRawQuery();
                    double lat = Double.NaN, lng = Double.NaN;
                    for (String kv : q.split("&")) {
                        String[] p = kv.split("=");
                        try { if (p.length == 2 && p[0].equals("lat")) lat = Double.parseDouble(p[1]); if (p.length == 2 && p[0].equals("lng")) lng = Double.parseDouble(p[1]); }
                        catch (NumberFormatException ignored) { }
                    }
                    ex.sendResponseHeaders(200, -1); ex.close();
                    if (!Double.isNaN(lat) && !Double.isNaN(lng)) fix(new double[]{lat, lng});
                    stopGeo();
                });
                srv.createContext("/fail", ex -> {
                    String q = String.valueOf(ex.getRequestURI().getRawQuery());
                    why = q.contains("code=1") ? "Your browser blocked location for this page. Allow it, and turn on Windows Settings > Privacy & security > Location."
                        : q.contains("code=2") ? "Windows could not work out where you are. Turn on Windows Location and connect to Wi-Fi."
                        : q.contains("code=3") ? "Finding your location took too long. Try again." : "Could not get your location from the browser.";
                    ex.sendResponseHeaders(200, -1); ex.close();
                    Platform.runLater(() -> { if (status.get() == Status.REQUESTING) fail(); });
                    stopGeo();
                });
                srv.start(); geo = srv;
                openBrowser("http://127.0.0.1:" + srv.getAddress().getPort() + "/");
            } catch (Throwable e) { why = "Could not open your browser to find your location."; Platform.runLater(this::fail); }
        }, "bofo-geo");
        t.setDaemon(true); t.start();
    }

    private synchronized void stopGeo() {
        if (geo != null) { try { geo.stop(0); } catch (Throwable ignored) { } geo = null; }
    }

    private static void openBrowser(String url) {
        try {   // reflection keeps java.awt out of the Android build
            Class<?> d = Class.forName("java.awt.Desktop");
            d.getMethod("browse", URI.class).invoke(d.getMethod("getDesktop").invoke(null), URI.create(url));
            return;
        } catch (Throwable ignored) { }
        try { new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start(); } catch (Throwable ignored) { }
    }

    /** Runs {@code then} with the user's position as soon as it is known (immediately when already known). */
    public void locate(Consumer<double[]> then) {
        if (!enabled.get()) {
            ask("Location is off", "Turn on location?", "BOFO needs your location to show where you are. You can switch it off again in Settings.",
                    "Turn on", "Cancel", yes -> { if (yes) { setEnabled(true); locate(then); } });
            return;
        }
        double[] now = State.gps.get();
        if (now != null) {                                   // we already know where the user is: use it right away, never wait for a new fix
            then.accept(now);
            if (status.get() != Status.ON) start();          // and refresh quietly in the background
            return;
        }
        waiting.clear();                                     // only the newest tap counts (old ones used to fly the map several times)
        waiting.add(then);
        Nav.toast("Finding your location...");
        if (status.get() == Status.UNAVAILABLE) status.set(Status.IDLE);
        start();
    }

    private void fix(double[] p) {
        Platform.runLater(() -> {
            if (timeout != null) timeout.stop();
            State.gpsApprox.set(nextFixApprox);
            if (nextFixApprox && !approxHintShown) { approxHintShown = true; Nav.toast("This PC can only guess where you are. Press and hold the map to set your exact spot."); }
            State.gps.set(p);
            status.set(Status.ON);
            List<Consumer<double[]>> todo = new ArrayList<>(waiting); waiting.clear();
            for (Consumer<double[]> c : todo) c.accept(p);
        });
    }

    /** The user pressed and held the map and said "I am here": use that spot instead of the (possibly wrong) automatic one. */
    public void setManual(double lat, double lng) {
        if (!Platform.isFxApplicationThread()) { Platform.runLater(() -> setManual(lat, lng)); return; }
        manual = new double[]{lat, lng};
        if (!enabled.get()) { enabled.set(true); asked = true; }
        save();
        if (timeout != null) timeout.stop();
        stopGeo();
        nextFixApprox = false;
        fix(manual);
        Nav.toast("Your location is set.");
    }

    private void fail() {
        status.set(Status.UNAVAILABLE);
        if (!waiting.isEmpty()) {                            // keep the tap: if the fix arrives late (permission dialog) the map still flies there
            Nav.toast(why != null ? why : "Can't find you. Turn on Location and allow BOFO in your phone settings.");
            why = null;
        }
    }
}
