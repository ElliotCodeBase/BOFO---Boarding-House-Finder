package com.bofo.finder.util;

import com.bofo.finder.model.Role;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v2.0.91 AI Chats monitor (admin only): the pure logic - query building, search text, date filter, grouping, time text, who may see the screen.
 * No Android classes, so it runs in plain JVM unit tests (AiChatRulesTest). Self-contained on purpose: delete this file with the rest of the feature (REMOVE_AI_CHATS.md).
 */
public final class AiChatRules {
    private AiChatRules() {}

    /** Name of the row in the Supabase table app_flags that switches the feature on and off. */
    public static final String FLAG = "ai_chat_monitor_enabled";
    /** Rows shown per page ("Show more" loads the next page). */
    public static final int PAGE = 30;
    /** Server-side retention (pg_cron); only used for the screen text. */
    public static final int RETENTION_DAYS = 90;
    private static final int SEARCH_MAX = 60;
    private static final String COLUMNS = "id,user_id,user_name,user_email,question,answer,created_at";

    public enum Range { TODAY, WEEK, ALL }

    /** One logged question + answer. */
    public static final class Row {
        public final String id, userId, name, email, question, answer, createdAt;
        /** created_at as epoch millis, -1 when it cannot be read (such a row sorts last). */
        public final long ms;
        public Row(String id, String userId, String name, String email, String question, String answer, String createdAt) {
            this.id = nz(id); this.userId = nz(userId); this.name = nz(name).trim(); this.email = nz(email).trim();
            this.question = nz(question); this.answer = nz(answer); this.createdAt = nz(createdAt);
            this.ms = parseMs(createdAt);
        }
    }

    /** All loaded rows of one user ("one conversation per user"): messages oldest first, so it reads like a chat. */
    public static final class Group {
        public final String key;
        /** Name and e-mail of the newest message that has one (a user may have changed them); the name falls back to the part of the e-mail before the @. */
        public String name = "", email = "";
        public final List<Row> messages = new ArrayList<>();
        /** Time of the newest message (epoch millis, -1 unknown). */
        public long latestMs = -1;
        public String latestIso = "";
        Group(String key) { this.key = key; }
        public Row latest() { return messages.get(messages.size() - 1); }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /* ---------- who may see it ---------- */

    /** The screen and its entry exist only for an admin while the switch is on. RLS on ai_chat_log is the real protection; this only keeps the UI honest. */
    public static boolean canSee(Role role, boolean flagOn) { return role == Role.ADMIN && flagOn; }

    /* ---------- time text ---------- */

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US);

    /** PostgREST gives "2026-11-01T01:14:00.123456+00:00"; a space instead of the T and a trailing Z are accepted too. */
    static String normalize(String iso) {
        String s = iso.trim();
        if (s.indexOf('T') < 0 && s.indexOf(' ') > 0) s = s.replace(' ', 'T');
        return s;
    }

    /** Epoch millis of a Postgres timestamptz, -1 when it cannot be read. */
    public static long parseMs(String iso) {
        if (iso == null || iso.trim().isEmpty()) return -1;
        try { return OffsetDateTime.parse(normalize(iso)).toInstant().toEpochMilli(); }
        catch (RuntimeException e) { return -1; }
    }

    /** "Nov 1, 2026 9:14 AM" in the given zone, "" when the text cannot be read. */
    public static String dateTime(String iso, ZoneId zone) {
        long ms = parseMs(iso);
        return ms < 0 ? "" : WHEN.format(Instant.ofEpochMilli(ms).atZone(zone));
    }

    /** "just now", "5 min ago", "1 hour ago", "3 days ago", "2 months ago"; "" when the time cannot be read. A time slightly in the future (clock difference) counts as just now. */
    public static String ago(String iso, long nowMs) {
        long ms = parseMs(iso);
        if (ms < 0) return "";
        long sec = Math.max(0, (nowMs - ms) / 1000);
        if (sec < 60) return "just now";
        long min = sec / 60;
        if (min < 60) return min + " min ago";
        long hr = min / 60;
        if (hr < 24) return hr + (hr == 1 ? " hour ago" : " hours ago");
        long day = hr / 24;
        if (day < 60) return day + (day == 1 ? " day ago" : " days ago");
        long mon = day / 30;
        return mon + " months ago";
    }

    /** "Nov 1, 2026 9:14 AM  -  5 min ago" (just the date part when the age cannot be told). */
    public static String whenLine(String iso, long nowMs, ZoneId zone) {
        String d = dateTime(iso, zone), a = ago(iso, nowMs);
        return d.isEmpty() ? a : a.isEmpty() ? d : d + "  -  " + a;
    }

    /* ---------- date filter and search ---------- */

    /** Start of the chosen range as an ISO instant for "created_at=gte.", "" for All. Today = since midnight on the phone, 7 days = the last 7 x 24 hours. */
    public static String sinceIso(Range r, long nowMs, ZoneId zone) {
        if (r == Range.TODAY) return Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toString();
        if (r == Range.WEEK) return Instant.ofEpochMilli(nowMs - 7L * 24 * 3600 * 1000).toString();
        return "";
    }

