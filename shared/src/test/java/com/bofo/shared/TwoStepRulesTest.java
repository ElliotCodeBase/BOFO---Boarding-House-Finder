package com.bofo.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TwoStepRulesTest {
    @Test public void codes() {
        assertEquals("123456", TwoStepRules.cleanCode(" 123 456 "));
        assertEquals("123456", TwoStepRules.cleanCode("12345678"));
        assertEquals("", TwoStepRules.cleanCode(null));
        assertEquals("12", TwoStepRules.cleanCode("a1b2"));
        assertTrue(TwoStepRules.codeReady("123 456")); assertFalse(TwoStepRules.codeReady("12345")); assertFalse(TwoStepRules.codeReady(null));
    }
    @Test public void secretIsGrouped() {
        assertEquals("JBSW Y3DP EHPK 3PXP", TwoStepRules.groupSecret("jbswy3dpehpk3pxp"));
        assertEquals("ABCD E", TwoStepRules.groupSecret("ab cd e"));
        assertEquals("", TwoStepRules.groupSecret(null));
    }
    @Test public void onlyAuthenticatorLinks() {
        assertTrue(TwoStepRules.validUri("otpauth://totp/BOFO:me@x.com?secret=ABC&issuer=BOFO"));
        assertFalse(TwoStepRules.validUri("https://evil.example/x")); assertFalse(TwoStepRules.validUri("intent://x#Intent;end")); assertFalse(TwoStepRules.validUri(null));
    }
}
