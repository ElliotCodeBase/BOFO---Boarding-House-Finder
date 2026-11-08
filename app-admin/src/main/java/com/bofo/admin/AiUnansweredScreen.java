package com.bofo.admin;

import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.bofo.shared.AiUnansweredRules;
import com.bofo.shared.AiUnansweredRules.Reason;
import com.bofo.shared.AiUnansweredRules.Row;
import com.bofo.shared.Api;
import com.bofo.shared.Async;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** "Needs attention" (ai_answer): the newest 50 questions BOFO could not answer well, read-only. The server policy ai_unanswered_admin_read is the real gate. Text is shown only if the server stored it. */
final class AiUnansweredScreen extends FrameLayout {
    private final LinearLayout list; private Reason filter = Reason.ALL;

    AiUnansweredScreen(MainActivity a) {
        super(a);
        list = Ui.col(a); int p = Ui.dp(a, 16); list.setPadding(p, p, p, p);
        addView(MainActivity.scroll(a, list), new LayoutParams(-1, -1));
        load();
    }

    private void load() {
        Async.run(() -> {
            JSONArray a = Api.get().select("ai_unanswered", AiUnansweredRules.query(filter));
            List<Row> out = new ArrayList<>(a.length());
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Row(o.optString("id", ""), o.optString("created_at", ""), o.optString("reason", ""),
                        o.isNull("intent") ? "" : o.optString("intent", ""), o.isNull("lang") ? "" : o.optString("lang", ""),
                        o.isNull("message") ? "" : o.optString("message", "")));
            }
            return out;
        }, this::render, e -> Ui.toast(getContext(), Ui.err(e)));
    }

    private static String label(Reason r) {
        switch (r) {
            case DEGRADED: return "AI was down";
            case CLARIFY: return "Needed more detail";
            case NOT_FOUND: return "Nothing found";
            case OFF_TOPIC: return "Off topic";
            case THUMBS_DOWN: return "Thumbs down";
            default: return "All";
        }
    }

    private void render(List<Row> loaded) {
        android.content.Context c = getContext();
        List<Row> rows = AiUnansweredRules.known(loaded);
        list.removeAllViews();
        list.addView(Ui.text(c, "Needs attention", R.style.Text_Bofo_Title));
        list.addView(Ui.text(c, "Questions BOFO could not answer well. Newest " + AiUnansweredRules.LIMIT + ".", R.style.Text_Bofo_Caption));
        Map<Reason, Integer> counts = AiUnansweredRules.counts(rows);
        LinearLayout chips = Ui.row(c); chips.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c); hs.setHorizontalScrollBarEnabled(false); hs.addView(chips);
        for (Reason r : Reason.values()) {
            String n = r == Reason.ALL || filter != Reason.ALL ? "" : " (" + counts.get(r) + ")";
            android.widget.Button b = Ui.chip(c, label(r) + n, r == filter);
            b.setOnClickListener(v -> { filter = r; load(); });
            chips.addView(b);
        }
        list.addView(hs);
        if (rows.isEmpty()) { list.addView(Ui.text(c, "Nothing here. Good news.", R.style.Text_Bofo_Caption)); return; }
        for (Row r : rows) {
            LinearLayout card = Ui.card(c);
            card.addView(Ui.text(c, r.hasText() ? r.message() : "(text not stored)", r.hasText() ? R.style.Text_Bofo_Body : R.style.Text_Bofo_Caption));
            String t = r.createdAt();
            String meta = label(r.kind()) + "  |  " + t.replace('T', ' ').substring(0, Math.min(16, t.length()))
                    + (r.intent().isEmpty() ? "" : "  |  " + r.intent()) + (r.lang().isEmpty() ? "" : "  |  " + r.lang());
            card.addView(Ui.text(c, meta, R.style.Text_Bofo_Caption));
            list.addView(card);
        }
    }
}
