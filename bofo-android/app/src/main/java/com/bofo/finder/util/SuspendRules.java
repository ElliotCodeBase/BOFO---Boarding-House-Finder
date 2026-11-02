package com.bofo.finder.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** v2.1.8: plain rules for reports and suspensions (no Android, so they are unit-tested on a plain JVM). */
public final class SuspendRules {
    private SuspendRules() {}

    /** Quick choices in the admin "Suspend for" list; the last list entry is "Custom". */
    public static final int[] PRESET_DAYS = {1, 3, 7, 30};
    /** Longest suspension the app offers (the database allows 10 years = 87600 hours). */
    public static final int MAX_DAYS = 3650;
    /** Same keys as the CHECK on reports.reason. Order = res/values/strings.xml array report_reasons. */
    public static final String[] REASONS = {"scam", "fake_listing", "harassment", "inappropriate", "wrong_info", "other"};
    /** Same keys as the CHECK on reports.status. Order = array rep_status_labels. */
    public static final String[] STATUSES = {"pending", "reviewed", "dismissed", "actioned"};

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US);

    /** Whole days 1..MAX_DAYS, or null when the text is not that. */
    public static Integer parseDays(String s) {
        if (s == null) return null;
        try { int d = Integer.parseInt(s.trim()); return d >= 1 && d <= MAX_DAYS ? d : null; }
        catch (NumberFormatException e) { return null; }
    }

    public static int hours(int days) { return days * 24; }

    public static int reasonIndex(String key) {
        for (int i = 0; i < REASONS.length; i++) if (REASONS[i].equals(key)) return i;
        return -1;
    }

    public static int statusIndex(String key) {
        for (int i = 0; i < STATUSES.length; i++) if (STATUSES[i].equals(key)) return i;
        return -1;
    }

    /** Free text of a report: control characters removed, max 500 (the database limit). */
    public static String details(String s) { return Validate.clean(s, 500); }

    /** A user cannot report themselves (the database refuses it too). */
    public static boolean canReport(String me, String target) {
        return me != null && !me.isEmpty() && target != null && !target.isEmpty() && !me.equals(target);
    }

    /** "Nov 9, 2026, 3:20 PM" from a Postgres timestamptz, or "" when it cannot be read. */
    public static String when(String iso, ZoneId zone) {
        if (iso == null || iso.isEmpty() || "null".equals(iso)) return "";
        try { return WHEN.format(OffsetDateTime.parse(BoostRules.normalize(iso)).toInstant().atZone(zone)); }
        catch (Exception e) { return ""; }
    }

    /** When a suspension of this many days that starts now would end (shown in the confirmation). */
    public static String endsAt(int days, Instant now, ZoneId zone) {
        return WHEN.format(now.plusSeconds(days * 86400L).atZone(zone));
    }

    /** True while the end time is still in the future. */
    public static boolean active(String iso, Instant now) {
        if (iso == null || iso.isEmpty() || "null".equals(iso)) return false;
        try { return OffsetDateTime.parse(BoostRules.normalize(iso)).toInstant().isAfter(now); }
        catch (Exception e) { return false; }
    }
}
