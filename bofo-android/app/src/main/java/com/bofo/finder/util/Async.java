package com.bofo.finder.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/** Runs blocking work off the main thread, then delivers the result on the main thread (replaces the JavaFX Platform.runLater version). */
public final class Async {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "bofo-io"); t.setDaemon(true); return t;
    });
    private static volatile Handler main;
    private Async() {}

    public interface Work<T> { T run() throws Exception; }

    /** Posts to the main thread. */
    public static void post(Runnable r) {
        Handler h = main;
        if (h == null) { h = new Handler(Looper.getMainLooper()); main = h; }   // created lazily so plain JVM unit tests can load this class
        h.post(r);
    }

    public static <T> void run(Work<T> work, Consumer<T> ok, Consumer<Throwable> fail) {
        POOL.submit(() -> {
            try { T r = work.run(); post(() -> ok.accept(r)); }
            catch (Throwable e) { post(() -> { if (fail != null) fail.accept(e); }); }
        });
    }
    public static <T> void run(Work<T> work, Consumer<T> ok) { run(work, ok, e -> android.util.Log.w("BOFO", "Async failed", e)); }
    public static ExecutorService pool() { return POOL; }

    /**
     * For work that a task ALREADY running on {@link #pool()} starts and then waits for (e.g. "load the list + the featured places in parallel").
     * If those sub-tasks went into the same 6-thread pool, six waiting tasks would block every thread and the sub-tasks behind them
     * could never run: every screen then sat on its loading skeleton forever. This pool grows as needed, so a waiting task never starves its own sub-tasks.
     */
    private static final ExecutorService FANOUT = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "bofo-fanout"); t.setDaemon(true); return t;
    });
    public static ExecutorService fanout() { return FANOUT; }
    public static void bg(Runnable r) { POOL.submit(r); }
    public static ScheduledExecutorService scheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "bofo-poll"); t.setDaemon(true); return t; });
    }
}
