package com.bofo.finder.util;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Keeps a copy of the last good server answer ON THE DEVICE (survives closing the app) and serves it when the network is down.
 * Online: fetch, save, return. Offline (network error only): return the saved copy if there is one, else rethrow so the
 * screen shows "No internet connection". Files are named user_*, so sign-out wipes them ({@link DiskCache#deletePrefix}).
 */
public final class Offline {
    private Offline() {}
    public interface FetchArray { JSONArray get() throws Exception; }
    public interface FetchObject { JSONObject get() throws Exception; }

    public static JSONArray array(String key, FetchArray f) throws Exception {
        try { JSONArray a = f.get(); save(key, a.toString()); return a; }
        catch (Exception e) {
            if (Connectivity.isNetworkError(e)) { String s = DiskCache.read("user_" + key); if (s != null) try { return new JSONArray(s); } catch (Exception bad) { /* fall through */ } }
            throw e;
        }
    }

    public static JSONObject object(String key, FetchObject f) throws Exception {
        try { JSONObject o = f.get(); save(key, o.toString()); return o; }
        catch (Exception e) {
            if (Connectivity.isNetworkError(e)) { String s = DiskCache.read("user_" + key); if (s != null) try { return new JSONObject(s); } catch (Exception bad) { /* fall through */ } }
            throw e;
        }
    }

    private static void save(String key, String text) {
        DiskCache.write("user_" + key, text);
        int i = key.lastIndexOf('_');
        if (i > 0) DiskCache.trim("user_" + key.substring(0, i + 1), 40);      // per-item files (chats, places): keep the 40 newest
    }
}
