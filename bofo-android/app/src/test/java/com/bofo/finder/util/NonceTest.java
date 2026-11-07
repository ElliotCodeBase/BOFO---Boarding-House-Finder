package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class NonceTest {
    @Test public void sha256MatchesTheKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Nonce.sha256Hex("abc"));
    }
    @Test public void rawNonceIsLongUrlSafeAndDifferentEachTime() {
        String a = Nonce.newRaw(), b = Nonce.newRaw();
        assertEquals(43, a.length());
        assertTrue(a.matches("[A-Za-z0-9_-]+"));
        assertNotEquals(a, b);
    }
    @Test public void hashIs64LowerCaseHexAndNeverTheRawValue() {
        String raw = Nonce.newRaw(), h = Nonce.sha256Hex(raw);
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]+"));
        assertNotEquals(raw, h);
    }
}
