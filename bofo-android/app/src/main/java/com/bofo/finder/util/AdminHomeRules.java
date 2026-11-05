package com.bofo.finder.util;

import java.time.OffsetDateTime;

/** v2.4.10: small pure rules for the Admin Home cards (no Android classes, unit tested). */
public final class AdminHomeRules {
    private AdminHomeRules() {}

    /** Whole hours between a PostgREST timestamp and now; -1 when the time cannot be read. A time in the future counts as 0. */
    public static long waitingHours(String iso, long nowMs) {
        if (iso == null || iso.isEmpty()) return -1;
        try {
            long ms = OffsetDateTime.parse(iso.trim().replace(' ', 'T')).toInstant().toEpochMilli();
            return Math.max(0, (nowMs - ms) / 3_600_000L);
        } catch (Exception e) { return -1; }
    }

    /** The count shown on the tab badge: unknown (negative) counts as 0. */
    public static int badge(int count) { return Math.max(0, count); }

    /** True when nothing needs the admin: every action count is known and 0. An unknown count (-1, failed to load) is never "all caught up". */
    public static boolean allCaughtUp(int... actionCounts) {
        if (actionCounts == null || actionCounts.length == 0) return false;
        for (int c : actionCounts) if (c != 0) return false;
        return true;
    }
}
