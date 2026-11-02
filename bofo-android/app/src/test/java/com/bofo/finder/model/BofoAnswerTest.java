package com.bofo.finder.model;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/** v2.1.2: the answer of bofo-chat parses with and without the new optional fields. */
public class BofoAnswerTest {
    private static final String OLD = "{\"reply\":\"Closest places:\",\"intent\":\"nearest\",\"filters\":{\"max_price\":2000},\"suggestions\":[\"Compare them\"],"
            + "\"results\":[{\"id\":\"b1\",\"name\":\"Casa Verde\",\"price\":1800,\"free_wifi\":true}],\"actions\":[{\"type\":\"show_on_map\",\"ids\":[\"b1\"]}]}";

    @Test public void oldJsonStillParsesAndNewFieldsAreEmpty() throws Exception {
        BofoAnswer a = BofoAnswer.parse(new JSONObject(OLD));
        assertEquals("Closest places:", a.reply());
        assertEquals("nearest", a.intent());
        assertEquals(1, a.results().size());
        assertEquals(List.of("Compare them"), a.suggestions());
        assertNull(a.clarify());
        assertTrue(a.quickReplies().isEmpty());
        assertNull(a.lang());
        assertNull(a.logId());
    }

    @Test public void onlyReplyAndFiltersParses() throws Exception {
        BofoAnswer a = BofoAnswer.parse(new JSONObject("{\"reply\":\"hi\",\"filters\":{}}"));
        assertEquals("hi", a.reply());
        assertEquals("unknown", a.intent());
        assertNull(a.clarify());
        assertNull(a.logId());
    }

    @Test public void newFieldsAreRead() throws Exception {
        BofoAnswer a = BofoAnswer.parse(new JSONObject("{\"reply\":\"Pila ang budget?\",\"clarify\":\" Which campus? \",\"quick_replies\":[\"NORSU\",\"Near me\",\"\",\"A\",\"B\",\"C\"],"
                + "\"lang\":\"ceb\",\"log_id\":\"0b6f6d5e-1c9d-4b8e-9f3a-2a7d4c1e8f90\"}"));
        assertEquals("Which campus?", a.clarify());
        assertEquals(List.of("NORSU", "Near me", "A", "B"), a.quickReplies());       // blanks dropped, at most 4
        assertEquals("ceb", a.lang());
        assertEquals("0b6f6d5e-1c9d-4b8e-9f3a-2a7d4c1e8f90", a.logId());
    }

    @Test public void badOrNullNewFieldsBecomeNull() throws Exception {
        BofoAnswer a = BofoAnswer.parse(new JSONObject("{\"reply\":\"x\",\"clarify\":null,\"lang\":\"xx\",\"log_id\":null,\"quick_replies\":null}"));
        assertNull(a.clarify());
        assertNull(a.lang());
        assertNull(a.logId());
        assertTrue(a.quickReplies().isEmpty());
        assertNull(BofoAnswer.parse(new JSONObject("{\"clarify\":\"\",\"log_id\":\"  \"}")).clarify());
        assertNull(BofoAnswer.parse(new JSONObject("{\"clarify\":\"\",\"log_id\":\"  \"}")).logId());
    }

    @Test public void nullJsonAndOldConstructorWork() {
        assertEquals("", BofoAnswer.parse(null).reply());
        BofoAnswer t = BofoAnswer.text("hello");
        assertEquals("hello", t.reply());
        assertNull(t.clarify());
        assertEquals(List.of("A"), t.withSuggestions(List.of("A")).suggestions());
    }
}
