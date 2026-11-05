package com.bofo.finder.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AdminActivityRulesTest {
    @Test public void labels() {
        assertEquals(0, AdminActivityRules.index("verification_approved"));
        assertEquals(8, AdminActivityRules.index("user_unban"));
        assertEquals(-1, AdminActivityRules.index("something_else"));
        assertEquals(-1, AdminActivityRules.index(null));
    }
}
