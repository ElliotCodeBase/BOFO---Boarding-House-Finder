package com.bofo.finder.util;

import com.bofo.finder.model.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v2.1.9 safety series step 3: plain rules for the ID number and the Banned users list (no Android, so they are unit-tested on a plain JVM).
 * The app never hashes an ID number: the server does (keyed hash, secret pepper). {@link #normalize} is for display / comparison hints only.
 */
public final class BanRules {
    private BanRules() {}

    public static final int ID_MIN = 4, ID_MAX = 30;
    /** The only text shown to a banned person (sign-in screen and ID check). Never says which account matched. */
    public static final String BANNED_TEXT = "You are banned.";
    /** Longest ban / unban reason, same as the database CHECK. */
    public static final int REASON_MAX = 300;

    /** True for a signed-in admin: only admins see the Banned users screen and the Ban button. */
    public static boolean canSee(Role role) { return role == Role.ADMIN; }

    /** Upper case, spaces and dashes removed ("ab-12 34" -> "AB1234"). Same idea as the server; the server's result is the one that counts. */
    public static String normalize(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    /** 4-30 characters, only letters, digits, dash and space, and at least 4 letters or digits once spaces and dashes are removed. */
    public static boolean validNumber(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() < ID_MIN || t.length() > ID_MAX) return false;
        if (!t.matches("[A-Za-z0-9 \\-]+")) return false;
        return normalize(t).length() >= ID_MIN;
    }

    /** The number as sent to the server: trimmed, nothing else changed. */
    public static String forServer(String s) { return s == null ? "" : s.trim(); }

    /** Reason text for ban / unban: control characters removed, max 300. */
    public static String reason(String s) { return Validate.clean(s, REASON_MAX); }

    /** One line of the Banned users list. */
    public record Row(String userId, String name, String email, String reason, String bannedAt, String bannedBy, boolean idBlocked) {
        public Row {
            userId = nz(userId); name = nz(name); email = nz(email); reason = nz(reason); bannedAt = nz(bannedAt); bannedBy = nz(bannedBy);
        }
        private static String nz(String s) { return s == null || "null".equals(s) ? "" : s; }
    }

    /** Client-side search: every word must appear in the name, e-mail or reason (case-insensitive). Empty query keeps everything. */
    public static List<Row> filter(List<Row> rows, String query) {
        List<Row> out = new ArrayList<>();
        if (rows == null) return out;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) { out.addAll(rows); return out; }
        String[] words = q.split("\\s+");
        for (Row r : rows) {
            String hay = (r.name() + " " + r.email() + " " + r.reason()).toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String w : words) if (!hay.contains(w)) { all = false; break; }
            if (all) out.add(r);
        }
        return out;
    }
}
