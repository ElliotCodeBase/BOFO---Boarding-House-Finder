package com.bofo.finder.util;

import com.bofo.finder.config.AppConfig;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Single source of truth for "are we online?". The HTTP layer reports every success / network failure here,
 * so the offline banner and the "No internet connection" screens switch on automatically from any screen.
 * While offline, a tiny probe runs every few seconds and flips the app back online as soon as the server answers
 * (screens waiting on a retry then reload by themselves).
 */
public final class Connectivity {
    public static final BooleanProperty OFFLINE = new SimpleBooleanProperty(false);   // read/bind on the FX thread
    private static volatile boolean offlineFlag = false;                               // thread-safe mirror for background threads
    private static final HttpClient PROBE = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private static ScheduledExecutorService probeExec;
    private static ScheduledFuture<?> probeJob;

    static {
        OFFLINE.addListener((o, was, now) -> { offlineFlag = now; if (now) startProbe(); else stopProbe(); });
    }
    private Connectivity() {}

    /** Called by the HTTP layer: true = the server answered (any status), false = no network. Safe from any thread. */
    public static void report(boolean online) {
        if (online != offlineFlag) return;                 // already in that state
        try { Platform.runLater(() -> OFFLINE.set(!online)); } catch (IllegalStateException ignored) { /* FX not running (tests) */ }
    }

    /** True when the failure is "no network / timeout" rather than a server or logic error. */
    public static boolean isNetworkError(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.io.IOException || t instanceof java.nio.channels.UnresolvedAddressException) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }

    private static synchronized void startProbe() {
        if (probeJob != null && !probeJob.isDone()) return;
        if (probeExec == null) probeExec = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "bofo-net"); t.setDaemon(true); return t; });
        probeJob = probeExec.scheduleWithFixedDelay(Connectivity::probe, 4, 4, TimeUnit.SECONDS);
    }

    private static synchronized void stopProbe() {
        if (probeJob != null) { probeJob.cancel(false); probeJob = null; }
    }

    private static void probe() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(AppConfig.supabaseUrl() + "/auth/v1/health"))
                    .timeout(Duration.ofSeconds(4)).header("apikey", AppConfig.anonKey()).GET().build();
            PROBE.send(req, HttpResponse.BodyHandlers.discarding());
            report(true);                                   // any HTTP answer = the network is back
        } catch (Exception stillOffline) { /* try again next tick */ }
    }
}
