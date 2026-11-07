package com.bofo.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class PushRulesTest {
    private static Set<String> p(String... k) { return new HashSet<>(Arrays.asList(k)); }

    @Test public void roleGates() {
        assertTrue(PushRules.allowed("report", p(PermissionRules.REPORTS_ACT)));
        assertFalse(PushRules.allowed("report", p(PermissionRules.REPORTS_VIEW)));          // support: view only
        assertTrue(PushRules.allowed("idreview", p(PermissionRules.ID_REVIEW)));
        assertFalse(PushRules.allowed("idreview", p(PermissionRules.REPORTS_ACT)));
        assertTrue(PushRules.allowed("approval", p(PermissionRules.APPROVALS_DECIDE)));
        assertFalse(PushRules.allowed("approval", p(PermissionRules.APPROVALS_REQUEST)));   // a moderator asks, never decides
    }

    @Test public void staleNeedsAnyActionablePermission() {
        assertTrue(PushRules.allowed("stale", p(PermissionRules.PAYMENTS_VIEW)));
        assertTrue(PushRules.allowed("stale", p(PermissionRules.ID_REVIEW)));
        assertFalse(PushRules.allowed("stale", p(PermissionRules.DASHBOARD_VIEW)));
    }

    @Test public void unknownAndNullAreDropped() {
        assertFalse(PushRules.allowed("dm", PermissionRules.ALL));
        assertFalse(PushRules.allowed("group", PermissionRules.ALL));
        assertFalse(PushRules.allowed(null, PermissionRules.ALL));
        assertFalse(PushRules.allowed("report", null));
        assertFalse(PushRules.allowed("report", Collections.<String>emptySet()));
        assertTrue(PushRules.allowed("test", Collections.<String>emptySet()));
    }

    @Test public void tabs() {
        assertEquals(PushRules.TAB_REPORTS, PushRules.tabFor("report", ""));
        assertEquals(PushRules.TAB_IDS, PushRules.tabFor("idreview", null));
        assertEquals(PushRules.TAB_APPROVALS, PushRules.tabFor("approval", ""));
        assertEquals(PushRules.TAB_IDS, PushRules.tabFor("stale", "queue"));
        assertEquals(PushRules.TAB_REPORTS, PushRules.tabFor("stale", "reports"));
        assertEquals(PushRules.TAB_MORE, PushRules.tabFor("stale", "payments"));
        assertEquals(PushRules.TAB_HOME, PushRules.tabFor("stale", "???"));
        assertEquals(PushRules.TAB_NONE, PushRules.tabFor("dm", ""));
        assertEquals(PushRules.TAB_NONE, PushRules.tabFor(null, ""));
    }

    @Test public void channels() {
        assertEquals("bofo_reports", PushRules.channelFor("report"));
        assertEquals("bofo_admin", PushRules.channelFor("idreview"));
        assertEquals("bofo_admin", PushRules.channelFor("approval"));
    }

    @Test public void stage4iKindsFollowPermissionsAndTabs() {
        assertTrue(PushRules.allowed("escalated", p(PermissionRules.APPROVALS_DECIDE)));
        assertFalse(PushRules.allowed("escalated", p(PermissionRules.APPROVALS_REQUEST)));
        assertTrue(PushRules.allowed("decided", p(PermissionRules.APPROVALS_REQUEST)));
        assertFalse(PushRules.allowed("decided", p(PermissionRules.REPORTS_VIEW)));
        assertTrue(PushRules.allowed("unusual", p(PermissionRules.AUDIT_VIEW_ALL)));
        assertFalse(PushRules.allowed("unusual", p(PermissionRules.APPROVALS_DECIDE)));
        assertEquals(PushRules.TAB_REPORTS, PushRules.tabFor("escalated", ""));
        assertEquals(PushRules.TAB_APPROVALS, PushRules.tabFor("decided", ""));
        assertEquals(PushRules.TAB_MORE, PushRules.tabFor("unusual", ""));
        assertEquals(PushRules.CHANNEL_ADMIN, PushRules.channelFor("unusual"));
    }
}
