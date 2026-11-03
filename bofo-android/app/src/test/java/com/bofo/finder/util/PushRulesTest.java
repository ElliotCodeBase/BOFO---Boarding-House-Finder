package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

/** v2.3.0 phone notifications: the pure rules (who may see a push, which channel, when to register). No Android classes. */
public class PushRulesTest {

    @Test public void onlyTheThreeKindsAreKnown() {
        assertTrue(PushRules.known("dm")); assertTrue(PushRules.known("group")); assertTrue(PushRules.known("report"));
        assertFalse(PushRules.known("x")); assertFalse(PushRules.known(null)); assertFalse(PushRules.known(""));
    }

    @Test public void reportsAreForAdminAccountsOnly() {
        assertTrue(PushRules.allowedFor("report", "admin"));
        assertFalse(PushRules.allowedFor("report", "student"));
        assertFalse(PushRules.allowedFor("report", "owner"));
        assertFalse(PushRules.allowedFor("report", ""));       // nobody signed in / signed out
        assertFalse(PushRules.allowedFor("report", null));
    }

    @Test public void chatsNeedASignedInAccountButAnyRole() {
        for (String r : new String[]{"student", "owner", "admin"}) {
            assertTrue(PushRules.allowedFor("dm", r));
            assertTrue(PushRules.allowedFor("group", r));
        }
        assertFalse(PushRules.allowedFor("dm", ""));
        assertFalse(PushRules.allowedFor("group", null));
        assertFalse(PushRules.allowedFor("nonsense", "admin"));
    }

    @Test public void channelsSplitChatsGroupsAnnouncementsAndReports() {
        assertEquals(PushRules.CH_CHATS, PushRules.channel("dm", false));
        assertEquals(PushRules.CH_BOARDING, PushRules.channel("group", false));
        assertEquals(PushRules.CH_ANNOUNCE, PushRules.channel("group", true));
        assertEquals(PushRules.CH_REPORTS, PushRules.channel("report", false));
    }

    @Test public void aChatOnScreenMakesNoBanner() {
        assertTrue(PushRules.isOpenNow("dm", "c1", "c1", null));
        assertTrue(PushRules.isOpenNow("group", "b1", null, "b1"));
        assertFalse(PushRules.isOpenNow("dm", "c1", "c2", "c1"));       // a group id never silences a direct chat
        assertFalse(PushRules.isOpenNow("report", "r1", "r1", "r1"));
        assertFalse(PushRules.isOpenNow("dm", "", "", ""));
        assertFalse(PushRules.isOpenNow("dm", null, null, null));
    }

    @Test public void tagKeepsOneNotificationPerChat() {
        assertEquals("dm:abc", PushRules.tag("dm", "abc"));
        assertNotEquals(PushRules.tag("dm", "abc"), PushRules.tag("group", "abc"));
    }

    @Test public void previewCollapsesSpacesAndCutsLongText() {
        assertEquals("hello there", PushRules.preview("  hello \n  there ", 50));
        assertEquals("", PushRules.preview(null, 10));
        String cut = PushRules.preview("abcdefghijklmnopqrstuvwxyz", 10);
        assertTrue(cut.length() <= 10);
        assertTrue(cut.endsWith("\u2026"));
    }

    @Test public void registerWhenAccountTokenOrAgeChanges() {
        long now = 10L * 60 * 60 * 1000 * 10;
        assertTrue(PushRules.needsRegister("", "", 0, "u1", "t1", now));                       // first time
        assertFalse(PushRules.needsRegister("u1", "t1", now - 1000, "u1", "t1", now));          // same, just done
        assertTrue(PushRules.needsRegister("u1", "t1", now - 1000, "u2", "t1", now));           // another account on this phone
        assertTrue(PushRules.needsRegister("u1", "t1", now - 1000, "u1", "t2", now));           // new token
        assertTrue(PushRules.needsRegister("u1", "t1", now - 7L * 60 * 60 * 1000, "u1", "t1", now));   // older than 6 h
        assertFalse(PushRules.needsRegister("u1", "t1", 0, "", "t1", now));                      // signed out: nothing to register
        assertFalse(PushRules.needsRegister("u1", "t1", 0, "u1", null, now));
    }
}
