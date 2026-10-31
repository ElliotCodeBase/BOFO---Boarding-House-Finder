package com.bofo.finder.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Home's "Lowest / Middle / Highest rent" row (pure maths). Middle = median; with an even count it is halfway between the two middle prices.
 * 0 prices: no row. 1 price or all equal: one "Rent" value. 2 prices: Lowest + Highest only. 3+: all three.
 */
public final class PriceRange {
    public final double lowest, middle, highest;
    public final int count;
    private PriceRange(double lo, double mid, double hi, int n) { lowest = lo; middle = mid; highest = hi; count = n; }

    /** @return null when there is nothing to show. */
    public static PriceRange of(List<Double> prices) {
        List<Double> p = new ArrayList<>();
        if (prices != null) for (Double d : prices) if (d != null && d > 0) p.add(d);
        if (p.isEmpty()) return null;
        Collections.sort(p);
        int n = p.size();
        double mid = n % 2 == 1 ? p.get(n / 2) : (p.get(n / 2 - 1) + p.get(n / 2)) / 2.0;
        return new PriceRange(p.get(0), mid, p.get(n - 1), n);
    }

    /** Label / value pairs to draw, in order. */
    public List<String[]> cells() {
        List<String[]> l = new ArrayList<>();
        if (count == 1 || lowest == highest) { l.add(new String[]{"Rent", Money.perMonth(lowest)}); return l; }
        l.add(new String[]{"Lowest", Money.perMonth(lowest)});
        if (count >= 3) l.add(new String[]{"Middle", Money.perMonth(middle)});
        l.add(new String[]{"Highest", Money.perMonth(highest)});
        return l;
    }
}
