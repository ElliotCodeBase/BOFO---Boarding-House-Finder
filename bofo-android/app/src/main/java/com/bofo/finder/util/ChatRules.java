package com.bofo.finder.util;

import com.bofo.finder.model.Message;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure chat rules (no Android classes, so they are unit tested): merging messages, poll timing, ticks, time labels, previews. */
public final class ChatRules {
    private ChatRules() {}

    /** Poll delay: 3 s while the chat is active, +1.5 s after every quiet tick, never more than 12 s. A new message resets it. */
    public static final long POLL_MIN_MS = 3000, POLL_MAX_MS = 12000;

    public static long nextDelayMs(int quietTicks) {
        if (quietTicks <= 0) return POLL_MIN_MS;
        long d = POLL_MIN_MS + quietTicks * 1500L;
        return Math.min(d, POLL_MAX_MS);
    }

    public enum State { SENT, READ, PENDING, FAILED }

    /** One bubble. {@code id} is the server id, or the outbox id while the message is still queued. */
    public static final class Item {
        public final String id, body, createdAt;
        public final boolean mine;
        public final State state;
        public Item(String id, String body, String createdAt, boolean mine, State state) {
            this.id = id; this.body = body; this.createdAt = createdAt; this.mine = mine; this.state = state;
        }
        public Item withState(State s) { return new Item(id, body, createdAt, mine, s); }
    }

    public static Item fromMessage(Message m, String me) {
        boolean mine = me != null && me.equals(m.senderId());
        return new Item(m.id(), m.body(), m.createdAt(), mine, mine && m.readAt() != null ? State.READ : State.SENT);
    }

    /**
     * Server messages (any order, may repeat) + queued ones. Server messages are sorted oldest first by created_at (Supabase writes
     * every value in the same +00:00 form, so text order = time order); queued / failed ones stay at the end in the order they were typed.
     */
    public static List<Item> merge(List<Item> server, List<Item> queued) {
        Map<String, Item> byId = new HashMap<>();
        for (Item i : server) {
            Item old = byId.get(i.id);
            if (old == null || (old.state == State.SENT && i.state == State.READ)) byId.put(i.id, i);   // a read tick never goes back
        }
        List<Item> out = new ArrayList<>(byId.values());
        Collections.sort(out, (a, b) -> a.createdAt.compareTo(b.createdAt));
        for (Item q : queued) if (!byId.containsKey(q.id)) out.add(q);
        return out;
    }

    /** The created_at to ask "newer than" for: the newest SERVER message, or null when there is none yet. */
    public static String newestServerTime(List<Item> items) {
        String best = null;
        for (Item i : items) {
            if (i.state == State.PENDING || i.state == State.FAILED) continue;
            if (best == null || i.createdAt.compareTo(best) > 0) best = i.createdAt;
        }
        return best;
    }

    /** The oldest server created_at (cursor to page backwards), or null. */
    public static String oldestServerTime(List<Item> items) {
        String best = null;
        for (Item i : items) {
            if (i.state == State.PENDING || i.state == State.FAILED) continue;
            if (best == null || i.createdAt.compareTo(best) < 0) best = i.createdAt;
        }
        return best;
    }

    /** True when the other person wrote something in {@code fresh} that I have not marked read yet. */
    public static boolean hasIncoming(List<Item> fresh) {
        for (Item i : fresh) if (!i.mine) return true;
        return false;
    }

    /** Text for the tick under my bubble: one tick sent, two ticks read, clock for queued, "!" for failed. Others get "". */
    public static String tick(Item i) {
        if (!i.mine) return "";
        switch (i.state) {
            case READ: return "✓✓";
            case SENT: return "✓";
            case PENDING: return "⏱";
            default: return "!";
        }
    }

    /** "14:05" today, "Yesterday", else "Oct 28" (UTC offset of {@code zone}). Returns "" for text it cannot read. */
    public static String timeLabel(String iso, long nowMs, ZoneId zone) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            OffsetDateTime t = OffsetDateTime.parse(iso);
            java.time.ZonedDateTime z = t.toInstant().atZone(zone);
            LocalDate today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate(), day = z.toLocalDate();
            if (day.equals(today)) return String.format("%02d:%02d", z.getHour(), z.getMinute());
            if (day.equals(today.minusDays(1))) return "Yesterday";
            String[] mon = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            return mon[z.getMonthValue() - 1] + " " + z.getDayOfMonth();
        } catch (RuntimeException bad) {
            return "";
        }
    }

    /** One line for the inbox: whitespace collapsed, cut at {@code max} characters with "...". */
    public static String preview(String body, int max) {
        if (body == null) return "";
        String s = body.replaceAll("\\s+", " ").trim();
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 3)).trim() + "...";
    }

    /** "5", "99+" or "" for the unread badge. */
    public static String badge(int unread) {
        return unread <= 0 ? "" : unread > 99 ? "99+" : String.valueOf(unread);
    }

    /** Chat title: the other person's name, else the place name, else "Chat". */
    public static String title(String otherName, String bhName) {
        if (otherName != null && !otherName.trim().isEmpty()) return otherName.trim();
        if (bhName != null && !bhName.trim().isEmpty()) return bhName.trim();
        return "Chat";
    }

    /** What the send box may send: trimmed, at most ChatService.MAX_LEN characters, or "" when there is nothing to send. */
    public static String outgoing(String typed, int max) {
        if (typed == null) return "";
        String s = typed.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
