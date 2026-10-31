package com.bofo.finder.util;

import static org.junit.Assert.*;

import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ListRulesTest {
    private static BoardingHouse bh(String id, double price) {
        return new BoardingHouse(id, "o", "BH " + id, "addr", 9.3, 123.3, price, 2, false, false, false, 0, 0, "", false, 0, false, new ArrayList<String>());
    }

    @Test public void moneyFormatsPesos() {
        assertEquals("\u20B11,500", Money.peso(1500));
        assertEquals("\u20B1950", Money.peso(950));
        assertEquals("\u20B11,500.50", Money.peso(1500.5));
        assertEquals("\u20B12,000/mo", Money.perMonth(2000));
    }
    @Test public void priceFilterIsInclusiveAndOptional() {
        assertTrue(ListRules.priceOk(1000, 1000.0, 2000.0));
        assertTrue(ListRules.priceOk(2000, 1000.0, 2000.0));
        assertFalse(ListRules.priceOk(999, 1000.0, null));
        assertFalse(ListRules.priceOk(2001, null, 2000.0));
        assertTrue(ListRules.priceOk(5, null, null));
    }
    @Test public void inPriceRangeKeepsOrder() {
        List<BoardingHouse> in = Arrays.asList(bh("a", 800), bh("b", 1500), bh("c", 2500));
        List<BoardingHouse> out = ListRules.inPriceRange(in, 1000.0, 2000.0);
        assertEquals(1, out.size());
        assertEquals("b", out.get(0).id());
        assertSame(in, ListRules.inPriceRange(in, null, null));
    }
    @Test public void collegesNearestFirstAndAlphabeticalFallback() {
        List<College> cs = Arrays.asList(new College("1", "Zeta U", 10.0, 124.0), new College("2", "alpha U", 9.31, 123.30), new College("3", "Mid U", 9.5, 123.4));
        List<College> near = ListRules.nearestFirst(cs, 9.30, 123.30);
        assertEquals("2", near.get(0).id());
        assertEquals("3", near.get(1).id());
        List<College> az = ListRules.nearestFirst(cs, null, null);
        assertEquals("alpha U", az.get(0).name());
        assertEquals("Zeta U", az.get(2).name());
        assertEquals("1", cs.get(0).id());          // input untouched
    }
    @Test public void texts() {
        assertEquals("New", ListRules.ratingText(0, 0));
        assertEquals("4.5 (12)", ListRules.ratingText(4.5, 12));
        assertEquals("1 bed left", ListRules.bedsText(1));
        assertEquals("3 beds left", ListRules.bedsText(3));
        assertEquals("", ListRules.freeText(false, false, false));
        assertEquals("Free water, Wi-Fi", ListRules.freeText(true, false, true));
        assertEquals("2026-10-30", ListRules.shortDate("2026-10-30T05:27:00+00:00"));
        assertEquals("", ListRules.shortDate(null));
        assertEquals("\u2605\u2605\u2605\u2606\u2606", ListRules.stars(3));
        assertEquals("\u2606\u2606\u2606\u2606\u2606", ListRules.stars(-2));
        assertEquals("\u2605\u2605\u2605\u2605\u2605", ListRules.stars(9));
    }
    @Test public void bedsLeftNeverNegative() {
        assertEquals(3, ListRules.bedsLeft(new int[]{2, 4}, new int[]{1, 2}));
        assertEquals(0, ListRules.bedsLeft(new int[]{2}, new int[]{5}));
        assertEquals(2, ListRules.bedsLeft(new int[]{2}, new int[]{}));
    }
    @Test public void firstTextSkipsEmptyAndNull() {
        assertEquals("Ana", ListRules.firstText(null, " ", "null", "Ana", "Bo"));
        assertEquals("", ListRules.firstText());
    }
}
