package com.bofo.finder.util;

/**
 * v2.3.0 phone notifications: the pure rules (no Android classes, so they run in plain JVM tests).
 * Kinds come from the send-push edge function: dm = student/owner chat, group = boarding group chat, test = a test push.
 * v2.11.0: the admin kinds (report, idreview, stale, approval ...) go only to the BOFO Admin app; this app does not know them and drops them.
 */
public final class PushRules {
    private PushRules() {}

    public static final String DM = "dm", GROUP = "group";
    /** v2.9.2: a plain test push for one account (server: push_notify('test', {"as":"me","user_id":...})). Any role may see it. */
    public static final String TEST = "test";
    public static final String CH_CHATS = "bofo_chats", CH_BOARDING = "bofo_boarding", CH_ANNOUNCE = "bofo_announcements";
    /** v2.11.0: channels of the old admin pushes. Removed from phones on the next push / sign-in (the admin app has its own). */
    public static final String OLD_CH_REPORTS = "bofo_reports", OLD_CH_ADMIN = "bofo_admin";
    /** Every notification uses this id together with its tag (kind:id), so one chat = one notification that grows. */
    public static final int NOTIFICATION_ID = 1;
    /** Messages kept inside one chat notification. */
    public static final int MAX_LINES = 6;

    public static boolean known(String kind) { return DM.equals(kind) || GROUP.equals(kind) || TEST.equals(kind); }

    /** Channel (= the on/off switch in Android's notification settings) of a push. */
    public static String channel(String kind, boolean announcement) {
        if (GROUP.equals(kind)) return announcement ? CH_ANNOUNCE : CH_BOARDING;
        return CH_CHATS;
    }

    public static String tag(String kind, String id) { return kind + ":" + id; }

    /**
     * Who may SEE a push on this phone. role = the role the server gave the account that is signed in here ("" = nobody).
     * Only the user kinds (dm, group, test) are known here, so an admin kind that reaches this phone is dropped whatever the role is.
     */
    public static boolean allowedFor(String kind, String role) {
        if (!known(kind) || role == null || role.isEmpty()) return false;
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
