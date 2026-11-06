package com.bofo.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RefundRulesTest {
    @Test public void onlyPaidCanBeRefunded() {
        assertTrue(RefundRules.canRefund("paid"));
        for (String s : new String[]{"pending", "failed", "expired", "refunded", "", null}) assertFalse(s, RefundRules.canRefund(s));
    }
    @Test public void reasonIsCleaned() {
        assertEquals("", RefundRules.cleanReason(null));
        assertEquals("double charge", RefundRules.cleanReason("  double \n\n charge  "));
        assertEquals(RefundRules.MAX_REASON, RefundRules.cleanReason(new String(new char[500]).replace('\0', 'a')).length());
    }
    @Test public void reasonNeedsFiveCharacters() {
        assertFalse(RefundRules.validReason(null));
        assertFalse(RefundRules.validReason("   ok  "));
        assertTrue(RefundRules.validReason("dupe!"));
    }
}
