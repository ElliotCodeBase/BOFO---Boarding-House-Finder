package com.bofo.finder.util;

import com.bofo.finder.model.AnnounceType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * v2.4.0 (AI Announcement Writer, step 3): pure rules for the "Write for me" sheet, no Android classes so they are unit tested.
 * Mirrors validation in supabase/functions/bofo-announce/clean.ts (normalizeValue, required, 200 chars, oneOf) so the app can tell the owner
 * what is wrong BEFORE calling the server; the server stays the authority (400 {error, message, field}).
 */
public final class AnnounceRules {
    private AnnounceRules() {}

    /** The only top-level keys the backend accepts (it answers 400 unknown_param for anything else). */
    public static final Set<String> REQUEST_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "type", "lang", "tone", "length", "emoji", "closing", "fields", "adjust")));

    /** What is wrong with the owner's details: the code (missing_field | too_long | unknown_field | one_of) and the field key (first key for one_of). */
    public static final class Problem {
        public static final String MISSING = "missing_field", TOO_LONG = "too_long", UNKNOWN = "unknown_field", ONE_OF = "one_of";
        public final String code, field;
        Problem(String code, String field) { this.code = code; this.field = field; }
        @Override public String toString() { return code + ":" + field; }
    }

    // same set as CONTROL in clean.ts (controls except tab/LF/CR, zero-width and bidi marks); JS \s also covers these Unicode spaces
    private static final Pattern CONTROL = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]");
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+");

    /** Same as normalizeValue in clean.ts: no control / bidi characters, one line, single spaces, trimmed. null -> "". */
    public static String normalize(String s) {
        if (s == null) return "";
        return SPACES.matcher(CONTROL.matcher(s).replaceAll("")).replaceAll(" ").trim();
    }

    /** Default tone of a type (the owner may change it). */
    public static AnnounceType.Tone defaultTone(AnnounceType t) { return t.defaultTone; }

    /**
     * First problem with the details, or null when they are fine. Order like the backend: too long, unknown key, missing required, oneOf.
     * Values are normalized first; an empty value counts as not filled.
     */
    public static Problem validateFields(AnnounceType type, Map<String, String> fields) {
        Map<String, String> f = clean(type, fields);
        if (fields != null) for (String k : fields.keySet()) if (type.field(k) == null) return new Problem(Problem.UNKNOWN, k);
        for (AnnounceType.Field fd : type.fields) {
            String v = normalize(fields == null ? null : fields.get(fd.key));
            if (v.length() > AnnounceType.MAX_FIELD_LEN) return new Problem(Problem.TOO_LONG, fd.key);
        }
        for (AnnounceType.Field fd : type.fields) if (fd.required && !f.containsKey(fd.key)) return new Problem(Problem.MISSING, fd.key);
        if (!type.oneOf.isEmpty()) {
            boolean any = false;
            for (String k : type.oneOf) if (f.containsKey(k)) any = true;
            if (!any) return new Problem(Problem.ONE_OF, type.oneOf.get(0));
        }
        return null;
    }

    /** Only this type's keys, normalized, empty values dropped, in field order. */
    public static Map<String, String> clean(AnnounceType type, Map<String, String> fields) {
        Map<String, String> out = new LinkedHashMap<>();
        for (AnnounceType.Field fd : type.fields) {
            String v = normalize(fields == null ? null : fields.get(fd.key));
            if (!v.isEmpty()) out.put(fd.key, v);
        }
        return out;
    }

    /** The sign-off name: normalized, cut at 60 characters (the backend answers 400 above 60). */
    public static String closingName(String name) {
        String n = normalize(name);
        return n.length() > AnnounceType.MAX_CLOSING_NAME ? n.substring(0, AnnounceType.MAX_CLOSING_NAME).trim() : n;
    }

    /**
     * The JSON body for POST /functions/v1/bofo-announce: exactly the keys the backend accepts. Throws IllegalArgumentException when
     * {@link #validateFields} would fail (callers validate first). {@code adjust} may be null. closing.on is false when the name is empty.
     */
    public static JSONObject buildRequest(AnnounceType type, AnnounceType.Lang lang, AnnounceType.Tone tone, AnnounceType.Length length,
                                          boolean emoji, boolean closingOn, String closingName, Map<String, String> fields,
                                          AnnounceType.Adjust adjust) throws JSONException {
        Problem p = validateFields(type, fields);
        if (p != null) throw new IllegalArgumentException("invalid fields: " + p);
        JSONObject f = new JSONObject();
        for (Map.Entry<String, String> e : clean(type, fields).entrySet()) f.put(e.getKey(), e.getValue());
        String name = closingName(closingName);
        JSONObject closing = new JSONObject();
        closing.put("on", closingOn && !name.isEmpty());
        closing.put("name", name);
        JSONObject body = new JSONObject();
        body.put("type", type.id);
        body.put("lang", (lang == null ? AnnounceType.Lang.EN : lang).wire);
        body.put("tone", (tone == null ? type.defaultTone : tone).wire);
        body.put("length", (length == null ? AnnounceType.Length.NORMAL : length).wire);
        body.put("emoji", emoji);
        body.put("closing", closing);
        body.put("fields", f);
        if (adjust != null) body.put("adjust", adjust.wire);
        return body;
    }
}
