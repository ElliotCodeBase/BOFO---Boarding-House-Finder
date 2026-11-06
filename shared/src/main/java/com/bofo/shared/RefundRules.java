package com.bofo.shared;

/** Refund rules, phone side (Stage 4 part 5). The server (admin_refund_boost, migration 043) enforces the same; this decides what to SHOW and cleans the reason. Pure Java, unit-tested. */
public final class RefundRules {
    private RefundRules() {}
    public static final int MIN_REASON = 5, MAX_REASON = 300;

    /** Only a paid payment can be refunded (pending / failed / expired / already refunded cannot). */
    public static boolean canRefund(String status) { return "paid".equals(status); }

    /** Trim, collapse white space, cut to MAX_REASON. Never null. */
    public static String cleanReason(String r) {
        if (r == null) return "";
        String t = r.replaceAll("\\s+", " ").trim();
        return t.length() > MAX_REASON ? t.substring(0, MAX_REASON) : t;
    }

    public static boolean validReason(String r) { return r != null && r.trim().length() >= MIN_REASON; }
}
