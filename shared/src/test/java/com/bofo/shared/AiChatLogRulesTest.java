package com.bofo.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AiChatLogRulesTest {
    private static final long NOW = 1_700_000_000_000L;   // 2023-11-14T22:13:20Z

    @Test public void searchIsCleaned() {
        assertEquals("", AiChatLogRules.cleanSearch(null));
        assertEquals("rent near USEP", AiChatLogRules.cleanSearch("  rent,  near (USEP) "));
        assertEquals("a b", AiChatLogRules.cleanSearch("a*%_\\\"'b").replace("  ", " "));
        assertEquals("joe@example.com", AiChatLogRules.cleanSearch("joe@example.com"));
        assertEquals(AiChatLogRules.SEARCH_MAX, AiChatLogRules.cleanSearch(new String(new char[200]).replace('\0', 'x')).length());
    }

    @Test public void searchCannotBreakTheFilter() {
        String q = AiChatLogRules.query(AiChatLogRules.Range.ALL, "x),user_id.neq.0,(y", 0, NOW);
        assertFalse(q.contains("user_id.neq"));          // the comma and dots-with-brackets are gone
        assertTrue(q.endsWith("*)"));
        assertEquals(1, q.split("&or=").length - 1);
    }

    @Test public void rangesAndPaging() {
        String today = AiChatLogRules.query(AiChatLogRules.Range.TODAY, "", 0, NOW);
        assertTrue(today.contains("&created_at=gte.2023-11-13T22:13:20Z"));
        String week = AiChatLogRules.query(AiChatLogRules.Range.WEEK, "", 30, NOW);
        assertTrue(week.contains("&created_at=gte.2023-11-07T22:13:20Z"));
        assertTrue(week.contains("&limit=31&offset=30"));
        assertFalse(AiChatLogRules.query(AiChatLogRules.Range.ALL, "", -5, NOW).contains("created_at=gte"));
        assertTrue(AiChatLogRules.query(AiChatLogRules.Range.ALL, "", -5, NOW).contains("offset=0"));
        assertFalse(AiChatLogRules.query(AiChatLogRules.Range.ALL, "", 0, NOW).contains("&or="));
    }

    @Test public void spacesAreEncoded() {
        assertTrue(AiChatLogRules.query(AiChatLogRules.Range.ALL, "cheap room", 0, NOW).contains("*cheap%20room*"));
    }

    @Test public void hasMoreOnlyWhenExtraRow() {
        assertFalse(AiChatLogRules.hasMore(30));
        assertTrue(AiChatLogRules.hasMore(31));
    }

    @Test public void switchDefaultsAndConfirm() {
        assertTrue(AiChatLogRules.aiOn(null)); assertTrue(AiChatLogRules.aiOn(true)); assertFalse(AiChatLogRules.aiOn(false));
        assertFalse(AiChatLogRules.monitorOn(null)); assertTrue(AiChatLogRules.monitorOn(true));
        assertTrue(AiChatLogRules.wipeConfirmed(" delete ")); assertFalse(AiChatLogRules.wipeConfirmed("del")); assertFalse(AiChatLogRules.wipeConfirmed(null));
    }

    @Test public void textHelpers() {
        assertEquals("2026-11-06 14:05", AiChatLogRules.when("2026-11-06T14:05:33.1+00:00"));
        assertEquals("", AiChatLogRules.when(null));
        assertEquals("abc", AiChatLogRules.clip("abc", 5));
        assertEquals(5, AiChatLogRules.clip("abcdefgh", 5).length());
    }
}
