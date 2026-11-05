package com.bofo.finder.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdminErrorRulesTest {
    @Test public void knownAnswers() {
        assertEquals("Someone already handled this.", AdminErrorRules.reason(400, "{\"message\":\"this report was already handled\"}"));
        assertTrue(AdminErrorRules.reason(403, "").startsWith("Only a signed-in admin"));
        assertEquals("It no longer exists.", AdminErrorRules.reason(404, ""));
        assertEquals("That was already done.", AdminErrorRules.reason(409, "duplicate"));
        assertTrue(AdminErrorRules.reason(503, "").startsWith("The server had a problem"));
    }
    @Test public void unknownGivesNothing() { assertEquals("", AdminErrorRules.reason(400, "weird")); }
    @Test public void neverShowsCodes() {
        String s = AdminErrorRules.plain("Could not save the decision.", new RuntimeException("boom"));
        assertEquals("Could not save the decision. Please try again.", s);
        assertFalse(s.contains("HTTP"));
    }
    @Test public void headlineGetsDot() { assertEquals("Oops. Please try again.", AdminErrorRules.plain("Oops", null)); }
}
