package com.bofo.finder.util;

import static org.junit.Assert.*;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.Test;

public class SuspendRulesTest {
    @Test public void days() {
        assertEquals(Integer.valueOf(1), SuspendRules.parseDays(" 1 "));
        assertEquals(Integer.valueOf(3650), SuspendRules.parseDays("3650"));
        assertNull(SuspendRules.parseDays("0"));
        assertNull(SuspendRules.parseDays("3651"));
        assertNull(SuspendRules.parseDays("-2"));
        assertNull(SuspendRules.parseDays("2.5"));
        assertNull(SuspendRules.parseDays(""));
        assertNull(SuspendRules.parseDays(null));
        assertEquals(168, SuspendRules.hours(7));
        assertTrue(SuspendRules.hours(SuspendRules.MAX_DAYS) <= 87600);
    }
    @Test public void keys() {
        assertEquals(0, SuspendRules.reasonIndex("scam"));
        assertEquals(5, SuspendRules.reasonIndex("other"));
        assertEquals(-1, SuspendRules.reasonIndex("nope"));
        assertEquals(3, SuspendRules.statusIndex("actioned"));
        assertEquals(-1, SuspendRules.statusIndex(""));
    }
    @Test public void reporting() {
        assertTrue(SuspendRules.canReport("a", "b"));
        assertFalse(SuspendRules.canReport("a", "a"));
        assertFalse(SuspendRules.canReport(null, "b"));
        assertFalse(SuspendRules.canReport("a", ""));
        assertEquals(500, SuspendRules.details("x".repeat(900)).length());
        assertEquals("hi", SuspendRules.details("  hi\u0000 "));
    }
    @Test public void times() {
        ZoneId utc = ZoneId.of("UTC");
        Instant now = Instant.parse("2026-11-02T10:00:00Z");
        assertEquals("Nov 9, 2026, 10:00 AM", SuspendRules.endsAt(7, now, utc));
        assertEquals("Nov 9, 2026, 3:20 PM", SuspendRules.when("2026-11-09T15:20:00.123456+00:00", utc));
        assertEquals("", SuspendRules.when("garbage", utc));
        assertEquals("", SuspendRules.when(null, utc));
        assertTrue(SuspendRules.active("2026-11-09T15:20:00+00:00", now));
        assertFalse(SuspendRules.active("2026-11-01T15:20:00+00:00", now));
        assertFalse(SuspendRules.active("null", now));
    }
}
