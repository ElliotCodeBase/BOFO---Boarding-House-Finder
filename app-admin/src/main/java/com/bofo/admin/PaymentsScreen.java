package com.bofo.admin;

import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.bofo.shared.Api;
import com.bofo.shared.Async;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Payments check (payments_view, owner only): read-only list of the latest 100 boost payments. Refunds stay in the old app until Stage 5 (a money action needs its own careful screen). */
final class PaymentsScreen extends FrameLayout {
    PaymentsScreen(MainActivity a) {
        super(a);
        LinearLayout list = Ui.col(a); int p = Ui.dp(a, 16); list.setPadding(p, p, p, p);
        addView(MainActivity.scroll(a, list), new LayoutParams(-1, -1));
        list.addView(Ui.text(a, "Payments", R.style.Text_Bofo_Title));
        Async.run(() -> {
            JSONArray rows = Api.get().select("payments", "order=created_at.desc&limit=100&select=id,owner_id,plan_id,amount_php,status,created_at,reference_number");
            StringBuilder in = new StringBuilder();
            for (int i = 0; i < rows.length(); i++) in.append(i == 0 ? "" : ",").append(rows.getJSONObject(i).getString("owner_id"));
            Map<String, String> names = new HashMap<>();
            if (in.length() > 0) { JSONArray u = Api.get().select("users", "id=in.(" + in + ")&select=id,full_name"); for (int i = 0; i < u.length(); i++) names.put(u.getJSONObject(i).getString("id"), u.getJSONObject(i).optString("full_name", "")); }
            return new Object[]{rows, names};
        }, r -> {
            JSONArray rows = (JSONArray) r[0]; Map<String, String> names = (Map<String, String>) r[1];
            if (rows.length() == 0) { list.addView(Ui.text(a, "No payments yet.", R.style.Text_Bofo_Caption)); return; }
            for (int i = 0; i < rows.length(); i++) {
                JSONObject o = rows.optJSONObject(i); if (o == null) continue;
                String st = o.optString("status"); boolean good = "paid".equals(st), bad = "failed".equals(st);
                LinearLayout c = Ui.card(a);
                String nm = names.get(o.optString("owner_id"));
                c.addView(Ui.text(a, (nm == null || nm.isEmpty() ? "Unnamed owner" : nm) + "  |  PHP " + String.format(Locale.US, "%,.2f", o.optDouble("amount_php", 0)), R.style.Text_Bofo_Section));
                String t = o.optString("created_at", "");
                c.addView(Ui.text(a, o.optString("plan_id") + "  |  " + t.replace('T', ' ').substring(0, Math.min(16, t.length())) + "  |  ref " + o.optString("reference_number", "-"), R.style.Text_Bofo_Caption));
                c.addView(Ui.pill(a, st, good ? R.color.bofo_success : bad ? R.color.bofo_error : R.color.bofo_card_ink, good ? R.color.bofo_success_tint : bad ? R.color.bofo_error_tint : R.color.bofo_warn_tint));
                list.addView(c);
            }
        }, e -> Ui.toast(a, Ui.err(e)));
    }
}
