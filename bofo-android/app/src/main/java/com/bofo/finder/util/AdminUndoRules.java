package com.bofo.finder.util;

import java.util.List;

/** v2.5.0: which admin decisions can be undone, and whether a later row of the log already undid them. Pure Java (no Android classes). */
public final class AdminUndoRules {
    public static final int NONE = 0, SUSPEND = 1, RELIST = 2, REOPEN = 3, UNBAN = 4;
    /** How many decisions "Recently decided" shows. */
    public static final int LIMIT = 10;

    private AdminUndoRules() {}

    /** One audit_logs row, without any reason or note text. */
    public static final class Entry {
        public final String action, targetId, reportId;
        public Entry(String action, String targetId, String reportId) {
            this.action = action == null ? "" : action;
            this.targetId = targetId == null ? "" : targetId;
            this.reportId = reportId == null || "null".equals(reportId) ? "" : reportId;
        }
    }

    /** PostgREST filter that keeps only the decisions we show: {@code action=in.(a,b,...)}. */
    public static String actionFilter() {
        StringBuilder sb = new StringBuilder("action=in.(");
        for (int i = 0; i < AdminActivityRules.ACTIONS.length; i++) { if (i > 0) sb.append(','); sb.append(AdminActivityRules.ACTIONS[i]); }
        return sb.append(')').toString();
    }

    /** What would undo this action, or NONE (approvals, rejections, refunds, and the undo actions themselves cannot be undone here). */
    public static int kind(String action) {
        if (action == null) return NONE;
        switch (action) {
            case "report_suspend": return SUSPEND;
            case "report_takedown":
            case "listing_takedown": return RELIST;
            case "report_dismiss": return REOPEN;
            case "report_ban": return UNBAN;
            default: return NONE;
        }
    }

    /** The audit action that the matching Undo writes. */
    static String undoAction(int kind) {
        switch (kind) {
            case SUSPEND: return "user_unsuspend";
            case RELIST: return "listing_relist";
            case REOPEN: return "report_reopen";
            case UNBAN: return "user_unban";
            default: return "";
        }
    }

    /** True when one of the NEWER rows is the matching Undo for the same person / place / report. */
    public static boolean undone(Entry e, List<Entry> newer) {
        int k = kind(e.action);
        if (k == NONE || newer == null) return false;
        String want = undoAction(k);
        for (Entry n : newer) {
            if (!want.equals(n.action)) continue;
            if (k == REOPEN ? (!e.reportId.isEmpty() && e.reportId.equals(n.reportId)) : (!e.targetId.isEmpty() && e.targetId.equals(n.targetId))) return true;
        }
        return false;
    }

    /** Show the Undo button: the action has an Undo, nothing newer undid it, and we know what to send to the server. */
    public static boolean canUndo(Entry e, List<Entry> newer) {
        int k = kind(e.action);
        if (k == NONE || undone(e, newer)) return false;
        return k == REOPEN ? !e.reportId.isEmpty() : !e.targetId.isEmpty();
    }

    /** A row swiped at least 40 % of its width (either direction) is committed. */
    public static boolean swipeCommits(float dx, int width) {
        return width > 0 && Math.abs(dx) >= width * 0.4f;
    }
}
