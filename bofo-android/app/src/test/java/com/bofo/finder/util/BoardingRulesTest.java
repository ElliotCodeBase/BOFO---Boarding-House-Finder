package com.bofo.finder.util;

import static org.junit.Assert.*;

import com.bofo.finder.model.GroupMessage;
import com.bofo.finder.service.BhGroupService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** v2.2.0 Boarding step 2: the pure group-chat rules (merge, labels, error classes, code cleaning). No network, no Android classes. */
public class BoardingRulesTest {

    private static BoardingRules.Row srv(String id, String at, boolean mine, String role) {
        return new BoardingRules.Row(id, mine ? "me" : "other", "N" + id, role, "b" + id, null, at, mine, false, BoardingRules.State.SENT);
    }

    @Test public void mergeSortsServerRowsOldestFirstDropsDuplicatesAndKeepsQueuedAtTheEnd() {
        List<BoardingRules.Row> server = new ArrayList<>(Arrays.asList(
                srv("b", "2026-11-02T10:05:00+00:00", false, "student"), srv("a", "2026-11-02T10:00:00+00:00", true, "owner"), srv("b", "2026-11-02T10:05:00+00:00", false, "student")));
        List<BoardingRules.Row> queued = Collections.singletonList(BoardingRules.queued("q1", "me", "", "student", "hi", 5L, BoardingRules.State.PENDING));
        List<BoardingRules.Row> out = BoardingRules.merge(server, queued);
        assertEquals(3, out.size());
        assertEquals("a", out.get(0).id);
        assertEquals("b", out.get(1).id);
        assertEquals("q1", out.get(2).id);
        assertEquals(BoardingRules.State.PENDING, out.get(2).state);
    }

    @Test public void queuedRowOfAnIdAlreadyOnTheServerIsNotShownTwice() {
        List<BoardingRules.Row> server = Collections.singletonList(srv("a", "2026-11-02T10:00:00+00:00", true, "student"));
        List<BoardingRules.Row> queued = Collections.singletonList(BoardingRules.queued("a", "me", "", "student", "x", 1L, BoardingRules.State.PENDING));
        assertEquals(1, BoardingRules.merge(server, queued).size());
    }

    @Test public void newestAndOldestIgnoreQueuedRows() {
        List<BoardingRules.Row> rows = Arrays.asList(srv("a", "2026-11-02T10:00:00+00:00", false, "student"), srv("b", "2026-11-02T11:00:00+00:00", false, "student"),
                BoardingRules.queued("q", "me", "", "student", "x", 9L, BoardingRules.State.PENDING));
        assertEquals("2026-11-02T11:00:00+00:00", BoardingRules.newestServerTime(rows));
        assertEquals("2026-11-02T10:00:00+00:00", BoardingRules.oldestServerTime(rows));
        assertNull(BoardingRules.newestServerTime(Collections.emptyList()));
    }

    @Test public void displayNameIsYouForMineElseNameElseRoleWord() {
        assertEquals("You", BoardingRules.displayName(srv("a", "t", true, "student"), "You"));
        assertEquals("Na", BoardingRules.displayName(srv("a", "t", false, "student"), "You"));
        BoardingRules.Row noName = new BoardingRules.Row("x", "o", "  ", "owner", "b", null, "t", false, false, BoardingRules.State.SENT);
        assertEquals("Owner", BoardingRules.displayName(noName, "You"));
        assertTrue(noName.fromOwner());
    }

    @Test public void initialHandlesBlankNames() {
        assertEquals("M", BoardingRules.initial(" maria "));
        assertEquals("?", BoardingRules.initial("   "));
        assertEquals("?", BoardingRules.initial(null));
    }

    @Test public void joinErrorsAreClassifiedFromTheServerText() {
        assertEquals(BoardingRules.JoinProblem.BAD_CODE, BoardingRules.classifyJoin("HTTP 400: {\"message\":\"invalid code\"}"));
        assertEquals(BoardingRules.JoinProblem.REMOVED, BoardingRules.classifyJoin("... you were removed from this group"));
        assertEquals(BoardingRules.JoinProblem.OTHER_HOUSE, BoardingRules.classifyJoin("leave your current group first"));
        assertEquals(BoardingRules.JoinProblem.STUDENTS_ONLY, BoardingRules.classifyJoin("students only"));
        assertEquals(BoardingRules.JoinProblem.SUSPENDED, BoardingRules.classifyJoin("your account cannot join groups right now"));
        assertEquals(BoardingRules.JoinProblem.OTHER, BoardingRules.classifyJoin(null));
    }

    @Test public void aRefusedSendIsBlockedOnlyForRowLevelSecurity() {
        assertTrue(BoardingRules.sendBlocked("new row violates row-level security policy for table"));
        assertTrue(BoardingRules.sendBlocked("{\"code\":\"42501\"}"));
        assertFalse(BoardingRules.sendBlocked("timeout"));
        assertFalse(BoardingRules.sendBlocked(null));
    }

