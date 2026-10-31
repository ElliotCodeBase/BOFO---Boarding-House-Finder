package com.bofo.finder.util;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class PriceRangeTest {
    @Test public void emptyOrNoValidPricesHidesTheRow() {
        assertNull(PriceRange.of(Collections.<Double>emptyList()));
        assertNull(PriceRange.of(null));
        assertNull(PriceRange.of(Arrays.asList(0.0, -5.0, null)));
    }
    @Test public void onePlaceShowsRent() {
        List<String[]> c = PriceRange.of(Arrays.asList(1500.0)).cells();
        assertEquals(1, c.size());
        assertEquals("Rent", c.get(0)[0]);
        assertEquals("\u20B11,500/mo", c.get(0)[1]);
    }
    @Test public void samePriceShowsRent() {
        assertEquals(1, PriceRange.of(Arrays.asList(1200.0, 1200.0, 1200.0)).cells().size());
    }
    @Test public void twoPlacesShowLowestAndHighestOnly() {
        List<String[]> c = PriceRange.of(Arrays.asList(2050.0, 1300.0)).cells();
        assertEquals(2, c.size());
        assertEquals("Lowest", c.get(0)[0]);
        assertEquals("Highest", c.get(1)[0]);
    }
    @Test public void oddCountMiddleIsMedian() {
        PriceRange r = PriceRange.of(Arrays.asList(2050.0, 1300.0, 1550.0));
        assertEquals(1300.0, r.lowest, 0);
        assertEquals(1550.0, r.middle, 0);
        assertEquals(2050.0, r.highest, 0);
        assertEquals(3, r.cells().size());
    }
    @Test public void evenCountMiddleIsHalfwayBetweenTheTwoMiddlePrices() {
        assertEquals(1500.0, PriceRange.of(Arrays.asList(1000.0, 1400.0, 1600.0, 3000.0)).middle, 0);
    }
}
