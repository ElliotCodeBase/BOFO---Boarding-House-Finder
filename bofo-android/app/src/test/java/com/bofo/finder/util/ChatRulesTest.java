package com.bofo.finder.util;

import static org.junit.Assert.*;

import com.bofo.finder.model.Message;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Stage 7: the pure chat rules (merge, poll timing, ticks, labels). No network, no Android classes. */
public class ChatRulesTest {

    private static ChatRules.Item srv(String id, String at, boolean mine, ChatRules.State s) { return new ChatRules.Item(id, "b" + id, at, mine, s); }

    @Test public void pollDelayStartsAt3sBacksOffAndStopsAt12s() {
        assertEquals(3000, ChatRules.nextDelayMs(0));
        assertEquals(4500, ChatRules.nextDelayMs(1));
        assertTrue(ChatRules.nextDelayMs(3) > ChatRules.nextDelayMs(2));
        assertEquals(12000, ChatRules.nextDelayMs(6));
        assertEquals(12000, ChatRules.nextDelayMs(500));
        assertEquals(3000, ChatRules.nextDelayMs(-4));
    }

    @Test public void mergeSortsDedupesAndKeepsQueuedAtTheEnd() {
        ChatRules.Item a = srv("1", "2026-10-30T10:00:00+00:00", false, ChatRules.State.SENT);
        ChatRules.Item b = srv("2", "2026-10-30T10:05:00+00:00", true, ChatRules.State.SENT);
        ChatRules.Item bAgain = srv("2", "2026-10-30T10:05:00+00:00", true, ChatRules.State.SENT);
        ChatRules.Item q = new ChatRules.Item("q1", "waiting", "1730000000", true, ChatRules.State.PENDING);
        List<ChatRules.Item> out = ChatRules.merge(Arrays.asList(b, a, bAgain), Collections.singletonList(q));
        assertEquals(3, out.size());
        assertEquals("1", out.get(0).id);
        assertEquals("2", out.get(1).id);
        assertEquals("q1", out.get(2).id);
    }

    @Test public void aReadTickNeverGoesBackToOneTick() {
        ChatRules.Item sent = srv("2", "2026-10-30T10:05:00+00:00", true, ChatRules.State.SENT);
        ChatRules.Item read = srv("2", "2026-10-30T10:05:00+00:00", true, ChatRules.State.READ);
        assertEquals(ChatRules.State.READ, ChatRules.merge(Arrays.asList(sent, read), new ArrayList<ChatRules.Item>()).get(0).state);
        assertEquals(ChatRules.State.READ, ChatRules.merge(Arrays.asList(read, sent), new ArrayList<ChatRules.Item>()).get(0).state);
    }

    @Test public void aSentMessageReplacesItsQueuedCopyOnlyByServerId() {
        ChatRules.Item q = new ChatRules.Item("local-1", "hi", "1", true, ChatRules.State.PENDING);
        ChatRules.Item s = srv("srv-1", "2026-10-30T10:00:00+00:00", true, ChatRules.State.SENT);
        // the outbox removes the queued copy when it is sent, so after that only the server copy is left
        assertEquals(1, ChatRules.merge(Collections.singletonList(s), new ArrayList<ChatRules.Item>()).size());
        assertEquals(2, ChatRules.merge(Collections.singletonList(s), Collections.singletonList(q)).size());
    }

    @Test public void cursorsIgnoreQueuedAndFailedMessages() {
        ChatRules.Item a = srv("1", "2026-10-30T10:00:00+00:00", false, ChatRules.State.SENT);
        ChatRules.Item b = srv("2", "2026-10-30T10:05:00+00:00", true, ChatRules.State.READ);
        ChatRules.Item q = new ChatRules.Item("q", "x", "9999999999", true, ChatRules.State.PENDING);
        ChatRules.Item f = new ChatRules.Item("f", "x", "0", true, ChatRules.State.FAILED);
        List<ChatRules.Item> l = Arrays.asList(a, b, q, f);
        assertEquals("2026-10-30T10:05:00+00:00", ChatRules.newestServerTime(l));
        assertEquals("2026-10-30T10:00:00+00:00", ChatRules.oldestServerTime(l));
        assertNull(ChatRules.newestServerTime(Collections.<ChatRules.Item>emptyList()));
        assertNull(ChatRules.oldestServerTime(Arrays.asList(q, f)));
    }

