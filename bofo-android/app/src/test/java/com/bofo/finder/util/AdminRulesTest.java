package com.bofo.finder.util;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class AdminRulesTest {
    @Test public void reasons() {
        assertEquals("", AdminRules.clean(null));
        assertEquals("blurry", AdminRules.clean("  blurry \n"));
        assertEquals(300, AdminRules.clean("x".repeat(500)).length());
        assertTrue(AdminRules.reasonOk(true, null));
        assertFalse(AdminRules.reasonOk(false, "   "));
        assertTrue(AdminRules.reasonOk(false, "ID is cut off"));
        assertFalse(AdminRules.takedownOk(""));
        assertTrue(AdminRules.takedownOk("fake listing"));
    }
    @Test public void refundAndTakedown() {
        assertTrue(AdminRules.canRefund("paid"));
        assertFalse(AdminRules.canRefund("pending"));
        assertFalse(AdminRules.canRefund("refunded"));
        assertFalse(AdminRules.canRefund(null));
        assertTrue(AdminRules.canTakeDown("active"));
        assertTrue(AdminRules.canTakeDown("unlisted"));
        assertFalse(AdminRules.canTakeDown("taken_down"));
    }
    @Test public void namesAndKinds() {
        assertEquals("Unknown user", AdminRules.who(null));
        assertEquals("Unknown user", AdminRules.who("  "));
        assertEquals("Ana", AdminRules.who(" Ana "));
        assertTrue(AdminRules.isReport(false));
        assertFalse(AdminRules.isReport(true));
    }
    @Test public void inListKeepsOnlyUuids() {
        String a = "123e4567-e89b-12d3-a456-426614174000", b = "123e4567-e89b-12d3-a456-426614174001";
        assertEquals(a + "," + b, AdminRules.inList(Arrays.asList(a, "x),id=neq.1", b, a, null)));
        assertEquals("", AdminRules.inList(Arrays.asList("nope")));
    }
}
