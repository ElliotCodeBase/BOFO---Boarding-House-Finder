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
    public static void bg(Runnable r) { POOL.submit(r); }
    public static ScheduledExecutorService scheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "bofo-poll"); t.setDaemon(true); return t; });
    }
}
