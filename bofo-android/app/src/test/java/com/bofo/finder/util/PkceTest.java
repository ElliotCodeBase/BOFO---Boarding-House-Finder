package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class PkceTest {
    @Test public void challengeMatchesRfc7636Example() {
        // RFC 7636 appendix B
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }

    @Test public void verifierIsLongRandomAndUrlSafe() {
        String a = Pkce.verifier(), b = Pkce.verifier();
        assertEquals(64, a.length());                       // 48 random bytes, base64url without padding
        assertTrue(a.matches("[A-Za-z0-9_-]+"));
        assertNotEquals(a, b);
        assertTrue(a.length() >= 43 && a.length() <= 128);   // RFC 7636 limits
    }
}
