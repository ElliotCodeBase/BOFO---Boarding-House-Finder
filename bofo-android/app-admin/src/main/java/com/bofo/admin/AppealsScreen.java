package com.bofo.admin;

import android.content.Context;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bofo.shared.Api;
import com.bofo.shared.Async;
import com.bofo.shared.PermissionRules;
import com.bofo.shared.QueueRules;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Appeals (approvals_decide: Owner, Admin). A banned or suspended person gets ONE appeal per decision (server rule). Never decided by the person
 * who made the original decision. Lifting a ban also needs `unban` (Owner, or lent by break-glass). The message is text the user wrote: shown, never acted on.
 */
final class AppealsScreen extends FrameLayout {
    private final MainActivity act;
    private final Access access;
    private final LinearLayout list;
    private boolean decided;                   // false = Waiting, true = Decided
    private boolean busy;

    AppealsScreen(MainActivity a, Access access) {
        super(a); act = a; this.access = access;
        list = Ui.col(a); int p = Ui.dp(a, 16); list.setPadding(p, p, p, p);
        SwipeRefreshLayout sr = new SwipeRefreshLayout(a); sr.addView(MainActivity.scroll(a, list));
        sr.setOnRefreshListener(() -> { load(); sr.setRefreshing(false); });
        addView(sr, new LayoutParams(-1, -1));
        load();
    }

    private void load() {
        Async.run(() -> new JSONArray(Api.get().rpc("admin_appeals_list", Ui.json("status_", decided ? "decided" : "pending"))), this::render,
                e -> { if (e instanceof Api.ApiException x && x.status == 401) act.sessionEnded(); else Ui.toast(getContext(), Ui.err(e)); });
    }

    private void render(JSONArray rows) {
        Context c = getContext();
        list.removeAllViews();
        list.addView(Ui.text(c, "Appeals", R.style.Text_Bofo_Title));
        LinearLayout chips = Ui.row(c);
        android.widget.Button w = Ui.button(c, "Waiting", !decided); w.setOnClickListener(v -> { decided = false; load(); });
        android.widget.Button d = Ui.button(c, "Decided", decided); d.setOnClickListener(v -> { decided = true; load(); });
        chips.addView(w); chips.addView(d); list.addView(chips);
        int shown = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i); if (o == null) continue;
            boolean pending = "pending".equals(o.optString("status"));
            if (decided == pending) continue;                                        // Waiting shows pending only, Decided shows the rest
            shown++;
            LinearLayout card = Ui.card(c);
            String nm = Marks.str(o, "full_name");
            card.addView(Ui.text(c, nm.isEmpty() ? "Unnamed user" : nm, R.style.Text_Bofo_Section));
            card.addView(Ui.text(c, ("ban".equals(o.optString("kind")) ? "Ban appeal" : "Suspension appeal") + "  |  " + shortDate(o.optString("created_at")), R.style.Text_Bofo_Caption));
            String msg = o.optString("message");
            card.addView(Ui.text(c, msg.length() > 140 ? msg.substring(0, 140) + "..." : msg, R.style.Text_Bofo_Body));
            if (pending && o.optBoolean("stale")) card.addView(Ui.pill(c, "About an older decision", R.color.bofo_card_ink, R.color.bofo_warn_tint));
            if (pending && o.optBoolean("mine")) card.addView(Ui.pill(c, "You made the original decision", R.color.bofo_error, R.color.bofo_error_tint));
            if (!pending) card.addView(Ui.pill(c, "accepted".equals(o.optString("status")) ? "Accepted" : "Rejected",
                    "accepted".equals(o.optString("status")) ? R.color.bofo_success : R.color.bofo_card_ink, "accepted".equals(o.optString("status")) ? R.color.bofo_success_tint : R.color.bofo_warn_tint));
            card.setOnClickListener(v -> detail(o));
            list.addView(card);
        }
        if (shown == 0) list.addView(Ui.text(c, decided ? "No decided appeals yet." : "No appeals are waiting.", R.style.Text_Bofo_Caption));
    }

    private static String shortDate(String iso) { return iso == null || iso.length() < 16 ? "" : iso.substring(0, 16).replace('T', ' '); }

    private void detail(JSONObject o) {
        Context c = getContext();
        boolean pending = "pending".equals(o.optString("status"));
        boolean isBan = "ban".equals(o.optString("kind"));
        LinearLayout col = Ui.col(c); int p = Ui.dp(c, 20); col.setPadding(p, p, p, 0);
        col.addView(Ui.text(c, o.optString("message"), R.style.Text_Bofo_Body));
        if (!pending && !Marks.str(o, "decision_note").isEmpty()) col.addView(Ui.text(c, "Note: " + Marks.str(o, "decision_note"), R.style.Text_Bofo_Caption));
        EditText note = new EditText(c); note.setHint("Note for the record (optional)");
        note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(QueueRules.NOTE_MAX)});
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(act).setTitle(isBan ? "Ban appeal" : "Suspension appeal").setNegativeButton("Close", null);
        if (pending) {
            col.addView(note);
            if (o.optBoolean("mine")) col.addView(Ui.text(c, "You made the original decision, so someone else must decide this appeal.", R.style.Text_Bofo_Caption));
            if (o.optBoolean("stale")) col.addView(Ui.text(c, "This appeal is about an older decision. You can reject it; the person can appeal the current one.", R.style.Text_Bofo_Caption));
            b.setNeutralButton("Reject", (d, w) -> decide(o, false, note.getText().toString()));
            if (!o.optBoolean("mine") && !o.optBoolean("stale")) b.setPositiveButton(isBan ? "Accept and lift the ban" : "Accept and end the suspension", (d, w) -> {
                if (isBan && !access.can(PermissionRules.UNBAN)) { Ui.toast(c, "Lifting a ban needs the Owner, or a lent \"Lift a ban\" permission."); return; }
                Reauth.ask(act, "Accept appeal", () -> decide(o, true, note.getText().toString()), null);
            });
        }
        b.setView(col).show();
    }

    private void decide(JSONObject o, boolean accept, String note) {
        if (busy) return; busy = true;
        final String n = note.trim();
        Async.run(() -> {
            JSONObject a = new JSONObject().put("id_", o.optString("id")).put("accept", accept);
            if (!n.isEmpty()) a.put("note_", n);
            return Api.get().rpc("admin_appeal_decide", a);
        }, r -> { busy = false; Ui.toast(getContext(), accept ? "Appeal accepted." : "Appeal rejected."); load(); },
           e -> { busy = false; Ui.toast(getContext(), Ui.err(e)); load(); });
    }
}
