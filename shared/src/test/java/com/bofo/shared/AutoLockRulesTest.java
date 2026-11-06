package com.bofo.shared;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoLockRulesTest {
    private static final long T0 = 1_000_000L;
    @Test public void locksAfterFiveMinutes() {
        assertFalse(AutoLockRules.shouldLock(true, T0, T0 + AutoLockRules.LIMIT_MS - 1));
        assertTrue(AutoLockRules.shouldLock(true, T0, T0 + AutoLockRules.LIMIT_MS));
    }
    @Test public void switchOffOrNotRecordedNeverLocks() {
        assertFalse(AutoLockRules.shouldLock(false, T0, T0 + 10 * AutoLockRules.LIMIT_MS));
        assertFalse(AutoLockRules.shouldLock(true, 0, T0 + 10 * AutoLockRules.LIMIT_MS));
    }
    @Test public void clockSetBackLocks() {
        assertTrue(AutoLockRules.shouldLock(true, T0, T0 - 1));
    }
}
