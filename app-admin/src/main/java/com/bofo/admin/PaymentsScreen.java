package com.bofo.admin;

import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.bofo.shared.Api;
import com.bofo.shared.Async;
import com.bofo.shared.PermissionRules;
import com.bofo.shared.RefundRules;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Payments (payments_view, owner only): the latest 100 boost payments. With payments_manage a PAID payment can be marked refunded (tap it):
 * reason + two confirmations + fingerprint / screen lock, then admin_refund_boost. That only does the bookkeeping and ends the boost; the money is
 * returned in the PayMongo dashboard (the dialog says so).
 */
final class PaymentsScreen extends FrameLayout {
    private final MainActivity act; private final Access access; private final LinearLayout list;
    private boolean busy;

    PaymentsScreen(MainActivity a, Access access) {
        super(a); act = a; this.access = access;
        list = Ui.col(a); int p = Ui.dp(a, 16); list.setPadding(p, p, p, p);
        addView(MainActivity.scroll(a, list), new LayoutParams(-1, -1));
        load();
    }

    private void load() {
        list.removeAllViews();
        list.addView(Ui.text(act, "Payments", R.style.Text_Bofo_Title));
        Async.run(() -> {
            JSONArray rows = Api.get().select("payments", "order=created_at.desc&limit=100&select=id,owner_id,plan_id,amount_php,status,created_at,reference_number");
            StringBuilder in = new StringBuilder();
            for (int i = 0; i < rows.length(); i++) in.append(i == 0 ? "" : ",").append(rows.getJSONObject(i).getString("owner_id"));
            Map<String, String> names = new HashMap<>();
            if (in.length() > 0) { JSONArray u = Api.get().select("users", "id=in.(" + in + ")&select=id,full_name"); for (int i = 0; i < u.length(); i++) names.put(u.getJSONObject(i).getString("id"), u.getJSONObject(i).optString("full_name", "")); }
            return new Object[]{rows, names};
        }, r -> render((JSONArray) r[0], (Map<String, String>) r[1]), e -> Ui.toast(act, Ui.err(e)));
    }

    private void render(JSONArray rows, Map<String, String> names) {
        if (rows.length() == 0) { list.addView(Ui.text(act, "No payments yet.", R.style.Text_Bofo_Caption)); return; }
        boolean manage = access.can(PermissionRules.PAYMENTS_MANAGE);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i); if (o == null) continue;
            String st = o.optString("status"); boolean good = "paid".equals(st), bad = "failed".equals(st);
            LinearLayout c = Ui.card(act);
            String nm = names.get(o.optString("owner_id"));
            String who = nm == null || nm.isEmpty() ? "Unnamed owner" : nm;
            String amount = "PHP " + String.format(Locale.US, "%,.2f", o.optDouble("amount_php", 0));
            c.addView(Ui.text(act, who + "  |  " + amount, R.style.Text_Bofo_Section));
            String t = o.optString("created_at", "");
            c.addView(Ui.text(act, o.optString("plan_id") + "  |  " + t.replace('T', ' ').substring(0, Math.min(16, t.length())) + "  |  ref " + o.optString("reference_number", "-"), R.style.Text_Bofo_Caption));
            c.addView(Ui.pill(act, st, good ? R.color.bofo_success : bad ? R.color.bofo_error : R.color.bofo_card_ink, good ? R.color.bofo_success_tint : bad ? R.color.bofo_error_tint : R.color.bofo_warn_tint));
            if (manage && RefundRules.canRefund(st)) {
                c.addView(Ui.text(act, "Tap to refund", R.style.Text_Bofo_Caption));
                final String id = o.optString("id");
                c.setOnClickListener(v -> askReason(id, who, amount));
            }
            list.addView(c);
        }
    }

    private void askReason(String id, String who, String amount) {
        if (busy) return;
        LinearLayout box = Ui.col(act); int p = Ui.dp(act, 20); box.setPadding(p, p / 2, p, 0);
        box.addView(Ui.text(act, "This marks the payment as refunded and ends the boost now. It does NOT send the money back: do that in the PayMongo dashboard.", R.style.Text_Bofo_Caption));
        EditText why = new EditText(act); why.setHint("Reason (required)"); why.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(RefundRules.MAX_REASON)});
        box.addView(why);
        new MaterialAlertDialogBuilder(act).setTitle("Refund " + amount + "?").setView(box).setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (d, w) -> {
                    String reason = RefundRules.cleanReason(why.getText().toString());
                    if (!RefundRules.validReason(reason)) { Ui.toast(act, "Write a reason of at least " + RefundRules.MIN_REASON + " characters."); return; }
                    confirm(id, who, amount, reason);
                }).show();
    }

    private void confirm(String id, String who, String amount, String reason) {
        new MaterialAlertDialogBuilder(act).setTitle("Mark as refunded?")
                .setMessage(amount + " from " + who + ".\n\nThe boost ends now. This cannot be undone here.\n\nReason: " + reason)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Refund", (d, w) -> Reauth.ask(act, "Refund payment", () -> {
                    busy = true;
                    Async.run(() -> Api.get().rpc("admin_refund_boost", new JSONObject().put("pid", id).put("reason", reason)),
                            ok -> { busy = false; Ui.toast(act, "Marked as refunded. Now refund the money in PayMongo."); load(); },
                            e -> { busy = false; Ui.toast(act, Ui.err(e)); });
                }, null)).show();
    }
}