    @Test public void inviteCodesAreCleanedBeforeSending() {
        assertEquals("ABCD2345", BhGroupService.normalizeCode(" abcd-2345 "));
        assertEquals("ABCD2345", BhGroupService.normalizeCode("ab cd 23 45"));
        assertEquals("", BhGroupService.normalizeCode(null));
        assertEquals(GroupMessage.OWNER, "owner");
    }

    // ---- step 3: announcements, pinned banner, report a message ----------------------------------------------------

    private static BoardingRules.Row ann(String id, boolean mine, boolean acked, boolean pinned, BoardingRules.State st) {
        return new BoardingRules.Row(id, mine ? "me" : "owner1", "Owner", "owner", "Water is off tomorrow 8-12", "Water cut", "2026-11-02T10:00:00+00:00",
                mine, true, pinned, acked, 0, st);
    }

    @Test public void gotItShowsOnlyOnSomeoneElsesServerAnnouncementNotYetAcknowledged() {
        assertTrue(BoardingRules.canAck(ann("a", false, false, true, BoardingRules.State.SENT)));
        assertFalse(BoardingRules.canAck(ann("a", false, true, true, BoardingRules.State.SENT)));      // already pressed
        assertFalse(BoardingRules.canAck(ann("a", true, false, true, BoardingRules.State.SENT)));       // my own (the owner)
        assertFalse(BoardingRules.canAck(srv("m", "t", false, "owner")));                               // plain message
        assertFalse(BoardingRules.canAck(null));
    }

    @Test public void reportOnlyOthersServerMessages() {
        BoardingRules.Row other = srv("m", "2026-11-02T10:00:00+00:00", false, "student");
        assertTrue(BoardingRules.canReport(other, "me"));
        assertFalse(BoardingRules.canReport(srv("m", "t", true, "student"), "me"));                      // mine
        assertFalse(BoardingRules.canReport(other.withState(BoardingRules.State.FAILED), "me"));
        assertFalse(BoardingRules.canReport(other, null));
        assertFalse(BoardingRules.canReport(other, "other"));                                            // same id as the sender
    }

    @Test public void reportQuoteIsOneShortLineWithTheTitleForAnnouncements() {
        assertEquals("Reported message: \"Water cut - Water is off tomorrow 8-12\"", BoardingRules.reportQuote(ann("a", false, false, true, BoardingRules.State.SENT)));
        BoardingRules.Row longMsg = new BoardingRules.Row("x", "o", "N", "student", "hello\n\n   world " + "z".repeat(400), null, "t", false, false, BoardingRules.State.SENT);
        String q = BoardingRules.reportQuote(longMsg);
        assertTrue(q.startsWith("Reported message: \"hello world "));
        assertFalse(q.contains("\n"));
        assertTrue(q.length() <= BoardingRules.REPORT_QUOTE.length() + 160 + 2);
        assertTrue(q.endsWith("\u2026\""));
        assertEquals("", BoardingRules.reportQuote(null));
    }

    @Test public void bannerListsUnacknowledgedFirstAndKeepsPinOrderInsideEachGroup() {
        List<BoardingRules.Row> pins = Arrays.asList(
                ann("newest-done", false, true, true, BoardingRules.State.SENT), ann("mid-todo", false, false, true, BoardingRules.State.SENT), ann("old-todo", false, false, true, BoardingRules.State.SENT));
        List<BoardingRules.Row> order = BoardingRules.bannerOrder(pins);
        assertEquals("mid-todo", order.get(0).id);
        assertEquals("old-todo", order.get(1).id);
        assertEquals("newest-done", order.get(2).id);
        assertEquals(2, BoardingRules.unackedCount(pins));
    }

    @Test public void markAckedChangesOnlyThatRowAndLeavesTheInputAlone() {
        List<BoardingRules.Row> rows = Arrays.asList(ann("a", false, false, true, BoardingRules.State.SENT), ann("b", false, false, true, BoardingRules.State.SENT));
        List<BoardingRules.Row> out = BoardingRules.markAcked(rows, "b");
        assertFalse(out.get(0).iAcked);
        assertTrue(out.get(1).iAcked);
        assertFalse(rows.get(1).iAcked);
        assertSame(rows.get(0), out.get(0));
        assertEquals(1, BoardingRules.unackedCount(out));
    }

    @Test public void oneLineCollapsesSpacesAndCutsLongText() {
        assertEquals("a b c", BoardingRules.oneLine("  a \n b\t c ", 20));
        assertEquals("abcd\u2026", BoardingRules.oneLine("abcdefghij", 5));
        assertEquals("", BoardingRules.oneLine(null, 5));
    }

    @Test public void announcementFieldsComeFromTheServerRow() {
        GroupMessage m = new GroupMessage("1", "bh", "owner1", "Owner", "owner", "text", "2026-11-02T10:00:00+00:00", true, "Title", true, "2026-11-02T10:01:00+00:00", null, true, 4);
        BoardingRules.Row r = BoardingRules.fromMessage(m, "me");
        assertTrue(r.announcement);
        assertTrue(r.pinned);
        assertTrue(r.iAcked);
        assertEquals(4, r.ackCount);
        assertFalse(r.mine);
        assertEquals(4, r.withState(BoardingRules.State.SENT).ackCount);                                  // copies keep the new fields
    }
}
