package com.bofo.finder.model;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

/** v2.4.1 AI Announcement Writer step 4: AnnounceDraft.fromJson (plain JVM, no network). */
public class AnnounceDraftTest {
    private static String rep(char c, int n) { StringBuilder b = new StringBuilder(); for (int i = 0; i < n; i++) b.append(c); return b.toString(); }
    private static void rejects(String json) throws Exception {
        try { AnnounceDraft.fromJson(new JSONObject(json)); fail("should reject " + json); } catch (IllegalArgumentException expected) { }
    }

    @Test public void fullBody() throws Exception {
        AnnounceDraft d = AnnounceDraft.fromJson(new JSONObject("{\"title\":\"Rent reminder\",\"message\":\"Hi all,\\nRent is due Nov 5.\",\"notes\":\"Add the amount\",\"degraded\":true}"));
        assertEquals("Rent reminder", d.title); assertEquals("Hi all,\nRent is due Nov 5.", d.message);
        assertEquals("Add the amount", d.notes); assertTrue(d.degraded);
    }

    @Test public void noNotesAndNullNotesAreFine() throws Exception {
        AnnounceDraft a = AnnounceDraft.fromJson(new JSONObject("{\"title\":\"T\",\"message\":\"M\",\"degraded\":false}"));
        assertNull(a.notes); assertFalse(a.degraded);
        AnnounceDraft b = AnnounceDraft.fromJson(new JSONObject("{\"title\":\"T\",\"message\":\"M\",\"notes\":null}"));
        assertNull(b.notes);
        AnnounceDraft c = AnnounceDraft.fromJson(new JSONObject("{\"title\":\"T\",\"message\":\"M\",\"notes\":\"   \"}"));
        assertNull(c.notes);
    }

    @Test public void wrongTypesAreTolerantOrRejected() throws Exception {
        AnnounceDraft d = AnnounceDraft.fromJson(new JSONObject("{\"title\":\"T\",\"message\":\"M\",\"notes\":42,\"degraded\":\"true\"}"));
        assertNull(d.notes);                                           // a number is not a hint
        assertFalse(d.degraded);                                       // only a real boolean counts
        assertFalse(AnnounceDraft.fromJson(new JSONObject("{\"title\":\"T\",\"message\":\"M\",\"degraded\":1}")).degraded);
        rejects("{\"title\":5,\"message\":\"M\"}");                    // a number is not a title (and is not turned into "5")
        rejects("{\"title\":\"T\",\"message\":[\"M\"]}");
        rejects("{\"title\":null,\"message\":\"M\"}");                 // null must not become the text "null"
        rejects("{\"title\":\"T\",\"message\":null}");
    }

    @Test public void missingOrBlankTitleOrMessageIsRejected() throws Exception {
        rejects("{}");
        rejects("{\"message\":\"M\"}");
        rejects("{\"title\":\"T\"}");
        rejects("{\"title\":\"   \",\"message\":\"M\"}");
        rejects("{\"title\":\"T\",\"message\":\" \\n \\t \"}");
        try { AnnounceDraft.fromJson(null); fail(); } catch (IllegalArgumentException expected) { }
    }

    @Test public void overLongTextIsClippedToThePostAnnouncementLimits() throws Exception {
        JSONObject o = new JSONObject().put("title", rep('t', 500)).put("message", rep('m', 5000)).put("notes", rep('n', 900));
        AnnounceDraft d = AnnounceDraft.fromJson(o);
        assertEquals(120, d.title.length()); assertEquals(1000, d.message.length()); assertEquals(300, d.notes.length());
        assertEquals(120, AnnounceDraft.MAX_TITLE); assertEquals(1000, AnnounceDraft.MAX_MESSAGE);
        // exactly at the limit is untouched
        assertEquals(1000, AnnounceDraft.fromJson(new JSONObject().put("title", "T").put("message", rep('m', 1000))).message.length());
    }

    @Test public void clippingNeverSplitsAnEmoji() throws Exception {
        String smile = new String(Character.toChars(0x1F600));          // 2 UTF-16 units
        String msg = rep('a', 999) + smile;                              // the emoji would be cut after its first half
        String out = AnnounceDraft.fromJson(new JSONObject().put("title", "T").put("message", msg)).message;
        assertEquals(999, out.length());
        assertFalse(Character.isHighSurrogate(out.charAt(out.length() - 1)));
    }

    @Test public void titleIsOneLineMessageKeepsItsParagraphs() throws Exception {
        AnnounceDraft d = AnnounceDraft.fromJson(new JSONObject().put("title", "  Water \n cut​  ").put("message", "Line 1  \r\n\r\n\r\n\r\nLine 2\u0007  "));
        assertEquals("Water cut", d.title);
        assertEquals("Line 1\n\nLine 2", d.message);
    }
}
