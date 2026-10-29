package com.bofo.finder.util;

import javafx.application.Platform;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Runs blocking work off the FX thread, then delivers the result on the FX thread. */
public final class Async {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "bofo-io"); t.setDaemon(true); return t;
    });
    private Async() {}

    public interface Work<T> { T run() throws Exception; }

    public static <T> void run(Work<T> work, Consumer<T> ok, Consumer<Throwable> fail) {
        POOL.submit(() -> {
            try { T r = work.run(); Platform.runLater(() -> ok.accept(r)); }
            catch (Throwable e) { Platform.runLater(() -> { if (fail != null) fail.accept(e); }); }
        });
    }
    public static <T> void run(Work<T> work, Consumer<T> ok) { run(work, ok, e -> e.printStackTrace()); }
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
