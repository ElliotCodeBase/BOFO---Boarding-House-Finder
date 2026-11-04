package com.bofo.finder.util;

import com.bofo.finder.model.AnnounceDraft;
import com.bofo.finder.model.AnnounceError;
import com.bofo.finder.model.AnnounceType;

import java.util.ArrayList;
import java.util.Collections;

/**
 * v2.4.3 (AI Announcement Writer, step 6): the logic of stage C (generate / review / use) that does not need Android, so it is unit tested.
 * The sheet (ui/AnnounceAiSheet) only draws it and calls AnnounceAiService; the host (AnnounceFragment) uses {@link #prepareUse} and
 * {@link #needsReplaceConfirm}.
 * <ul>
 * <li>It keeps the validated {@link AnnounceFormState.Submission} (the facts) so every Try again / Shorter / More friendly / More formal /
 *     language change re-sends the SAME facts; only language, tone, length and the adjust hint change.</li>
 * <li>Every regenerate method returns ONE {@link Request} (or null = not allowed now). Nothing here retries by itself.</li>
 * <li>Nothing is logged and nothing is stored: not the facts, not the sign-off name, not the draft text. No {@code toString} on purpose.</li>
 * </ul>
 */
public final class AnnounceDraftState {
    public enum Phase { IDLE, LOADING, SUCCESS, ERROR }

    /** Where the sheet goes after a failure: show the error on stage C, or back to the form with the field marked. */
    public enum Route { SHOW_ERROR, BACK_TO_FORM }

    /** What to pass to {@code AnnounceAiService.generate} together with the Submission's facts. */
    public static final class Request {
        public final AnnounceType.Lang lang;
        public final AnnounceType.Tone tone;
        public final AnnounceType.Length length;
        /** null = a plain first request or a language change. */
        public final AnnounceType.Adjust adjust;
        Request(AnnounceType.Lang lang, AnnounceType.Tone tone, AnnounceType.Length length, AnnounceType.Adjust adjust) {
            this.lang = lang; this.tone = tone; this.length = length; this.adjust = adjust;
        }
    }

    private final AnnounceFormState.Submission submission;
    private AnnounceType.Lang lang;
    private AnnounceType.Tone tone;
    private AnnounceType.Length length;

    private Phase phase = Phase.IDLE;
    private AnnounceDraft draft;            // last successful draft (kept while a regenerate is running, so Cancel can go back to it)
    private boolean edited;                 // the owner changed the draft by hand
    private AnnounceError error;
    private String errorField;              // INVALID only
    private AnnounceRules.Problem problem;  // INVALID_DETAILS only
    private int secondsLeft;                // RATE_LIMITED only

    private Request pending;                // the request in flight / the one that failed (Try again re-sends exactly this)
    private AnnounceType.Lang prevLang;     // options before the request in flight, restored by cancel()
    private AnnounceType.Tone prevTone;
    private AnnounceType.Length prevLength;

    public AnnounceDraftState(AnnounceFormState.Submission submission) {
        if (submission == null) throw new IllegalArgumentException("submission");
        this.submission = submission;
        this.lang = submission.lang; this.tone = submission.tone; this.length = submission.length;
    }

    /* ---------- read ---------- */

    public AnnounceFormState.Submission submission() { return submission; }
    public Phase phase() { return phase; }
    public AnnounceType.Lang lang() { return lang; }
    public AnnounceType.Tone tone() { return tone; }
    public AnnounceType.Length length() { return length; }
    /** The last successful draft, or null. */
    public AnnounceDraft draft() { return draft; }
    public boolean hasDraft() { return draft != null; }
    public AnnounceError error() { return error; }
    public String errorField() { return errorField; }
    public boolean loading() { return phase == Phase.LOADING; }

    /* ---------- requests ---------- */

    /** The first request (stage B -> C): the options of the Submission, no adjust. Only from IDLE; else null. */
    public Request first() {
        if (phase != Phase.IDLE) return null;
        return start(new Request(lang, tone, length, null), true);
    }

    /** Try again with the same options: a new draft of the same kind (Adjust.AGAIN). Allowed on a draft, never while loading. */
    public Request again() { return adjust(AnnounceType.Adjust.AGAIN); }

    /**
     * Shorter (length short), More friendly (tone friendly), More formal (tone formal) or Try again (no change). Allowed only while a draft is
     * shown (SUCCESS); null while loading, on an error or with a null argument: the buttons are disabled then and a double tap cannot send two requests.
     */
    public Request adjust(AnnounceType.Adjust a) {
        if (phase != Phase.SUCCESS || a == null) return null;
        AnnounceType.Tone t = tone; AnnounceType.Length l = length;
        switch (a) {
            case SHORTER: l = AnnounceType.Length.SHORT; break;
            case FRIENDLIER: t = AnnounceType.Tone.FRIENDLY; break;
            case FORMAL: t = AnnounceType.Tone.FORMAL; break;
            default: break;                                           // AGAIN: same options
        }
        return start(new Request(lang, t, l, a), true);
    }

    /** Change language: regenerate in the new language, tone and length kept, no adjust. Same language or not on a draft = null. */
    public Request changeLang(AnnounceType.Lang l) {
        if (phase != Phase.SUCCESS || l == null || l == lang) return null;
        return start(new Request(l, tone, length, null), true);
    }

    /** Try again after an error that can be retried and whose wait is over: re-sends the SAME request. Else null. */
    public Request retry() {
        if (!canRetryNow() || pending == null) return null;
        return start(pending, false);
    }

