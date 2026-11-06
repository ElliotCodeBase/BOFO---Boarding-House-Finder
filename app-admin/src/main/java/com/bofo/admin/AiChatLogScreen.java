package com.bofo.admin;

import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bofo.shared.AiChatLogRules;
import com.bofo.shared.AiChatLogRules.Range;
import com.bofo.shared.AiChatLogRules.Row;
import com.bofo.shared.Api;
import com.bofo.shared.Async;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * AI chats (ai_log_view, Owner only): what people asked the BOFO assistant, newest first, 30 at a time, with a range filter and a search box.
 * Read-only. The server policy on ai_chat_log is the real gate; the log is only filled while the monitor flag is on (see AI settings).
 */
final class AiChatLogScreen extends FrameLayout {
    private final MainActivity act; private final LinearLayout items, chips; private final Button more; private final TextView empty; private final EditText box;
    private Range range = Range.WEEK; private String search = ""; private int shown; private boolean busy;

    AiChatLogScreen(MainActivity a) {
        super(a); act = a;
        LinearLayout col = Ui.col(a); int p = Ui.dp(a, 16); col.setPadding(p, p, p, p);
        addView(MainActivity.scroll(a, col), new LayoutParams(-1, -1));
        col.addView(Ui.text(a, "AI chats", R.style.Text_Bofo_Title));
        col.addView(Ui.text(a, "What people asked BOFO. Newest first. Kept " + AiChatLogRules.RETENTION_DAYS + " days. Do not share these.", R.style.Text_Bofo_Caption));
        LinearLayout sr = Ui.row(a); sr.setPadding(0, Ui.dp(a, 8), 0, 0);
        box = new EditText(a); box.setHint("Search"); box.setSingleLine(); box.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(AiChatLogRules.SEARCH_MAX)});
        sr.addView(box, new LinearLayout.LayoutParams(0, -2, 1f));
        Button go = Ui.button(a, "Search", true);
        go.setOnClickListener(v -> { search = box.getText().toString(); reload(); });
        sr.addView(go);
        col.addView(sr);
        chips = Ui.row(a); chips.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a); hs.setHorizontalScrollBarEnabled(false); hs.addView(chips);
        col.addView(hs);
        items = Ui.col(a); col.addView(items);
        empty = Ui.text(a, "No chats found.", R.style.Text_Bofo_Caption); empty.setVisibility(GONE); col.addView(empty);
        more = Ui.button(a, "Show more", false); more.setVisibility(GONE); more.setOnClickListener(v -> loadMore()); col.addView(more);
        drawChips(); reload();
    }

    private void drawChips() {
        chips.removeAllViews();
        String[] names = {"Last 24 h", "Last 7 days", "All"}; Range[] rs = {Range.TODAY, Range.WEEK, Range.ALL};
        for (int i = 0; i < 3; i++) {
            final Range r = rs[i];
            Button b = Ui.button(act, names[i], r == range);
            b.setOnClickListener(v -> { range = r; drawChips(); reload(); });
            chips.addView(b);
        }
    }

    private void reload() { items.removeAllViews(); shown = 0; empty.setVisibility(GONE); more.setVisibility(GONE); loadMore(); }

    private void loadMore() {
        if (busy) return;
        busy = true;
        final String q = AiChatLogRules.query(range, search, shown, System.currentTimeMillis());
        Async.run(() -> {
            JSONArray a = Api.get().select("ai_chat_log", q);
            List<Row> out = new ArrayList<>(a.length());
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Row(o.optString("id", ""), o.optString("user_name", ""), o.optString("question", ""), o.optString("answer", ""), o.optString("created_at", "")));
            }
            return out;
        }, rows -> {
            busy = false;
            int n = Math.min(rows.size(), AiChatLogRules.PAGE);
            for (int i = 0; i < n; i++) items.addView(card(rows.get(i)));
            shown += n;
            more.setVisibility(AiChatLogRules.hasMore(rows.size()) ? VISIBLE : GONE);
            empty.setVisibility(shown == 0 ? VISIBLE : GONE);
        }, e -> { busy = false; Ui.toast(act, Ui.err(e)); });
    }

    private LinearLayout card(Row r) {
        LinearLayout c = Ui.card(act);
        c.addView(Ui.text(act, (r.name.isEmpty() ? "Unknown user" : r.name) + "  |  " + AiChatLogRules.when(r.createdAt), R.style.Text_Bofo_Caption));
        c.addView(Ui.text(act, AiChatLogRules.clip(r.question, AiChatLogRules.CLIP), R.style.Text_Bofo_Section));
        c.addView(Ui.text(act, AiChatLogRules.clip(r.answer, AiChatLogRules.CLIP), R.style.Text_Bofo_Body));
        return c;
    }
}
