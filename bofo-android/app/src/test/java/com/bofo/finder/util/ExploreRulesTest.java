package com.bofo.finder.util;

import static org.junit.Assert.*;

import com.bofo.finder.model.BoardingHouse;

import java.util.List;
import org.junit.Test;

public class ExploreRulesTest {
    private static BoardingHouse bh(String id, double price, int beds, boolean wifi, List<String> am) {
        return new BoardingHouse(id, "o", id, "", 0, 0, price, beds, false, false, wifi, 0, 0, "", false, 0, false, am);
    }
    private static final List<BoardingHouse> ALL = List.of(
            bh("a", 2000, 1, true, List.of("Aircon")),
            bh("b", 1000, 4, false, List.of("Laundry", "Kitchen")),
            bh("c", 1500, 2, false, List.of()));

    @Test public void priceSortIsCheapestFirst() { assertEquals("b", ExploreRules.sort(ALL, ExploreRules.SORT_PRICE).get(0).id()); }
    @Test public void bedsSortIsMostFirst() { assertEquals("b", ExploreRules.sort(ALL, ExploreRules.SORT_BEDS).get(0).id()); }
    @Test public void nearestKeepsOrder() { assertEquals("a", ExploreRules.sort(ALL, ExploreRules.SORT_NEAREST).get(0).id()); }
    @Test public void wifiUsesTheFlag() { assertEquals(1, ExploreRules.withAll(ALL, List.of("Free Wi-Fi")).size()); }
    @Test public void nameMatchesAmenityList() { assertEquals("b", ExploreRules.withAll(ALL, List.of("Laundry")).get(0).id()); }
    @Test public void mustHaveAll() { assertTrue(ExploreRules.withAll(ALL, List.of("Laundry", "Aircon")).isEmpty()); }
    @Test public void parseMax() {
        assertNull(ExploreRules.parseMax("  "));
        assertEquals(1500.0, ExploreRules.parseMax("1,500"), 0);
        assertTrue(Double.isNaN(ExploreRules.parseMax("abc")));
        assertTrue(Double.isNaN(ExploreRules.parseMax("0")));
    }
}
