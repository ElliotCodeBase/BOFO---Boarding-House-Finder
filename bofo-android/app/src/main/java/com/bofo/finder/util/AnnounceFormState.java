package com.bofo.finder.util;

import com.bofo.finder.model.AnnounceType;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * v2.4.2 (AI Announcement Writer, step 5): the logic of the "Write for me" sheet that does not need Android, so it is unit tested:
 * which stage is shown (A = type list, B = details form), what Back does, the defaults of a type, the search filter, how a field problem is
 * shown, and the validated {@link Submission} handed to the next step. The sheet (ui/AnnounceAiSheet) only draws this.
 */
public final class AnnounceFormState {
    public enum Stage { LIST, DETAILS }

    private Stage stage = Stage.LIST;
    private AnnounceType type;

    public Stage stage() { return stage; }
    /** The chosen type while on the details stage, else null. */
    public AnnounceType type() { return type; }

    /** Stage A -> B. A null type is ignored. */
    public void pick(AnnounceType t) { if (t != null) { type = t; stage = Stage.DETAILS; } }

    /** Back: from B to A (returns true = handled); on A returns false = the sheet may close. */
    public boolean back() {
        if (stage == Stage.DETAILS) { stage = Stage.LIST; type = null; return true; }
        return false;
    }

    /** For onSaveInstanceState: the id of the chosen type, or null on stage A. */
    public String typeIdToSave() { return type == null ? null : type.id; }

    /** After rotation / process death: an unknown or null id = stage A. */
    public static AnnounceFormState restore(String typeId) {
        AnnounceFormState s = new AnnounceFormState();
        s.pick(AnnounceType.byId(typeId));
        return s;
    }

    /** The options shown on stage B. */
    public static final class Options {
        public final AnnounceType.Lang lang; public final AnnounceType.Tone tone; public final AnnounceType.Length length; public final boolean emoji;
        Options(AnnounceType.Lang lang, AnnounceType.Tone tone, AnnounceType.Length length, boolean emoji) {
            this.lang = lang; this.tone = tone; this.length = length; this.emoji = emoji;
        }
    }

    /**
     * Starting options for a type from the remembered wire strings (AppPrefs.announceLang / Tone / Length / Emoji): unknown language -> English,
     * unknown length -> normal, tone "" or unknown -> the type's own default tone. Null type -> the same but tone FRIENDLY.
     */
    public static Options defaults(AnnounceType t, String langWire, String toneWire, String lengthWire, boolean emoji) {
        AnnounceType.Tone fallback = t == null ? AnnounceType.Tone.FRIENDLY : t.defaultTone;
        return new Options(AnnounceType.Lang.fromWire(langWire, AnnounceType.Lang.EN),
                AnnounceType.Tone.fromWire(toneWire, fallback), AnnounceType.Length.fromWire(lengthWire, AnnounceType.Length.NORMAL), emoji);
    }

    /** What to store in AppPrefs for the tone: "" when the owner kept the type's default (so the next type uses its own default), else the wire string. */
    public static String toneToRemember(AnnounceType t, AnnounceType.Tone chosen) {
        return chosen == null || (t != null && chosen == t.defaultTone) ? "" : chosen.wire;
    }

    /** Stage A search: types whose label (resolved by the caller from the string resources) or id contains every word typed. Empty query = all, same order. */
    public static List<AnnounceType> filter(List<AnnounceType> all, String query, Function<AnnounceType, String> labelOf) {
        String q = AnnounceRules.normalize(query).toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return all;
        String[] words = q.split(" ");
        List<AnnounceType> out = new ArrayList<>();
        for (AnnounceType t : all) {
            String label = labelOf == null ? null : labelOf.apply(t);
            String hay = ((label == null ? "" : label) + " " + t.id.replace('_', ' ')).toLowerCase(Locale.ROOT);
            boolean ok = true;
            for (String w : words) if (!hay.contains(w)) { ok = false; break; }
            if (ok) out.add(t);
        }
        return out;
    }

    /** String resource name of the urgency badge, or null for a normal type (no badge). */
    public static String badgeStringName(AnnounceType t) {
        switch (t.urgency) {
            case URGENT: return "announce_urgency_urgent";
            case IMPORTANT: return "announce_urgency_important";
            default: return null;
        }
    }

