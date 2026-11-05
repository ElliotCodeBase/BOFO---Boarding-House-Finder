package com.bofo.finder.service;

import com.bofo.finder.util.InsightRules;

import org.json.JSONArray;

import java.time.Instant;

/**
 * v2.8.0: the numbers of the "Last 7 days" card on Admin Home: new users, new listings and reports resolved, for the last 7 days and the 7 before.
 * Read-only, existing admin read access (ids only, no names or details). A number that cannot be read stays -1; one failure never hides the rest.
 */
public final class AdminWeeklyService {
    public static final int UNKNOWN = -1;
    public static final int CAP = 1000;

    public static final class Weekly {
        public int usersNow = UNKNOWN, usersBefore = UNKNOWN, listingsNow = UNKNOWN, listingsBefore = UNKNOWN, resolvedNow = UNKNOWN, resolvedBefore = UNKNOWN;
    }

    private final SupabaseClient api = SupabaseClient.get();

    public Weekly load() {
        Instant[] e = InsightRules.weekEdges(Instant.now());
        Weekly w = new Weekly();
        w.usersNow = count("users", "created_at", e[1], e[2], "");
        w.usersBefore = count("users", "created_at", e[0], e[1], "");
        w.listingsNow = count("bh_cards", "created_at", e[1], e[2], "");
        w.listingsBefore = count("bh_cards", "created_at", e[0], e[1], "");
        w.resolvedNow = count("reports_admin_v", "reviewed_at", e[1], e[2], "&status=neq.pending");
        w.resolvedBefore = count("reports_admin_v", "reviewed_at", e[0], e[1], "&status=neq.pending");
        return w;
    }

    private int count(String table, String col, Instant from, Instant to, String extra) {
        try {
            JSONArray a = api.select(table, col + "=gte." + from + "&" + col + "=lt." + to + extra + "&select=id&limit=" + CAP);
            return a == null ? UNKNOWN : a.length();
        } catch (Exception e) { return UNKNOWN; }
    }
}
