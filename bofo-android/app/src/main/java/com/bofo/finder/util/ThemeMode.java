package com.bofo.finder.util;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatDelegate;

import com.bofo.finder.R;

/**
 * v2.0.117: the ORANGE / CLASSIC (purple) switch.
 * "Night" is only the switch mechanism: res/values-night/colors.xml holds the ORANGE palette, res/values/colors.xml the classic one.
 * The app is never dark. Orange = MODE_NIGHT_YES, classic = MODE_NIGHT_NO. Never MODE_NIGHT_FOLLOW_SYSTEM, so the phone's own dark mode cannot change the app.
 */
public final class ThemeMode {
    private ThemeMode() {}

    /** BofoApp.onCreate: before any activity exists, so the first screen already has the right colours (no flash). */
    public static void applySaved(Context c) {
        AppCompatDelegate.setDefaultNightMode(AppPrefs.orangeTheme(c) ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /** Settings switch. Saves the choice now; the mode change (which recreates the activity on the same screen) follows a moment later so the switch finishes sliding. */
    public static void set(Activity a, boolean orange) {
        final Context app = a.getApplicationContext();
        AppPrefs.setOrangeTheme(app, orange);
        syncSplashTheme(a);
        new Handler(Looper.getMainLooper()).postDelayed(() -> applySaved(app), 180);
    }

    /**
     * Android 13+: tells the system which launch (splash) theme to draw next time, so the very first frame is already the colour of the chosen loading screen.
     * Older Android keeps the manifest theme (white = orange), because the system draws that window before the app can say anything.
     */
    public static void syncSplashTheme(Activity a) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        try {
            a.getSplashScreen().setSplashScreenTheme(AppPrefs.orangeTheme(a) ? R.style.Theme_Bofo_Launch : R.style.Theme_Bofo_Launch_Classic);
        } catch (RuntimeException ignored) { /* cosmetic only */ }
    }
}
