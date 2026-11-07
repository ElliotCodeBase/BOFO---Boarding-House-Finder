package com.bofo.admin;

import android.content.Context;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bofo.shared.Api;
import com.bofo.shared.Async;
import com.bofo.shared.QueueRules;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Unusual activity (audit_view_all: Owner). Team members who did many sensitive actions lately: bans, suspensions, take-downs, rejected IDs,
 * unbans, refunds, strikes. Flagged at 10 or more in one hour (the same rule that sends the notification). Counts only, no details.
 */
final class UnusualScreen extends FrameLayout {
    private final MainActivity act;
    private final LinearLayout list;

    UnusualScreen(MainActivity a) {
        super(a); act = a;
        list = Ui.col(a); int p = Ui.dp(a, 16); list.setPadding(p, p, p, p);
        SwipeRefreshLayout sr = new SwipeRefreshLayout(a); sr.addView(MainActivity.scroll(a, list));
        sr.setOnRefreshListener(() -> { load(); sr.setRefreshing(false); });
        addView(sr, new LayoutParams(-1, -1));
        load();
    }

    private void load() {
        Async.run(() -> new JSONArray(Api.get().rpc("admin_unusual_activity", new JSONObject())), this::render,
                e -> { if (e instanceof Api.ApiException x && x.status == 401) act.sessionEnded(); else Ui.toast(getContext(), Ui.err(e)); });
    }

    private void render(JSONArray rows) {
        Context c = getContext();
        list.removeAllViews();
        list.addView(Ui.text(c, "Unusual activity", R.style.Text_Bofo_Title));
        list.addView(Ui.text(c, "Team members (not owners) with sensitive actions in the last 24 hours. Flagged at " + QueueRules.UNUSUAL_PER_HOUR + " or more in one hour.", R.style.Text_Bofo_Caption));
        if (rows.length() == 0) { list.addView(Ui.text(c, "Nothing to show. Quiet day.", R.style.Text_Bofo_Body)); return; }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i); if (o == null) continue;
            LinearLayout card = Ui.card(c);
            String em = Marks.str(o, "email");
            card.addView(Ui.text(c, em.isEmpty() ? "Unknown person" : em, R.style.Text_Bofo_Section));
            card.addView(Ui.text(c, "Role: " + Marks.str(o, "role") + "  |  " + QueueRules.unusualLine(o.optInt("n_1h"), o.optInt("n_24h")), R.style.Text_Bofo_Caption));
            boolean flagged = o.optBoolean("flagged");
            card.addView(flagged ? Ui.pill(c, "Flagged: check with them", R.color.bofo_error, R.color.bofo_error_tint) : Ui.pill(c, "Within normal range", R.color.bofo_success, R.color.bofo_success_tint));
            list.addView(card);
        }
    }
}