    @Test public void fromMessageKnowsMineAndRead() {
        Message m = new Message("1", "c", "me", "hello", "2026-10-30T10:00:00+00:00", "2026-10-30T10:01:00+00:00");
        Message n = new Message("2", "c", "other", "hey", "2026-10-30T10:02:00+00:00", null);
        assertTrue(ChatRules.fromMessage(m, "me").mine);
        assertEquals(ChatRules.State.READ, ChatRules.fromMessage(m, "me").state);
        assertFalse(ChatRules.fromMessage(n, "me").mine);
        assertEquals(ChatRules.State.SENT, ChatRules.fromMessage(n, "me").state);          // their messages never show my ticks
        assertTrue(ChatRules.hasIncoming(Collections.singletonList(ChatRules.fromMessage(n, "me"))));
        assertFalse(ChatRules.hasIncoming(Collections.singletonList(ChatRules.fromMessage(m, "me"))));
    }

    @Test public void ticks() {
        assertEquals("✓", ChatRules.tick(srv("1", "x", true, ChatRules.State.SENT)));
        assertEquals("✓✓", ChatRules.tick(srv("1", "x", true, ChatRules.State.READ)));
        assertEquals("!", ChatRules.tick(srv("1", "x", true, ChatRules.State.FAILED)));
        assertEquals("", ChatRules.tick(srv("1", "x", false, ChatRules.State.SENT)));
        assertFalse(ChatRules.tick(srv("1", "x", true, ChatRules.State.PENDING)).isEmpty());
    }

    @Test public void timeLabels() {
        long now = java.time.OffsetDateTime.parse("2026-10-30T12:00:00+00:00").toInstant().toEpochMilli();
        assertEquals("09:05", ChatRules.timeLabel("2026-10-30T09:05:30.123456+00:00", now, ZoneOffset.UTC));
        assertEquals("Yesterday", ChatRules.timeLabel("2026-10-29T23:59:00+00:00", now, ZoneOffset.UTC));
        assertEquals("Oct 20", ChatRules.timeLabel("2026-10-20T08:00:00+00:00", now, ZoneOffset.UTC));
        assertEquals("17:05", ChatRules.timeLabel("2026-10-30T09:05:30+00:00", now, ZoneOffset.ofHours(8)));
        assertEquals("", ChatRules.timeLabel("1730000000", now, ZoneOffset.UTC));
        assertEquals("", ChatRules.timeLabel("", now, ZoneOffset.UTC));
        assertEquals("", ChatRules.timeLabel(null, now, ZoneOffset.UTC));
    }

    @Test public void textHelpers() {
        assertEquals("a b c", ChatRules.preview("a\n  b\t c ", 80));
        assertEquals("abcdefg...", ChatRules.preview("abcdefghijklmnop", 10));
        assertEquals("", ChatRules.preview(null, 10));
        assertEquals("", ChatRules.badge(0));
        assertEquals("7", ChatRules.badge(7));
        assertEquals("99+", ChatRules.badge(250));
        assertEquals("Ana", ChatRules.title(" Ana ", "Sunrise BH"));
        assertEquals("Sunrise BH", ChatRules.title("", "Sunrise BH"));
        assertEquals("Chat", ChatRules.title(null, null));
        assertEquals("", ChatRules.outgoing("   ", 1000));
        assertEquals("", ChatRules.outgoing(null, 1000));
        assertEquals("hi", ChatRules.outgoing("  hi  ", 1000));
        assertEquals(5, ChatRules.outgoing("abcdefghij", 5).length());
    }

    @Test
    public void dayLabelNamesTodayYesterdayAndDate() {
        java.time.ZoneId utc = java.time.ZoneOffset.UTC;
        long now = java.time.OffsetDateTime.parse("2026-10-31T12:00:00Z").toInstant().toEpochMilli();
        org.junit.Assert.assertEquals("Today", ChatRules.dayLabel("2026-10-31T01:00:00Z", now, utc));
        org.junit.Assert.assertEquals("Yesterday", ChatRules.dayLabel("2026-10-30T23:00:00Z", now, utc));
        org.junit.Assert.assertEquals("Oct 28", ChatRules.dayLabel("2026-10-28T10:00:00Z", now, utc));
        org.junit.Assert.assertEquals("", ChatRules.dayLabel("1761900000000", now, utc));
    }
}
