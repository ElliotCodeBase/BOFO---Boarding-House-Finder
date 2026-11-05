package com.bofo.finder.util;

/** v2.4.11: builds the reason text for rejecting an ID request from a quick chip plus an optional note (pure, unit tested). */
public final class RejectReasonRules {
    public static final int MAX = 300;
    private RejectReasonRules() {}

    /**
     * @param label the chip text, null when no chip is chosen
     * @param other true for the "Other" chip: then the note is required
     * @return "Label" or "Label: note" (max 300 characters), or null when the choice is not valid yet
     */
    public static String compose(String label, boolean other, String note) {
        String l = label == null ? "" : label.trim();
        String n = note == null ? "" : note.trim().replaceAll("\\s+", " ");
        if (l.isEmpty()) return null;
        if (other && n.isEmpty()) return null;
        String r = n.isEmpty() ? l : (other ? n : l + ": " + n);
        return r.length() > MAX ? r.substring(0, MAX) : r;
    }
}