    /** The sign-off name to prefill from the owner's profile name: normalized, at most 60 characters; null -> "". */
    public static String closingDefault(String profileName) { return AnnounceRules.closingName(profileName); }

    /** How to show a {@link AnnounceRules.Problem}: the string resource name, and the field key(s) it is about (the first one gets the focus). */
    public static final class ProblemText {
        /** announce_err_missing | announce_err_one_of | announce_err_too_long: each takes ONE %1$s = the field label(s). */
        public final String stringName;
        /** Field keys to name in the message (several for one_of, joined by the UI with ", "). */
        public final List<String> fieldKeys;
        ProblemText(String stringName, List<String> fieldKeys) { this.stringName = stringName; this.fieldKeys = Collections.unmodifiableList(fieldKeys); }
        public String focusKey() { return fieldKeys.get(0); }
    }

    public static ProblemText describe(AnnounceType t, AnnounceRules.Problem p) {
        if (AnnounceRules.Problem.ONE_OF.equals(p.code) && t != null && !t.oneOf.isEmpty())
            return new ProblemText("announce_err_one_of", new ArrayList<>(t.oneOf));
        List<String> one = new ArrayList<>(); one.add(p.field);
        if (AnnounceRules.Problem.TOO_LONG.equals(p.code)) return new ProblemText("announce_err_too_long", one);
        return new ProblemText("announce_err_missing", one);        // missing_field (and, defensively, anything else)
    }

    /** The validated details the sheet hands to Step 6 (which calls AnnounceAiService.generate). Immutable; values are already cleaned. */
    public static final class Submission {
        public final AnnounceType type;
        public final AnnounceType.Lang lang;
        public final AnnounceType.Tone tone;
        public final AnnounceType.Length length;
        public final boolean emoji;
        /** false when the owner turned the sign-off off OR left the name empty (same rule as AnnounceRules.buildRequest). */
        public final boolean closingOn;
        public final String closingName;
        /** Only this type's keys, trimmed, empty values dropped, in field order. */
        public final Map<String, String> fields;

        Submission(AnnounceType type, AnnounceType.Lang lang, AnnounceType.Tone tone, AnnounceType.Length length, boolean emoji,
                   boolean closingOn, String closingName, Map<String, String> fields) {
            this.type = type; this.lang = lang; this.tone = tone; this.length = length; this.emoji = emoji;
            this.closingOn = closingOn; this.closingName = closingName; this.fields = Collections.unmodifiableMap(fields);
        }

        /** The request body for bofo-announce (adjust may be null). Convenience for Step 6; never logs. */
        public JSONObject toRequest(AnnounceType.Adjust adjust) throws JSONException {
            return AnnounceRules.buildRequest(type, lang, tone, length, emoji, closingOn, closingName, fields, adjust);
        }
    }

    /** Result of {@link #check}: either a submission or a problem (exactly one is non-null). */
    public static final class Check {
        public final Submission submission;
        public final ProblemText problem;
        Check(Submission s, ProblemText p) { this.submission = s; this.problem = p; }
        public boolean ok() { return submission != null; }
    }

    /** Validates what the owner typed (AnnounceRules.validateFields) and, when fine, builds the Submission. Never throws, never logs. */
    public static Check check(AnnounceType type, AnnounceType.Lang lang, AnnounceType.Tone tone, AnnounceType.Length length, boolean emoji,
                              boolean closingOn, String closingName, Map<String, String> rawFields) {
        if (type == null) return new Check(null, new ProblemText("announce_err_invalid", new ArrayList<>(Collections.singletonList(""))));
        AnnounceRules.Problem p = AnnounceRules.validateFields(type, rawFields);
        if (p != null) return new Check(null, describe(type, p));
        String name = AnnounceRules.closingName(closingName);
        return new Check(new Submission(type, lang == null ? AnnounceType.Lang.EN : lang, tone == null ? type.defaultTone : tone,
                length == null ? AnnounceType.Length.NORMAL : length, emoji, closingOn && !name.isEmpty(), name,
                new LinkedHashMap<>(AnnounceRules.clean(type, rawFields))), null);
    }
}
