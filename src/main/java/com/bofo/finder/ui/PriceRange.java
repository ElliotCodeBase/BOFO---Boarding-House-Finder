package com.bofo.finder.ui;

import com.bofo.finder.model.BoardingHouse;

import java.util.List;

/** Lowest, middle (median) and highest monthly rent of a list of boarding houses. Pure maths, no UI. */
public final class PriceRange {
    public final int count;
    public final double lowest, middle, highest;

    private PriceRange(int count, double lowest, double middle, double highest) {
        this.count = count; this.lowest = lowest; this.middle = middle; this.highest = highest;
    }

    /** Null when no place has a real price (a price of 0 or less is treated as "not set" and ignored). */
    public static PriceRange of(List<BoardingHouse> items) {
        double[] p = items.stream().mapToDouble(BoardingHouse::price).filter(x -> x > 0).sorted().toArray();
        if (p.length == 0) return null;
        int n = p.length;
        double mid = n % 2 == 1 ? p[n / 2] : Math.round((p[n / 2 - 1] + p[n / 2]) / 2);   // even count: halfway between the two middle prices
        return new PriceRange(n, p[0], mid, p[n - 1]);
    }

    /** Which cells to show: 1 place or all the same price = one price; 2 places = lowest + highest; 3 or more = lowest, middle, highest. */
    public boolean single() { return count == 1 || lowest == highest; }
    public boolean hasMiddle() { return count >= 3 && !single(); }

    public static String peso(double v) { return "\u20B1" + String.format(java.util.Locale.ROOT, "%,.0f", v); }
}
