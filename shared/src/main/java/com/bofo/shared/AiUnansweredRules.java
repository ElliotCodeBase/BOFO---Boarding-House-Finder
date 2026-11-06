package com.bofo.shared;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * "Needs attention" (ai_answer), moved to shared in 2.9.10: the pure logic for the list of questions BOFO could not answer well (table ai_unanswered, migration 020).
 * No Android classes, so it runs in plain JVM unit tests (AiUnansweredRulesTest).
 */
public final class AiUnansweredRules {
    private AiUnansweredRules() {}

    /** Newest rows loaded at once. */
    public static final int LIMIT = 50;
    public static final String COLUMNS = "id,created_at,reason,intent,lang,message";

    /** The reasons the Edge Function writes (check constraint of ai_unanswered.reason); ALL = no filter. */
    public enum Reason {
        ALL(null), DEGRADED("degraded"), CLARIFY("clarify"), NOT_FOUND("not_found"), OFF_TOPIC("off_topic"), THUMBS_DOWN("thumbs_down");
        public final String key;
        Reason(String key) { this.key = key; }

        /** The reason for a column value; null or an unknown text gives ALL (so a new reason on the server never crashes the screen). */
        public static Reason of(String key) {
            if (key != null) for (Reason r : values()) if (key.equals(r.key)) return r;
            return ALL;
        }
    }

    /** One row. {@code message} is empty when the server did not store the text (AI chat monitor off, or a thumbs down). */
    public record Row(String id, String createdAt, String reason, String intent, String lang, String message) {
        public Row {
            id = nz(id); createdAt = nz(createdAt); reason = nz(reason); intent = nz(intent); lang = nz(lang); message = nz(message).trim();
        }
        public Reason kind() { return Reason.of(reason); }
        public boolean hasText() { return !message.isEmpty(); }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /** Shown only to roles with ai_answer (RLS policy ai_unanswered_admin_read in 040 is the real gate: others get 0 rows). */
    public static boolean canSee(String role, java.util.Collection<String> grants, java.util.Collection<String> denies) {
        return PermissionRules.has(role, grants, denies, PermissionRules.AI_ANSWER);
    }

    /** PostgREST query for the newest rows, optionally of one reason. */
    public static String query(Reason r) {
        String q = "select=" + COLUMNS + "&order=created_at.desc&limit=" + LIMIT;
        if (r != null && r.key != null) q += "&reason=eq." + r.key;
        return q;
    }

    /** How many of the loaded rows each reason has (every reason is present, 0 when none). */
    public static Map<Reason, Integer> counts(List<Row> rows) {
        Map<Reason, Integer> m = new EnumMap<>(Reason.class);
        for (Reason r : Reason.values()) if (r != Reason.ALL) m.put(r, 0);
        if (rows != null) for (Row row : rows) { Reason k = row.kind(); if (k != Reason.ALL) m.merge(k, 1, Integer::sum); }
        return m;
    }

    /** Same list without rows of an unknown reason, newest first as the server sent them. */
    public static List<Row> known(List<Row> rows) {
        List<Row> out = new ArrayList<>();
        if (rows != null) for (Row r : rows) if (r.kind() != Reason.ALL) out.add(r);
        return out;
    }
}
