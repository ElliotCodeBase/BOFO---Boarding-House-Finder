package com.bofo.finder.ui;

import com.bofo.finder.model.BoardingHouse;

import java.util.List;

/** Lowest, middle (median) and highest monthly rent of a list of boarding houses, and the place behind each (so a tap can open it). No UI. */
public final class PriceRange {
    public final int count;
    public final double lowest, middle, highest;
    public final BoardingHouse lowestBh, middleBh, highestBh;
    public BoardingHouse bestBh;                 // the top-rated place with a price (null when nobody has reviews yet)

    private PriceRange(int count, double lowest, double middle, double highest, BoardingHouse lowestBh, BoardingHouse middleBh, BoardingHouse highestBh) {
        this.count = count; this.lowest = lowest; this.middle = middle; this.highest = highest;
        this.lowestBh = lowestBh; this.middleBh = middleBh; this.highestBh = highestBh;
    }

    /** Null when no place has a real price (a price of 0 or less is treated as "not set" and ignored). */
    public static PriceRange of(List<BoardingHouse> items) {
        List<BoardingHouse> s = items.stream().filter(x -> x.price() > 0)
                .sorted(java.util.Comparator.comparingDouble(BoardingHouse::price)).collect(java.util.stream.Collectors.toList());
        if (s.isEmpty()) return null;
        double[] p = s.stream().mapToDouble(BoardingHouse::price).toArray();
        int n = p.length;
        double mid = p[n / 2];                                    // always a real place's price (the middle one; with an even count the upper of the two middle ones), so the price and the place it lights up agree
        PriceRange r = new PriceRange(n, p[0], mid, p[n - 1], s.get(0), s.get(n / 2), s.get(n - 1));
        r.bestBh = s.stream().filter(x -> x.reviewCount() > 0 && x.avgRating() > 0)         // best rated: highest stars, then more reviews, then cheaper
                .max(java.util.Comparator.comparingDouble(BoardingHouse::avgRating)
                        .thenComparingInt(BoardingHouse::reviewCount)
                        .thenComparingDouble(x -> -x.price())).orElse(null);
        return r;
    }

    /** Which cells to show: 1 place or all the same price = one price; 2 places = lowest + highest; 3 or more = lowest, middle, highest. */
    public boolean single() { return count == 1 || lowest == highest; }
    public boolean hasMiddle() { return count >= 3 && !single(); }

    public static String peso(double v) { return "\u20B1" + String.format(java.util.Locale.ROOT, "%,.0f", v); }
}
