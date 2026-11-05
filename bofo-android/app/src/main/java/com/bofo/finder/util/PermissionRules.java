package com.bofo.finder.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Admin permission model, client side (Stage 2). A MIRROR of supabase/migrations/039_admin_roles.sql, used only to decide what to SHOW.
 * The server (has_permission / require_permission) is the real gate and always wins; keep the two lists identical.
 * Pure Java, no Android classes, so it is unit-tested (PermissionRulesTest) and moves to the shared module in Stage 3.
 */
public final class PermissionRules {
    private PermissionRules() {}

    public static final String LEGACY_ADMIN = "legacy_admin";
    public static final String DASHBOARD_VIEW = "dashboard_view", ID_REVIEW = "id_review", ID_VIEW_RAW = "id_view_raw",
            REPORTS_VIEW = "reports_view", REPORTS_ACT = "reports_act", SUSPEND_SHORT = "suspend_short", SUSPEND_LONG = "suspend_long",
            BAN_PERMANENT = "ban_permanent", UNBAN = "unban", BANNED_VIEW = "banned_view", LISTING_TAKEDOWN = "listing_takedown",
            AI_ANSWER = "ai_answer", AI_LOG_VIEW = "ai_log_view", AI_SETTINGS = "ai_settings",
            PAYMENTS_VIEW = "payments_view", PAYMENTS_MANAGE = "payments_manage", CSV_EXPORT = "csv_export",
            AUDIT_VIEW_OWN = "audit_view_own", AUDIT_VIEW_ALL = "audit_view_all", MEMBERS_MANAGE = "members_manage",
            ANNOUNCE_PUSH = "announce_push", MFA_MANAGE = "mfa_manage", APP_CONFIG = "app_config", USER_DATA_DELETE = "user_data_delete",
            APPROVALS_REQUEST = "approvals_request", APPROVALS_DECIDE = "approvals_decide";

    /** Same order as admin_permission_keys() in 039. */
    public static final Set<String> ALL = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            LEGACY_ADMIN, DASHBOARD_VIEW, ID_REVIEW, ID_VIEW_RAW, REPORTS_VIEW, REPORTS_ACT, SUSPEND_SHORT, SUSPEND_LONG, BAN_PERMANENT, UNBAN,
            BANNED_VIEW, LISTING_TAKEDOWN, AI_ANSWER, AI_LOG_VIEW, AI_SETTINGS, PAYMENTS_VIEW, PAYMENTS_MANAGE, CSV_EXPORT, AUDIT_VIEW_OWN,
            AUDIT_VIEW_ALL, MEMBERS_MANAGE, ANNOUNCE_PUSH, MFA_MANAGE, APP_CONFIG, USER_DATA_DELETE, APPROVALS_REQUEST, APPROVALS_DECIDE)));

    /** Same as admin_owner_only_keys() in 039: the 12 owner-only items. */
    public static final Set<String> OWNER_ONLY = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            MEMBERS_MANAGE, PAYMENTS_VIEW, PAYMENTS_MANAGE, CSV_EXPORT, BAN_PERMANENT, UNBAN, LISTING_TAKEDOWN, AUDIT_VIEW_ALL,
            AI_SETTINGS, ANNOUNCE_PUSH, AI_LOG_VIEW, MFA_MANAGE, APP_CONFIG, USER_DATA_DELETE, ID_VIEW_RAW)));

    private static final Map<String, Set<String>> PRESETS = new HashMap<>();
    static {
        PRESETS.put("owner", ALL);
        PRESETS.put("legacy_admin", ALL);
        PRESETS.put("admin", set(DASHBOARD_VIEW, ID_REVIEW, REPORTS_VIEW, REPORTS_ACT, SUSPEND_SHORT, SUSPEND_LONG, BANNED_VIEW,
                AI_ANSWER, AUDIT_VIEW_OWN, APPROVALS_REQUEST, APPROVALS_DECIDE));
        PRESETS.put("moderator", set(DASHBOARD_VIEW, ID_REVIEW, REPORTS_VIEW, REPORTS_ACT, SUSPEND_SHORT, BANNED_VIEW,
                AI_ANSWER, AUDIT_VIEW_OWN, APPROVALS_REQUEST));
        PRESETS.put("support", set(DASHBOARD_VIEW, REPORTS_VIEW, BANNED_VIEW, AI_ANSWER, AUDIT_VIEW_OWN));
    }

    /** Longest suspension (hours) a role without suspend_long may apply. Same 168 as admin_review_report in 040. */
    public static final int SHORT_SUSPEND_MAX_HOURS = 168;

    private static Set<String> set(String... k) { return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(k))); }

    /** The preset for a role key, or an empty set for null/unknown (unknown = no access). */
    public static Set<String> preset(String role) {
        Set<String> p = role == null ? null : PRESETS.get(role);
        return p == null ? Collections.<String>emptySet() : p;
    }

    /** What a member can do: preset + grants - denies. The owner always keeps everything. Unknown keys are ignored. */
    public static boolean has(String role, Collection<String> grants, Collection<String> denies, String key) {
        if (key == null || !ALL.contains(key)) return false;
        if ("owner".equals(role)) return true;
        Set<String> p = preset(role);
        boolean granted = p.contains(key) || (grants != null && grants.contains(key));
        return granted && !(denies != null && denies.contains(key));
    }

    /** True when the key is on the owner-only list. */
    public static boolean ownerOnly(String key) { return key != null && OWNER_ONLY.contains(key); }

    /** True when a per-person grant may be given to this role (owner-only keys never go to a sub-admin). */
    public static boolean canGrant(String role, String key) {
        if (key == null || !ALL.contains(key)) return false;
        return "owner".equals(role) || !OWNER_ONLY.contains(key);
    }

    /** The permission needed to suspend for this many hours. */
    public static String suspendKey(int hours) { return hours > SHORT_SUSPEND_MAX_HOURS ? SUSPEND_LONG : SUSPEND_SHORT; }

    /** What a person without this permission should see: a "Request approval" button instead of the action. */
    public static boolean needsApproval(Collection<String> perms, String key) {
        return perms != null && perms.contains(APPROVALS_REQUEST) && !perms.contains(key);
    }

    /** True when this account can decide the request (needs approvals_decide AND the permission of the action itself). */
    public static boolean canDecide(Collection<String> perms, String actionKey, String requesterId, String myId) {
        return perms != null && perms.contains(APPROVALS_DECIDE) && perms.contains(actionKey)
                && requesterId != null && !requesterId.equals(myId);
    }

    /** The permission an approval request needs from the decider, by kind (same names as admin_approvals.kind). */
    public static String approvalKey(String kind) {
        if ("report_ban".equals(kind)) return BAN_PERMANENT;
        if ("report_suspend".equals(kind)) return SUSPEND_LONG;
        if ("report_takedown".equals(kind)) return LISTING_TAKEDOWN;
        return null;
    }

    /** Conflict check mirrored from admin_review_report: nobody decides a report about themselves or by themselves. */
    public static boolean conflict(String me, String reporter, String reported, String reportedInvitedBy) {
        if (me == null) return true;
        return me.equals(reporter) || me.equals(reported) || me.equals(reportedInvitedBy);
    }
}
