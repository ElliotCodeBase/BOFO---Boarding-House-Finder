package com.bofo.finder.util;

import java.util.List;
import java.util.Locale;

/** Pure sums for the owner Dashboard (8f, unit tested). One {@link Row} per place, read from bh_activity. */
public final class DashboardRules {
    private DashboardRules() {}

    public record Row(int views, int saves, int chats, int reviews, double rating, int beds) {}

    public record Totals(int places, int views, int saves, int chats, int reviews, double rating, int beds) {}

    /** Sums; the rating is the average of all reviews (each place weighted by its review count), 0 when there are no reviews. */
    public static Totals totals(List<Row> rows) {
        int v = 0, s = 0, c = 0, r = 0, b = 0;
        double weighted = 0;
        for (Row x : rows) {
            v += Math.max(0, x.views()); s += Math.max(0, x.saves()); c += Math.max(0, x.chats()); b += Math.max(0, x.beds());
            if (x.reviews() > 0) { r += x.reviews(); weighted += x.rating() * x.reviews(); }
        }
        return new Totals(rows.size(), v, s, c, r, r > 0 ? weighted / r : 0, b);
    }

    /** "4.3", or "-" when there is no rating yet. */
    public static String rating(double r) { return r <= 0 ? "-" : String.format(Locale.US, "%.1f", r); }
}
