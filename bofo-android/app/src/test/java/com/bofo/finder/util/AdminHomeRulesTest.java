package com.bofo.finder.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdminHomeRulesTest {
    private static final long NOW = java.time.Instant.parse("2026-11-04T12:00:00Z").toEpochMilli();

    @Test public void hoursAndDays() {
        assertEquals(72, AdminHomeRules.waitingHours("2026-11-01T12:00:00+00:00", NOW));
        assertEquals(0, AdminHomeRules.waitingHours("2026-11-04T11:30:00.123456+00:00", NOW));
        assertEquals(0, AdminHomeRules.waitingHours("2026-11-05T12:00:00Z", NOW));
    }
    @Test public void unreadableTimes() {
        assertEquals(-1, AdminHomeRules.waitingHours(null, NOW));
        assertEquals(-1, AdminHomeRules.waitingHours("", NOW));
        assertEquals(-1, AdminHomeRules.waitingHours("yesterday", NOW));
    }
    @Test public void badgeAndCaughtUp() {
        assertEquals(0, AdminHomeRules.badge(-1));
        assertEquals(5, AdminHomeRules.badge(5));
        assertTrue(AdminHomeRules.allCaughtUp(0, 0, 0));
        assertFalse(AdminHomeRules.allCaughtUp(0, 1, 0));
        assertFalse(AdminHomeRules.allCaughtUp(0, -1, 0));
    }
}
