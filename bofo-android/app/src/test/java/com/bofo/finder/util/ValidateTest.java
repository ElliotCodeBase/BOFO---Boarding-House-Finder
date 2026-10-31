package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class ValidateTest {
    @Test public void blankDetectsNullAndWhitespace() {
        assertTrue(Validate.blank(null));
        assertTrue(Validate.blank("  \t "));
        assertFalse(Validate.blank(" a "));
    }

    @Test public void cleanTrimsStripsControlCharsAndCuts() {
        assertEquals("", Validate.clean(null, 10));
        assertEquals("hello", Validate.clean("  hel\u0007lo ", 10));
        assertEquals("a\nb", Validate.clean("a\nb", 10));         // newline and tab are kept
        assertEquals("abcd", Validate.clean("abcdefgh", 4));
    }

    private static byte[] bytes(int n, int... head) {
        byte[] b = new byte[n];
        for (int i = 0; i < head.length; i++) b[i] = (byte) head[i];
        return b;
    }

    @Test public void imageOkAcceptsJpegAndPngOnly() {
        assertTrue(Validate.imageOk(bytes(100, 0xFF, 0xD8, 0xFF), 1000));
        assertTrue(Validate.imageOk(bytes(100, 0x89, 'P', 'N', 'G'), 1000));
        assertFalse(Validate.imageOk(bytes(100, 'G', 'I', 'F', '8'), 1000));
    }

    @Test public void imageOkChecksSizeLimits() {
        assertFalse(Validate.imageOk(null, 1000));
        assertFalse(Validate.imageOk(bytes(11, 0xFF, 0xD8), 1000));      // too short to be an image
        assertFalse(Validate.imageOk(bytes(2000, 0xFF, 0xD8), 1000));    // over the limit
        assertTrue(Validate.imageOk(bytes(1000, 0xFF, 0xD8), 1000));     // exactly at the limit
    }
}
