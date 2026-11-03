package com.bofo.finder.model;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Assume;
import org.junit.Test;

/** v2.4.0 AI Announcement Writer step 3: the Java catalogue is complete and equal to supabase/functions/bofo-announce/catalogue.ts. No Android classes. */
public class AnnounceTypeTest {

    private static final List<String> EXPECTED = Arrays.asList("rent_due", "water_cut", "power_cut", "house_rules", "visitors_curfew", "cleaning_schedule",
            "welcome_tenant", "vacancy", "payment_thanks", "general", "emergency", "typhoon", "move_out", "deposit_advance", "wifi_update", "pest_control",
            "room_inspection", "gate_lock_change", "meeting", "holiday", "price_update", "lost_found", "rule_violation", "thanks", "congrats", "survey");

    /** Gradle runs unit tests in bofo-android/app; also work from the repo root. */
    static File find(String relFromApp) {
        for (String base : new String[]{"", "bofo-android/app/"}) { File f = new File(base + relFromApp); if (f.exists()) return f; }
        return null;
    }
    static String read(File f) throws Exception { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }
    private static String unxml(String s) { return s.replace("\\'", "'").replace("\\\"", "\"").replace("&lt;", "<").replace("&amp;", "&"); }

    @Test public void hasTheTwentySixTypesInOrderAndLookUpWorks() {
        List<String> ids = new ArrayList<>();
        for (AnnounceType t : AnnounceType.ALL) ids.add(t.id);
        assertEquals(EXPECTED, ids);
        assertEquals(26, new HashSet<>(ids).size());
        assertNull(AnnounceType.byId("nope")); assertNull(AnnounceType.byId(null));
        assertEquals("emergency", AnnounceType.byId("emergency").id);
    }

    @Test public void everyTypeIsComplete() {
        for (AnnounceType t : AnnounceType.ALL) {
            assertTrue(t.id, t.fields.size() >= 1 && t.fields.size() <= 4);
            Set<String> keys = new HashSet<>();
            for (AnnounceType.Field f : t.fields) { assertTrue(t.id + "." + f.key, f.key.matches("[a-z_]+")); assertTrue("dup " + f.key, keys.add(f.key)); }
            for (String k : t.oneOf) assertNotNull(t.id + " oneOf " + k, t.field(k));
            assertTrue(t.id, t.fields.stream().anyMatch(f -> f.required) || !t.oneOf.isEmpty() || t.id.equals("welcome_tenant") || t.id.equals("wifi_update"));
            assertNotNull(t.defaultTone); assertFalse(t.emoji.isEmpty()); assertTrue(t.icon.startsWith("ic_"));
        }
        assertTrue(AnnounceType.byId("emergency").isUrgent()); assertTrue(AnnounceType.byId("typhoon").isUrgent());
        assertFalse(AnnounceType.byId("rent_due").isUrgent());
        assertEquals(AnnounceType.Tone.FIRM, AnnounceType.byId("rent_due").defaultTone);
        assertEquals(AnnounceType.Tone.FIRM, AnnounceType.byId("rule_violation").defaultTone);
        assertEquals(AnnounceType.Tone.SHORT, AnnounceType.byId("typhoon").defaultTone);
    }

    @Test public void enumsRoundTripTheirWireStrings() {
        assertEquals("taglish", AnnounceType.Lang.TAGLISH.wire);
        assertEquals(AnnounceType.Lang.CEB, AnnounceType.Lang.fromWire("ceb", AnnounceType.Lang.EN));
        assertEquals(AnnounceType.Lang.EN, AnnounceType.Lang.fromWire("xx", AnnounceType.Lang.EN));
        assertNull(AnnounceType.Tone.fromWire("", null));
        assertEquals(AnnounceType.Length.DETAILED, AnnounceType.Length.fromWire("detailed", AnnounceType.Length.NORMAL));
        assertEquals("friendlier", AnnounceType.Adjust.FRIENDLIER.wire);
        assertEquals(200, AnnounceType.MAX_FIELD_LEN); assertEquals(60, AnnounceType.MAX_CLOSING_NAME);
    }

