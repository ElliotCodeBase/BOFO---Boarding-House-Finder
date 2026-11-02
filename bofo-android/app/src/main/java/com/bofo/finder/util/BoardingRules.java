package com.bofo.finder.util;

import com.bofo.finder.model.GroupMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure Boarding (group chat) rules, no Android classes so they are unit tested: rows, merging, labels, error classes. */
public final class BoardingRules {
    private BoardingRules() {}

    public enum State { SENT, PENDING, FAILED }

    /** One line of the group chat (a server message, or one waiting in / refused by the Outbox). */
    public static final class Row {
        public final String id, senderId, name, role, body, title, createdAt;
        public final boolean mine, announcement, pinned, iAcked;
        public final int ackCount;            // owner only: how many members pressed "Got it" (0 for everybody else)
        public final State state;
        public Row(String id, String senderId, String name, String role, String body, String title, String createdAt,
                   boolean mine, boolean announcement, State state) {
            this(id, senderId, name, role, body, title, createdAt, mine, announcement, false, false, 0, state);
        }
        public Row(String id, String senderId, String name, String role, String body, String title, String createdAt,
                   boolean mine, boolean announcement, boolean pinned, boolean iAcked, int ackCount, State state) {
            this.id = id; this.senderId = senderId; this.name = name == null ? "" : name; this.role = role; this.body = body;
            this.title = title; this.createdAt = createdAt; this.mine = mine; this.announcement = announcement;
            this.pinned = pinned; this.iAcked = iAcked; this.ackCount = ackCount; this.state = state;
        }
        public Row withState(State s) { return new Row(id, senderId, name, role, body, title, createdAt, mine, announcement, pinned, iAcked, ackCount, s); }
        public Row withAcked(boolean acked) { return new Row(id, senderId, name, role, body, title, createdAt, mine, announcement, pinned, acked, ackCount, state); }
        public boolean fromOwner() { return GroupMessage.OWNER.equals(role); }
    }

    public static Row fromMessage(GroupMessage m, String me) {
        boolean mine = me != null && me.equals(m.senderId());
        return new Row(m.id(), m.senderId(), m.senderName(), m.senderRole(), m.body(), m.title(), m.createdAt(), mine, m.announcement(),
                m.pinned(), m.iAcked(), m.ackCount(), State.SENT);
    }

    /** A message typed by me that is still in the Outbox (or was refused). createdAt = the typing time in ms, as text. */
    public static Row queued(String id, String me, String myName, String myRole, String body, long atMs, State state) {
        return new Row(id, me, myName, myRole, body, null, String.valueOf(atMs), true, false, state);
    }

    /** Server rows (any order, may repeat) oldest first by created_at, then the queued / failed ones in typing order. */
    public static List<Row> merge(List<Row> server, List<Row> queued) {
        Map<String, Row> byId = new HashMap<>();
        for (Row r : server) byId.put(r.id, r);
        List<Row> out = new ArrayList<>(byId.values());
        Collections.sort(out, (a, b) -> a.createdAt.compareTo(b.createdAt));
        for (Row q : queued) if (!byId.containsKey(q.id)) out.add(q);
        return out;
    }

    public static String newestServerTime(List<Row> rows) {
        String best = null;
        for (Row r : rows) if (r.state == State.SENT && (best == null || r.createdAt.compareTo(best) > 0)) best = r.createdAt;
        return best;
    }

    public static String oldestServerTime(List<Row> rows) {
        String best = null;
        for (Row r : rows) if (r.state == State.SENT && (best == null || r.createdAt.compareTo(best) < 0)) best = r.createdAt;
        return best;
    }

    /** Name shown above a bubble: "You" for my own messages, else the sender's name, else the role word. */
    public static String displayName(Row r, String you) {
        if (r.mine) return you;
        if (!r.name.trim().isEmpty()) return r.name.trim();
        return r.fromOwner() ? "Owner" : "Student";
    }

    /** First letter for the header avatar. */
    public static String initial(String name) {
        String s = name == null ? "" : name.trim();
        return s.isEmpty() ? "?" : s.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    // ---- error classes (the server's own words -> a friendly text chosen by the screen) ----------------------------

    public enum JoinProblem { BAD_CODE, REMOVED, OTHER_HOUSE, STUDENTS_ONLY, SUSPENDED, OTHER }

    /** Classifies the error text of rpc join_bh_group (the exception text contains the server's message). */
    public static JoinProblem classifyJoin(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (t.contains("invalid code")) return JoinProblem.BAD_CODE;
        if (t.contains("removed from this group")) return JoinProblem.REMOVED;
        if (t.contains("leave your current group")) return JoinProblem.OTHER_HOUSE;
        if (t.contains("students only")) return JoinProblem.STUDENTS_ONLY;
        if (t.contains("cannot join groups") || t.contains("suspended") || t.contains("banned")) return JoinProblem.SUSPENDED;
        return JoinProblem.OTHER;
    }

    /** True when a refused send means "you may not post here" (muted, removed, suspended: the database's row-level security says no). */
    public static boolean sendBlocked(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return t.contains("row-level security") || t.contains("42501");
    }

    // ---- step 3: announcements ("Got it"), pinned banner, report a message ---------------------------------------

    /** Prefix of the quote the admin sees in reports.details (the admin screens are in English). */
    public static final String REPORT_QUOTE = "Reported message: ";
    private static final int QUOTE_MAX = 160;

    /** "Got it" button: a server announcement from someone else that I have not acknowledged yet. */
    public static boolean canAck(Row r) {
        return r != null && r.announcement && r.state == State.SENT && !r.mine && !r.iAcked;
    }

    /** Report: only a message from somebody else that the server has (not a waiting / failed one of mine). */
    public static boolean canReport(Row r, String me) {
        return r != null && r.state == State.SENT && !r.mine && r.senderId != null && !r.senderId.isEmpty() && me != null && !me.isEmpty() && !me.equals(r.senderId);
    }

    /** One line, spaces collapsed, cut with "..." after {@code max} characters. */
    public static String oneLine(String s, int max) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, Math.max(0, max - 1)).trim() + "\u2026";
    }

    /** The quote put in front of the reporter's own words: 'Reported message: "title - text"' (announcement) or '"text"'. */
    public static String reportQuote(Row r) {
        if (r == null) return "";
        String body = r.announcement && r.title != null && !r.title.trim().isEmpty() ? r.title.trim() + " - " + r.body : r.body;
        String q = oneLine(body, QUOTE_MAX);
        return q.isEmpty() ? "" : REPORT_QUOTE + "\"" + q + "\"";
    }

    /** Pinned announcements for the banner: unacknowledged ones first (those still need a "Got it"), then the newest pin; order kept inside each group. */
    public static List<Row> bannerOrder(List<Row> pinned) {
        List<Row> todo = new ArrayList<>(), done = new ArrayList<>();
        for (Row r : pinned) (canAck(r) ? todo : done).add(r);
        todo.addAll(done);
        return todo;
    }

    /** Copy of {@code rows} where message {@code id} is marked acknowledged (other rows are the same objects). */
    public static List<Row> markAcked(List<Row> rows, String id) {
        List<Row> out = new ArrayList<>(rows.size());
        for (Row r : rows) out.add(r.id.equals(id) ? r.withAcked(true) : r);
        return out;
    }

    /** How many pinned announcements still wait for my "Got it". */
    public static int unackedCount(List<Row> pinned) {
        int n = 0; for (Row r : pinned) if (canAck(r)) n++; return n;
    }
}
