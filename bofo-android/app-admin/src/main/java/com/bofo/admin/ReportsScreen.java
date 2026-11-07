package com.bofo.admin;

import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bofo.shared.Api;
import com.bofo.shared.Async;
import com.bofo.shared.PermissionRules;
import com.bofo.shared.QueueRules;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports: waiting reports grouped per person, answered list, reports about one person, and the actions
 * (dismiss, suspend, ban, take down, undo ban). A role without the permission gets "Request approval" when it may ask; the server decides either way.
 */
final class ReportsScreen extends FrameLayout {
    private static final String COLS = "&select=id,reporter_id,reported_user_id,bh_id,reason,details,status,created_at,reviewed_at,action,reporter_name,reported_name,bh_name,reported_suspended_until";
    private static final String[] CHIPS = {"Waiting", "Answered", "All", "Escalated"};
    private static final String[] FILTER = {"status=eq.pending&", "status=neq.pending&", "", "status=eq.pending&"};
    private final MainActivity act;
    private final Access access;
    private final LinearLayout list;
    private int chip = 0;
    private String person, personName;
    private boolean busy;
    private Map<String, JSONObject> marks = new HashMap<>();          // report id -> claim / escalation (Stage 4i)

    ReportsScreen(MainActivity a, Access access) {
        super(a); act = a; this.access = access;
        list = Ui.col(a); int p = Ui.dp(a, 16); list.setPadding(p, p, p, p);
        SwipeRefreshLayout sr = new SwipeRefreshLayout(a); sr.addView(MainActivity.scroll(a, list));
        sr.setOnRefreshListener(() -> { load(); sr.setRefreshing(false); });
        addView(sr, new LayoutParams(-1, -1));
        load();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        final String q = (person == null ? "" : "reported_user_id=eq." + person + "&") + FILTER[chip] + "order=created_at." + (chip == 0 ? "asc" : "desc") + "&limit=200" + COLS;
        Async.run(() -> { JSONArray rows = Api.get().select("reports_admin_v", q); return new Object[]{rows, Marks.list("report")}; },
                r -> { marks = (Map<String, JSONObject>) r[1]; render((JSONArray) r[0]); },
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
        JSONArray shown = new JSONArray();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i); if (o == null) continue;
            if (chip == 3 && !Marks.escalated(marks.get(o.optString("id")))) continue;           // "Escalated" chip: waiting reports with an open escalation
            shown.put(o);
        }
        rows = shown;
        if (rows.length() == 0) { list.addView(Ui.text(c, chip == 0 ? "No reports are waiting." : chip == 3 ? "Nothing is escalated." : "Nothing here.", R.style.Text_Bofo_Caption)); return; }