    @Test public void everyStringResourceAndIconTheCatalogueNeedsExists() throws Exception {
        File xml = find("src/main/res/values/strings_announce_ai.xml");
        assertNotNull("strings_announce_ai.xml", xml);
        String s = read(xml);
        Set<String> names = new HashSet<>();
        Matcher m = Pattern.compile("<string name=\"(\\w+)\">").matcher(s);
        while (m.find()) names.add(m.group(1));
        for (AnnounceType t : AnnounceType.ALL) {
            assertTrue(t.titleName(), names.contains(t.titleName())); assertTrue(t.descName(), names.contains(t.descName()));
            for (AnnounceType.Field f : t.fields) { assertTrue(f.labelName(), names.contains(f.labelName())); assertTrue(t.hintName(f.key), names.contains(t.hintName(f.key))); }
            File icon = find("src/main/res/drawable/" + t.icon + ".xml");
            assertNotNull("icon " + t.icon, icon);
        }
        assertNotNull("ic_sparkle", find("src/main/res/drawable/ic_sparkle.xml"));
        for (String n : new String[]{"announce_urgency_important", "announce_urgency_urgent", "announce_write_for_me", "announce_use_this", "announce_err_not_owner", "announce_err_ai_disabled",
                "announce_err_rate_limited", "announce_err_invalid", "announce_err_offline", "announce_err_generic", "announce_degraded_note", "announce_err_missing", "announce_err_one_of", "announce_err_too_long"})
            assertTrue(n, names.contains(n));
        for (AnnounceType.Lang v : AnnounceType.Lang.values()) assertTrue(names.contains("announce_lang_" + v.wire));
        for (AnnounceType.Tone v : AnnounceType.Tone.values()) assertTrue(names.contains("announce_tone_" + v.wire));
        for (AnnounceType.Length v : AnnounceType.Length.values()) assertTrue(names.contains("announce_length_" + v.wire));
        for (AnnounceType.Adjust v : AnnounceType.Adjust.values()) assertTrue(names.contains("announce_adjust_" + v.wire));
    }

    /** Fails when the backend catalogue and the Java catalogue drift apart (ids, order, urgency, tone, emoji, field keys, required flags, oneOf, hints, enums, limits). */
    @Test public void javaCatalogueEqualsTheBackendCatalogue() throws Exception {
        File ts = find("../../supabase/functions/bofo-announce/catalogue.ts");
        if (ts == null) ts = new File("supabase/functions/bofo-announce/catalogue.ts");
        Assume.assumeTrue("catalogue.ts not found (run from the repo)", ts.exists());
        String src = read(ts);
        File xml = find("src/main/res/values/strings_announce_ai.xml");
        String strings = read(xml);

        Matcher m = Pattern.compile("\\{ id: \"(\\w+)\", urgency: \"(\\w)\", tone: \"(\\w+)\", emoji: \"([^\"]+)\",\\s*fields: \\[(.*?)\\],\\s*(?:oneOf: \\[(.*?)\\],\\s*)?guard:", Pattern.DOTALL).matcher(src);
        int n = 0;
        while (m.find()) {
            AnnounceType t = AnnounceType.ALL.get(n++);
            assertEquals("order/id #" + n, m.group(1), t.id);
            assertEquals(t.id + " urgency", m.group(2), t.urgency.wire);
            assertEquals(t.id + " tone", m.group(3), t.defaultTone.wire);
            assertEquals(t.id + " emoji", m.group(4), t.emoji);
            Matcher f = Pattern.compile("\\b([ro])\\(\"(\\w+)\", \"((?:[^\"\\\\]|\\\\.)*)\"\\)").matcher(m.group(5));
            int i = 0;
            while (f.find()) {
                AnnounceType.Field jf = t.fields.get(i++);
                assertEquals(t.id + " field key", f.group(2), jf.key);
                assertEquals(t.id + "." + jf.key + " required", f.group(1).equals("r"), jf.required);
                Matcher h = Pattern.compile("<string name=\"" + t.hintName(jf.key) + "\">(.*?)</string>").matcher(strings);
                assertTrue("hint string " + t.hintName(jf.key), h.find());
                assertEquals(t.id + "." + jf.key + " hint", f.group(3).replace("\\\"", "\""), unxml(h.group(1)));
            }
            assertEquals(t.id + " field count", i, t.fields.size());
            List<String> oo = new ArrayList<>();
            if (m.group(6) != null) { Matcher k = Pattern.compile("\"(\\w+)\"").matcher(m.group(6)); while (k.find()) oo.add(k.group(1)); }
            assertEquals(t.id + " oneOf", oo, t.oneOf);
        }
        assertEquals("type count", 26, n); assertEquals(n, AnnounceType.ALL.size());

        assertEquals(wires("LANGS", src), wires(AnnounceType.Lang.values()));
        assertEquals(wires("TONES", src), wires(AnnounceType.Tone.values()));
        assertEquals(wires("LENGTHS", src), wires(AnnounceType.Length.values()));
        assertEquals(wires("ADJUSTS", src), wires(AnnounceType.Adjust.values()));
        assertTrue(src.contains("MAX_FIELD_LEN = " + AnnounceType.MAX_FIELD_LEN));
        assertTrue(src.contains("MAX_CLOSING_NAME = " + AnnounceType.MAX_CLOSING_NAME));
    }

    private static List<String> wires(String name, String src) {
        Matcher m = Pattern.compile("export const " + name + " = \\[(.*?)\\] as const").matcher(src);
        assertTrue(name, m.find());
        List<String> out = new ArrayList<>(); Matcher k = Pattern.compile("\"(\\w+)\"").matcher(m.group(1));
        while (k.find()) out.add(k.group(1));
        return out;
    }
    private static List<String> wires(Object[] values) throws Exception {
        List<String> out = new ArrayList<>();
        for (Object v : values) out.add((String) v.getClass().getField("wire").get(v));
        return out;
    }
}
