package com.bofo.admin;

import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bofo.shared.Api;
import com.bofo.shared.Async;
import com.bofo.shared.PermissionRules;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports: waiting reports grouped per person, answered list, reports about one person, and the actions
 * (dismiss, suspend, ban, take down, undo ban). A role without the permission gets "Request approval" when it may ask; the server decides either way.
 */
final class ReportsScreen extends FrameLayout {
    private static final String COLS = "&select=id,reporter_id,reported_user_id,bh_id,reason,details,status,created_at,reviewed_at,action,reporter_name,reported_name,bh_name,reported_suspended_until";
    private static final String[] CHIPS = {"Waiting", "Answered", "All"};
    private static final String[] FILTER = {"status=eq.pending&", "status=neq.pending&", ""};
    private final MainActivity act;
    private final Access access;
    private final LinearLayout list;
    private int chip = 0;
    private String person, personName;
    private boolean busy;

    ReportsScreen(MainActivity a, Access access) {
        super(a); act = a; this.access = access;
        list = Ui.col(a); int p = Ui.dp(a, 16); list.setPadding(p, p, p, p);
        SwipeRefreshLayout sr = new SwipeRefreshLayout(a); sr.addView(MainActivity.scroll(a, list));
        sr.setOnRefreshListener(() -> { load(); sr.setRefreshing(false); });
        addView(sr, new LayoutParams(-1, -1));
        load();
    }

    private void load() {
        final String q = (person == null ? "" : "reported_user_id=eq." + person + "&") + FILTER[chip] + "order=created_at." + (chip == 0 ? "asc" : "desc") + "&limit=200" + COLS;
        Async.run(() -> Api.get().select("reports_admin_v", q), r -> render(r),
                e -> { if (e instanceof Api.ApiException x && x.status == 401) act.sessionEnded(); else Ui.toast(getContext(), Ui.err(e)); });
    }

