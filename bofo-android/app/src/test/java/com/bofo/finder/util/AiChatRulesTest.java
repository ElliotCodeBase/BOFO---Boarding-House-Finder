package com.bofo.finder.util;

import static org.junit.Assert.*;

import com.bofo.finder.model.Role;
import com.bofo.finder.util.AiChatRules.Group;
import com.bofo.finder.util.AiChatRules.Range;
import com.bofo.finder.util.AiChatRules.Row;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** v2.0.91 AI Chats monitor: search, date filter, paging, grouping and time text. Delete with the feature (REMOVE_AI_CHATS.md). */
public class AiChatRulesTest {
    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");          // UTC+8 all year, so the tests do not depend on the machine's zone
    private static final long NOW = java.time.Instant.parse("2026-11-01T05:00:00Z").toEpochMilli();   // Nov 1, 2026 1:00 PM in Manila

    private static Row row(String id, String user, String name, String email, String q, String iso) {
        return new Row(id, user, name, email, q, "answer of " + id, iso);
    }

    @Test public void onlyAdminsWithTheSwitchOnSeeIt() {
        assertTrue(AiChatRules.canSee(Role.ADMIN, true));
        assertFalse(AiChatRules.canSee(Role.ADMIN, false));
        assertFalse(AiChatRules.canSee(Role.STUDENT, true));
        assertFalse(AiChatRules.canSee(Role.OWNER, true));
        assertFalse(AiChatRules.canSee(null, true));
    }

    @Test public void dateAndTimeText() {
        assertEquals("Nov 1, 2026 9:14 AM", AiChatRules.dateTime("2026-11-01T01:14:00.123456+00:00", MANILA));
        assertEquals("Nov 1, 2026 9:14 AM", AiChatRules.dateTime("2026-11-01 01:14:00+00:00", MANILA));      // space instead of T
        assertEquals("Nov 1, 2026 9:14 AM", AiChatRules.dateTime("2026-11-01T01:14:00Z", MANILA));
        assertEquals("Nov 1, 2026 12:05 AM", AiChatRules.dateTime("2026-10-31T16:05:00+00:00", MANILA));
        assertEquals("Nov 1, 2026 1:00 PM", AiChatRules.dateTime("2026-11-01T05:00:00+00:00", MANILA));
        assertEquals("", AiChatRules.dateTime("not a date", MANILA));
        assertEquals("", AiChatRules.dateTime(null, MANILA));
        assertEquals("", AiChatRules.dateTime("", MANILA));
    }

    @Test public void agoText() {
        assertEquals("just now", AiChatRules.ago("2026-11-01T04:59:30Z", NOW));
        assertEquals("just now", AiChatRules.ago("2026-11-01T05:00:20Z", NOW));          // a few seconds in the future
        assertEquals("1 min ago", AiChatRules.ago("2026-11-01T04:59:00Z", NOW));
        assertEquals("5 min ago", AiChatRules.ago("2026-11-01T04:55:00Z", NOW));
        assertEquals("59 min ago", AiChatRules.ago("2026-11-01T04:01:00Z", NOW));
        assertEquals("1 hour ago", AiChatRules.ago("2026-11-01T04:00:00Z", NOW));
        assertEquals("3 hours ago", AiChatRules.ago("2026-11-01T02:00:00Z", NOW));
        assertEquals("1 day ago", AiChatRules.ago("2026-10-31T05:00:00Z", NOW));
        assertEquals("7 days ago", AiChatRules.ago("2026-10-25T05:00:00Z", NOW));
        assertEquals("3 months ago", AiChatRules.ago("2026-08-01T05:00:00Z", NOW));
        assertEquals("", AiChatRules.ago("garbage", NOW));
        assertEquals("Nov 1, 2026 9:14 AM  -  5 min ago", AiChatRules.whenLine("2026-11-01T01:14:00Z", java.time.Instant.parse("2026-11-01T01:19:00Z").toEpochMilli(), MANILA));
        assertEquals("", AiChatRules.whenLine("garbage", NOW, MANILA));
    }

    @Test public void dateFilter() {
        assertEquals("2026-10-31T16:00:00Z", AiChatRules.sinceIso(Range.TODAY, NOW, MANILA));          // midnight in Manila = 16:00 UTC the day before
        assertEquals("2026-10-25T05:00:00Z", AiChatRules.sinceIso(Range.WEEK, NOW, MANILA));
        assertEquals("", AiChatRules.sinceIso(Range.ALL, NOW, MANILA));
    }

    @Test public void searchTermIsSafe() {
        assertEquals("", AiChatRules.searchTerm(null));
        assertEquals("", AiChatRules.searchTerm("  \n "));
        assertEquals("ada cruz", AiChatRules.searchTerm("  ada   cruz \n"));
        assertEquals("and or x", AiChatRules.searchTerm("and,or),(x"));                         // filter syntax characters are dropped
        assertEquals("abc", AiChatRules.searchTerm("a*b%c\\\""));
        assertEquals("john_doe@gmail.com", AiChatRules.searchTerm("john_doe@gmail.com"));
        assertEquals(60, AiChatRules.searchTerm("x".repeat(200)).length());
    }

    @Test public void orFilter() {
        assertEquals("", AiChatRules.orFilter("  "));
        assertEquals("or=(user_name.ilike.*ada%20cruz*,user_email.ilike.*ada%20cruz*,question.ilike.*ada%20cruz*,answer.ilike.*ada%20cruz*)", AiChatRules.orFilter("ada cruz"));
        assertTrue(AiChatRules.orFilter("ñandú & co").contains("*%C3%B1and%C3%BA%20%26%20co*"));        // UTF-8 and & are percent-encoded
        assertTrue(AiChatRules.orFilter("a@gmail.com").contains("*a%40gmail.com*"));
        assertFalse(AiChatRules.orFilter("a,b").contains("a,b"));                                    // the comma cannot start a new filter
    }

