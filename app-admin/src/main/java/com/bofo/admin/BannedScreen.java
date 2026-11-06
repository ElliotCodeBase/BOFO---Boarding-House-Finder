package com.bofo.admin;

import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.bofo.shared.Api;
import com.bofo.shared.Async;
import com.bofo.shared.PermissionRules;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;

/** Banned and currently suspended users (banned_view). Unban needs `unban` (owner only) and asks for fingerprint / screen lock. */
final class BannedScreen extends FrameLayout {
    private final MainActivity act; private final Access access; private final LinearLayout list;

    BannedScreen(MainActivity a, Access access) {
        super(a); act = a; this.access = access;
        list = Ui.col(a); int p = Ui.dp(a, 16); list.setPadding(p, p, p, p);
        addView(MainActivity.scroll(a, list), new LayoutParams(-1, -1));
        load();
    }

    private void load() {
        Async.run(() -> new JSONArray[]{ new JSONArray(Api.get().rpc("admin_banned_list", new JSONObject())),
                Api.get().select("users", "suspended_until=gt." + Instant.now() + "&order=suspended_until.desc&limit=100&select=id,full_name,suspended_until") },
                r -> render(r[0], r[1]), e -> Ui.toast(getContext(), Ui.err(e)));
    }

    private void render(JSONArray banned, JSONArray susp) {
        android.content.Context c = getContext();
        list.removeAllViews();
        list.addView(Ui.text(c, "Banned users", R.style.Text_Bofo_Title));
        if (banned.length() == 0) list.addView(Ui.text(c, "Nobody is banned.", R.style.Text_Bofo_Caption));
        for (int i = 0; i < banned.length(); i++) {
            JSONObject o = banned.optJSONObject(i); if (o == null) continue;
            LinearLayout card = Ui.card(c);
            String nm = o.optString("full_name", ""); card.addView(Ui.text(c, nm.isEmpty() ? o.optString("email") : nm, R.style.Text_Bofo_Section));
            card.addView(Ui.text(c, "Reason: " + o.optString("ban_reason", "-"), R.style.Text_Bofo_Body));
            String t = o.optString("banned_at", ""); card.addView(Ui.text(c, "Banned " + t.replace('T', ' ').substring(0, Math.min(16, t.length())) + (o.optString("banned_by_name", "").isEmpty() ? "" : " by " + o.optString("banned_by_name"))
                    + (o.optBoolean("id_banned") ? "  |  ID number blocked" : ""), R.style.Text_Bofo_Caption));
            if (access.can(PermissionRules.UNBAN)) { android.widget.Button u = Ui.button(c, "Unban", false); u.setOnClickListener(v -> unban(o.optString("user_id"), nm)); card.addView(u); }
            list.addView(card);
        }
        list.addView(Ui.text(c, "Suspended now", R.style.Text_Bofo_Title));
        if (susp.length() == 0) list.addView(Ui.text(c, "Nobody is suspended.", R.style.Text_Bofo_Caption));
        for (int i = 0; i < susp.length(); i++) {
            JSONObject o = susp.optJSONObject(i); if (o == null) continue;
            LinearLayout card = Ui.card(c);
            card.addView(Ui.text(c, o.optString("full_name", "Unnamed user"), R.style.Text_Bofo_Section));
            String t = o.optString("suspended_until", ""); card.addView(Ui.text(c, "Until " + t.replace('T', ' ').substring(0, Math.min(16, t.length())), R.style.Text_Bofo_Caption));
            list.addView(card);
        }
    }

    private void unban(String uid, String name) {
        EditText note = new EditText(getContext()); note.setHint("Reason (required)");
        note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(300)});
        new MaterialAlertDialogBuilder(act).setTitle("Unban " + (name.isEmpty() ? "this user" : name)).setView(note).setNegativeButton("Cancel", null)
                .setPositiveButton("Unban", (d, w) -> {
                    String n = note.getText().toString().trim();
                    if (n.isEmpty()) { Ui.toast(getContext(), "A reason is required."); return; }
                    Reauth.ask(act, "Unban", () -> Async.run(() -> Api.get().rpc("admin_unban", new JSONObject().put("uid", uid).put("note", n)),
                            r -> { Ui.toast(getContext(), "Unbanned."); load(); }, e -> Ui.toast(getContext(), Ui.err(e))), null);
                }).show();
    }
}
