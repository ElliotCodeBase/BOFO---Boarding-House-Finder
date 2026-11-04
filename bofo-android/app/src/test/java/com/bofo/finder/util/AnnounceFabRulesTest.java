package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

/** v2.4.7: floating Announce button rules (plain JVM). */
public class AnnounceFabRulesTest {
    @Test public void ownerOnly() {
        assertTrue(AnnounceFabRules.visible(true, false, false, true, false, false));
        assertFalse("students never see it", AnnounceFabRules.visible(false, false, false, true, false, false));
    }

    @Test public void hiddenWhileTypingOrKeyboardOpen() {
        assertFalse(AnnounceFabRules.visible(true, true, false, true, false, false));
        assertFalse(AnnounceFabRules.visible(true, false, true, true, false, false));
    }

    @Test public void hiddenWhileLoadingFailedOrOffline() {
        assertFalse(AnnounceFabRules.visible(true, false, false, false, false, false));
        assertFalse(AnnounceFabRules.visible(true, false, false, true, true, false));
        assertFalse(AnnounceFabRules.visible(true, false, false, true, false, true));
    }

    @Test public void tapOpensTheFormDirectlyWhenAiIsOff() {
        assertEquals(AnnounceFabRules.Tap.OPEN_FORM, AnnounceFabRules.onTap(false));
        assertEquals(AnnounceFabRules.Tap.SPEED_DIAL, AnnounceFabRules.onTap(true));
    }

    @Test public void shrinksDownExtendsUpAndAtTop() {
        assertFalse(AnnounceFabRules.extended(true, 12, false));
        assertTrue(AnnounceFabRules.extended(false, -3, false));
        assertTrue("top of the list always extends", AnnounceFabRules.extended(false, 5, true));
        assertTrue("no movement keeps the state", AnnounceFabRules.extended(true, 0, false));
        assertFalse(AnnounceFabRules.extended(false, 0, false));
    }

    @Test public void dialClosesWhenTheButtonGoes() {
        assertTrue(AnnounceFabRules.keepDialOpen(true, true));
        assertFalse(AnnounceFabRules.keepDialOpen(true, false));
        assertFalse(AnnounceFabRules.keepDialOpen(false, true));
    }
}
