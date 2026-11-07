package com.bofo.admin;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.bofo.shared.Api;
import com.bofo.shared.Async;
import com.bofo.shared.PermissionRules;
import com.bofo.shared.QueueRules;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Team (Owner only, members_manage): invite, role, per-person toggles, suspend, remove, log out all devices. Every change is logged by the server. */
final class TeamScreen extends FrameLayout {
    private static final String[] ROLES = {"admin", "moderator", "support"};
    private final MainActivity act; private final LinearLayout list;

    TeamScreen(MainActivity a) {
        super(a); act = a;
        list = Ui.col(a); int p = Ui.dp(a, 16); list.setPadding(p, p, p, p);
        addView(MainActivity.scroll(a, list), new LayoutParams(-1, -1));
        load();
    }

    private void load() { Async.run(() -> new JSONArray(Api.get().rpc("admin_members_list", new JSONObject())), this::render, e -> Ui.toast(getContext(), Ui.err(e))); }

    private void render(JSONArray rows) {
        Context c = getContext();
        list.removeAllViews();
        list.addView(Ui.text(c, "Team", R.style.Text_Bofo_Title));
        android.widget.Button inv = Ui.button(c, "Invite someone", true); inv.setOnClickListener(v -> invite()); list.addView(inv);
        android.widget.Button lent = Ui.button(c, "Lent right now", false); lent.setOnClickListener(v -> lentNow()); list.addView(lent);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i); if (o == null) continue;
            LinearLayout card = Ui.card(c);
            String nm = o.isNull("full_name") ? "" : o.optString("full_name");
            card.addView(Ui.text(c, nm.isEmpty() ? o.optString("email") : nm, R.style.Text_Bofo_Section));
            card.addView(Ui.text(c, o.optString("email"), R.style.Text_Bofo_Caption));
            String la = o.isNull("last_active_at") ? "never" : o.optString("last_active_at").replace('T', ' ').substring(0, 16);
            card.addView(Ui.text(c, "Role: " + o.optString("role") + "  |  last active " + la + "  |  " + o.optInt("actions") + " actions", R.style.Text_Bofo_Caption));
            boolean active = "active".equals(o.optString("status"));
            card.addView(Ui.pill(c, o.optString("status"), active ? R.color.bofo_success : R.color.bofo_error, active ? R.color.bofo_success_tint : R.color.bofo_error_tint));
            if (!"owner".equals(o.optString("role"))) card.setOnClickListener(v -> manage(o));           // owners cannot be changed (server rule)
            list.addView(card);
        }
    }

    private void invite() {
        Context c = getContext();
        LinearLayout box = Ui.col(c); int p = Ui.dp(c, 20); box.setPadding(p, p, p, 0);
        EditText email = new EditText(c); email.setHint("Google e-mail"); email.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS); box.addView(email);
        EditText days = new EditText(c); days.setHint("Access ends after N days (optional)"); days.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); box.addView(days);
        final int[] role = {1};
        new MaterialAlertDialogBuilder(act).setTitle("Invite").setView(box).setSingleChoiceItems(ROLES, 1, (d, i) -> role[0] = i)
                .setNegativeButton("Cancel", null).setPositiveButton("Create invite", (d, w) -> {
                    String e = email.getText().toString().trim().toLowerCase();
                    Integer n = days.getText().toString().trim().isEmpty() ? null : Integer.valueOf(days.getText().toString().trim());
                    Reauth.ask(act, "Invite " + e, () -> Async.run(() -> {
                        JSONObject a = new JSONObject().put("email_", e).put("role_", ROLES[role[0]]);
                        if (n != null) a.put("member_days", n);
                        return new JSONObject(Api.get().rpc("admin_invite_create", a)).getString("token");
                    }, tok -> showToken(e, tok), ex -> Ui.toast(c, Ui.err(ex))), null);
                }).show();
    }

    /** The code is shown ONCE (the server keeps only a hash). */
    private void showToken(String email, String token) {
        new MaterialAlertDialogBuilder(act).setTitle("Invite code").setMessage("Send this code to " + email + ". It works for 48 hours, once, and only for that Google account. It will not be shown again.\n\n" + token)
                .setPositiveButton("Copy", (d, w) -> { ((ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("invite", token)); })
                .setNegativeButton("Close", null).show();
        load();
    }

    private void manage(JSONObject o) {
        final String uid = o.optString("user_id"); boolean active = "active".equals(o.optString("status"));
        String[] items = {"Change role", "Permission toggles", active ? "Suspend" : "Reactivate", "Recent sign-ins", "Lend a permission for 1 hour", "Log out all devices", "Remove from team"};
        new MaterialAlertDialogBuilder(act).setTitle(o.optString("email")).setItems(items, (d, i) -> {
            if (i == 0) pickRole(uid, o.optString("role"));
            else if (i == 1) toggles(o);
            else if (i == 2) confirm(active ? "Suspend this person?" : "Reactivate this person?", () -> call("admin_member_suspend", Ui.json("uid", uid, "on_", active)));
            else if (i == 3) sessions(uid, o.optString("email"));
            else if (i == 4) lend(uid, o.optString("email"));
            else if (i == 5) confirm("Log this person out of every device?", () -> call("admin_member_logout_all", Ui.json("uid", uid)));
            else confirm("Remove from the team? They lose all admin access.", () -> call("admin_member_remove", Ui.json("uid", uid)));
        }).setNegativeButton("Close", null).show();
    }

    /* ---------- Stage 4i: recent sign-ins and break-glass ---------- */
    /** Phone model and time of each app start (written by admin_touch_session). A stolen phone shows up here; \"Log out all devices\" ends every session. */
    private void sessions(String uid, String email) {
        Async.run(() -> new JSONArray(Api.get().rpc("admin_member_sessions", Ui.json("uid", uid))), rows -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.optJSONObject(i); if (r == null) continue;
                String dev = Marks.str(r, "device"); String at = Marks.str(r, "started_at");
                sb.append(dev.isEmpty() ? "Unknown phone" : dev).append("  |  ").append(at.length() >= 16 ? at.substring(0, 16).replace('T', ' ') + " UTC" : at).append('\n');
            }
            new MaterialAlertDialogBuilder(act).setTitle("Recent sign-ins: " + email).setMessage(sb.length() == 0 ? "No sign-ins recorded yet." : sb.toString())
                    .setNeutralButton("Log out all devices", (d, w) -> confirm("Log this person out of every device?", () -> call("admin_member_logout_all", Ui.json("uid", uid))))
                    .setNegativeButton("Close", null).show();
        }, e -> Ui.toast(getContext(), Ui.err(e)));
    }

    /** The owner lends ONE permission for 1 hour (ban, lift a ban, take down, long suspension, payments, CSV). Reason required, audit-logged, ends by itself. */
    private void lend(String uid, String email) {
        final int[] pick = {-1};
        new MaterialAlertDialogBuilder(act).setTitle("Lend to " + email + " for 1 hour").setSingleChoiceItems(QueueRules.LEND_LABELS, -1, (d, i) -> pick[0] = i)
                .setNegativeButton("Cancel", null).setPositiveButton("Next", (d, w) -> {
                    if (pick[0] < 0) { Ui.toast(getContext(), "Pick one permission."); return; }
                    final String key = QueueRules.LEND_KEYS[pick[0]];
                    EditText why = new EditText(getContext()); why.setHint("Why? (5+ characters, kept in the log)");
                    why.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(QueueRules.NOTE_MAX)});
                    LinearLayout box = Ui.col(getContext()); int pd = Ui.dp(getContext(), 20); box.setPadding(pd, 0, pd, 0); box.addView(why);
                    new MaterialAlertDialogBuilder(act).setTitle(QueueRules.lendLabel(key)).setMessage("It ends after " + QueueRules.BREAK_GLASS_MINUTES + " minutes, or earlier under Lent right now.").setView(box)
                            .setNegativeButton("Cancel", null).setPositiveButton("Lend", (dd, ww) -> {
                                String r = why.getText().toString().trim();
                                if (!QueueRules.noteOk(r)) { Ui.toast(getContext(), "Write a reason of at least 5 characters."); return; }
                                Reauth.ask(act, "Lend " + QueueRules.lendLabel(key), () -> Async.run(
                                        () -> Api.get().rpc("admin_break_glass_grant", Ui.json("uid", uid, "perm", key, "reason_", r)),
                                        ok -> Ui.toast(getContext(), "Lent for " + QueueRules.BREAK_GLASS_MINUTES + " minutes."), e -> Ui.toast(getContext(), Ui.err(e))), null);
                            }).show();
                }).show();
    }

    private void lentNow() {
        Async.run(() -> new JSONArray(Api.get().rpc("admin_break_glass_list", new JSONObject())), rows -> {
            if (rows.length() == 0) { Ui.toast(getContext(), "Nothing is lent right now."); return; }
            final String[] labels = new String[rows.length()]; final String[] ids = new String[rows.length()];
            long now = System.currentTimeMillis();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.optJSONObject(i); if (r == null) { labels[i] = ""; ids[i] = ""; continue; }
                ids[i] = r.optString("id");
                labels[i] = Marks.str(r, "email") + ": " + QueueRules.lendLabel(r.optString("permission")) + " (" + QueueRules.minutesLeft(r.optString("expires_at"), now) + " min left)";
            }
            new MaterialAlertDialogBuilder(act).setTitle("Lent right now (tap to end it)").setItems(labels, (d, i) ->
                    confirm("End this now?", () -> call("admin_break_glass_end", Ui.json("gid", ids[i])))).setNegativeButton("Close", null).show();
        }, e -> Ui.toast(getContext(), Ui.err(e)));
    }

    private void pickRole(String uid, String current) {
        final int[] r = {-1};
        new MaterialAlertDialogBuilder(act).setTitle("Role").setSingleChoiceItems(ROLES, java.util.Arrays.asList(ROLES).indexOf(current), (d, i) -> r[0] = i)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", (d, w) -> { if (r[0] >= 0) call("admin_member_update", Ui.json("uid", uid, "role_", ROLES[r[0]])); }).show();
    }

    /** Per-person extras on top of the role. Owner-only permissions are not offered (the server refuses them too). */
    private void toggles(JSONObject o) {
        final String uid = o.optString("user_id");
        List<String> keys = new ArrayList<>();
        for (String k : PermissionRules.ALL) if (!PermissionRules.OWNER_ONLY.contains(k) && !PermissionRules.LEGACY_ADMIN.equals(k)) keys.add(k);
        String role = o.optString("role");
        List<String> have = new ArrayList<>(); JSONArray g = o.optJSONArray("extra_grant"), dn = o.optJSONArray("extra_deny");
        for (String k : keys) {
            boolean on = PermissionRules.preset(role).contains(k) || contains(g, k);
            if (contains(dn, k)) on = false;
            if (on) have.add(k);
        }
        boolean[] checked = new boolean[keys.size()];
        for (int i = 0; i < checked.length; i++) checked[i] = have.contains(keys.get(i));
        String[] labels = keys.toArray(new String[0]);
        new MaterialAlertDialogBuilder(act).setTitle("Can do (" + role + ")").setMultiChoiceItems(labels, checked, (d, i, b) -> checked[i] = b)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", (d, w) -> {
                    JSONArray grant = new JSONArray(), deny = new JSONArray();
                    for (int i = 0; i < keys.size(); i++) {
                        boolean inPreset = PermissionRules.preset(role).contains(keys.get(i));
                        if (checked[i] && !inPreset) grant.put(keys.get(i));
                        if (!checked[i] && inPreset) deny.put(keys.get(i));
                    }
                    call("admin_member_update", Ui.json("uid", uid, "extra_grant_", grant, "extra_deny_", deny));
                }).show();
    }

    private static boolean contains(JSONArray a, String k) { if (a != null) for (int i = 0; i < a.length(); i++) if (k.equals(a.optString(i))) return true; return false; }

    private void confirm(String msg, Runnable go) {
        new MaterialAlertDialogBuilder(act).setMessage(msg).setNegativeButton("Cancel", null).setPositiveButton("Yes", (d, w) -> Reauth.ask(act, "Confirm", go, null)).show();
    }

    private void call(String fn, JSONObject args) {
        Async.run(() -> Api.get().rpc(fn, args), r -> { Ui.toast(getContext(), "Saved."); load(); }, e -> Ui.toast(getContext(), Ui.err(e)));
    }
}
