package com.bofo.shared;

import java.util.ArrayList;
import java.util.List;

/** Saved replies for rejecting an ID request (Stage 4 part 8). Built-in friendly wording plus up to MAX_OWN of the reviewer's own, kept on the phone only. Pure Java, unit-tested. */
public final class SavedReplyRules {
    private SavedReplyRules() {}
    public static final int MAX_OWN = 10, MAX_LEN = 300, MIN_LEN = 5;

    /** Chip label -> full text (same index). The owner reads the text, so it says what to do next. */
    public static final String[] LABELS = {"Blurry photo", "Name differs", "ID expired", "Selfie mismatch", "Number unreadable", "Back missing", "Wrong ID type", "Cropped"};
    public static final String[] TEXTS = {
            "The ID photo is blurry. Please retake it in good light so every word is readable.",
            "The name on the ID does not match your BOFO profile name. Please fix one of them and send again.",
            "This ID has expired. Please send a valid, unexpired ID.",
            "The selfie does not clearly match the photo on the ID. Please send a clear selfie holding the same ID.",
            "We could not read the ID number. Please retake the photo so the number is sharp.",
            "The back of the ID is missing or not readable. Please send both sides.",
            "This type of ID is not accepted. Please send a government-issued ID.",
            "Part of the ID is cut off. Please show all four corners of the card in the photo."};

    /** Trim, collapse white space, cut to MAX_LEN. Never null. */
    public static String clean(String t) {
        if (t == null) return "";
        String s = t.replaceAll("\\s+", " ").trim();
        return s.length() > MAX_LEN ? s.substring(0, MAX_LEN).trim() : s;
    }

    /** A reason can be sent only when it has at least MIN_LEN characters after cleaning. */
    public static boolean validReason(String t) { return clean(t).length() >= MIN_LEN; }

    /** The list after saving one reply: unchanged if the text is too short, already there (ignoring case) or the list is full; newest first otherwise. */
    public static List<String> add(List<String> own, String text) {
        List<String> out = new ArrayList<>(own == null ? new ArrayList<String>() : own);
        String c = clean(text);
        if (c.length() < MIN_LEN || out.size() >= MAX_OWN) return out;
        for (String s : out) if (s.equalsIgnoreCase(c)) return out;
        for (String s : TEXTS) if (s.equalsIgnoreCase(c)) return out;
        out.add(0, c);
        return out;
    }

    public static List<String> remove(List<String> own, int index) {
        List<String> out = new ArrayList<>(own == null ? new ArrayList<String>() : own);
        if (index >= 0 && index < out.size()) out.remove(index);
        return out;
    }

    /** One reply per line (clean() removes line breaks from every reply, so the format is safe). */
    public static String encode(List<String> own) {
        StringBuilder b = new StringBuilder();
        if (own != null) for (String s : own) { if (b.length() > 0) b.append('\n'); b.append(clean(s)); }
        return b.toString();
    }

    /** Tolerates null / odd storage (returns empty); re-cleans every entry and respects the caps. */
    public static List<String> decode(String stored) {
        List<String> out = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return out;
        for (String line : stored.split("\n")) {
            String c = clean(line);
            if (out.size() >= MAX_OWN) break;
            if (c.length() >= MIN_LEN && !out.contains(c)) out.add(c);
        }
        return out;
    }
}
