package com.bofo.finder.util;

import androidx.lifecycle.MutableLiveData;

import com.bofo.finder.config.AppConfig;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.Request;
import okhttp3.Response;

/**
 * Single source of truth for "are we online?". The HTTP layer reports every success / network failure here,
 * so the offline banner and the "No internet connection" screens switch on automatically from any screen.
 * While offline, a tiny probe runs every few seconds and flips the app back online as soon as the server answers.
 * Observe {@link #OFFLINE} from the UI (true = offline).
 */
public final class Connectivity {
    public static final MutableLiveData<Boolean> OFFLINE = new MutableLiveData<>(false);
    private static volatile boolean offlineFlag = false;            // thread-safe mirror for background threads
    private static ScheduledExecutorService probeExec;
    private static ScheduledFuture<?> probeJob;
    private Connectivity() {}

    public static boolean isOffline() { return offlineFlag; }

    /** Called by the HTTP layer: true = the server answered (any status), false = no network. Safe from any thread. */
    public static void report(boolean online) {
        if (online != offlineFlag) return;                          // already in that state
        offlineFlag = !online;
        try { OFFLINE.postValue(!online); } catch (Throwable notOnAndroid) { /* plain JVM unit tests: no main looper */ }
        if (offlineFlag) startProbe(); else stopProbe();
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
            Request req = new Request.Builder().url(AppConfig.supabaseUrl() + "/auth/v1/health")
                    .header("apikey", AppConfig.anonKey()).get().build();
            try (Response r = Http.client(4).newCall(req).execute()) { report(true); }   // any HTTP answer = the network is back
        } catch (Exception stillOffline) { /* try again next tick */ }
    }
}
