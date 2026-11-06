package com.bofo.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bofo.shared.AiUnansweredRules.Reason;
import com.bofo.shared.AiUnansweredRules.Row;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AiUnansweredRulesTest {
    @Test public void rolesWithAiAnswerSeeTheScreen() {
        assertTrue(AiUnansweredRules.canSee("owner", null, null));
        assertTrue(AiUnansweredRules.canSee("support", null, null));
        assertTrue(AiUnansweredRules.canSee("moderator", null, null));
        assertFalse(AiUnansweredRules.canSee("support", null, java.util.Arrays.asList("ai_answer")));
        assertFalse(AiUnansweredRules.canSee(null, null, null));
        assertFalse(AiUnansweredRules.canSee("nobody", null, null));
    }

    @Test public void queryFiltersByReason() {
        assertEquals("select=id,created_at,reason,intent,lang,message&order=created_at.desc&limit=50", AiUnansweredRules.query(Reason.ALL));
        assertEquals("select=id,created_at,reason,intent,lang,message&order=created_at.desc&limit=50", AiUnansweredRules.query(null));
        assertTrue(AiUnansweredRules.query(Reason.THUMBS_DOWN).endsWith("&reason=eq.thumbs_down"));
        assertTrue(AiUnansweredRules.query(Reason.NOT_FOUND).endsWith("&reason=eq.not_found"));
    }

    @Test public void unknownReasonNeverCrashes() {
        assertEquals(Reason.ALL, Reason.of(null));
        assertEquals(Reason.ALL, Reason.of("something_new"));
        assertEquals(Reason.CLARIFY, Reason.of("clarify"));
    }

    @Test public void rowIsNullSafe() {
        Row r = new Row(null, null, null, null, null, null);
        assertFalse(r.hasText());
        assertEquals(Reason.ALL, r.kind());
        assertTrue(new Row("1", "t", "degraded", "search", "tl", "  malapit  ").hasText());
        assertEquals("malapit", new Row("1", "t", "degraded", "search", "tl", "  malapit  ").message());
    }

    @Test public void countsAndKnown() {
        List<Row> rows = Arrays.asList(
                new Row("1", "t", "clarify", "", "", "a"), new Row("2", "t", "clarify", "", "", "b"),
                new Row("3", "t", "thumbs_down", "", "", ""), new Row("4", "t", "future_reason", "", "", "x"));
        Map<Reason, Integer> m = AiUnansweredRules.counts(rows);
        assertEquals(2, (int) m.get(Reason.CLARIFY));
        assertEquals(1, (int) m.get(Reason.THUMBS_DOWN));
        assertEquals(0, (int) m.get(Reason.DEGRADED));
        assertEquals(3, AiUnansweredRules.known(rows).size());
        assertEquals(0, (int) AiUnansweredRules.counts(null).get(Reason.OFF_TOPIC));
    }
}
