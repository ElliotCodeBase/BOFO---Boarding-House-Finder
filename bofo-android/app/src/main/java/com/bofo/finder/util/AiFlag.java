package com.bofo.finder.util;

import com.bofo.finder.service.SupabaseClient;

import org.json.JSONArray;

import java.util.function.Consumer;

/**
 * v2.3.2: the admin switch for the BOFO AI assistant (app_flags row 'ai_enabled', migration 030). Unknown / offline / row missing = ON (the app behaves as before).
 * Off = the floating BOFO button and the chat are hidden for every account.
 */
public final class AiFlag {
    private AiFlag() {}

    private static volatile boolean on = true;

    public static boolean on() { return on; }
    public static void set(boolean v) { on = v; }

    /** Reads the flag in the background and tells the main thread the current value (keeps the last known value when the read fails). */
    public static void refresh(Consumer<Boolean> done) {
        Async.run(() -> {
            JSONArray a = SupabaseClient.get().select("app_flags", "select=value&key=eq.ai_enabled");
            return a.length() == 0 ? Boolean.TRUE : Boolean.valueOf(a.getJSONObject(0).optBoolean("value", true));
        }, v -> { on = v; if (done != null) done.accept(v); }, e -> { if (done != null) done.accept(on); });
    }
}