    /** What the admin typed, made safe for a PostgREST filter: * % \ " dropped, control characters and , ( ) turned into spaces, spaces collapsed, 60 characters at most. */
    public static String searchTerm(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '*' || c == '%' || c == '\\' || c == '"') continue;                       // dropped
            sb.append(Character.isISOControl(c) || Character.isWhitespace(c) || c == ',' || c == '(' || c == ')' ? ' ' : c);   // a separator becomes a space ("Ada, Cruz" -> "Ada Cruz")
        }
        String s = sb.toString().replaceAll(" {2,}", " ").trim();
        return s.length() > SEARCH_MAX ? s.substring(0, SEARCH_MAX).trim() : s;
    }

    /** "or=(user_name.ilike.*x*,user_email.ilike.*x*,question.ilike.*x*,answer.ilike.*x*)" with the term URL-encoded, or "" when there is nothing to search. */
    public static String orFilter(String raw) {
        String t = searchTerm(raw);
        if (t.isEmpty()) return "";
        String v = "*" + URLEncoder.encode(t, StandardCharsets.UTF_8).replace("+", "%20") + "*";
        return "or=(user_name.ilike." + v + ",user_email.ilike." + v + ",question.ilike." + v + ",answer.ilike." + v + ")";
    }

    /** The whole PostgREST query for one page: newest first, PAGE + 1 rows (the extra one only tells that "Show more" is needed), offset, date filter and search. */
    public static String query(Range r, String raw, int offset, long nowMs, ZoneId zone) {
        StringBuilder q = new StringBuilder("select=" + COLUMNS + "&order=created_at.desc,id.desc&limit=" + (PAGE + 1) + "&offset=" + Math.max(0, offset));
        String since = sinceIso(r, nowMs, zone);
        if (!since.isEmpty()) q.append("&created_at=gte.").append(since);
        String or = orFilter(raw);
        if (!or.isEmpty()) q.append('&').append(or);
        return q.toString();
    }

    /** The server sent PAGE + 1 rows: there is more to load. */
    public static boolean hasMore(int returned) { return returned > PAGE; }

    /** Rows to show from one server answer (the extra row is dropped). */
    public static <T> List<T> firstPage(List<T> answer) { return answer.size() > PAGE ? new ArrayList<>(answer.subList(0, PAGE)) : new ArrayList<>(answer); }

    /* ---------- merge and group ---------- */

    /** Adds the next page to the list without duplicates (new chats arriving while paging shift the offsets), newest first. */
    public static List<Row> merge(List<Row> have, List<Row> more) {
        Map<String, Row> byId = new LinkedHashMap<>();
        for (Row r : have) byId.put(r.id, r);
        for (Row r : more) if (!byId.containsKey(r.id)) byId.put(r.id, r);
        List<Row> out = new ArrayList<>(byId.values());
        out.sort((a, b) -> Long.compare(b.ms, a.ms));              // stable: equal times keep the server order
        return out;
    }

    /** "Ada Cruz" or the part of the e-mail before the @, "Unknown user" when both are empty. */
    public static String who(String name, String email) {
        if (name != null && !name.trim().isEmpty()) return name.trim();
        if (email != null && !email.trim().isEmpty()) { String e = email.trim(); int at = e.indexOf('@'); return at > 0 ? e.substring(0, at) : e; }
        return "Unknown user";
    }

    /** One group per user (by user id, else by e-mail), the user with the newest chat first, each user's messages oldest first. */
    public static List<Group> group(List<Row> rows) {
        Map<String, Group> by = new LinkedHashMap<>();
        for (Row r : rows) {
            String key = !r.userId.isEmpty() ? r.userId : !r.email.isEmpty() ? r.email.toLowerCase(Locale.ROOT) : "?";
            Group g = by.get(key);
            if (g == null) { g = new Group(key); by.put(key, g); }
            g.messages.add(r);
        }
        List<Group> out = new ArrayList<>(by.values());
        for (Group g : out) {
            g.messages.sort((a, b) -> Long.compare(a.ms, b.ms));
            Row last = g.latest();
            g.latestMs = last.ms; g.latestIso = last.createdAt;
            String nm = "", em = "";
            for (int i = g.messages.size() - 1; i >= 0 && (nm.isEmpty() || em.isEmpty()); i--) {
                if (nm.isEmpty()) nm = g.messages.get(i).name;
                if (em.isEmpty()) em = g.messages.get(i).email;
            }
            g.email = em;
            g.name = who(nm, em);
        }
        out.sort((a, b) -> Long.compare(b.latestMs, a.latestMs));
        return out;
    }

    /** "1 question" / "3 questions". */
    public static String countText(int n) { return n + (n == 1 ? " question" : " questions"); }
}
