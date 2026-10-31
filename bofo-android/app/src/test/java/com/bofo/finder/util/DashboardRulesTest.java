package com.bofo.finder.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class DashboardRulesTest {
    @Test public void sumsAndWeightedRating() {
        List<DashboardRules.Row> l = new ArrayList<>();
        l.add(new DashboardRules.Row(10, 2, 1, 3, 5.0, 4));
        l.add(new DashboardRules.Row(5, 1, 0, 1, 1.0, 2));
        l.add(new DashboardRules.Row(0, 0, 0, 0, 0.0, 0));
        DashboardRules.Totals t = DashboardRules.totals(l);
        assertEquals(3, t.places());
        assertEquals(15, t.views());
        assertEquals(3, t.saves());
        assertEquals(1, t.chats());
        assertEquals(4, t.reviews());
        assertEquals(6, t.beds());
        assertEquals(4.0, t.rating(), 0.0001);          // (5*3 + 1*1) / 4
    }
    @Test public void emptyAndFormat() {
        DashboardRules.Totals t = DashboardRules.totals(new ArrayList<>());
        assertEquals(0, t.places());
        assertEquals(0.0, t.rating(), 0.0001);
        assertEquals("-", DashboardRules.rating(0));
        assertEquals("4.3", DashboardRules.rating(4.26));
    }
    @Test public void negativesIgnored() {
        List<DashboardRules.Row> l = new ArrayList<>();
        l.add(new DashboardRules.Row(-3, -1, -2, 0, 0, -4));
        assertEquals(0, DashboardRules.totals(l).views());
        assertEquals(0, DashboardRules.totals(l).beds());
    }
}
