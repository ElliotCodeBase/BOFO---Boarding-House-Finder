package com.bofo.shared;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Stage 4i rules that need no Android classes: waiting-time tiers, the claim line, the strikes ladder labels, appeal text checks,
 * break-glass keys and the unusual-activity threshold. Mirrors migration 044 (the server is the real gate; this decides what to SHOW).
 */
public final class QueueRules {
    private QueueRules() {}

    public static final int CLAIM_MINUTES = 30;                 // 044: a claim older than this is ignored
    public static final int UNUSUAL_PER_HOUR = 10;              // 044: trigger / admin_unusual_activity threshold
    public static final int APPEAL_MIN = 10, APPEAL_MAX = 1000; // 044: appeals.message check
    public static final int NOTE_MIN = 5, NOTE_MAX = 300;       // escalation note, strike reason, lend reason
    public static final int BREAK_GLASS_MINUTES = 60;

    public static final int TIER_FRESH = 0, TIER_WAITING = 1, TIER_LATE = 2;
    private static final long HOUR = 3_600_000L, DAY = 24 * HOUR;

    /** Green under 1 day, amber 1 to 3 days, red after 3 days (a negative or unknown age counts as fresh). */
    public static int tier(long ageMillis) {
        if (ageMillis >= 3 * DAY) return TIER_LATE;
        if (ageMillis >= DAY) return TIER_WAITING;
        return TIER_FRESH;
    }

    /** "Waiting 5 h", "Waiting 2 days". Words as well as colour. */
    public static String waitLabel(long ageMillis) {
        if (ageMillis < HOUR) return "Waiting under 1 h";
        if (ageMillis < DAY) return "Waiting " + (ageMillis / HOUR) + " h";
        long d = ageMillis / DAY;
        return "Waiting " + d + (d == 1 ? " day" : " days");
    }

    /** Millis since the ISO timestamp the server sent, or -1 when it cannot be read. */
    public static long ageMillis(String iso, long nowMillis) {
        if (iso == null || iso.isEmpty() || "null".equals(iso)) return -1;
        try { return nowMillis - OffsetDateTime.parse(iso).toInstant().toEpochMilli(); }
        catch (RuntimeException e) { return -1; }
    }

    public static String claimLine(String by) {
        String n = by == null ? "" : by.trim();
        return "Being handled by " + (n.isEmpty() ? "a colleague" : n);
    }

    /* ---------- strikes ladder (strike_step_for in 044) ---------- */
    public static final String S_WARNING = "warning", S_3D = "suspend_3d", S_7D = "suspend_7d", S_BAN = "ban";

    public static String strikeStep(int priorStrikes) {
        if (priorStrikes <= 0) return S_WARNING;
        if (priorStrikes == 1) return S_3D;
        if (priorStrikes == 2) return S_7D;
        return S_BAN;
    }

    public static String strikeLabel(String step) {
        if (S_WARNING.equals(step)) return "a warning (a record for staff, the person is not messaged)";
        if (S_3D.equals(step)) return "a 3-day suspension";
        if (S_7D.equals(step)) return "a 7-day suspension";
        if (S_BAN.equals(step)) return "a permanent ban";
        return "the next step";
    }

    /** Does this step need the permanent-ban permission? (A moderator is then told to use Ban > Request approval.) */
    public static boolean stepNeedsBanPermission(String step) { return S_BAN.equals(step); }

    /* ---------- text checks ---------- */
    public static boolean noteOk(String s) { int n = s == null ? 0 : s.trim().length(); return n >= NOTE_MIN && n <= NOTE_MAX; }
    public static boolean appealOk(String s) { int n = s == null ? 0 : s.trim().length(); return n >= APPEAL_MIN && n <= APPEAL_MAX; }

    /* ---------- break-glass (break_glass_keys in 044) ---------- */
    public static final Set<String> LENDABLE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            PermissionRules.BAN_PERMANENT, PermissionRules.UNBAN, PermissionRules.LISTING_TAKEDOWN,
            PermissionRules.SUSPEND_LONG, PermissionRules.PAYMENTS_VIEW, PermissionRules.CSV_EXPORT)));
    public static final String[] LEND_KEYS = {PermissionRules.BAN_PERMANENT, PermissionRules.UNBAN, PermissionRules.LISTING_TAKEDOWN,
            PermissionRules.SUSPEND_LONG, PermissionRules.PAYMENTS_VIEW, PermissionRules.CSV_EXPORT};
    public static final String[] LEND_LABELS = {"Permanent ban", "Lift a ban", "Take down a listing", "Suspend longer than 7 days", "View payments", "Export CSV"};

    public static String lendLabel(String key) {
        for (int i = 0; i < LEND_KEYS.length; i++) if (LEND_KEYS[i].equals(key)) return LEND_LABELS[i];
        return key == null ? "" : key;
    }

    /** Minutes left of a lend, never negative. */
    public static long minutesLeft(String expiresIso, long nowMillis) {
        long a = ageMillis(expiresIso, nowMillis);
        if (a == -1 && (expiresIso == null || expiresIso.isEmpty())) return 0;
        return Math.max(0, -a / 60_000L);
    }

    /** One line for the Unusual activity screen. */
    public static String unusualLine(int n1h, int n24h) {
        return n1h + " sensitive action" + (n1h == 1 ? "" : "s") + " in the last hour, " + n24h + " in 24 hours";
    }
}
