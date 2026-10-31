package com.bofo.finder.util;

import java.util.Locale;

/** Philippine peso text: 1500 -> "₱1,500", 1500.5 -> "₱1,500.50". */
public final class Money {
    private Money() {}
    public static String peso(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.005) return "\u20B1" + String.format(Locale.US, "%,d", Math.round(v));
        return "\u20B1" + String.format(Locale.US, "%,.2f", v);
    }
    /** "₱1,500/mo". */
    public static String perMonth(double v) { return peso(v) + "/mo"; }
}