        Map<String, List<JSONObject>> byPerson = new LinkedHashMap<>();                 // grouped reports: one card per reported person
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i); if (o == null) continue;
            boolean group = (chip == 0 || chip == 3) && "pending".equals(o.optString("status"));
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
        if (waiting) {
            card.addView(Ui.waitPill(c, x.optString("created_at")));                      // oldest report of the group (the list is oldest first)
            if (g.size() > 1) card.addView(Ui.pill(c, g.size() + " waiting reports about this person", R.color.bofo_error, R.color.bofo_error_tint));
            if (markOf(g, true) != null) card.addView(Ui.pill(c, "Escalated", R.color.bofo_error, R.color.bofo_error_tint));
            JSONObject other = markOf(g, false);
            if (other != null) card.addView(Ui.text(c, QueueRules.claimLine(Marks.str(other, "claimed_by_name")), R.style.Text_Bofo_Caption));
        } else card.addView(Ui.pill(c, "Answered: " + (x.isNull("action") ? x.optString("status") : x.optString("action")), R.color.bofo_success, R.color.bofo_success_tint));
        card.setOnClickListener(v -> detail(g));
        return card;
    }

    /** The first escalated mark (escalated = true) or the first mark of a colleague who has it open (escalated = false) among a group's reports. */
    private JSONObject markOf(List<JSONObject> g, boolean escalated) {
        for (JSONObject o : g) {
            JSONObject m = marks.get(o.optString("id"));
            if (escalated ? Marks.escalated(m) : Marks.claimedByOther(m)) return m;
        }
        return null;
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
        final boolean waitingNow = "pending".equals(x.optString("status"));
        if (waitingNow) {
            final String firstId = x.optString("id");
            JSONObject esc = markOf(g, true);
            if (esc != null) col.addView(Ui.text(c, "Escalated: " + Marks.str(esc, "escalation_note"), R.style.Text_Bofo_Body));
            android.widget.TextView claimNote = Ui.text(c, "", R.style.Text_Bofo_Caption); claimNote.setVisibility(android.view.View.GONE);
            claimNote.setTextColor(c.getColor(R.color.bofo_error)); col.addView(claimNote);
            Async.run(() -> Marks.claim("report", firstId), r -> {                       // taking it for 30 minutes; a colleague who has it sees that
                if (!r.optBoolean("mine", false)) { claimNote.setText(QueueRules.claimLine(r.optString("by")) + ". They may be deciding it right now."); claimNote.setVisibility(android.view.View.VISIBLE); }
            }, e -> { /* heads-up only: never block the review */ });
            dlg.setOnDismissListener(d -> Async.run(() -> { Marks.release("report", firstId); return 1; }, ok -> load(), e -> { }));
        }
        if (waitingNow && access.can(PermissionRules.APPROVALS_DECIDE) && markOf(g, true) != null) {
            LinearLayout clr = Ui.col(c);
            clr.addView(action(c, "Clear the escalation", () -> { dlg.dismiss(); clearEscalation(g); }));
            col.addView(clr);
        }
        if (waitingNow && access.can(PermissionRules.REPORTS_ACT)) {
            LinearLayout acts = Ui.col(c);
            if (markOf(g, true) == null) acts.addView(action(c, "Escalate to a senior", () -> { dlg.dismiss(); escalate(g); }));
            if (!x.isNull("reported_user_id") && !x.optString("reported_user_id").isEmpty()) acts.addView(action(c, "Strike: next step", () -> { dlg.dismiss(); strike(x, g); }));
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

    /* ---------- Stage 4i: escalate, clear, strike ---------- */
    private void escalate(List<JSONObject> g) {
        EditText note = new EditText(getContext()); note.setHint("Why does a senior need to look? (5+ characters)");
        note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(QueueRules.NOTE_MAX)});
        LinearLayout box = Ui.col(getContext()); int pd = Ui.dp(getContext(), 20); box.setPadding(pd, 0, pd, 0); box.addView(note);
        new MaterialAlertDialogBuilder(act).setTitle("Escalate to a senior").setMessage("Owners and Admins get a notification (no names in it).").setView(box)
                .setNegativeButton("Cancel", null).setPositiveButton("Escalate", (d, w) -> {
                    String n = note.getText().toString().trim();
                    if (!QueueRules.noteOk(n)) { Ui.toast(getContext(), "Write a note of at least 5 characters."); return; }
                    Async.run(() -> { for (JSONObject o : g) Api.get().rpc("admin_escalate_report", Ui.json("rid", o.optString("id"), "note", n)); return 1; },
                            ok -> { Ui.toast(getContext(), "Escalated."); load(); }, e -> { Ui.toast(getContext(), Ui.err(e)); load(); });
                }).show();
    }

    private void clearEscalation(List<JSONObject> g) {
        Async.run(() -> { for (JSONObject o : g) Api.get().rpc("admin_clear_escalation", Ui.json("rid", o.optString("id"))); return 1; },
                ok -> { Ui.toast(getContext(), "Escalation cleared."); load(); }, e -> Ui.toast(getContext(), Ui.err(e)));
    }

    /** The server counts earlier strikes and picks the step; the app only shows it and asks for the reason. */
    private void strike(JSONObject x, List<JSONObject> g) {
        Async.run(() -> new JSONObject(Api.get().rpc("admin_user_strikes", Ui.json("uid", x.optString("reported_user_id")))), s -> {
            final int n = s.optInt("count", 0);
            final String step = s.optString("next", QueueRules.strikeStep(n));
            String msg = "Earlier strikes: " + n + ". The next step is " + QueueRules.strikeLabel(step) + "."
                    + (g.size() > 1 ? "\nThe other waiting reports about this person are marked as reviewed." : "");
            if (QueueRules.stepNeedsBanPermission(step) && !access.can(PermissionRules.BAN_PERMANENT)) {
                new MaterialAlertDialogBuilder(act).setTitle("Strike " + (n + 1)).setMessage(msg + "\n\nYour role cannot ban directly. Close this and use Ban, then Request approval.")
                        .setPositiveButton("OK", null).show();
                return;
            }
            EditText why = new EditText(getContext()); why.setHint("Reason (5+ characters)");
            why.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(QueueRules.NOTE_MAX)});
            LinearLayout box = Ui.col(getContext()); int pd = Ui.dp(getContext(), 20); box.setPadding(pd, 0, pd, 0); box.addView(why);
            new MaterialAlertDialogBuilder(act).setTitle("Strike " + (n + 1) + " for " + name(x, "reported_name")).setMessage(msg).setView(box)
                    .setNegativeButton("Cancel", null).setPositiveButton("Apply", (d, w) -> {
                        final String r = why.getText().toString().trim();
                        if (!QueueRules.noteOk(r)) { Ui.toast(getContext(), "Write a reason of at least 5 characters."); return; }
                        Reauth.ask(act, "Apply strike " + (n + 1), () -> applyStrike(g, r), null);
                    }).show();
        }, e -> Ui.toast(getContext(), Ui.err(e)));
    }

    private void applyStrike(List<JSONObject> g, String reason) {
        if (busy) return; busy = true;
        Async.run(() -> {
            JSONObject r = Api.get().rpcObject("admin_issue_strike", Ui.json("rid", g.get(0).optString("id"), "reason", reason));
            for (int i = 1; i < g.size(); i++) {                                                  // the person's other waiting reports are covered by this strike
                try { Api.get().rpc("admin_review_report", Ui.json("rid", g.get(i).optString("id"), "act", "reviewed", "notes", "Covered by a strike")); } catch (Exception ignored) { }
            }
            return r;
        }, r -> { busy = false; Ui.toast(getContext(), "Strike " + r.optInt("count") + " applied: " + QueueRules.strikeLabel(r.optString("step")) + "."); load(); },
           e -> { busy = false; Ui.toast(getContext(), Ui.err(e)); load(); });
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