    private void render(JSONArray rows) {
        android.content.Context c = getContext();
        list.removeAllViews();
        list.addView(Ui.text(c, person == null ? "Reports" : "Reports about " + personName, R.style.Text_Bofo_Title));
        LinearLayout chips = Ui.row(c);
        for (int i = 0; i < CHIPS.length; i++) {
            final int k = i;
            android.widget.Button b = Ui.button(c, CHIPS[i], chip == i);
            b.setOnClickListener(v -> { chip = k; load(); });
            chips.addView(b);
        }
        if (person != null) { android.widget.Button all = Ui.button(c, "Show everyone", false); all.setOnClickListener(v -> { person = null; load(); }); chips.addView(all); }
        list.addView(chips);
        if (rows.length() == 0) { list.addView(Ui.text(c, chip == 0 ? "No reports are waiting." : "Nothing here.", R.style.Text_Bofo_Caption)); return; }

        Map<String, List<JSONObject>> byPerson = new LinkedHashMap<>();                 // grouped reports: one card per reported person
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i); if (o == null) continue;
            boolean group = chip == 0 && "pending".equals(o.optString("status"));
            String key = group ? o.optString("reported_user_id") : o.optString("id");
            byPerson.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }
        for (List<JSONObject> g : byPerson.values()) list.addView(card(c, g));
    }

    private LinearLayout card(android.content.Context c, List<JSONObject> g) {
        JSONObject x = g.get(0);
        boolean waiting = "pending".equals(x.optString("status"));
        LinearLayout card = Ui.card(c);
        card.addView(Ui.text(c, name(x, "reported_name"), R.style.Text_Bofo_Section));
        card.addView(Ui.text(c, "Reason: " + x.optString("reason").replace('_', ' ') + (x.optString("bh_name", "").isEmpty() ? "" : "  |  " + x.optString("bh_name")), R.style.Text_Bofo_Caption));
        if (waiting && g.size() > 1) card.addView(Ui.pill(c, g.size() + " waiting reports about this person", R.color.bofo_error, R.color.bofo_error_tint));
        else card.addView(waiting ? Ui.pill(c, "Waiting", R.color.bofo_card_ink, R.color.bofo_warn_tint)
                : Ui.pill(c, "Answered: " + (x.isNull("action") ? x.optString("status") : x.optString("action")), R.color.bofo_success, R.color.bofo_success_tint));
        card.setOnClickListener(v -> detail(g));
        return card;
    }

    private static String name(JSONObject x, String k) { String n = x.optString(k, ""); return n.isEmpty() ? "Unnamed user" : n; }

    private void detail(List<JSONObject> g) {
        JSONObject x = g.get(0);
        android.content.Context c = getContext();
        LinearLayout col = Ui.col(c); int p = Ui.dp(c, 20); col.setPadding(p, p, p, 0);
        col.addView(Ui.text(c, "Reported by " + name(x, "reporter_name"), R.style.Text_Bofo_Caption));
        col.addView(Ui.text(c, x.isNull("details") || x.optString("details").isEmpty() ? "No details written." : x.optString("details"), R.style.Text_Bofo_Body));
        if (g.size() > 1) col.addView(Ui.text(c, g.size() + " waiting reports about this person. Your choice is applied to all of them.", R.style.Text_Bofo_Caption));
        androidx.appcompat.app.AlertDialog.Builder b = new MaterialAlertDialogBuilder(act).setTitle(name(x, "reported_name")).setView(col).setNeutralButton("All reports about them", (d, w) -> {
            person = x.optString("reported_user_id"); personName = name(x, "reported_name"); chip = 2; load(); }).setNegativeButton("Close", null);
        androidx.appcompat.app.AlertDialog dlg = b.create();
        if ("pending".equals(x.optString("status")) && access.can(PermissionRules.REPORTS_ACT)) {
            LinearLayout acts = Ui.col(c);
            acts.addView(action(c, "Dismiss", () -> { dlg.dismiss(); askReason(x, g, "dismiss", null, false); }));
            acts.addView(action(c, "Suspend", () -> { dlg.dismiss(); askDays(x, g); }));
            acts.addView(action(c, "Ban", () -> { dlg.dismiss(); askReason(x, g, "ban", null, true); }));
            if (!x.optString("bh_id", "").isEmpty() && !"null".equals(x.optString("bh_id"))) acts.addView(action(c, "Take down the place", () -> { dlg.dismiss(); askReason(x, g, "takedown", null, true); }));
            col.addView(acts);
        }
        dlg.show();
    }

    private android.widget.Button action(android.content.Context c, String label, Runnable r) { android.widget.Button b = Ui.button(c, label, false); b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); b.setOnClickListener(v -> r.run()); return b; }

    private void askDays(JSONObject x, List<JSONObject> g) {
        String[] opts = {"1 day", "3 days", "7 days", "14 days", "30 days"}; int[] days = {1, 3, 7, 14, 30};
        new MaterialAlertDialogBuilder(act).setTitle("Suspend for how long?").setItems(opts, (d, i) -> askReason(x, g, "suspend", days[i] * 24, true)).setNegativeButton("Cancel", null).show();
    }

    private void askReason(JSONObject x, List<JSONObject> g, String act0, Integer hrs, boolean required) {
        EditText in = new EditText(getContext()); in.setHint(required ? "Reason (required)" : "Note (optional)");
        in.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(300)});
        String key = keyFor(act0, hrs);
        boolean direct = key == null || access.can(key);
        boolean ask = !direct && PermissionRules.needsApproval(access.perms, key);
        if (!direct && !ask) { Ui.toast(getContext(), "Your role cannot do this."); return; }
        String title = (ask ? "Request approval: " : "") + act0 + " " + name(x, "reported_name");
        LinearLayout box = Ui.col(getContext()); int pd = Ui.dp(getContext(), 20); box.setPadding(pd, 0, pd, 0);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(getContext()); LinearLayout chips = Ui.row(getContext()); hs.addView(chips);
        for (String q : new String[]{"Scam", "Fake listing", "Harassment", "Inappropriate content", "Wrong information", "Repeat offence"}) {       // quick-pick reasons
            android.widget.Button cb = Ui.button(getContext(), q, false); cb.setOnClickListener(v -> in.setText(q)); chips.addView(cb);
        }
        box.addView(hs); box.addView(in);
        new MaterialAlertDialogBuilder(act).setTitle(title).setView(box).setNegativeButton("Cancel", null)
                .setPositiveButton(ask ? "Send request" : "Confirm", (d, w) -> {
                    String r = in.getText().toString().trim();
                    if (required && r.isEmpty()) { Ui.toast(getContext(), "A reason is required."); return; }
                    Runnable go = () -> perform(x, g, act0, hrs, r, ask);
                    if (ask || "dismiss".equals(act0)) go.run(); else Reauth.ask(act, "Confirm " + act0, go, null);       // sensitive actions ask for fingerprint / screen lock
                }).show();
    }

    private static String keyFor(String a, Integer hrs) {
        switch (a) { case "suspend": return PermissionRules.suspendKey(hrs == null ? 0 : hrs); case "ban": return PermissionRules.BAN_PERMANENT; case "takedown": return PermissionRules.LISTING_TAKEDOWN; default: return null; }
    }

    private void perform(JSONObject x, List<JSONObject> g, String a, Integer hrs, String reason, boolean requestOnly) {
        if (busy) return; busy = true;
        Async.run(() -> {
            int done = 0; boolean idBanned = false;
            for (JSONObject o : g) {
                String rid = o.optString("id");
                if (requestOnly) {
                    JSONObject pl = new JSONObject().put("rid", rid).put("reason", reason);
                    if (hrs != null) pl.put("hrs", hrs);
                    if ("takedown".equals(a)) pl.put("bh", o.optString("bh_id"));
                    String kind = "ban".equals(a) ? "report_ban" : "suspend".equals(a) ? "report_suspend" : "report_takedown";
                    Api.get().rpc("request_admin_approval", new JSONObject().put("kind_", kind).put("payload_", pl));
                } else if ("takedown".equals(a)) {
                    Api.get().rpc("admin_report_takedown", new JSONObject().put("rid", rid).put("bh", o.optString("bh_id")).put("reason", reason));
                } else {
                    JSONObject args = new JSONObject().put("rid", rid).put("act", a);
                    if (hrs != null) args.put("hrs", hrs);
                    if (!reason.isEmpty()) args.put(("dismiss".equals(a) ? "notes" : "reason"), reason);
                    JSONObject r = Api.get().rpcObject("admin_review_report", args);
                    idBanned |= r.optBoolean("id_banned", false);
                }
                done++;
            }
            return new Object[]{done, idBanned};
        }, r -> {
            busy = false;
            int n = (Integer) r[0];
            String msg = requestOnly ? "Approval requested for " + n + " report(s)." : "Done for " + n + " report(s).";
            if ("ban".equals(a) && !requestOnly) {
                msg = ((Boolean) r[1]) ? "Banned, and the ID number is blocked." : "Banned (no ID number was on file, so only the account is blocked).";
                Snackbar sb = Snackbar.make(this, msg, Snackbar.LENGTH_LONG);
                if (access.can(PermissionRules.UNBAN)) sb.setAction("Undo", v -> Async.run(() -> Api.get().rpc("admin_unban", new JSONObject().put("uid", x.optString("reported_user_id"))),
                        ok -> { Ui.toast(getContext(), "Ban undone."); load(); }, e -> Ui.toast(getContext(), Ui.err(e))));
                sb.show();
            } else Ui.toast(getContext(), msg);
            load();
        }, e -> { busy = false; Ui.toast(getContext(), Ui.err(e)); load(); });
    }
}
