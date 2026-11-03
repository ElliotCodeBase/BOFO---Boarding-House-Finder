package com.bofo.finder.util;

import static org.junit.Assert.*;

import com.bofo.finder.model.AnnounceType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import org.junit.Test;

/** v2.4.0 AI Announcement Writer step 3: validation, normalization and the request body. No network, no Android classes. */
public class AnnounceRulesTest {
    private static Map<String, String> map(String... kv) { Map<String, String> m = new HashMap<>(); for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]); return m; }
    private static AnnounceType t(String id) { return AnnounceType.byId(id); }
    private static String rep(char c, int n) { StringBuilder b = new StringBuilder(); for (int i = 0; i < n; i++) b.append(c); return b.toString(); }

    @Test public void normalizeMatchesTheBackendRules() {
        assertEquals("", AnnounceRules.normalize(null));
        assertEquals("a b c", AnnounceRules.normalize("  a \n\t b\u00A0\u00A0c  "));
        assertEquals("ab cd", AnnounceRules.normalize("a\u202Eb\nc\u0000d"));
        assertEquals("ab", AnnounceRules.normalize("a\u200Bb"));
        assertEquals("Nov 5", AnnounceRules.normalize("Nov\u3000 5"));
    }

    @Test public void requiredFieldsAreChecked() {
        Map<String, String> m = map("due_date", "Nov 5", "amount", "1500");
        AnnounceRules.Problem p = AnnounceRules.validateFields(t("rent_due"), m);
        assertEquals(AnnounceRules.Problem.MISSING, p.code); assertEquals("how_to_pay", p.field);
        m.put("how_to_pay", "   ");
        assertEquals("how_to_pay", AnnounceRules.validateFields(t("rent_due"), m).field);
        m.put("how_to_pay", "GCash");
        assertNull(AnnounceRules.validateFields(t("rent_due"), m));
        assertEquals("due_date", AnnounceRules.validateFields(t("rent_due"), null).field);
    }

    @Test public void lengthLimitIs200AfterNormalizing() {
        assertNull(AnnounceRules.validateFields(t("general"), map("idea", rep('x', 200))));
        AnnounceRules.Problem p = AnnounceRules.validateFields(t("general"), map("idea", rep('x', 201)));
        assertEquals(AnnounceRules.Problem.TOO_LONG, p.code); assertEquals("idea", p.field);
        assertNull(AnnounceRules.validateFields(t("general"), map("idea", "  " + rep('x', 200) + "   ")));   // padding does not count
    }

    @Test public void oneOfRuleForGateAndLockChange() {
        AnnounceRules.Problem p = AnnounceRules.validateFields(t("gate_lock_change"), map("effective_date", "Nov 1"));
        assertEquals(AnnounceRules.Problem.ONE_OF, p.code); assertEquals("new_gate_time", p.field);
        assertNull(AnnounceRules.validateFields(t("gate_lock_change"), map("key_code_instructions", "Ask the owner")));
        assertNull(AnnounceRules.validateFields(t("gate_lock_change"), map("new_gate_time", "10 PM")));
    }

    @Test public void unknownKeysAreRejectedAndOptionalOnlyTypesNeedNothing() {
        AnnounceRules.Problem p = AnnounceRules.validateFields(t("general"), map("idea", "x", "extra", "y"));
        assertEquals(AnnounceRules.Problem.UNKNOWN, p.code); assertEquals("extra", p.field);
        assertNull(AnnounceRules.validateFields(t("welcome_tenant"), map()));
        assertNull(AnnounceRules.validateFields(t("wifi_update"), map()));
    }

    @Test public void everyTypeAcceptsItsOwnFullFieldSetAndRejectsAnEmptyOne() {
        for (AnnounceType a : AnnounceType.ALL) {
            Map<String, String> full = new HashMap<>(); for (AnnounceType.Field f : a.fields) full.put(f.key, "v " + f.key);
            assertNull(a.id, AnnounceRules.validateFields(a, full));
            boolean needsSomething = a.fields.stream().anyMatch(f -> f.required) || !a.oneOf.isEmpty();
            assertEquals(a.id, needsSomething, AnnounceRules.validateFields(a, map()) != null);
        }
    }

    @Test public void requestBodyHasOnlyKeysTheBackendAccepts() throws Exception {
        Map<String, String> f = map("due_date", " Nov 5 ", "amount", "1500", "how_to_pay", "GCash", "late_fee", "  ");
        JSONObject j = AnnounceRules.buildRequest(t("rent_due"), AnnounceType.Lang.FIL, null, null, true, true, "  Mrs.  Reyes ", f, AnnounceType.Adjust.SHORTER);
        Set<String> keys = new LinkedHashSet<>(); for (Iterator<String> it = j.keys(); it.hasNext(); ) keys.add(it.next());
        assertEquals(AnnounceRules.REQUEST_KEYS, keys);
        assertEquals("rent_due", j.getString("type")); assertEquals("fil", j.getString("lang"));
        assertEquals("firm", j.getString("tone")); assertEquals("normal", j.getString("length"));      // null -> type default / normal
        assertTrue(j.getBoolean("emoji")); assertEquals("shorter", j.getString("adjust"));
        JSONObject c = j.getJSONObject("closing");
        assertTrue(c.getBoolean("on")); assertEquals("Mrs. Reyes", c.getString("name"));
        JSONObject fields = j.getJSONObject("fields");
        assertEquals(3, fields.length()); assertEquals("Nov 5", fields.getString("due_date")); assertFalse(fields.has("late_fee"));   // empty optional dropped
    }

    @Test public void closingAndAdjustDefaults() throws Exception {
        JSONObject j = AnnounceRules.buildRequest(t("general"), null, AnnounceType.Tone.FORMAL, AnnounceType.Length.SHORT, false, true, "   ", map("idea", "Hi"), null);
        assertFalse(j.has("adjust")); assertEquals("en", j.getString("lang")); assertEquals("formal", j.getString("tone")); assertEquals("short", j.getString("length"));
        assertFalse(j.getJSONObject("closing").getBoolean("on"));                                     // no name = no sign-off
        JSONObject k = AnnounceRules.buildRequest(t("general"), null, null, null, false, false, "Ana", map("idea", "Hi"), null);
        assertFalse(k.getJSONObject("closing").getBoolean("on"));
        assertEquals(60, AnnounceRules.closingName(rep('n', 80)).length());
        assertEquals("", AnnounceRules.closingName(null));
    }

    @Test public void buildRequestRefusesInvalidDetails() throws Exception {
        try { AnnounceRules.buildRequest(t("rent_due"), null, null, null, false, false, "", map("amount", "1"), null); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("due_date")); }
        assertEquals(AnnounceType.Tone.FORMAL, AnnounceRules.defaultTone(t("move_out")));
    }
}
