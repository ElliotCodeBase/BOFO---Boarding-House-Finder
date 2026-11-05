package com.bofo.finder.util;

/** v2.7.0: pure rules for the Queue second-opinion flag (no Android classes, unit tested). */
public final class ReviewSpeedRules {
    public static final int WAITING = 0, SECOND_OPINION = 1, APPROVED = 2, REJECTED = 3;
    private ReviewSpeedRules() {}

    /** Which pill a Queue row shows. A flag only counts while the request is still pending. */
    public static int rowState(String status, boolean flagged) {
        if ("verified".equals(status)) return APPROVED;
        if ("rejected".equals(status)) return REJECTED;
        return flagged ? SECOND_OPINION : WAITING;
    }

    /** The flag can only be set or cleared on a request that is still waiting. */
    public static boolean canFlag(String status) { return "pending".equals(status); }
}
