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
}
