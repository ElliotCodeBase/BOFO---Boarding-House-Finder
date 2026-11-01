package com.bofo.finder.util;

import android.content.Context;
import android.content.SharedPreferences;

/** Small on-device settings (not secrets). Wiped on sign-out by {@link #clear}. */
public final class AppPrefs {
    private AppPrefs() {}
    private static final String FILE = "bofo_settings";
    private static final String K_LOCATION = "use_location";
    private static final String K_PATH_FADE = "path_see_through";
    private static final String K_VIEWED = "viewed_ids";
    private static final int VIEWED_MAX = 200;

    private static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    /** Settings > "Use my location": lets Home / Explore centre on the user. Default on. The permission itself is asked in Stage 5. */
    public static boolean useLocation(Context c) { return sp(c).getBoolean(K_LOCATION, true); }
    public static void setUseLocation(Context c, boolean on) { sp(c).edit().putBoolean(K_LOCATION, on).apply(); }
    /** Settings > "See-through path": draws the map route line 80% transparent so the road underneath can be checked. Default off. */
    public static boolean seeThroughPath(Context c) { return sp(c).getBoolean(K_PATH_FADE, false); }
    public static void setSeeThroughPath(Context c, boolean on) { sp(c).edit().putBoolean(K_PATH_FADE, on).apply(); }
    /** v2.0.81: ids of the places the person opened (oldest first, at most 200). The Explore map dims those pins. */
    public static java.util.List<String> viewedIds(Context c) {
        java.util.List<String> out = new java.util.ArrayList<>();
        String s = sp(c).getString(K_VIEWED, "");
        if (s != null && !s.isEmpty()) for (String id : s.split(",")) if (!id.isEmpty()) out.add(id);
        return out;
    }
    public static void addViewed(Context c, String id) {
        if (id == null || id.isEmpty() || id.contains(",")) return;
        java.util.List<String> l = viewedIds(c);
        l.remove(id);
        l.add(id);
        while (l.size() > VIEWED_MAX) l.remove(0);
        sp(c).edit().putString(K_VIEWED, android.text.TextUtils.join(",", l)).apply();
    }
    public static void clear(Context c) { sp(c).edit().clear().apply(); }
}
