package com.bofo.finder.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class AdminListRulesTest {
    @Test public void search() {
        assertTrue(AdminListRules.matches("", "x"));
        assertTrue(AdminListRules.matches(null, "x"));
        assertTrue(AdminListRules.matches("ana  cruz", "Ana Maria", "Dela Cruz"));
        assertFalse(AdminListRules.matches("ben", "Ana", null));
    }
    @Test public void grouping() {
        List<String> in = Arrays.asList("a1", "b1", "a2", "c1", "a3");
        List<List<String>> g = AdminListRules.groupBy(in, s -> s.substring(0, 1));
        assertEquals(3, g.size());
        assertEquals(Arrays.asList("a1", "a2", "a3"), g.get(0));
        assertEquals(Arrays.asList("b1"), g.get(1));
    }
    @Test public void chips() {
        assertEquals(0, AdminListRules.reportsFilter(AdminListRules.WAITING));
        assertEquals(1, AdminListRules.reportsFilter(AdminListRules.DONE));
        assertEquals(6, AdminListRules.reportsFilter(AdminListRules.ALL));
        assertEquals(-1, AdminListRules.reportsChip(2));
        assertEquals(AdminListRules.ALL, AdminListRules.reportsChip(6));
    }
    @Test public void queryAndPayments() {
        assertTrue(AdminListRules.queueQuery(0).contains("submitted_at.asc"));
        assertTrue(AdminListRules.queueQuery(1).contains("verified,rejected"));
        assertTrue(AdminListRules.paymentInChip("failed", AdminListRules.WAITING));
        assertFalse(AdminListRules.paymentInChip("paid", AdminListRules.WAITING));
        assertTrue(AdminListRules.paymentInChip("refunded", AdminListRules.DONE));
        assertTrue(AdminListRules.paymentInChip("paid", AdminListRules.ALL));
    }
}
