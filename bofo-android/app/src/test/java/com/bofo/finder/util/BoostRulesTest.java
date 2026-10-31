package com.bofo.finder.util;

import static org.junit.Assert.*;

import java.time.ZoneId;
import org.junit.Test;

public class BoostRulesTest {
    @Test public void statusMapping() {
        assertEquals(BoostRules.Pay.PAID, BoostRules.pay("paid"));
        assertEquals(BoostRules.Pay.PENDING, BoostRules.pay(" Pending "));
        assertEquals(BoostRules.Pay.REFUNDED, BoostRules.pay("refunded"));
        assertEquals(BoostRules.Pay.UNKNOWN, BoostRules.pay("weird"));
        assertEquals(BoostRules.Pay.UNKNOWN, BoostRules.pay(null));
        assertTrue(BoostRules.isFinal(BoostRules.Pay.EXPIRED));
        assertFalse(BoostRules.isFinal(BoostRules.Pay.PENDING));
        assertFalse(BoostRules.isFinal(BoostRules.Pay.UNKNOWN));
    }
    @Test public void pollingDelay() {
        assertEquals(3, BoostRules.pollDelaySeconds(0));
        assertEquals(5, BoostRules.pollDelaySeconds(1));
        assertEquals(10, BoostRules.pollDelaySeconds(50));
        assertEquals(3, BoostRules.pollDelaySeconds(-4));
    }
    @Test public void checkoutUrlGuard() {
        assertEquals("https://pm.link/abc?x=1", BoostRules.safeCheckoutUrl(" https://pm.link/abc?x=1 "));
        assertNull(BoostRules.safeCheckoutUrl("http://pm.link/abc"));
        assertNull(BoostRules.safeCheckoutUrl("javascript:alert(1)"));
        assertNull(BoostRules.safeCheckoutUrl("intent://x#Intent;end"));
        assertNull(BoostRules.safeCheckoutUrl("https://"));
        assertNull(BoostRules.safeCheckoutUrl("https://user@evil.com/x"));
        assertNull(BoostRules.safeCheckoutUrl("https://localhost/x"));
        assertNull(BoostRules.safeCheckoutUrl("https://a.com/x y"));
        assertNull(BoostRules.safeCheckoutUrl(null));
    }
    @Test public void missingFunction() {
        assertTrue(BoostRules.functionMissing(404, ""));
        assertTrue(BoostRules.functionMissing(400, "{\"message\":\"Requested function was not found\"}"));
        assertFalse(BoostRules.functionMissing(500, "boom"));
    }
    @Test public void planLineAndCanPay() {
        assertEquals("Weekly \u00B7 7 days  -  \u20B199", BoostRules.planLine("Weekly \u00B7 7 days", 7, 99));
        assertEquals("Pro - 1 day  -  \u20B1149.50", BoostRules.planLine("Pro", 1, 149.5));
        assertTrue(BoostRules.canPay("a", "b", false));
        assertFalse(BoostRules.canPay("a", "b", true));
        assertFalse(BoostRules.canPay(null, "b", false));
        assertFalse(BoostRules.canPay("a", "", false));
    }
    @Test public void dayLabel() {
        assertEquals("Oct 30, 2026", BoostRules.day("2026-10-30T11:25:00.123456+00:00", ZoneId.of("UTC")));
        assertEquals("Oct 30, 2026", BoostRules.day("2026-10-30T11:25:00Z", ZoneId.of("UTC")));
        assertEquals("", BoostRules.day("nonsense", ZoneId.of("UTC")));
        assertEquals("", BoostRules.day(null, ZoneId.of("UTC")));
    }
}
