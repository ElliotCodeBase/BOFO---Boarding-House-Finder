package com.bofo.finder.service;

import org.json.JSONArray;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * v2.4.10: the numbers on Admin Home and the Queue / Reports tab badges. Read-only: each count is one small select that returns
 * only ids / timestamps (no names, no ID photos, no messages). A count that cannot be read stays -1 (shown as "-"); one failure never hides the rest.
 * RLS decides what an admin can read, so a non-admin account would just get 0 rows.
 */
public final class AdminCountsService {
    public static final int UNKNOWN = -1;
    private static final int CAP = 1000;

    public static final class Counts {
        public int ids = UNKNOWN, reports = UNKNOWN, aiWeek = UNKNOWN, payments = UNKNOWN, suspended = UNKNOWN, banned = UNKNOWN, takenDown = UNKNOWN;
        public String idsOldest, reportsOldest, paymentsOldest;
    }

    private final SupabaseClient api = SupabaseClient.get();

    /** Only the two tab badges (cheap: two selects). Index 0 = IDs waiting, 1 = open reports; -1 when unknown. */
    public int[] badges() {
        return new int[]{ count("owner_verifications", "status=eq.pending&select=id&limit=" + CAP),
                          count("reports", "status=eq.pending&select=id&limit=" + CAP) };
    }

    public Counts load() {
        Counts c = new Counts();
        JSONArray a = rows("owner_verifications", "status=eq.pending&order=submitted_at.asc&select=submitted_at&limit=" + CAP);
        if (a != null) { c.ids = a.length(); c.idsOldest = a.length() > 0 ? a.optJSONObject(0).optString("submitted_at", null) : null; }
        a = rows("reports", "status=eq.pending&order=created_at.asc&select=created_at&limit=" + CAP);
        if (a != null) { c.reports = a.length(); c.reportsOldest = a.length() > 0 ? a.optJSONObject(0).optString("created_at", null) : null; }
        a = rows("payments", "status=in.(pending,failed)&order=created_at.asc&select=created_at&limit=" + CAP);
        if (a != null) { c.payments = a.length(); c.paymentsOldest = a.length() > 0 ? a.optJSONObject(0).optString("created_at", null) : null; }
        c.aiWeek = count("ai_unanswered", "created_at=gte." + Instant.now().minus(7, ChronoUnit.DAYS) + "&select=id&limit=" + CAP);
        c.suspended = count("users", "suspended_until=gt." + Instant.now() + "&select=id&limit=" + CAP);
        c.banned = count("users", "banned_at=not.is.null&select=id&limit=" + CAP);
        c.takenDown = count("bh_cards", "status=eq.taken_down&select=id&limit=" + CAP);
        return c;
    }

    private int count(String table, String q) {
        JSONArray a = rows(table, q);
        return a == null ? UNKNOWN : a.length();
    }

    private JSONArray rows(String table, String q) {
        try { return api.select(table, q); } catch (Exception e) { return null; }
    }
}
