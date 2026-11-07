package com.bofo.admin;

import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.bofo.shared.Api;
import com.bofo.shared.Async;
import com.bofo.shared.PermissionRules;

import org.json.JSONArray;
import org.json.JSONObject;

/** Approval requests. A moderator's ban / long suspend / take-down waits here; an Admin or the Owner decides. Approving runs the action as the approver (server-side). */
final class ApprovalsScreen extends FrameLayout {
    private final MainActivity act; private final Access access; private final LinearLayout list;

    ApprovalsScreen(MainActivity a, Access access) {
        super(a); act = a; this.access = access;
        list = Ui.col(a); int p = Ui.dp(a, 16); list.setPadding(p, p, p, p);
        addView(MainActivity.scroll(a, list), new LayoutParams(-1, -1));
        load();
    }

    private void load() {
        Async.run(() -> new JSONArray(Api.get().rpc("admin_approvals_list", new JSONObject())), this::render, e -> Ui.toast(getContext(), Ui.err(e)));
    }

    private void render(JSONArray rows) {
        android.content.Context c = getContext();
        list.removeAllViews();
        list.addView(Ui.text(c, "Approvals", R.style.Text_Bofo_Title));
        if (rows.length() == 0) { list.addView(Ui.text(c, "No requests.", R.style.Text_Bofo_Caption)); return; }
        String me = AdminAuth.get().userId();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i); if (o == null) continue;
            JSONObject pl = o.optJSONObject("payload") == null ? new JSONObject() : o.optJSONObject("payload");
            String kind = o.optString("kind"), st = o.optString("status");
            LinearLayout card = Ui.card(c);
            card.addView(Ui.text(c, kind.replace("report_", "").replace("takedown", "take down") + (pl.has("hrs") ? " " + pl.optInt("hrs") / 24 + " days" : ""), R.style.Text_Bofo_Section));
            card.addView(Ui.text(c, "Reason: " + pl.optString("reason", "-"), R.style.Text_Bofo_Body));
            if (!o.isNull("note") && !o.optString("note").isEmpty()) card.addView(Ui.text(c, "Note: " + o.optString("note"), R.style.Text_Bofo_Caption));
            String when = o.optString("requested_at", ""); card.addView(Ui.text(c, "Requested " + when.replace('T', ' ').substring(0, Math.min(16, when.length())) + (o.optString("requested_by").equals(me) ? "  |  by you" : ""), R.style.Text_Bofo_Caption));
            if ("pending".equals(st)) {
                if (PermissionRules.canDecide(access.perms, PermissionRules.approvalKey(kind), o.optString("requested_by"), me)) {
                    LinearLayout r = Ui.actions(c);
                    android.widget.Button no = Ui.button(c, "Deny", false); no.setOnClickListener(v -> decide(o.optString("id"), false));
                    android.widget.Button ok = Ui.button(c, "Approve", true); ok.setOnClickListener(v -> decide(o.optString("id"), true));
                    r.addView(no); r.addView(ok); card.addView(r);
                } else card.addView(Ui.pill(c, "Waiting for an approver", R.color.bofo_card_ink, R.color.bofo_warn_tint));
            } else card.addView(Ui.pill(c, st, "approved".equals(st) ? R.color.bofo_success : R.color.bofo_text_2, "approved".equals(st) ? R.color.bofo_success_tint : R.color.bofo_icon_bg));
            list.addView(card);
        }
    }

    private void decide(String id, boolean approve) {
        Reauth.ask(act, approve ? "Approve request" : "Deny request", () -> Async.run(
                () -> Api.get().rpc("decide_admin_approval", new JSONObject().put("id_", id).put("approve", approve)),
                r -> { Ui.toast(getContext(), approve ? "Approved and done." : "Denied."); load(); }, e -> Ui.toast(getContext(), Ui.err(e))), null);
    }
}
