package com.bofo.finder.service;

import com.bofo.finder.util.AdminUndoRules;
import com.bofo.finder.util.AdminUndoRules.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** v2.5.0 (admin only): the last decisions (read from audit_logs through the existing admin-read policy; reasons are never selected) and the Undo calls. */
public final class AdminUndoService {
    private final SupabaseClient api = SupabaseClient.get();

    /** One decision row. {@code actorId} may be empty. */
    public static final class Decision {
        public final Entry entry;
        public final String targetType, actorId, createdAt;
        Decision(Entry entry, String targetType, String actorId, String createdAt) { this.entry = entry; this.targetType = targetType; this.actorId = actorId; this.createdAt = createdAt; }
    }

    /** Newest first, at most {@link AdminUndoRules#LIMIT}. */
    public List<Decision> recent() throws Exception {
        JSONArray a = api.select("audit_logs", AdminUndoRules.actionFilter() + "&order=created_at.desc&limit=" + AdminUndoRules.LIMIT
                + "&select=action,target_type,target_id,actor_id,created_at,report_id:details->>report_id");
        List<Decision> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            out.add(new Decision(new Entry(o.optString("action", ""), clean(o.optString("target_id", "")), clean(o.optString("report_id", ""))),
                    o.optString("target_type", ""), clean(o.optString("actor_id", "")), o.optString("created_at", "")));
        }
        return out;
    }

    private static String clean(String s) { return s == null || "null".equals(s) ? "" : s; }

    /** Runs the Undo that belongs to the decision. The server refuses (clear error) when there is nothing to undo any more. */
    public void undo(Entry e) throws Exception {
        switch (AdminUndoRules.kind(e.action)) {
            case AdminUndoRules.SUSPEND: api.rpc("admin_unsuspend", new JSONObject().put("uid", e.targetId)); break;
            case AdminUndoRules.RELIST: api.rpc("admin_relist", new JSONObject().put("bh", e.targetId)); break;
            case AdminUndoRules.REOPEN: reopen(e.reportId); break;
            case AdminUndoRules.UNBAN: new BannedUsersService().unban(e.targetId, null); break;
            default: throw new IllegalArgumentException("not undoable");
        }
    }

    public void reopen(String reportId) throws Exception {
        api.rpc("admin_reopen_report", new JSONObject().put("rid", reportId));
    }
}
