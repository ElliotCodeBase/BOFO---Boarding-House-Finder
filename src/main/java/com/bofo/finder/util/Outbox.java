package com.bofo.finder.util;

import com.bofo.finder.model.Message;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.ChatService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Messages written while offline. Every chat message goes in here FIRST (saved on the device, so it survives closing the app),
 * then {@link #flushAsync()} sends the queue oldest-first. No internet = the message stays queued and is sent by itself when the
 * connection returns (Main flushes on reconnect, an open chat flushes on every poll tick). Files are user_*, wiped on sign-out.
 */
public final class Outbox {
    private Outbox() {}

    /** Called from a background thread: wrap UI work in Platform.runLater. */
    public interface Listener { void sent(String id, Message m); void failed(String id, Throwable why); void waiting(String id); }
    public static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private static final String FILE = "user_outbox.json";
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    public static synchronized JSONObject add(String conv, String body) {
        JSONArray a = load();
        JSONObject e = new JSONObject().put("id", UUID.randomUUID().toString()).put("conv", conv).put("body", body).put("at", System.currentTimeMillis());
        a.put(e); DiskCache.write(FILE, a.toString());
        return e;
    }

    /** Queued messages of one chat, oldest first. */
    public static synchronized List<JSONObject> pending(String conv) {
        List<JSONObject> l = new ArrayList<>(); JSONArray a = load();
        for (int i = 0; i < a.length(); i++) if (conv.equals(a.getJSONObject(i).optString("conv"))) l.add(a.getJSONObject(i));
        return l;
    }

    public static synchronized boolean hasPending() { return load().length() > 0; }

    private static synchronized List<JSONObject> all() {
        List<JSONObject> l = new ArrayList<>(); JSONArray a = load();
        for (int i = 0; i < a.length(); i++) l.add(a.getJSONObject(i));
        return l;
    }

    private static synchronized void remove(String id) {
        JSONArray a = load(), keep = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (!id.equals(a.getJSONObject(i).optString("id"))) keep.put(a.getJSONObject(i));
        DiskCache.write(FILE, keep.toString());
    }

    private static JSONArray load() {
        try { String s = DiskCache.read(FILE); return s == null ? new JSONArray() : new JSONArray(s); } catch (Exception bad) { return new JSONArray(); }
    }

    /** Sends everything queued (one flush at a time). Safe to call often and from any thread. */
    public static void flushAsync() {
        if (!RUNNING.compareAndSet(false, true)) return;
        Async.bg(() -> { try { flush(); } finally { RUNNING.set(false); } });
    }

    private static void flush() {
        String me = AuthService.get().userId();
        if (me == null) return;
        ChatService chat = new ChatService();
        List<JSONObject> queue = all();
        for (int i = 0; i < queue.size(); i++) {
            JSONObject e = queue.get(i); String id = e.getString("id");
            try {
                Message m = chat.send(e.getString("conv"), me, e.getString("body"));
                remove(id);
                LISTENERS.forEach(l -> l.sent(id, m));
            } catch (Exception ex) {
                if (Connectivity.isNetworkError(ex)) {                   // still offline: keep everything queued, in order
                    for (int j = i; j < queue.size(); j++) { String w = queue.get(j).getString("id"); LISTENERS.forEach(l -> l.waiting(w)); }
                    return;
                }
                remove(id);                                              // the server said no (blocked, too long...): retrying can never work
                LISTENERS.forEach(l -> l.failed(id, ex));
            }
        }
    }
}
