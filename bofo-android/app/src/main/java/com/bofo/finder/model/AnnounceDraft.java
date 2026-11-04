package com.bofo.finder.model;

import com.bofo.finder.util.AnnounceRules;

import org.json.JSONObject;

/**
 * v2.4.1 (AI Announcement Writer, step 4): the draft the Edge Function bofo-announce returns: {title, message, notes?, degraded}.
 * Immutable. Plain JVM (no Android classes) so it is unit tested. The draft is only text for the owner to review; nothing here posts it.
 */
public final class AnnounceDraft {
    /** The limits of post_announcement (migration 024): title 1-120, message 1-1000. The server already stays below them (100 / 900). */
    public static final int MAX_TITLE = 120, MAX_MESSAGE = 1000, MAX_NOTES = 300;

    public final String title;
    public final String message;
    /** A hint for the owner (for example "Add the amount"), or null. */
    public final String notes;
    /** true = the server built the text from a simple template (the AI was busy / its output was rejected): the UI shows announce_degraded_note. */
    public final boolean degraded;

    public AnnounceDraft(String title, String message, String notes, boolean degraded) {
        this.title = title; this.message = message; this.notes = notes; this.degraded = degraded;
    }

    /**
     * Reads the 200 body. Tolerant: no notes / null notes / wrong-typed notes -> null, degraded missing or not a boolean -> false,
     * over-long text is clipped to 120 / 1000 (never splits a surrogate pair). Strict: title and message must be non-blank strings,
     * otherwise {@link IllegalArgumentException} (the service reports it as a generic error). Never logs or echoes the text.
     */
    public static AnnounceDraft fromJson(JSONObject o) {
        if (o == null) throw new IllegalArgumentException("announce draft: empty body");
        String title = clip(AnnounceRules.normalize(str(o, "title")), MAX_TITLE);
        String message = clip(tidy(str(o, "message")), MAX_MESSAGE);
        if (title.isEmpty()) throw new IllegalArgumentException("announce draft: missing title");
        if (message.isEmpty()) throw new IllegalArgumentException("announce draft: missing message");
        String notes = clip(AnnounceRules.normalize(str(o, "notes")), MAX_NOTES);
        Object d = o.opt("degraded");
        return new AnnounceDraft(title, message, notes.isEmpty() ? null : notes, d instanceof Boolean && (Boolean) d);
    }

    /** The string under key, or "" when absent / null / not a string (org.json's optString would turn null into "null" and numbers into text). */
    private static String str(JSONObject o, String key) {
        Object v = o.opt(key);
        return v instanceof String ? (String) v : "";
    }

    /** Message: unix line breaks, no control characters except \n, no trailing spaces on a line, at most one blank line in a row, trimmed. */
    static String tidy(String s) {
        String t = s.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
        t = t.replaceAll("[\\u0000-\\u0009\\u000B-\\u001F\\u007F\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]", "");
        t = t.replaceAll("[ \\u00A0]+\\n", "\n").replaceAll("\\n{3,}", "\n\n");
        return t.trim();
    }

    private static String clip(String s, int max) {
        if (s.length() <= max) return s;
        int end = max;
        if (Character.isHighSurrogate(s.charAt(end - 1))) end--;      // do not cut an emoji in half
        return s.substring(0, end).trim();
    }
}
