package com.bofo.finder.util;

/**
 * v2.3.0 phone notifications: the pure rules (no Android classes, so they run in plain JVM tests).
 * Kinds come from the send-push edge function: dm = student/owner chat, group = boarding group chat, report = a user report (admins only).
 */
public final class PushRules {
    private PushRules() {}

    public static final String DM = "dm", GROUP = "group", REPORT = "report";
    /** v2.6.0 (admins only): IDREVIEW = an owner sent an ID; STALE = the daily "items waiting more than 3 days" reminder. */
    public static final String IDREVIEW = "idreview", STALE = "stale";
    public static final String CH_CHATS = "bofo_chats", CH_BOARDING = "bofo_boarding", CH_ANNOUNCE = "bofo_announcements", CH_REPORTS = "bofo_reports", CH_ADMIN = "bofo_admin";
    /** Which admin tab a push opens. */
    public static final int TAB_NONE = -1, TAB_QUEUE = 0, TAB_REPORTS = 1, TAB_PAYMENTS = 2;
    /** Every notification uses this id together with its tag (kind:id), so one chat = one notification that grows. */
    public static final int NOTIFICATION_ID = 1;
    /** Messages kept inside one chat notification. */
    public static final int MAX_LINES = 6;

    public static boolean known(String kind) { return DM.equals(kind) || GROUP.equals(kind) || adminOnly(kind); }

    /** Pushes only an admin account may get or see (v2.6.0: report, idreview, stale). */
    public static boolean adminOnly(String kind) { return REPORT.equals(kind) || IDREVIEW.equals(kind) || STALE.equals(kind); }

    /** Admin tab a tap opens (always with the Waiting filter), or TAB_NONE. STALE carries the target the server chose in the "place" field. */
    public static int adminTab(String kind, String target) {
        if (REPORT.equals(kind)) return TAB_REPORTS;
        if (IDREVIEW.equals(kind)) return TAB_QUEUE;
        if (STALE.equals(kind)) return "reports".equals(target) ? TAB_REPORTS : "payments".equals(target) ? TAB_PAYMENTS : TAB_QUEUE;
        return TAB_NONE;
    }

    /** Channel (= the on/off switch in Android's notification settings) of a push. */
    public static String channel(String kind, boolean announcement) {
        if (REPORT.equals(kind)) return CH_REPORTS;
        if (IDREVIEW.equals(kind) || STALE.equals(kind)) return CH_ADMIN;
        if (GROUP.equals(kind)) return announcement ? CH_ANNOUNCE : CH_BOARDING;
        return CH_CHATS;
    }

    public static String tag(String kind, String id) { return kind + ":" + id; }

    /**
     * Who may SEE a push on this phone. role = the role the server gave the account that is signed in here ("" = nobody).
     * Reports, ID reviews and the stale reminder are admin-only (the server already sends them only to admin accounts; this is the second lock).
     */
    public static boolean allowedFor(String kind, String role) {
        if (!known(kind) || role == null || role.isEmpty()) return false;
        if (adminOnly(kind)) return "admin".equals(role);
        return true;
    }

    /** The person is looking at this very chat right now: no banner for it. */
    public static boolean isOpenNow(String kind, String id, String openDm, String openGroup) {
        if (id == null || id.isEmpty()) return false;
        return (DM.equals(kind) && id.equals(openDm)) || (GROUP.equals(kind) && id.equals(openGroup));
    }

    /** One line for the banner: collapses white space, cuts long text with an ellipsis. */
    public static String preview(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, Math.max(0, max - 1)).trim() + "\u2026";
    }

    /** Register again when the account changed, the token changed, or the last registration is older than 6 hours. */
    public static boolean needsRegister(String savedUid, String savedToken, long savedAtMs, String uid, String token, long nowMs) {
        if (uid == null || uid.isEmpty() || token == null || token.isEmpty()) return false;
        return !uid.equals(savedUid) || !token.equals(savedToken) || nowMs - savedAtMs > 6L * 60 * 60 * 1000;
    }
}
