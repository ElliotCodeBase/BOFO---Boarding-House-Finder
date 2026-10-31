package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class VerificationRulesTest {
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4, 5, 6, 7, 8};

    @Test public void statusAndSubmit() {
        assertEquals(VerificationRules.State.PENDING, VerificationRules.state(" Pending "));
        assertEquals(VerificationRules.State.VERIFIED, VerificationRules.state("verified"));
        assertEquals(VerificationRules.State.REJECTED, VerificationRules.state("rejected"));
        assertEquals(VerificationRules.State.UNVERIFIED, VerificationRules.state("unverified"));
        assertEquals(VerificationRules.State.UNVERIFIED, VerificationRules.state(null));
        assertEquals(VerificationRules.State.UNVERIFIED, VerificationRules.state("???"));
        assertTrue(VerificationRules.canSubmit(VerificationRules.State.UNVERIFIED));
        assertTrue(VerificationRules.canSubmit(VerificationRules.State.REJECTED));
        assertFalse(VerificationRules.canSubmit(VerificationRules.State.PENDING));
        assertFalse(VerificationRules.canSubmit(VerificationRules.State.VERIFIED));
    }

    @Test public void effectiveState() {
        assertEquals(VerificationRules.State.VERIFIED, VerificationRules.effective(true, "rejected"));
        assertEquals(VerificationRules.State.PENDING, VerificationRules.effective(false, "pending"));
        assertEquals(VerificationRules.State.UNVERIFIED, VerificationRules.effective(false, null));
    }

    @Test public void missingSlotAndFormats() {
        assertEquals(VerificationRules.FRONT, VerificationRules.firstMissing(null, JPG, JPG));
        assertEquals(VerificationRules.BACK, VerificationRules.firstMissing(JPG, new byte[0], JPG));
        assertEquals(VerificationRules.SELFIE, VerificationRules.firstMissing(JPG, PNG, null));
        assertEquals(-1, VerificationRules.firstMissing(JPG, PNG, JPG));
        assertTrue(VerificationRules.isJpeg(JPG));
        assertFalse(VerificationRules.isJpeg(PNG));
        assertFalse(VerificationRules.isJpeg(null));
        assertEquals(".jpg", VerificationRules.ext(JPG));
        assertEquals("image/jpeg", VerificationRules.mime(JPG));
        assertEquals(".png", VerificationRules.ext(PNG));
        assertEquals("image/png", VerificationRules.mime(PNG));
    }

    @Test public void idTypes() {
        assertEquals("Passport", VerificationRules.idType(2));
        assertEquals(VerificationRules.ID_TYPES[0], VerificationRules.idType(-1));
        assertEquals(VerificationRules.ID_TYPES[0], VerificationRules.idType(99));
    }
}
