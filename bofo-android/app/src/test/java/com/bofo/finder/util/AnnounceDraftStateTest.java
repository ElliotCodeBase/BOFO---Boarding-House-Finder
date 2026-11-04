package com.bofo.finder.util;

import static org.junit.Assert.*;

import com.bofo.finder.model.AnnounceDraft;
import com.bofo.finder.model.AnnounceError;
import com.bofo.finder.model.AnnounceType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;

/**
 * v2.4.3 AI Announcement Writer step 6: the stage C decisions (plain JVM). The sheet (ui/AnnounceAiSheet) and the host (ui/AnnounceFragment)
 * have no unit test: no instrumented tests exist in this project. The live function is never called here.
 */
public class AnnounceDraftStateTest {
    private static Map<String, String> map(String... kv) { Map<String, String> m = new LinkedHashMap<>(); for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]); return m; }
    private static final Map<String, String> RENT = map("due_date", "Nov 5", "amount", "P2,500", "how_to_pay", "GCash");

    private static AnnounceFormState.Submission sub() {
        AnnounceFormState.Check c = AnnounceFormState.check(AnnounceType.byId("rent_due"), AnnounceType.Lang.FIL, AnnounceType.Tone.FIRM,
                AnnounceType.Length.DETAILED, true, true, "Mrs. Reyes", RENT);
        assertTrue(c.ok());
        return c.submission;
    }
    private static AnnounceDraft draft(String t, String m) { return new AnnounceDraft(t, m, null, false); }
    private static AnnounceDraftState loading() { AnnounceDraftState s = new AnnounceDraftState(sub()); assertNotNull(s.first()); return s; }
    private static AnnounceDraftState withDraft() { AnnounceDraftState s = loading(); assertTrue(s.onResult(draft("Rent", "Pay by Nov 5."), null, 0, null, null)); return s; }
    private static AnnounceDraftState withError(AnnounceError e, int retryAfter) { AnnounceDraftState s = loading(); assertTrue(s.onResult(null, e, retryAfter, null, null)); return s; }

    @Test public void loadingThenSuccessOrError() {
        AnnounceDraftState s = new AnnounceDraftState(sub());
        assertEquals(AnnounceDraftState.Phase.IDLE, s.phase());
        AnnounceDraftState.Request r = s.first();
        assertEquals(AnnounceDraftState.Phase.LOADING, s.phase()); assertTrue(s.loading());
        assertEquals(AnnounceType.Lang.FIL, r.lang); assertEquals(AnnounceType.Tone.FIRM, r.tone); assertEquals(AnnounceType.Length.DETAILED, r.length); assertNull(r.adjust);
        assertNull("only one first request", s.first());
        assertTrue(s.onResult(draft("A", "B"), null, 0, null, null));
        assertEquals(AnnounceDraftState.Phase.SUCCESS, s.phase()); assertEquals("A", s.draft().title); assertFalse(s.edited());
        AnnounceDraftState e = withError(AnnounceError.SERVER, 0);
        assertEquals(AnnounceDraftState.Phase.ERROR, e.phase()); assertEquals(AnnounceError.SERVER, e.error()); assertNull(e.draft());
    }

    @Test public void lateOrEmptyResultsAreIgnored() {
        AnnounceDraftState idle = new AnnounceDraftState(sub());
        assertFalse("nothing in flight", idle.onResult(draft("A", "B"), null, 0, null, null));
        AnnounceDraftState s = withDraft();
        assertFalse("a second result for the same request", s.onResult(draft("X", "Y"), null, 0, null, null));
        assertEquals("Rent", s.draft().title);
        AnnounceDraftState l = loading();
        assertFalse("neither draft nor error", l.onResult(null, null, 0, null, null));
        assertEquals(AnnounceDraftState.Phase.LOADING, l.phase());
    }

    @Test public void eachAdjustSetsItsToneAndLengthAndKeepsTheFacts() {
        AnnounceFormState.Submission base = sub();
        AnnounceDraftState s = withDraft();
        AnnounceDraftState.Request sh = s.adjust(AnnounceType.Adjust.SHORTER);
        assertEquals(AnnounceType.Length.SHORT, sh.length); assertEquals("tone stays", AnnounceType.Tone.FIRM, sh.tone); assertEquals(AnnounceType.Adjust.SHORTER, sh.adjust);
        assertEquals(AnnounceType.Length.SHORT, s.length());
        s.onResult(draft("A", "B"), null, 0, null, null);
        AnnounceDraftState.Request fr = s.adjust(AnnounceType.Adjust.FRIENDLIER);
        assertEquals(AnnounceType.Tone.FRIENDLY, fr.tone); assertEquals("length stays (set by Shorter before)", AnnounceType.Length.SHORT, fr.length);
        s.onResult(draft("A", "B"), null, 0, null, null);
        AnnounceDraftState.Request fo = s.adjust(AnnounceType.Adjust.FORMAL);
        assertEquals(AnnounceType.Tone.FORMAL, fo.tone); assertEquals(AnnounceType.Adjust.FORMAL, fo.adjust);
        s.onResult(draft("A", "B"), null, 0, null, null);
        AnnounceDraftState.Request ag = s.again();
        assertEquals(AnnounceType.Adjust.AGAIN, ag.adjust); assertEquals(AnnounceType.Tone.FORMAL, ag.tone); assertEquals(AnnounceType.Length.SHORT, ag.length);
        assertEquals("the same facts for every request", base.fields, s.submission().fields);
        assertEquals(RENT.size(), s.submission().fields.size()); assertEquals("GCash", s.submission().fields.get("how_to_pay"));
        assertEquals("Mrs. Reyes", s.submission().closingName); assertTrue(s.submission().emoji);
    }

    @Test public void changeLanguageRegeneratesInTheNewLanguageWithoutAdjust() {
        AnnounceDraftState s = withDraft();
        assertNull("same language does nothing", s.changeLang(AnnounceType.Lang.FIL));
        assertEquals(AnnounceDraftState.Phase.SUCCESS, s.phase());
        AnnounceDraftState.Request r = s.changeLang(AnnounceType.Lang.CEB);
        assertEquals(AnnounceType.Lang.CEB, r.lang); assertNull(r.adjust); assertEquals(AnnounceType.Tone.FIRM, r.tone); assertEquals(AnnounceType.Length.DETAILED, r.length);
        assertEquals(AnnounceDraftState.Phase.LOADING, s.phase());
        assertNull(withDraft().changeLang(null));
    }

    @Test public void noSecondRequestWhileLoadingOrWithoutADraft() {
        AnnounceDraftState s = loading();
        assertNull(s.adjust(AnnounceType.Adjust.SHORTER)); assertNull(s.again()); assertNull(s.changeLang(AnnounceType.Lang.EN)); assertNull(s.retry());
        AnnounceDraftState e = withError(AnnounceError.OFFLINE, 0);
        assertNull("adjust needs a draft on screen", e.adjust(AnnounceType.Adjust.SHORTER)); assertNull(e.changeLang(AnnounceType.Lang.EN));
        AnnounceDraftState d = withDraft();
        assertNotNull(d.adjust(AnnounceType.Adjust.SHORTER));
        assertNull("double tap while the first request runs", d.adjust(AnnounceType.Adjust.SHORTER));
        assertNull(withDraft().adjust(null));
        assertNull(new AnnounceDraftState(sub()).again());
    }

    @Test public void errorTableRetryAndRoute() {
        for (AnnounceError e : AnnounceError.values()) {
            AnnounceDraftState s = withError(e, 5);
            boolean noRetry = e == AnnounceError.SIGNED_OUT || e == AnnounceError.NOT_OWNER || e == AnnounceError.AI_DISABLED || e == AnnounceError.INVALID || e == AnnounceError.INVALID_DETAILS;
            assertEquals(e.name(), !noRetry, s.errorCanRetry());
            assertEquals(e.name(), e.canRetry(), s.errorCanRetry());
            boolean toForm = e == AnnounceError.INVALID || e == AnnounceError.INVALID_DETAILS;
            assertEquals(e.name(), toForm ? AnnounceDraftState.Route.BACK_TO_FORM : AnnounceDraftState.Route.SHOW_ERROR, s.route());
            assertEquals(e.name(), s.route(), AnnounceDraftState.routeFor(e));
            if (noRetry) assertNull(e.name(), s.retry());
        }
        assertNull(withDraft().route());
    }

    @Test public void rateLimitedCountdownEnablesTheButtonAtZero() {
        AnnounceDraftState s = withError(AnnounceError.RATE_LIMITED, 3);
        assertEquals(3, s.secondsLeft()); assertTrue(s.errorCanRetry()); assertFalse(s.canRetryNow()); assertNull("not before the wait is over", s.retry());
        assertEquals(2, s.tick()); assertFalse(s.canRetryNow());
        assertEquals(1, s.tick()); assertFalse(s.canRetryNow());
        assertEquals(0, s.tick()); assertTrue(s.canRetryNow());
        assertEquals("never below zero", 0, s.tick());
        assertNotNull(s.retry());
        AnnounceDraftState zero = withError(AnnounceError.RATE_LIMITED, 0);
        assertTrue("0 or negative seconds = ready", zero.canRetryNow());
        assertEquals(0, withError(AnnounceError.RATE_LIMITED, -4).secondsLeft());
        AnnounceDraftState server = withError(AnnounceError.SERVER, 99);
        assertEquals("only RATE_LIMITED waits", 0, server.secondsLeft()); assertEquals(0, server.tick()); assertTrue(server.canRetryNow());
        assertEquals("no countdown outside an error", 0, withDraft().tick());
    }

    @Test public void retryResendsTheSameRequestAndKeepsTheFacts() {
        AnnounceDraftState s = withDraft();
        AnnounceDraftState.Request first = s.adjust(AnnounceType.Adjust.FRIENDLIER);
        s.onResult(null, AnnounceError.OFFLINE, 0, null, null);
        AnnounceDraftState.Request again = s.retry();
        assertEquals(first.adjust, again.adjust); assertEquals(first.tone, again.tone); assertEquals(first.length, again.length); assertEquals(first.lang, again.lang);
        assertEquals(AnnounceDraftState.Phase.LOADING, s.phase());
        assertNull(s.retry());
    }

    @Test public void cancelGoesBackToThePreviousDraftOrToTheForm() {
        AnnounceDraftState first = loading();
        assertEquals("no draft yet: the sheet shows the form", AnnounceDraftState.Phase.IDLE, first.cancel());
        AnnounceDraftState s = withDraft();
        s.adjust(AnnounceType.Adjust.SHORTER); s.changeLang(AnnounceType.Lang.EN);        // second call is ignored (loading)
        assertEquals(AnnounceType.Length.SHORT, s.length());
        assertEquals(AnnounceDraftState.Phase.SUCCESS, s.cancel());
        assertEquals("options restored", AnnounceType.Length.DETAILED, s.length()); assertEquals(AnnounceType.Lang.FIL, s.lang()); assertEquals("Rent", s.draft().title);
        AnnounceDraftState r = withDraft();
        r.adjust(AnnounceType.Adjust.FORMAL); r.onResult(null, AnnounceError.SERVER, 0, null, null); r.retry();
        assertEquals("a retry keeps the snapshot of the first attempt", AnnounceDraftState.Phase.SUCCESS, r.cancel());
        assertEquals(AnnounceType.Tone.FIRM, r.tone());
        assertEquals(AnnounceDraftState.Phase.SUCCESS, withDraft().cancel());              // not loading: unchanged
    }

    @Test public void editedDraftNeedsAConfirmBeforeEveryRegenerate() {
        AnnounceDraftState s = withDraft();
        assertFalse("fresh draft", s.needsConfirmBeforeRegenerate());
        s.setEdited("Rent", "Pay by Nov 5.");
        assertFalse("same text = not edited", s.edited());
        s.setEdited("Rent!", "Pay by Nov 5."); assertTrue(s.edited()); assertTrue(s.needsConfirmBeforeRegenerate());
        s.setEdited("Rent", "Pay by Nov 5."); assertFalse("typed back to the original", s.needsConfirmBeforeRegenerate());
        s.setEdited(null, "Pay by Nov 5."); assertTrue("a cleared title is an edit", s.needsConfirmBeforeRegenerate());
        s.adjust(AnnounceType.Adjust.SHORTER);
        assertFalse("while the request runs there is nothing to ask", s.needsConfirmBeforeRegenerate());
        s.onResult(draft("New", "Text"), null, 0, null, null);
        assertFalse("a new draft starts clean", s.edited());
        s.setEdited("New x", "Text");
        s.adjust(AnnounceType.Adjust.AGAIN); s.onResult(null, AnnounceError.OFFLINE, 0, null, null);
        assertEquals(AnnounceDraftState.Phase.ERROR, s.phase());
        assertTrue("the draft and its edit flag survive an error: Try again must ask", s.hasDraft() && s.edited() && s.needsConfirmBeforeRegenerate());
        AnnounceDraftState noDraft = withError(AnnounceError.OFFLINE, 0);
        noDraft.setEdited("x", "y"); assertFalse("no draft, nothing to lose", noDraft.needsConfirmBeforeRegenerate());
    }

    @Test public void editsSurviveAnErrorAndAVisitToTheForm() {
        AnnounceDraftState s = withDraft();
        s.setEdited("My title", "My text");
        s.adjust(AnnounceType.Adjust.AGAIN); s.onResult(null, AnnounceError.GENERIC, 0, null, null);   // a failed regenerate
        assertEquals(AnnounceDraftState.Phase.ERROR, s.phase());
        assertTrue("the sheet keeps this state while the owner opens Edit details; Write for me pressed again must ask", s.needsConfirmBeforeRegenerate());
        assertNotNull("a confirmed Try again still works", s.retry());
    }

    @Test public void formProblemReusesTheStepFiveTexts() {
        AnnounceType rent = AnnounceType.byId("rent_due");
        AnnounceRules.Problem p = AnnounceRules.validateFields(rent, map("due_date", "", "amount", "P2,500", "how_to_pay", "GCash"));
        assertNotNull(p);
        AnnounceDraftState s = loading();
        s.onResult(null, AnnounceError.INVALID_DETAILS, 0, p.field, p);
        AnnounceFormState.ProblemText t = s.formProblem();
        assertEquals(AnnounceFormState.describe(rent, p).stringName, t.stringName); assertEquals(p.field, t.focusKey());
        AnnounceDraftState inv = loading();
        inv.onResult(null, AnnounceError.INVALID, 0, "amount", null);
        assertEquals("announce_err_invalid", inv.formProblem().stringName); assertEquals("amount", inv.formProblem().focusKey()); assertEquals("amount", inv.errorField());
        AnnounceDraftState nofield = loading();
        nofield.onResult(null, AnnounceError.INVALID, 0, null, null);
        assertEquals("unknown field: nothing to focus", "", nofield.formProblem().focusKey());
        assertNull(withError(AnnounceError.SERVER, 0).formProblem()); assertNull(withDraft().formProblem());
    }

    @Test public void useThisClipsTo120And1000WithoutCuttingAnEmoji() {
        StringBuilder t = new StringBuilder(), m = new StringBuilder();
        for (int i = 0; i < 300; i++) t.append('t');
        for (int i = 0; i < 3000; i++) m.append('m');
        AnnounceDraftState.Use u = AnnounceDraftState.prepareUse(t.toString(), m.toString());
        assertEquals(120, u.title.length()); assertEquals(1000, u.message.length());
        String smile = "\uD83D\uDE0A";                                                    // one emoji = two chars
        StringBuilder e = new StringBuilder(); for (int i = 0; i < 119; i++) e.append('a'); e.append(smile);
        String clipped = AnnounceDraftState.prepareUse(e.toString(), "x").title;
        assertEquals("the emoji is dropped whole, not split", 119, clipped.length()); assertFalse(Character.isHighSurrogate(clipped.charAt(clipped.length() - 1)));
        StringBuilder em = new StringBuilder(); for (int i = 0; i < 999; i++) em.append('b'); em.append(smile);
        assertEquals(999, AnnounceDraftState.prepareUse("T", em.toString()).message.length());
    }

    @Test public void useThisKeepsParagraphsAndPutsTheTitleOnOneLine() {
        AnnounceDraftState.Use u = AnnounceDraftState.prepareUse("  Water \n  cut   tomorrow ", "Hello all,\r\n\r\nNo water 8-12.\r\nThank you.  ");
        assertEquals("Water cut tomorrow", u.title);
        assertEquals("Hello all,\n\nNo water 8-12.\nThank you.", u.message);
        assertEquals("exactly at the limit stays", 120, AnnounceDraftState.prepareUse(rep('x', 120), "m").title.length());
    }

    @Test public void useThisNeedsATitleAndAMessage() {
        assertNull(AnnounceDraftState.prepareUse("", "text")); assertNull(AnnounceDraftState.prepareUse("   ", "text"));
        assertNull(AnnounceDraftState.prepareUse("Title", "")); assertNull(AnnounceDraftState.prepareUse("Title", " \n ")); assertNull(AnnounceDraftState.prepareUse(null, null));
        assertNotNull(AnnounceDraftState.prepareUse("T", "M"));
    }

    @Test public void replacementIsConfirmedOnlyWhenTheFormHasText() {
        assertFalse(AnnounceDraftState.needsReplaceConfirm("", "")); assertFalse(AnnounceDraftState.needsReplaceConfirm(null, null));
        assertFalse("spaces only", AnnounceDraftState.needsReplaceConfirm("  ", " \n"));
        assertTrue(AnnounceDraftState.needsReplaceConfirm("Title", "")); assertTrue(AnnounceDraftState.needsReplaceConfirm("", "Body"));
        assertTrue(AnnounceDraftState.needsReplaceConfirm("T", "B"));
    }

    @Test public void theStateNeverPrintsItsContents() {
        AnnounceDraftState s = withDraft();
        s.setEdited("Secret title", "Secret body GCash 0917");
        String out = s.toString();
        assertFalse(out.contains("Rent")); assertFalse(out.contains("Secret")); assertFalse(out.contains("GCash")); assertFalse(out.contains("Reyes"));
        assertNotNull(new AnnounceDraftState(sub()).submission());
        try { new AnnounceDraftState(null); fail(); } catch (IllegalArgumentException expected) { }
    }

    @Test public void everyStringStageCNeedsExists() throws Exception {
        File xml = null;
        for (String p : new String[]{"src/main/res/values/strings_announce_ai.xml", "bofo-android/app/src/main/res/values/strings_announce_ai.xml"}) if (new File(p).exists()) xml = new File(p);
        Assume.assumeTrue("strings_announce_ai.xml not found", xml != null);
        String s = new String(Files.readAllBytes(xml.toPath()), StandardCharsets.UTF_8);
        String[] names = {"announce_result_title", "announce_writing", "announce_cancel", "announce_edit_details", "announce_change_language", "announce_draft_title_label",
                "announce_draft_message_label", "announce_draft_counter", "announce_notes_line", "announce_err_write_yourself", "announce_err_rate_ready", "announce_back_details_cd",
                "announce_use_empty", "announce_regen_title", "announce_regen_body", "announce_regen_yes", "announce_regen_no", "announce_replace_title", "announce_replace_body",
                "announce_replace_yes", "announce_replace_no", "announce_draft_added", "announce_use_this", "announce_write_for_me", "announce_check_before_posting",
                "announce_degraded_note", "announce_notes_label", "announce_adjust_again", "announce_adjust_shorter", "announce_adjust_friendlier", "announce_adjust_formal"};
        for (String n : names) assertTrue(n, s.contains("name=\"" + n + "\""));
        for (AnnounceError e : AnnounceError.values()) assertTrue(e.stringName(), s.contains("name=\"" + e.stringName() + "\""));
        assertTrue(s.contains("%1$d seconds"));
        assertTrue(s.contains("name=\"announce_draft_counter\">%1$d / %2$d<"));
    }

    private static String rep(char c, int n) { StringBuilder b = new StringBuilder(); for (int i = 0; i < n; i++) b.append(c); return b.toString(); }
}
