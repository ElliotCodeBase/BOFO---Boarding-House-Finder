package com.bofo.finder.util;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory TTL cache with stale fallback: if the network fails, the last good data is served (offline support). */
public final class Cache {
    private record Entry(Object value, long at) {}
    private static final Map<String, Entry> M = new ConcurrentHashMap<>();
    /** True when the most recent {@link #get} had to serve stale data because the network failed. */
    public static volatile boolean servedStale = false;
    public interface Loader<T> { T load() throws Exception; }
    private Cache() {}

    @SuppressWarnings("unchecked")
    public static <T> T get(String key, long ttlMs, Loader<T> loader) throws Exception {
        Entry e = M.get(key);
        if (e != null && System.currentTimeMillis() - e.at() < ttlMs) { servedStale = false; return (T) e.value(); }
        try {
            T v = loader.load();
            M.put(key, new Entry(v, System.currentTimeMillis()));
            servedStale = false;
            return v;
        } catch (Exception ex) {
            if (e != null) { servedStale = true; return (T) e.value(); }
            throw ex;
        }
    }
    public static void remove(String prefix) { M.keySet().removeIf(k -> k.startsWith(prefix)); }
    /** Call on sign-out: cached data belongs to the previous user. */
    public static void clear() { M.clear(); }
}
