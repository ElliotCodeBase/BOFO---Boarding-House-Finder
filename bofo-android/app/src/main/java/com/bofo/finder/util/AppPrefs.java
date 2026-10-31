package com.bofo.finder.util;

import android.content.Context;
import android.content.SharedPreferences;

/** Small on-device settings (not secrets). Wiped on sign-out by {@link #clear}. */
public final class AppPrefs {
    private AppPrefs() {}
    private static final String FILE = "bofo_settings";
    private static final String K_LOCATION = "use_location";

    private static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    /** Settings > "Use my location": lets Home / Explore centre on the user. Default on. The permission itself is asked in Stage 5. */
    public static boolean useLocation(Context c) { return sp(c).getBoolean(K_LOCATION, true); }
    public static void setUseLocation(Context c, boolean on) { sp(c).edit().putBoolean(K_LOCATION, on).apply(); }
    public static void clear(Context c) { sp(c).edit().clear().apply(); }
}
