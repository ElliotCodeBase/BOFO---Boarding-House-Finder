package com.bofo.finder.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import org.junit.Test;

public class InsightRulesTest {
    @Test public void edges() {
        Instant now = Instant.parse("2026-11-15T00:00:00Z");
        Instant[] e = InsightRules.weekEdges(now);
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), e[0]);
        assertEquals(Instant.parse("2026-11-08T00:00:00Z"), e[1]);
        assertEquals(now, e[2]);
    }
    @Test public void counts() {
        assertEquals("-", InsightRules.countText(-1, 1000));
        assertEquals("7", InsightRules.countText(7, 1000));
        assertEquals("1000+", InsightRules.countText(1000, 1000));
    }
    @Test public void others() {
        assertEquals(2, InsightRules.otherWaiting(3, true));
        assertEquals(3, InsightRules.otherWaiting(3, false));
        assertEquals(0, InsightRules.otherWaiting(0, true));
    }
    @Test public void uuidAndDay() {
        assertTrue(InsightRules.isUuid("0b9f2a4e-1c3d-4e5f-8a6b-7c8d9e0f1a2b"));
        assertFalse(InsightRules.isUuid("1;drop"));
        assertFalse(InsightRules.isUuid(null));
        assertEquals("2026-11-05", InsightRules.dayOf("2026-11-05T12:00:00+00:00"));
        assertEquals("", InsightRules.dayOf("null"));
    }
}
