package com.bofo.admin;

import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.bofo.shared.Api;
import com.bofo.shared.Async;
import com.bofo.shared.CsvRules;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CSV export (csv_export, owner only). Same columns as the old app: id, date, reason or amount, status. No names, places or details.
 * Order matters: fingerprint / screen lock -> read rows -> admin_log_export (audit log, server checks csv_export) -> only then write the file.
 * If the log call fails, nothing is exported.
 */
final class ExportScreen extends FrameLayout {
    private static final int MAX_ROWS = 1000;
    private final MainActivity act; private final LinearLayout list; private boolean busy;

    ExportScreen(MainActivity a) {
        super(a); act = a;
        list = Ui.col(a); int p = Ui.dp(a, 16); list.setPadding(p, p, p, p);
        addView(MainActivity.scroll(a, list), new LayoutParams(-1, -1));
        list.addView(Ui.text(a, "Export CSV", R.style.Text_Bofo_Title));
        list.addView(Ui.text(a, "Up to " + MAX_ROWS + " newest rows. Columns: id, date, reason or amount, status. Every export is written to the audit log.", R.style.Text_Bofo_Caption));
        list.addView(Ui.space(a, 8));
        list.addView(entry("Reports", "reports"));
        list.addView(entry("Payments", "payments"));
    }

    private LinearLayout entry(String label, String kind) {
        LinearLayout c = Ui.card(act); c.addView(Ui.text(act, label, R.style.Text_Bofo_Section));
        Ui.asButton(c);
        c.setOnClickListener(v -> { if (!busy) Reauth.ask(act, "Export " + label.toLowerCase(Locale.ROOT), () -> run(kind), null); });
        return c;
    }

    private void run(String kind) {
        busy = true;
        Async.run(() -> {
            boolean reports = "reports".equals(kind);
            JSONArray a = reports
                    ? Api.get().select("reports_admin_v", "order=created_at.desc&limit=" + MAX_ROWS + "&select=id,reason,status,created_at")
                    : Api.get().select("payments", "order=created_at.desc&limit=" + MAX_ROWS + "&select=id,amount_php,status,created_at");
            List<String[]> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.getJSONObject(i);
                String d = x.optString("created_at", ""); d = d.length() >= 10 ? d.substring(0, 10) : d;
                out.add(new String[]{x.optString("id"), d, reports ? x.optString("reason") : String.format(Locale.ROOT, "%.2f", x.optDouble("amount_php", 0)), x.optString("status")});
            }
            if (out.isEmpty()) return null;
            Api.get().rpc("admin_log_export", new JSONObject().put("kind", kind).put("row_count", out.size()));   // throws (no file) if not allowed or not logged
            return CsvRules.document(reports ? new String[]{"id", "date", "reason", "status"} : new String[]{"id", "date", "amount_php", "status"}, out);
        }, csv -> {
            busy = false;
            if (csv == null) { Ui.toast(act, "Nothing to export."); return; }
            try { CsvShare.share(act, kind + ".csv", csv); } catch (Exception e) { Ui.toast(act, "Could not create the file."); }
        }, e -> { busy = false; Ui.toast(act, Ui.err(e)); });
    }
}
