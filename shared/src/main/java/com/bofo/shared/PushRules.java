package com.bofo.shared;

import java.util.Set;

/**
 * Admin push rules, phone side (Stage 4 part 4). Mirrors supabase/functions/send-push: the server already sends each kind only to people who hold
 * the permission; this is the second check on the phone (drop a push the signed-in account may not see) and the tap target.
 * Pure Java, no Android classes, unit-tested (PushRulesTest).
 */
public final class PushRules {
    private PushRules() {}

    public static final String REPORT = "report", IDREVIEW = "idreview", STALE = "stale", APPROVAL = "approval", ESCALATED = "escalated", DECIDED = "decided", UNUSUAL = "unusual", TEST = "test";
    public static final String CHANNEL_ADMIN = "bofo_admin", CHANNEL_REPORTS = "bofo_reports";
    public static final String ACTION_OPEN = "com.bofo.admin.OPEN_PUSH";

    /** Tab ids of MainActivity: Home, IDs, Reports, Approvals, More. */
    public static final int TAB_NONE = -1, TAB_HOME = 0, TAB_IDS = 1, TAB_REPORTS = 2, TAB_APPROVALS = 3, TAB_MORE = 4;

    /** May this account see a push of this kind? Unknown kinds (chat pushes, anything new) are dropped. */
    public static boolean allowed(String kind, Set<String> perms) {
        if (kind == null || perms == null) return false;
        switch (kind) {
            case REPORT: return perms.contains(PermissionRules.REPORTS_ACT);
            case IDREVIEW: return perms.contains(PermissionRules.ID_REVIEW);
            case APPROVAL: return perms.contains(PermissionRules.APPROVALS_DECIDE);
            case STALE: return perms.contains(PermissionRules.ID_REVIEW) || perms.contains(PermissionRules.REPORTS_ACT) || perms.contains(PermissionRules.PAYMENTS_VIEW);
            case ESCALATED: return perms.contains(PermissionRules.APPROVALS_DECIDE);
            case DECIDED: return perms.contains(PermissionRules.APPROVALS_REQUEST);
            case UNUSUAL: return perms.contains(PermissionRules.AUDIT_VIEW_ALL);
            case TEST: return true;
            default: return false;
        }
    }

    /** Which tab a tap opens. TAB_NONE for an unknown kind (just open the app). */
    public static int tabFor(String kind, String place) {
        if (kind == null) return TAB_NONE;
        switch (kind) {
            case REPORT: return TAB_REPORTS;
            case IDREVIEW: return TAB_IDS;
            case APPROVAL: return TAB_APPROVALS;
            case DECIDED: return TAB_APPROVALS;
            case ESCALATED: return TAB_REPORTS;
            case UNUSUAL: return TAB_MORE;
            case TEST: return TAB_HOME;
            case STALE:
                if ("queue".equals(place)) return TAB_IDS;
                if ("reports".equals(place)) return TAB_REPORTS;
                if ("payments".equals(place)) return TAB_MORE;       // Payments lives under More
                return TAB_HOME;
            default: return TAB_NONE;
        }
    }

    public static String channelFor(String kind) { return REPORT.equals(kind) ? CHANNEL_REPORTS : CHANNEL_ADMIN; }
}
