package com.bofo.shared;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * AI chats + AI settings (Stage 4 part 6, Owner only). Pure logic: the PostgREST query (with the search text cleaned so it cannot change the filter),
 * paging, the "type DELETE" check and the switch defaults. The server (ai_log_view policy on ai_chat_log, ai_settings in set_ai_enabled /
 * ai_chat_log_wipe, migration 040) is the real gate; this only decides what to show. No Android classes: unit-tested.
 */
public final class AiChatLogRules {
    private AiChatLogRules() {}

    public static final String FLAG_AI = "ai_enabled", FLAG_MONITOR = "ai_chat_monitor_enabled";
    public static final int PAGE = 30, RETENTION_DAYS = 90, SEARCH_MAX = 60, CLIP = 600;
    public enum Range { TODAY, WEEK, ALL }

    public static final class Row {
        public final String id, name, question, answer, createdAt;
        public Row(String id, String name, String question, String answer, String createdAt) {
            this.id = nz(id); this.name = nz(name).trim(); this.question = nz(question); this.answer = nz(answer); this.createdAt = nz(createdAt);
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /** Letters, digits, space, @ . - only; everything else (commas, brackets, * % _ quotes, backslash) is dropped so it cannot widen or break the filter. */
    public static String cleanSearch(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[^\\p{L}\\p{N} @.\\-]", " ").replaceAll("\\s+", " ").trim();
        return t.length() > SEARCH_MAX ? t.substring(0, SEARCH_MAX).trim() : t;
    }

    /** Query for ai_chat_log: newest first, PAGE + 1 rows from offset (the extra row only tells "there is more"). */
    public static String query(Range range, String search, int offset, long nowMs) {
        StringBuilder q = new StringBuilder("select=id,user_name,question,answer,created_at&order=created_at.desc&limit=" + (PAGE + 1) + "&offset=" + Math.max(0, offset));
        if (range == Range.TODAY) q.append("&created_at=gte.").append(Instant.ofEpochMilli(nowMs - 24L * 3600_000L));
        else if (range == Range.WEEK) q.append("&created_at=gte.").append(Instant.ofEpochMilli(nowMs - 7L * 24 * 3600_000L));
        String t = cleanSearch(search);
        if (!t.isEmpty()) {
            String e = URLEncoder.encode(t, StandardCharsets.UTF_8).replace("+", "%20");
            q.append("&or=(question.ilike.*").append(e).append("*,answer.ilike.*").append(e).append("*,user_name.ilike.*").append(e).append("*)");
        }
        return q.toString();
    }

    public static boolean hasMore(int returned) { return returned > PAGE; }

    public static String clip(String s, int n) { String t = nz(s); return t.length() > n ? t.substring(0, n - 1) + "…" : t; }

    /** "2026-11-06T14:05:33.1+00:00" -> "2026-11-06 14:05" (UTC as stored). */
    public static String when(String iso) { String t = nz(iso); return t.replace('T', ' ').substring(0, Math.min(16, t.length())); }

    /** A missing ai_enabled row counts as ON (migration 030 inserts true); a missing monitor row counts as OFF (014). */
    public static boolean aiOn(Boolean v) { return v == null || v; }
    public static boolean monitorOn(Boolean v) { return v != null && v; }

    /** The wipe needs the word DELETE typed (any case, spaces around ignored). */
    public static boolean wipeConfirmed(String typed) { return typed != null && "DELETE".equals(typed.trim().toUpperCase(java.util.Locale.ROOT)); }
}
