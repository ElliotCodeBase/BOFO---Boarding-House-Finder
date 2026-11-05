package com.bofo.finder.util;

/** v2.4.14: which plain label an audit_logs action gets in the admin's "What I did" list (index into R.array.admin_act_labels). */
public final class AdminActivityRules {
    public static final String[] ACTIONS = {"verification_approved", "verification_rejected", "report_suspend", "report_ban", "report_dismiss",
            "report_reviewed", "report_takedown", "listing_takedown", "user_unban", "boost_refunded"};
    private AdminActivityRules() {}

    /** Index of the action in {@link #ACTIONS}, or -1 (shown as "Other action"). */
    public static int index(String action) {
        if (action != null) for (int i = 0; i < ACTIONS.length; i++) if (ACTIONS[i].equals(action)) return i;
        return -1;
    }
}
