package com.bofo.finder.util;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/** v2.8.0: small pure rules for the weekly summary, the per-person report count and the CSV export (no Android classes, unit tested). */
public final class InsightRules {
    private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private InsightRules() {}

    /** {start of the 7 days before, start of the last 7 days, now}. */
    public static Instant[] weekEdges(Instant now) {
        return new Instant[]{now.minus(14, ChronoUnit.DAYS), now.minus(7, ChronoUnit.DAYS), now};
    }

    /** "-" when it could not be read, "1000+" when the read hit its cap, else the number. */
    public static String countText(int n, int cap) {
        if (n < 0) return "-";
        return n >= cap ? cap + "+" : String.valueOf(n);
    }

    /** Waiting reports about the same person, not counting the one on screen. */
    public static int otherWaiting(int waitingTotal, boolean thisOneWaiting) {
        return Math.max(0, waitingTotal - (thisOneWaiting ? 1 : 0));
    }

    public static boolean isUuid(String s) { return s != null && UUID.matcher(s).matches(); }

    /** The date part of a timestamp (2026-11-05), "" when it is too short. */
    public static String dayOf(String iso) { return iso == null || iso.length() < 10 || "null".equals(iso) ? "" : iso.substring(0, 10); }
}
