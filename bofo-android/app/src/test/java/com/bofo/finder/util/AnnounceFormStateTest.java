package com.bofo.finder.util;

import static org.junit.Assert.*;

import com.bofo.finder.model.AnnounceType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;

/** v2.4.2 AI Announcement Writer step 5: the sheet's logic (plain JVM). The sheet itself (ui/AnnounceAiSheet) has no unit test: no instrumented tests exist. */
public class AnnounceFormStateTest {
    private static AnnounceType t(String id) { return AnnounceType.byId(id); }
    private static Map<String, String> map(String... kv) { Map<String, String> m = new LinkedHashMap<>(); for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]); return m; }
    private static final Map<String, String> RENT = map("due_date", " Nov 5 ", "amount", "P2,500", "how_to_pay", "GCash", "late_fee", "  ");

    @Test public void startsOnTheListPickGoesToDetailsBackReturns() {
        AnnounceFormState s = new AnnounceFormState();
        assertEquals(AnnounceFormState.Stage.LIST, s.stage()); assertNull(s.type());
        assertFalse("Back on the list lets the sheet close", s.back());
        s.pick(null); assertEquals(AnnounceFormState.Stage.LIST, s.stage());
        s.pick(t("rent_due"));
        assertEquals(AnnounceFormState.Stage.DETAILS, s.stage()); assertEquals("rent_due", s.type().id);
        assertTrue("Back on the form only returns to the list", s.back());
        assertEquals(AnnounceFormState.Stage.LIST, s.stage()); assertNull(s.type());
        assertFalse(s.back());
    }

    @Test public void restoreAfterRotation() {
        AnnounceFormState s = new AnnounceFormState(); s.pick(t("typhoon"));
        AnnounceFormState r = AnnounceFormState.restore(s.typeIdToSave());
        assertEquals(AnnounceFormState.Stage.DETAILS, r.stage()); assertEquals("typhoon", r.type().id);
        assertNull(new AnnounceFormState().typeIdToSave());
        assertEquals(AnnounceFormState.Stage.LIST, AnnounceFormState.restore(null).stage());
        assertEquals(AnnounceFormState.Stage.LIST, AnnounceFormState.restore("no_such_type").stage());
    }

    @Test public void defaultsUseTheRememberedChoicesAndTheTypesOwnTone() {
        AnnounceFormState.Options o = AnnounceFormState.defaults(t("rent_due"), "taglish", "", "detailed", true);
        assertEquals(AnnounceType.Lang.TAGLISH, o.lang); assertEquals(AnnounceType.Tone.FIRM, o.tone);       // "" = the type's default
        assertEquals(AnnounceType.Length.DETAILED, o.length); assertTrue(o.emoji);
        assertEquals(AnnounceType.Tone.FORMAL, AnnounceFormState.defaults(t("rent_due"), "en", "formal", "normal", false).tone);
        AnnounceFormState.Options bad = AnnounceFormState.defaults(t("house_rules"), "xx", "loud", null, false);
        assertEquals(AnnounceType.Lang.EN, bad.lang); assertEquals(t("house_rules").defaultTone, bad.tone); assertEquals(AnnounceType.Length.NORMAL, bad.length);
        assertEquals(AnnounceType.Tone.FRIENDLY, AnnounceFormState.defaults(null, null, null, null, false).tone);
    }

    @Test public void toneIsRememberedOnlyWhenItDiffersFromTheTypeDefault() {
        assertEquals("", AnnounceFormState.toneToRemember(t("rent_due"), AnnounceType.Tone.FIRM));
        assertEquals("formal", AnnounceFormState.toneToRemember(t("rent_due"), AnnounceType.Tone.FORMAL));
        assertEquals("", AnnounceFormState.toneToRemember(t("rent_due"), null));
    }

    @Test public void searchFiltersByLabelAndIdWordsKeepingTheOrder() {
        java.util.function.Function<AnnounceType, String> label = x -> x.id.equals("rent_due") ? "Rent reminder" : x.id.equals("water_cut") ? "Water interruption" : null;
        List<AnnounceType> all = AnnounceType.ALL;
        assertSame(all, AnnounceFormState.filter(all, "", label)); assertSame(all, AnnounceFormState.filter(all, "   ", label)); assertSame(all, AnnounceFormState.filter(all, null, label));
        List<AnnounceType> r = AnnounceFormState.filter(all, "RENT", label);
        assertEquals(1, r.size()); assertEquals("rent_due", r.get(0).id);
        assertEquals("water_cut", AnnounceFormState.filter(all, "water  inter", label).get(0).id);          // spaces collapse, words in any place
        assertEquals("typhoon", AnnounceFormState.filter(all, "typhoon", label).get(0).id);                  // falls back to the id when no label
        assertEquals("lost_found", AnnounceFormState.filter(all, "lost found", null).get(0).id);             // label function may be null
        assertTrue(AnnounceFormState.filter(all, "zzzzqq", label).isEmpty());
        List<AnnounceType> gate = AnnounceFormState.filter(all, "o", label);
        for (int i = 1; i < gate.size(); i++) assertTrue(all.indexOf(gate.get(i - 1)) < all.indexOf(gate.get(i)));
    }

    @Test public void badgeNamesFollowTheUrgency() {
        assertEquals("announce_urgency_urgent", AnnounceFormState.badgeStringName(t("emergency")));
        assertEquals("announce_urgency_urgent", AnnounceFormState.badgeStringName(t("typhoon")));
        assertEquals("announce_urgency_important", AnnounceFormState.badgeStringName(t("rent_due")));
        assertNull(AnnounceFormState.badgeStringName(t("general")));
    }

    @Test public void closingNameIsPrefilledFromTheProfileAndClipped() {
        assertEquals("Mrs. Reyes", AnnounceFormState.closingDefault("  Mrs.   Reyes "));
        assertEquals("", AnnounceFormState.closingDefault(null));
        StringBuilder b = new StringBuilder(); for (int i = 0; i < 90; i++) b.append('n');
        assertEquals(60, AnnounceFormState.closingDefault(b.toString()).length());
    }

    @Test public void problemsAreMappedToAStringAndAFieldKey() {
        AnnounceFormState.Check miss = AnnounceFormState.check(t("rent_due"), null, null, null, false, false, null, map("due_date", "Nov 5"));
        assertFalse(miss.ok()); assertNull(miss.submission);
        assertEquals("announce_err_missing", miss.problem.stringName); assertEquals("amount", miss.problem.focusKey());
        StringBuilder big = new StringBuilder(); for (int i = 0; i < 201; i++) big.append('x');
        AnnounceFormState.Check tooLong = AnnounceFormState.check(t("rent_due"), null, null, null, false, false, null, map("due_date", big.toString(), "amount", "1", "how_to_pay", "x"));
        assertEquals("announce_err_too_long", tooLong.problem.stringName); assertEquals("due_date", tooLong.problem.focusKey());
        AnnounceFormState.Check oneOf = AnnounceFormState.check(t("gate_lock_change"), null, null, null, false, false, null, map("effective_date", "Nov 10"));
        assertEquals("announce_err_one_of", oneOf.problem.stringName);
        assertEquals(t("gate_lock_change").oneOf, oneOf.problem.fieldKeys); assertEquals(t("gate_lock_change").oneOf.get(0), oneOf.problem.focusKey());
        assertEquals("announce_err_invalid", AnnounceFormState.check(null, null, null, null, false, false, null, RENT).problem.stringName);
        assertFalse(AnnounceFormState.check(t("rent_due"), null, null, null, false, false, null, null).ok());
    }

    @Test public void everyProblemStringExists() throws Exception {
        File xml = null;
        for (String p : new String[]{"src/main/res/values/strings_announce_ai.xml", "bofo-android/app/src/main/res/values/strings_announce_ai.xml"}) if (new File(p).exists()) xml = new File(p);
        Assume.assumeTrue("strings_announce_ai.xml not found", xml != null);
        String s = new String(Files.readAllBytes(xml.toPath()), StandardCharsets.UTF_8);
        for (String n : new String[]{"announce_err_missing", "announce_err_one_of", "announce_err_too_long", "announce_err_invalid", "announce_urgency_urgent", "announce_urgency_important"})
            assertTrue(n, s.contains("<string name=\"" + n + "\">"));
    }

    @Test public void validDetailsBecomeACleanSubmission() throws Exception {
        AnnounceFormState.Check c = AnnounceFormState.check(t("rent_due"), AnnounceType.Lang.FIL, null, null, true, true, "  Mrs.  Reyes ", RENT);
        assertTrue(c.ok()); assertNull(c.problem);
        AnnounceFormState.Submission s = c.submission;
        assertEquals("rent_due", s.type.id); assertEquals(AnnounceType.Lang.FIL, s.lang);
        assertEquals(AnnounceType.Tone.FIRM, s.tone); assertEquals(AnnounceType.Length.NORMAL, s.length);   // null -> defaults
        assertTrue(s.emoji); assertTrue(s.closingOn); assertEquals("Mrs. Reyes", s.closingName);
        assertEquals(3, s.fields.size()); assertEquals("Nov 5", s.fields.get("due_date")); assertFalse(s.fields.containsKey("late_fee"));
        try { s.fields.put("x", "y"); fail("immutable"); } catch (UnsupportedOperationException expected) { }
        JSONObject req = s.toRequest(AnnounceType.Adjust.SHORTER);
        assertEquals("rent_due", req.getString("type")); assertEquals("shorter", req.getString("adjust")); assertEquals("fil", req.getString("lang"));
    }

    @Test public void signOffNeedsAName() {
        assertFalse(AnnounceFormState.check(t("general"), null, null, null, false, true, "   ", map("idea", "Hi")).submission.closingOn);
        assertFalse(AnnounceFormState.check(t("general"), null, null, null, false, false, "Ana", map("idea", "Hi")).submission.closingOn);
        assertTrue(AnnounceFormState.check(t("general"), null, null, null, false, true, "Ana", map("idea", "Hi")).submission.closingOn);
    }

    @Test public void noFieldValuesInTheSubmissionToString() {
        // the carrier classes define no toString that would put the owner's text (e.g. a wifi password) into a log line
        AnnounceFormState.Check c = AnnounceFormState.check(t("wifi_update"), null, null, null, false, false, null, map("password", "s3cret-pw"));
        assertTrue(c.ok());
        assertFalse(c.submission.toString().contains("s3cret-pw"));
        assertFalse(c.toString().contains("s3cret-pw"));
    }
}