    private Request start(Request r, boolean snapshot) {
        if (snapshot) { prevLang = lang; prevTone = tone; prevLength = length; }     // a retry keeps the snapshot of the first attempt
        lang = r.lang; tone = r.tone; length = r.length;
        pending = r;
        phase = Phase.LOADING;
        error = null; errorField = null; problem = null; secondsLeft = 0;
        return r;
    }

    /* ---------- outcome ---------- */

    /**
     * The outcome of the request in flight (unpack AnnounceAiService.Result: {@code draft} or {@code error} + its extras). Returns false and changes
     * nothing when no request is in flight (a late or double result), or when neither a draft nor an error is given.
     */
    public boolean onResult(AnnounceDraft d, AnnounceError e, int retryAfterSeconds, String field, AnnounceRules.Problem p) {
        if (phase != Phase.LOADING) return false;
        if (d != null) {
            draft = d; edited = false; phase = Phase.SUCCESS;
            error = null; errorField = null; problem = null; secondsLeft = 0;
            return true;
        }
        if (e == null) return false;
        phase = Phase.ERROR;
        error = e; errorField = field; problem = p;
        secondsLeft = e == AnnounceError.RATE_LIMITED ? Math.max(retryAfterSeconds, 0) : 0;
        return true;
    }

    /** Cancel while loading: back to the previous draft (options restored) or, when there was none, to IDLE (the sheet shows the form). */
    public Phase cancel() {
        if (phase != Phase.LOADING) return phase;
        lang = prevLang; tone = prevTone; length = prevLength;
        pending = null;
        phase = draft != null ? Phase.SUCCESS : Phase.IDLE;
        return phase;
    }

    /** Where to go after the current error (null when there is no error). */
    public Route route() { return error == null ? null : routeFor(error); }

    public static Route routeFor(AnnounceError e) {
        return e == AnnounceError.INVALID || e == AnnounceError.INVALID_DETAILS ? Route.BACK_TO_FORM : Route.SHOW_ERROR;
    }

    /** For BACK_TO_FORM: how to mark the field on stage B (reuses the Step 5 texts). Null when there is no such error. */
    public AnnounceFormState.ProblemText formProblem() {
        if (error == AnnounceError.INVALID_DETAILS && problem != null) return AnnounceFormState.describe(submission.type, problem);
        if (error == AnnounceError.INVALID) {
            return new AnnounceFormState.ProblemText("announce_err_invalid",
                    new ArrayList<>(Collections.singletonList(errorField == null ? "" : errorField)));
        }
        return null;
    }

    /* ---------- retry wait ---------- */

    /** One second passed (the sheet's timer). Returns the seconds still to wait; only counts down on a rate-limit error. */
    public int tick() {
        if (phase == Phase.ERROR && secondsLeft > 0) secondsLeft--;
        return secondsLeft;
    }

    public int secondsLeft() { return phase == Phase.ERROR ? secondsLeft : 0; }

    /** Retry is offered for this error (SIGNED_OUT / NOT_OWNER / AI_DISABLED are not: the owner writes it by hand). */
    public boolean errorCanRetry() { return phase == Phase.ERROR && error != null && error.canRetry(); }

    /** The Try again button after an error is enabled: retry makes sense and the wait (RATE_LIMITED) is over. */
    public boolean canRetryNow() { return errorCanRetry() && secondsLeft == 0; }

    /* ---------- manual edits ---------- */

    /** The owner typed in the draft fields: remember whether they differ from the generated draft. */
    public void setEdited(String title, String message) {
        if (draft == null) { edited = false; return; }
        edited = !draft.title.equals(title == null ? "" : title) || !draft.message.equals(message == null ? "" : message);
    }

    public boolean edited() { return edited; }

    /**
     * True when a regenerate (Try again, Shorter, More friendly, More formal, a language change, Try again after an error, or Write for me
     * pressed again on the form) would throw away the owner's manual edits of a draft: the sheet must ask first. Edits are only lost after such
     * an action, so this stays true after an error or a visit to the form, and is false while a request runs.
     */
    public boolean needsConfirmBeforeRegenerate() { return draft != null && edited && phase != Phase.LOADING; }

    /* ---------- Use this ---------- */

    /** The text handed to the announcement form. */
    public static final class Use {
        public final String title;
        public final String message;
        Use(String title, String message) { this.title = title; this.message = message; }
    }

    /**
     * What "Use this" hands to the host: the title on one line (spaces collapsed), the message with its paragraphs, both trimmed and clipped to
     * 120 / 1000 characters without cutting an emoji in half. Null when the title or the message is empty (the form cannot be posted empty).
     */
    public static Use prepareUse(String title, String message) {
        String t = clip(AnnounceRules.normalize(title), AnnounceDraft.MAX_TITLE);
        String m = clip(message == null ? "" : message.replace("\r\n", "\n").replace('\r', '\n').trim(), AnnounceDraft.MAX_MESSAGE);
        if (t.isEmpty() || m.isEmpty()) return null;
        return new Use(t, m);
    }

    /** The host asks before replacing when the announcement form already has any text in the title or the message. */
    public static boolean needsReplaceConfirm(String currentTitle, String currentMessage) {
        return !isBlank(currentTitle) || !isBlank(currentMessage);
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static String clip(String s, int max) {
        if (s.length() <= max) return s;
        int end = max;
        if (Character.isHighSurrogate(s.charAt(end - 1))) end--;      // do not cut an emoji in half
        return s.substring(0, end).trim();
    }
}