    @Test public void queryForOnePage() {
        String q = AiChatRules.query(Range.ALL, "", 0, NOW, MANILA);
        assertTrue(q.contains("order=created_at.desc,id.desc"));
        assertTrue(q.contains("limit=31"));
        assertTrue(q.contains("offset=0"));
        assertFalse(q.contains("created_at=gte"));
        assertFalse(q.contains("or=("));
        String q2 = AiChatRules.query(Range.TODAY, "wifi", 30, NOW, MANILA);
        assertTrue(q2.contains("offset=30"));
        assertTrue(q2.contains("&created_at=gte.2026-10-31T16:00:00Z"));
        assertTrue(q2.contains("&or=(user_name.ilike.*wifi*"));
        assertTrue(AiChatRules.query(Range.ALL, "", -5, NOW, MANILA).contains("offset=0"));          // never a negative offset
    }

    @Test public void pagingNeedsOneExtraRow() {
        assertFalse(AiChatRules.hasMore(30));
        assertTrue(AiChatRules.hasMore(31));
        List<Integer> thirtyOne = new ArrayList<>();
        for (int i = 0; i < 31; i++) thirtyOne.add(i);
        assertEquals(30, AiChatRules.firstPage(thirtyOne).size());
        assertEquals(5, AiChatRules.firstPage(Arrays.asList(1, 2, 3, 4, 5)).size());
    }

    @Test public void mergeSkipsDuplicatesAndKeepsNewestFirst() {
        Row a = row("a", "u1", "Ada", "ada@gmail.com", "q1", "2026-11-01T04:00:00Z");
        Row b = row("b", "u1", "Ada", "ada@gmail.com", "q2", "2026-11-01T03:00:00Z");
        Row c = row("c", "u2", "Ben", "ben@gmail.com", "q3", "2026-11-01T02:00:00Z");
        Row bad = row("x", "u3", "Cy", "cy@gmail.com", "q4", "garbage");
        List<Row> m = AiChatRules.merge(Arrays.asList(a, b), Arrays.asList(b, c, bad));              // b arrives twice (a new chat moved the offset)
        assertEquals(4, m.size());
        assertEquals("a", m.get(0).id);
        assertEquals("b", m.get(1).id);
        assertEquals("c", m.get(2).id);
        assertEquals("x", m.get(3).id);                                                             // unreadable time sorts last
    }

    @Test public void names() {
        assertEquals("Ada Cruz", AiChatRules.who(" Ada Cruz ", "ada@gmail.com"));
        assertEquals("ada", AiChatRules.who("", "ada@gmail.com"));
        assertEquals("ada", AiChatRules.who(null, " ada@gmail.com "));
        assertEquals("noatsign", AiChatRules.who(null, "noatsign"));
        assertEquals("Unknown user", AiChatRules.who(null, null));
        assertEquals("Unknown user", AiChatRules.who(" ", ""));
        assertEquals("1 question", AiChatRules.countText(1));
        assertEquals("3 questions", AiChatRules.countText(3));
    }

    @Test public void groupOneConversationPerUser() {
        List<Row> rows = Arrays.asList(                                                              // newest first, as the screen holds them
                row("5", "u2", "Ben", "ben@gmail.com", "q5", "2026-11-01T04:30:00Z"),
                row("4", "u1", "Ada", "ada@gmail.com", "q4", "2026-11-01T04:00:00Z"),
                row("3", "u1", "Ada", "ada@gmail.com", "q3", "2026-11-01T03:00:00Z"),
                row("2", "u2", "", "ben@gmail.com", "q2", "2026-11-01T02:00:00Z"),
                row("1", "u1", "Ada Old", "ada@gmail.com", "q1", "2026-11-01T01:00:00Z"));
        List<Group> g = AiChatRules.group(rows);
        assertEquals(2, g.size());
        assertEquals("Ben", g.get(0).name);                                                         // Ben wrote last, so he is first
        assertEquals(2, g.get(0).messages.size());
        assertEquals("Ada", g.get(1).name);                                                         // newest message's name wins over "Ada Old"
        assertEquals(3, g.get(1).messages.size());
        assertEquals("1", g.get(1).messages.get(0).id);                                             // oldest first inside a conversation
        assertEquals("4", g.get(1).latest().id);
        assertEquals("2026-11-01T04:00:00Z", g.get(1).latestIso);
        assertEquals("ada@gmail.com", g.get(1).email);
    }

    @Test public void groupFallsBackToEmailWithoutUserId() {
        List<Row> g1 = Arrays.asList(row("1", "", "", "Ada@Gmail.com", "q", "2026-11-01T01:00:00Z"), row("2", "", "", "ada@gmail.com", "q", "2026-11-01T02:00:00Z"));
        assertEquals(1, AiChatRules.group(g1).size());
        assertEquals("ada", AiChatRules.group(g1).get(0).name);
        List<Row> g2 = Arrays.asList(row("1", "", "", "", "q", "2026-11-01T01:00:00Z"), row("2", "", "", "", "q", "2026-11-01T02:00:00Z"));
        assertEquals(1, AiChatRules.group(g2).size());
        assertEquals("Unknown user", AiChatRules.group(g2).get(0).name);
        assertTrue(AiChatRules.group(new ArrayList<Row>()).isEmpty());
    }
}
