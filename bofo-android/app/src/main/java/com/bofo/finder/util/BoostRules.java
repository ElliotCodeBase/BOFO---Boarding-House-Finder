package com.bofo.finder.util;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Pure rules for Boost + Payments (8g, unit tested). The server decides prices and activation; this only names things and guards the checkout link. */
public final class BoostRules {
    private BoostRules() {}

    public enum Pay { PENDING, PAID, FAILED, EXPIRED, REFUNDED, UNKNOWN }

    /** Maps payments.status (check: pending / paid / failed / expired / refunded); anything else is UNKNOWN. */
    public static Pay pay(String status) {
        if (status == null) return Pay.UNKNOWN;
        switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "pending": return Pay.PENDING;
            case "paid": return Pay.PAID;
            case "failed": return Pay.FAILED;
            case "expired": return Pay.EXPIRED;
            case "refunded": return Pay.REFUNDED;
            default: return Pay.UNKNOWN;
        }
    }

    /** Polling stops once the payment is no longer waiting (an UNKNOWN answer keeps waiting: the row may not be visible yet). */
    public static boolean isFinal(Pay p) { return p == Pay.PAID || p == Pay.FAILED || p == Pay.EXPIRED || p == Pay.REFUNDED; }

    /** Seconds before the next status check: 3 s, then +2 s per try, at most 10 s. */
    public static int pollDelaySeconds(int attempt) { return Math.min(10, 3 + 2 * Math.max(0, attempt)); }

    /** About two minutes of checking while the screen is open (the payment page itself can take longer; Check again stays available). */
    public static final int MAX_POLLS = 16;

    /** The checkout link is opened in the browser only when it is a plain https link with a host; anything else (javascript:, intent:, http:) is refused. */
    public static String safeCheckoutUrl(String url) {
        if (url == null) return null;
        String u = url.trim();
        if (u.length() > 2000 || u.indexOf(' ') >= 0 || u.indexOf('\n') >= 0) return null;
        if (!u.regionMatches(true, 0, "https://", 0, 8)) return null;
        String rest = u.substring(8);
        int end = rest.length();
        for (char c : new char[]{'/', '?', '#'}) { int i = rest.indexOf(c); if (i >= 0 && i < end) end = i; }
        String host = rest.substring(0, end);
        if (host.isEmpty() || host.indexOf('@') >= 0 || host.startsWith(".") || !host.contains(".")) return null;
        return u;
    }

    /** True when the Edge Function is simply not deployed (HTTP 404 / "not found"), so the screen can say so in plain words. */
    public static boolean functionMissing(int httpStatus, String body) {
        if (httpStatus == 404) return true;
        return body != null && body.toLowerCase(Locale.ROOT).contains("requested function was not found");
    }

    /** "Weekly - 7 days - P99" style line for the plan list; the plan name already holds the days in the live data, so the days are added only when the name lacks them. */
    public static String planLine(String name, int days, double price) {
        String n = name == null ? "" : name.trim();
        String d = days + (days == 1 ? " day" : " days");
        boolean hasDays = n.toLowerCase(Locale.ROOT).contains(d) ;
        return (hasDays || n.isEmpty() ? n : n + " - " + d) + "  -  " + Money.peso(price);
    }

    public static boolean canPay(String placeId, String planId, boolean busy) {
        return !busy && placeId != null && !placeId.isEmpty() && planId != null && !planId.isEmpty();
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    /** "Oct 30, 2026" from a Postgres timestamptz, or "" when it cannot be read. */
    public static String day(String iso, ZoneId zone) {
        if (iso == null || iso.isEmpty()) return "";
        try { return DAY.format(OffsetDateTime.parse(normalize(iso)).toInstant().atZone(zone)); }
        catch (Exception e) { return ""; }
    }

    /** PostgREST gives "2026-10-30T11:25:00.123456+00:00" (OffsetDateTime reads it, and also a trailing Z); a space instead of the T is accepted too. */
    static String normalize(String iso) {
        String s = iso.trim();
        if (s.indexOf('T') < 0 && s.indexOf(' ') > 0) s = s.replace(' ', 'T');
        return s;
    }
}
