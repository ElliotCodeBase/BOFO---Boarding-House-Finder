package com.bofo.admin;

import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bofo.shared.Api;
import com.bofo.shared.Async;
import com.bofo.shared.PermissionRules;

import org.json.JSONArray;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Home: grouped cards (Needs you / Safety / Last 7 days). A card is shown only when the role can read it; a number that cannot be read shows "-". */
final class HomeScreen extends FrameLayout {
    private static final int CAP = 1000;
    private final Access access;
    private final LinearLayout list;

    HomeScreen(MainActivity a, Access access) {
        super(a);
        this.access = access;
        list = Ui.col(a); int p = Ui.dp(a, 16); list.setPadding(p, p, p, p);
        android.widget.ScrollView sv = MainActivity.scroll(a, list);
        SwipeRefreshLayout sr = new SwipeRefreshLayout(a); sr.addView(sv);
        sr.setOnRefreshListener(() -> { load(); sr.setRefreshing(false); });
        addView(sr, new LayoutParams(-1, -1));
        load();
    }

    private static int count(String table, String q) {
        try { JSONArray r = Api.get().select(table, q + "&select=id&limit=" + CAP); return r.length(); } catch (Exception e) { return -1; }
    }
    private static String n(int v) { return v < 0 ? "-" : (v >= CAP ? CAP + "+" : String.valueOf(v)); }

    private void load() {
        Async.run(() -> {
            int[] v = {-2, -2, -2, -2, -2, -2, -2, -2, -2};
            Instant now = Instant.now(), w1 = now.minus(7, ChronoUnit.DAYS), w0 = now.minus(14, ChronoUnit.DAYS);
            String[][] q = new String[9][];                                  // {table, query} per number; null = this role cannot read it
            if (access.can(PermissionRules.ID_REVIEW)) q[0] = new String[]{"owner_verifications", "status=eq.pending"};
            if (access.can(PermissionRules.REPORTS_VIEW)) q[1] = new String[]{"reports", "status=eq.pending"};
            if (access.can(PermissionRules.REPORTS_VIEW)) q[2] = new String[]{"users", "suspended_until=gt." + now};
            if (access.can(PermissionRules.BANNED_VIEW)) q[3] = new String[]{"users", "banned_at=not.is.null"};
            if (access.can(PermissionRules.REPORTS_VIEW)) q[4] = new String[]{"bh_cards", "status=eq.taken_down"};
            q[5] = new String[]{"users", "created_at=gte." + w1};
            q[6] = new String[]{"users", "created_at=gte." + w0 + "&created_at=lt." + w1};
            q[7] = new String[]{"bh_cards", "created_at=gte." + w1};
            if (access.can(PermissionRules.REPORTS_VIEW)) q[8] = new String[]{"reports_admin_v", "reviewed_at=gte." + w1 + "&status=neq.pending"};
            java.util.List<java.util.concurrent.Future<Integer>> fs = new java.util.ArrayList<>();
            for (String[] x : q) fs.add(x == null ? null : Async.fanout().submit(() -> count(x[0], x[1])));   // all at the same time
            for (int i = 0; i < 9; i++) if (fs.get(i) != null) v[i] = fs.get(i).get();
            return v;
        }, v -> render(v), e -> Ui.toast(getContext(), Ui.err(e)));
    }

    private void render(int[] v) {
        android.content.Context c = getContext();
        list.removeAllViews();
        list.addView(Ui.text(c, "Home", R.style.Text_Bofo_Title));
        list.addView(Ui.space(c, 10));
        if (v[0] != -2 || v[1] != -2) {
            list.addView(Ui.text(c, "Needs you", R.style.Text_Bofo_Section));
            if (v[0] != -2) list.addView(stat(c, "ID requests waiting", v[0], v[0] > 0));
            if (v[1] != -2) list.addView(stat(c, "Reports waiting", v[1], v[1] > 0));
        }
        if (v[2] != -2 || v[3] != -2 || v[4] != -2) {
            list.addView(Ui.text(c, "Safety", R.style.Text_Bofo_Section));
            if (v[2] != -2) list.addView(stat(c, "Suspended now", v[2], false));
            if (v[3] != -2) list.addView(stat(c, "Banned users", v[3], false));
            if (v[4] != -2) list.addView(stat(c, "Places taken down", v[4], false));
        }
        list.addView(Ui.text(c, "Last 7 days", R.style.Text_Bofo_Section));
        list.addView(stat(c, "New users" + (v[6] >= 0 && v[5] >= 0 ? "  (" + (v[5] - v[6] >= 0 ? "+" : "") + (v[5] - v[6]) + " vs week before)" : ""), v[5], false));
        list.addView(stat(c, "New places", v[7], false));
        if (v[8] != -2) list.addView(stat(c, "Reports answered", v[8], false));
    }

    private LinearLayout stat(android.content.Context c, String label, int value, boolean attention) {
        LinearLayout card = Ui.card(c);
        LinearLayout r = Ui.row(c);
        r.addView(Ui.text(c, label, R.style.Text_Bofo_Body), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(Ui.text(c, n(value), R.style.Text_Bofo_Title));
        card.addView(r);
        if (attention) card.addView(Ui.pill(c, "Needs a look", R.color.bofo_error, R.color.bofo_error_tint));     // words, not only colour
        return card;
    }
}
