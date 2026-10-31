package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class ListingRulesTest {
    @Test public void money() {
        assertEquals(1500.0, ListingRules.money("1,500").doubleValue(), 0.0001);
        assertEquals(1500.5, ListingRules.money("P 1500.50").doubleValue(), 0.0001);
        assertNull(ListingRules.money(""));
        assertNull(ListingRules.money("0"));
        assertNull(ListingRules.money("abc"));
        assertNull(ListingRules.money("1.2.3"));
        assertNull(ListingRules.money(null));
    }
    @Test public void coords() {
        double[] c = ListingRules.coords("9.3068", " 123,3054 ");
        assertEquals(9.3068, c[0], 0.00001);
        assertEquals(123.3054, c[1], 0.00001);
        assertNull(ListingRules.coords("91", "10"));
        assertNull(ListingRules.coords("10", "181"));
        assertNull(ListingRules.coords("", "10"));
        assertNull(ListingRules.coords("x", "10"));
        assertNull(ListingRules.coords(null, null));
    }
    @Test public void intsAndPhotos() {
        assertEquals(4, ListingRules.intOr(" 4 ", 1));
        assertEquals(1, ListingRules.intOr("", 1));
        assertEquals(1, ListingRules.intOr("-3", 1));
        assertEquals(1, ListingRules.intOr(null, 1));
        assertEquals(8, ListingRules.photoRoom(0));
        assertEquals(0, ListingRules.photoRoom(12));
    }
}
