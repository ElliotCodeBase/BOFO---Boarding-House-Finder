package com.bofo.shared;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Test;

public class PermissionRulesTest {
    private static boolean has(String role, String key) { return PermissionRules.has(role, null, null, key); }

    @Test public void ownerHasEverythingAndCannotBeLimited() {
        for (String k : PermissionRules.ALL) assertTrue(k, has("owner", k));
        assertTrue(PermissionRules.has("owner", null, Arrays.asList("ban_permanent"), "ban_permanent"));   // a deny never limits an owner
    }
    @Test public void ownerOnlyListIsNeverInASubAdminPreset() {
        for (String role : new String[] {"admin", "moderator", "support"})
            for (String k : PermissionRules.OWNER_ONLY) assertFalse(role + " must not have " + k, has(role, k));
        assertEquals(15, PermissionRules.OWNER_ONLY.size());   // the 12 prompt items; some map to 2-3 keys
    }
    @Test public void adminCanDoEverythingElse() {
        for (String k : new String[] {"id_review", "reports_act", "suspend_short", "suspend_long", "approvals_decide", "ai_answer"})
            assertTrue(k, has("admin", k));
        assertFalse(has("admin", "ban_permanent"));
        assertFalse(has("admin", "unban"));
    }
    @Test public void moderatorLimits() {
        assertTrue(has("moderator", "id_review"));
        assertTrue(has("moderator", "suspend_short"));
        assertFalse(has("moderator", "suspend_long"));
        assertFalse(has("moderator", "approvals_decide"));
        assertTrue(has("moderator", "approvals_request"));
    }
    @Test public void supportIsReadOnly() {
        assertTrue(has("support", "reports_view"));
        assertTrue(has("support", "ai_answer"));
        assertFalse(has("support", "reports_act"));
        assertFalse(has("support", "id_review"));
        assertFalse(has("support", "suspend_short"));
    }
    @Test public void unknownRoleOrKeyHasNothing() {
        assertFalse(has(null, "dashboard_view"));
        assertFalse(has("hacker", "dashboard_view"));
        assertFalse(has("moderator", "made_up"));
        assertFalse(has("moderator", null));
        assertTrue(PermissionRules.preset("nope").isEmpty());
    }
    @Test public void grantsAndDenies() {
        assertTrue(PermissionRules.has("support", Arrays.asList("id_review"), null, "id_review"));
        assertFalse(PermissionRules.has("moderator", null, Arrays.asList("id_review"), "id_review"));
        assertFalse(PermissionRules.has("moderator", Arrays.asList("id_review"), Arrays.asList("id_review"), "id_review"));   // deny wins
        assertTrue(PermissionRules.canGrant("moderator", "id_review"));
        assertFalse(PermissionRules.canGrant("moderator", "csv_export"));
        assertFalse(PermissionRules.canGrant("admin", "members_manage"));
        assertFalse(PermissionRules.canGrant("moderator", "unknown"));
    }
    @Test public void suspendKeyThreshold() {
        assertEquals("suspend_short", PermissionRules.suspendKey(24));
        assertEquals("suspend_short", PermissionRules.suspendKey(168));
        assertEquals("suspend_long", PermissionRules.suspendKey(169));
        assertEquals("suspend_long", PermissionRules.suspendKey(87600));
    }
    @Test public void approvalButtonAndDeciding() {
        HashSet<String> mod = new HashSet<>(PermissionRules.preset("moderator"));
        HashSet<String> adm = new HashSet<>(PermissionRules.preset("admin"));
        HashSet<String> own = new HashSet<>(PermissionRules.ALL);
        assertTrue(PermissionRules.needsApproval(mod, "ban_permanent"));
        assertFalse(PermissionRules.needsApproval(own, "ban_permanent"));          // the owner just does it
        assertFalse(PermissionRules.needsApproval(Collections.<String>emptySet(), "ban_permanent"));   // no request right: no button at all
        assertEquals("ban_permanent", PermissionRules.approvalKey("report_ban"));
        assertEquals("suspend_long", PermissionRules.approvalKey("report_suspend"));
        assertNull(PermissionRules.approvalKey("other"));
        assertFalse(PermissionRules.canDecide(adm, "ban_permanent", "u1", "u2"));  // an admin cannot approve a ban
        assertTrue(PermissionRules.canDecide(adm, "suspend_long", "u1", "u2"));
        assertTrue(PermissionRules.canDecide(own, "ban_permanent", "u1", "u2"));
        assertFalse(PermissionRules.canDecide(own, "ban_permanent", "u1", "u1"));  // never your own request
        assertFalse(PermissionRules.canDecide(mod, "suspend_long", "u1", "u2"));
    }
    @Test public void conflictOfInterest() {
        assertTrue(PermissionRules.conflict("me", "me", "x", null));
        assertTrue(PermissionRules.conflict("me", "x", "me", null));
        assertTrue(PermissionRules.conflict("me", "x", "y", "me"));
        assertFalse(PermissionRules.conflict("me", "x", "y", "z"));
        assertTrue(PermissionRules.conflict(null, "x", "y", null));
    }
}
