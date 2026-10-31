package com.bofo.finder.util;

/** Pure rules for the admin shell (8h, unit tested). The database re-checks everything (is_admin() inside each RPC); these only keep bad input off the wire. */
public final class AdminRules {
    private AdminRules() {}

    public static final int REASON_MAX = 300;       // blocks_reports.reason is capped at 300; the same limit is used for every reason typed here

    /** Reasons are trimmed and cut at 300 characters; null becomes "". */
    public static String clean(String reason) {
        String r = reason == null ? "" : reason.trim();
        return r.length() > REASON_MAX ? r.substring(0, REASON_MAX).trim() : r;
    }

    /** review_verification refuses a rejection without a reason ('reason required'); an approval needs none. */
    public static boolean reasonOk(boolean approve, String reason) { return approve || !clean(reason).isEmpty(); }

    /** admin_takedown needs a reason for the audit log in this app (the function itself accepts any text). */
    public static boolean takedownOk(String reason) { return !clean(reason).isEmpty(); }

    /** Only a paid payment can be refunded; the database would also accept others, but a refund of a waiting / failed payment makes no sense. */
    public static boolean canRefund(String status) { return BoostRules.pay(status) == BoostRules.Pay.PAID; }

    /** A place already taken down has nothing left to take down. */
    public static boolean canTakeDown(String placeStatus) { return !"taken_down".equals(placeStatus); }

    /** Name for a user id that could not be looked up (deleted account, or the lookup failed). */
    public static String who(String name) { return name == null || name.trim().isEmpty() ? "Unknown user" : name.trim(); }

    /** blocks_reports holds both blocks and reports (is_block). */
    public static boolean isReport(boolean isBlock) { return !isBlock; }

    /** Comma list for a PostgREST in.(...) filter; only UUID-looking ids are kept, so a stray value can never alter the query. */
    public static String inList(Iterable<String> ids) {
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String id : ids) {
            if (id == null || !id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") || !seen.add(id)) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }
}
