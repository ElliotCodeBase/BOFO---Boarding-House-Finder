package com.bofo.finder.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RejectReasonRulesTest {
    @Test public void chipOnly() { assertEquals("Photo is blurry", RejectReasonRules.compose("Photo is blurry", false, "")); }
    @Test public void chipAndNote() { assertEquals("ID is expired: ended 2023", RejectReasonRules.compose("ID is expired", false, "  ended   2023 ")); }
    @Test public void otherNeedsNote() {
        assertNull(RejectReasonRules.compose("Other", true, "  "));
        assertEquals("Name differs", RejectReasonRules.compose("Other", true, "Name differs"));
    }
    @Test public void noChip() { assertNull(RejectReasonRules.compose(null, false, "x")); }
    @Test public void capped() { assertEquals(RejectReasonRules.MAX, RejectReasonRules.compose("A", false, "x".repeat(500)).length()); }
}
