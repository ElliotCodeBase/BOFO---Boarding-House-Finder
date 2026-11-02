package com.bofo.finder.service;

import com.bofo.finder.util.SuspendRules;

import org.json.JSONObject;

/** v2.1.8: a signed-in user reports another user (about a place). The database allows one waiting report per person + place. */
public final class ReportService {
    /** True when the server refused because this reporter already has a waiting report about the same person + place. */
    public static boolean isDuplicate(Throwable e) {
        return e instanceof SupabaseClient.ApiException a && (a.status == 409 || a.body.contains("23505"));
    }

    public void submit(String reportedUserId, String bhId, String reason, String details) throws Exception {
        String me = AuthService.get().userId();
        if (!SuspendRules.canReport(me, reportedUserId)) throw new IllegalArgumentException("You cannot report yourself.");
        if (SuspendRules.reasonIndex(reason) < 0) throw new IllegalArgumentException("Pick a reason.");
        String d = SuspendRules.details(details);
        JSONObject row = new JSONObject().put("reporter_id", me).put("reported_user_id", reportedUserId).put("reason", reason)
                .put("details", d.isEmpty() ? JSONObject.NULL : d);
        if (bhId != null && !bhId.isEmpty()) row.put("bh_id", bhId);
        SupabaseClient.get().insert("reports", row);
    }
}
