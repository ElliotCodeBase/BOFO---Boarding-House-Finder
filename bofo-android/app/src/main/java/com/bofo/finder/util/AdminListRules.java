package com.bofo.finder.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** v2.4.13: pure rules shared by the admin lists (filter chips, search, grouping). No Android classes, unit tested. */
public final class AdminListRules {
    public static final int WAITING = 0, DONE = 1, ALL = 2;
    private AdminListRules() {}

    /** Every word typed must appear in one of the fields (case-insensitive). An empty query matches everything. */
    public static boolean matches(String query, String... fields) {
        if (query == null || query.trim().isEmpty()) return true;
        StringBuilder sb = new StringBuilder();
        if (fields != null) for (String f : fields) if (f != null) sb.append(f.toLowerCase(Locale.ROOT)).append(' ');
        String hay = sb.toString();
        for (String w : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) if (!hay.contains(w)) return false;
        return true;
    }

    /** Groups items that share a key, keeping the order of first appearance. */
    public static <T> List<List<T>> groupBy(List<T> items, Function<T, String> key) {
        Map<String, List<T>> m = new LinkedHashMap<>();
        for (T t : items) m.computeIfAbsent(key.apply(t), k -> new ArrayList<>()).add(t);
        return new ArrayList<>(m.values());
    }

    /** Reports screen: chip index -> index into its FILTERS (0 = Waiting, 1 = Answered, 6 = All reports). */
    public static int reportsFilter(int chip) { return chip == DONE ? 1 : chip == ALL ? 6 : 0; }
    /** The other way round: which chip shows a Reports filter index (-1 = none: a finer filter chosen from the title menu). */
    public static int reportsChip(int filterIndex) { return filterIndex == 0 ? WAITING : filterIndex == 1 ? DONE : filterIndex == 6 ? ALL : -1; }

    /** Queue: the PostgREST filter + order for a chip. Waiting is oldest first; the others newest first. */
    public static String queueQuery(int chip) {
        if (chip == WAITING) return "status=eq.pending&order=submitted_at.asc&select=*";
        if (chip == DONE) return "status=in.(verified,rejected)&order=reviewed_at.desc&limit=100&select=*";
        return "order=submitted_at.desc&limit=100&select=*";
    }

    /** Payments: which chip a payment status belongs to (WAITING = pending or failed: needs a look; DONE = paid, refunded, expired). */
    public static boolean paymentInChip(String status, int chip) {
        if (chip == ALL) return true;
        boolean waiting = "pending".equals(status) || "failed".equals(status);
        return chip == WAITING ? waiting : !waiting;
    }
}
