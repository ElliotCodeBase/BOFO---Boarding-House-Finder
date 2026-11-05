package com.bofo.finder.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReviewSpeedRulesTest {
    @Test public void pill() {
        assertEquals(ReviewSpeedRules.WAITING, ReviewSpeedRules.rowState("pending", false));
        assertEquals(ReviewSpeedRules.SECOND_OPINION, ReviewSpeedRules.rowState("pending", true));
        assertEquals(ReviewSpeedRules.APPROVED, ReviewSpeedRules.rowState("verified", true));
        assertEquals(ReviewSpeedRules.REJECTED, ReviewSpeedRules.rowState("rejected", true));
    }
    @Test public void flagOnlyWhenWaiting() {
        assertTrue(ReviewSpeedRules.canFlag("pending"));
        assertFalse(ReviewSpeedRules.canFlag("verified"));
        assertFalse(ReviewSpeedRules.canFlag(null));
    }
}
