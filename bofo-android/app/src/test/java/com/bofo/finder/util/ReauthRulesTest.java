package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class ReauthRulesTest {
    @Test public void sameAccountNeedsTheSameNonEmptyId() {
        assertTrue(ReauthRules.sameAccount("AB-12", "ab-12"));
        assertFalse(ReauthRules.sameAccount("ab-12", "cd-34"));
        assertFalse(ReauthRules.sameAccount(null, "ab-12"));
        assertFalse(ReauthRules.sameAccount("ab-12", null));
        assertFalse(ReauthRules.sameAccount("", ""));
    }
    @Test public void codeIsSixToEightDigits() {
        assertEquals("123456", ReauthRules.cleanCode(" 123 456 "));
        assertEquals("12345678", ReauthRules.cleanCode("12345678"));
        assertNull(ReauthRules.cleanCode("12345"));
        assertNull(ReauthRules.cleanCode("123456789"));
        assertNull(ReauthRules.cleanCode("abcdef"));
        assertNull(ReauthRules.cleanCode(null));
    }
}
