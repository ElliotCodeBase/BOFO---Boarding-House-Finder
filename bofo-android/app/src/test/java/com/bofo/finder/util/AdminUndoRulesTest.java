package com.bofo.finder.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bofo.finder.util.AdminUndoRules.Entry;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class AdminUndoRulesTest {
    @Test public void kinds() {
        assertEquals(AdminUndoRules.SUSPEND, AdminUndoRules.kind("report_suspend"));
        assertEquals(AdminUndoRules.RELIST, AdminUndoRules.kind("listing_takedown"));
        assertEquals(AdminUndoRules.RELIST, AdminUndoRules.kind("report_takedown"));
        assertEquals(AdminUndoRules.REOPEN, AdminUndoRules.kind("report_dismiss"));
        assertEquals(AdminUndoRules.UNBAN, AdminUndoRules.kind("report_ban"));
        assertEquals(AdminUndoRules.NONE, AdminUndoRules.kind("boost_refunded"));
        assertEquals(AdminUndoRules.NONE, AdminUndoRules.kind("verification_approved"));
        assertEquals(AdminUndoRules.NONE, AdminUndoRules.kind("user_unsuspend"));
        assertEquals(AdminUndoRules.NONE, AdminUndoRules.kind(null));
    }

    @Test public void filterListsEveryAction() {
        String f = AdminUndoRules.actionFilter();
        assertTrue(f.startsWith("action=in.(") && f.endsWith(")"));
        assertTrue(f.contains("report_suspend") && f.contains("user_unsuspend") && f.contains("report_reopen") && f.contains("listing_relist"));
    }

    @Test public void undoneOnlyByMatchingNewerRow() {
        Entry susp = new Entry("report_suspend", "u1", "r1");
        List<Entry> newer = new ArrayList<>();
        assertTrue(AdminUndoRules.canUndo(susp, newer));
        newer.add(new Entry("user_unsuspend", "u2", ""));              // another person: still undoable
        assertTrue(AdminUndoRules.canUndo(susp, newer));
        newer.add(new Entry("user_unsuspend", "u1", ""));
        assertFalse(AdminUndoRules.canUndo(susp, newer));
        assertTrue(AdminUndoRules.undone(susp, newer));
    }

    @Test public void dismissMatchesByReportId() {
        Entry d = new Entry("report_dismiss", "u1", "r1");
        List<Entry> newer = new ArrayList<>();
        newer.add(new Entry("report_reopen", "u1", "r2"));
        assertTrue(AdminUndoRules.canUndo(d, newer));
        newer.add(new Entry("report_reopen", "u1", "r1"));
        assertFalse(AdminUndoRules.canUndo(d, newer));
        assertFalse(AdminUndoRules.canUndo(new Entry("report_dismiss", "u1", "null"), null));    // no report id: cannot reopen
    }

    @Test public void noButtonWithoutTarget() {
        assertFalse(AdminUndoRules.canUndo(new Entry("listing_takedown", "", ""), null));
        assertFalse(AdminUndoRules.canUndo(new Entry("boost_refunded", "p1", ""), null));
    }

    @Test public void swipe() {
        assertTrue(AdminUndoRules.swipeCommits(-500f, 1000));
        assertTrue(AdminUndoRules.swipeCommits(400f, 1000));
        assertFalse(AdminUndoRules.swipeCommits(-300f, 1000));
        assertFalse(AdminUndoRules.swipeCommits(-900f, 0));
    }
}
