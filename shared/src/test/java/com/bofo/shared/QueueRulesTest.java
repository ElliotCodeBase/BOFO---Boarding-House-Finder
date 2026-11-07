package com.bofo.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QueueRulesTest {
    private static final long H = 3_600_000L, D = 24 * H;

    @Test public void tiersFollowOneAndThreeDays() {
        assertEquals(QueueRules.TIER_FRESH, QueueRules.tier(0));
        assertEquals(QueueRules.TIER_FRESH, QueueRules.tier(D - 1));
        assertEquals(QueueRules.TIER_WAITING, QueueRules.tier(D));
        assertEquals(QueueRules.TIER_WAITING, QueueRules.tier(3 * D - 1));
        assertEquals(QueueRules.TIER_LATE, QueueRules.tier(3 * D));
        assertEquals(QueueRules.TIER_FRESH, QueueRules.tier(-1));               // unknown age is never red
    }

    @Test public void waitLabelsUseWords() {
        assertEquals("Waiting under 1 h", QueueRules.waitLabel(5 * 60_000L));
        assertEquals("Waiting 5 h", QueueRules.waitLabel(5 * H));
        assertEquals("Waiting 1 day", QueueRules.waitLabel(D + H));
        assertEquals("Waiting 4 days", QueueRules.waitLabel(4 * D));
    }

    @Test public void ageParsesServerTimestamps() {
        long now = java.time.OffsetDateTime.parse("2026-11-07T12:00:00+00:00").toInstant().toEpochMilli();
        assertEquals(2 * H, QueueRules.ageMillis("2026-11-07T10:00:00.123456+00:00", now) + 123);
        assertEquals(D, QueueRules.ageMillis("2026-11-06T12:00:00Z", now));
        assertEquals(-1, QueueRules.ageMillis(null, now));
        assertEquals(-1, QueueRules.ageMillis("null", now));
        assertEquals(-1, QueueRules.ageMillis("not a date", now));
    }

    @Test public void claimLine() {
        assertEquals("Being handled by Ana", QueueRules.claimLine(" Ana "));
        assertEquals("Being handled by a colleague", QueueRules.claimLine(""));
        assertEquals("Being handled by a colleague", QueueRules.claimLine(null));
    }

    @Test public void ladderIsWarningThreeSevenBan() {
        assertEquals("warning", QueueRules.strikeStep(0));
        assertEquals("suspend_3d", QueueRules.strikeStep(1));
        assertEquals("suspend_7d", QueueRules.strikeStep(2));
        assertEquals("ban", QueueRules.strikeStep(3));
        assertEquals("ban", QueueRules.strikeStep(40));
        assertEquals("warning", QueueRules.strikeStep(-5));
        assertTrue(QueueRules.stepNeedsBanPermission("ban"));
        assertFalse(QueueRules.stepNeedsBanPermission("suspend_7d"));
        assertTrue(QueueRules.strikeLabel("warning").contains("not messaged"));
    }

    @Test public void textLimitsMatchTheServer() {
        assertFalse(QueueRules.noteOk("abcd")); assertTrue(QueueRules.noteOk(" abcde ")); assertFalse(QueueRules.noteOk(null));
        assertFalse(QueueRules.noteOk(new String(new char[301]).replace('\0', 'x'))); assertTrue(QueueRules.noteOk(new String(new char[300]).replace('\0', 'x')));
        assertFalse(QueueRules.appealOk("too short")); assertTrue(QueueRules.appealOk("0123456789"));
        assertFalse(QueueRules.appealOk(new String(new char[1001]).replace('\0', 'x')));
    }

    @Test public void lendableKeysAreTheSixAndNeverOwnerOnlyAdminTools() {
        assertEquals(6, QueueRules.LENDABLE.size());
        assertEquals(6, QueueRules.LEND_KEYS.length);
        assertEquals(QueueRules.LEND_KEYS.length, QueueRules.LEND_LABELS.length);
        for (String k : QueueRules.LEND_KEYS) assertTrue(k, QueueRules.LENDABLE.contains(k));
        assertFalse(QueueRules.LENDABLE.contains(PermissionRules.MEMBERS_MANAGE));
        assertFalse(QueueRules.LENDABLE.contains(PermissionRules.MFA_MANAGE));
        assertFalse(QueueRules.LENDABLE.contains(PermissionRules.APP_CONFIG));
        assertFalse(QueueRules.LENDABLE.contains(PermissionRules.USER_DATA_DELETE));
        assertFalse(QueueRules.LENDABLE.contains(PermissionRules.ID_VIEW_RAW));
        assertEquals("Lift a ban", QueueRules.lendLabel("unban"));
    }

    @Test public void minutesLeftNeverNegative() {
        long now = java.time.OffsetDateTime.parse("2026-11-07T12:00:00Z").toInstant().toEpochMilli();
        assertEquals(30, QueueRules.minutesLeft("2026-11-07T12:30:00Z", now));
        assertEquals(0, QueueRules.minutesLeft("2026-11-07T11:00:00Z", now));
        assertEquals(0, QueueRules.minutesLeft(null, now));
    }

    @Test public void unusualLine() {
        assertEquals("1 sensitive action in the last hour, 3 in 24 hours", QueueRules.unusualLine(1, 3));
        assertEquals("12 sensitive actions in the last hour, 12 in 24 hours", QueueRules.unusualLine(12, 12));
    }
}
