package com.bofo.admin;

import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.bofo.shared.Api;
import com.bofo.shared.Async;

import org.json.JSONArray;
import org.json.JSONObject;

/** Activity log: "what I did" (audit_view_own) or everyone's actions (audit_view_all, owner). Read-only; the log is append-only on the server. */
final class ActivityScreen extends FrameLayout {
    ActivityScreen(MainActivity a, boolean all) {
        super(a);
        LinearLayout list = Ui.col(a); int p = Ui.dp(a, 16); list.setPadding(p, p, p, p);
        addView(MainActivity.scroll(a, list), new LayoutParams(-1, -1));
        list.addView(Ui.text(a, all ? "All admin activity" : "What I did", R.style.Text_Bofo_Title));
        Async.run(() -> new JSONArray(Api.get().rpc(all ? "admin_activity_all" : "admin_my_activity", new JSONObject().put(all ? "lim" : "lim", 100))), rows -> {
            if (rows.length() == 0) { list.addView(Ui.text(a, "Nothing yet.", R.style.Text_Bofo_Caption)); return; }
            for (int i = 0; i < rows.length(); i++) {
                JSONObject o = rows.optJSONObject(i); if (o == null) continue;
                LinearLayout c = Ui.card(a);
                c.addView(Ui.text(a, o.optString("action").replace('_', ' '), R.style.Text_Bofo_Section));
                String who = all ? o.optString("actor_email", "") + (o.optString("actor_role", "").isEmpty() ? "" : " (" + o.optString("actor_role") + ")") + "  |  " : "";
                String t = o.optString("created_at", ""); 
                c.addView(Ui.text(a, who + t.replace('T', ' ').substring(0, Math.min(16, t.length())), R.style.Text_Bofo_Caption));
                if (!o.isNull("target_type")) c.addView(Ui.text(a, o.optString("target_type") + " " + o.optString("target_id", ""), R.style.Text_Bofo_Caption));
                list.addView(c);
            }
        }, e -> Ui.toast(a, Ui.err(e)));
    }
}
