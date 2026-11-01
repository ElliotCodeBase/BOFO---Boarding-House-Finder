package com.bofo.finder.util;

import java.util.Locale;

/** Philippine peso text: 1500 -> "₱1,500", 1500.5 -> "₱1,500.50". */
public final class Money {
    private Money() {}
    public static String peso(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.005) return "\u20B1" + String.format(Locale.US, "%,d", Math.round(v));
        return "\u20B1" + String.format(Locale.US, "%,.2f", v);
    }
    /** v2.0.80: map pin text. 950 -> "₱950", 1500 -> "₱1.5k", 2000 -> "₱2k", 1250 -> "₱1.25k". (Named shortPeso: "short" is a Java keyword.) */
    public static String shortPeso(double v) {
        if (Double.isNaN(v) || v <= 0) return "";
        if (v < 1000) return "\u20B1" + Math.round(v);
        String k = String.format(Locale.US, "%.2f", Math.round(v / 10.0) / 100.0);
        if (k.endsWith("0")) k = k.substring(0, k.length() - 1);
        if (k.endsWith(".0")) k = k.substring(0, k.length() - 2);
        return "\u20B1" + k + "k";
    }
    /** "₱1,500/mo". */
    public static String perMonth(double v) { return peso(v) + "/mo"; }
}
