package com.bofo.finder.ui;

import android.text.Layout;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bofo.finder.R;
import com.bofo.finder.databinding.ItemAiChatBinding;
import com.bofo.finder.databinding.ItemAiChatMsgBinding;
import com.bofo.finder.util.AiChatRules;
import com.bofo.finder.util.AiChatRules.Group;
import com.bofo.finder.util.AiChatRules.Row;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * v2.0.91 AI Chats monitor: the list. Flat mode = one card per question (answer cut to 2 lines, tap = whole answer);
 * grouped mode = one card per user (latest question; tap = the whole conversation). Read-only: nothing here edits or deletes. Removed with the feature.
 */
final class AiChatLogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int ITEM = 0, MORE = 1;

    private final List<Row> rows = new ArrayList<>();
    private final List<Group> groups = new ArrayList<>();
    private final Set<String> open = new HashSet<>();            // row ids with the full answer, user keys with the whole conversation
    private final ZoneId zone = ZoneId.systemDefault();
    private boolean grouped, more, busy;
    private long now = System.currentTimeMillis();
    private Runnable onMore;

    void setOnMore(Runnable r) { onMore = r; }

    /** Shows these rows (newest first). {@code more} adds the "Show more" button; {@code busy} makes it say Loading. */
    void submit(List<Row> list, boolean group, boolean hasMore, boolean loadingMore) {
        rows.clear(); rows.addAll(list);
        groups.clear();
        if (group) groups.addAll(AiChatRules.group(rows));
        grouped = group; more = hasMore; busy = loadingMore;
        now = System.currentTimeMillis();
        notifyDataSetChanged();
    }

    int dataCount() { return grouped ? groups.size() : rows.size(); }

    @Override public int getItemCount() { return dataCount() + (more ? 1 : 0); }

    @Override public int getItemViewType(int pos) { return pos >= dataCount() ? MORE : ITEM; }

    private static final class ItemHolder extends RecyclerView.ViewHolder {
        final ItemAiChatBinding b;
        ItemHolder(ItemAiChatBinding b) { super(b.getRoot()); this.b = b; }
    }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater in = LayoutInflater.from(parent.getContext());
        if (type == MORE) return new RecyclerView.ViewHolder(in.inflate(R.layout.item_show_more, parent, false)) { };
        return new ItemHolder(ItemAiChatBinding.inflate(in, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
        if (holder instanceof ItemHolder) { if (grouped) bindGroup((ItemHolder) holder, groups.get(pos)); else bindRow((ItemHolder) holder, rows.get(pos)); return; }
        Button btn = holder.itemView.findViewById(R.id.show_more);
        btn.setText(busy ? R.string.ai_chats_loading_more : R.string.ai_chats_more);
        btn.setEnabled(!busy);
        btn.setOnClickListener(v -> { if (!busy && onMore != null) onMore.run(); });
    }

    /* ---------- flat: one question ---------- */

    private void bindRow(ItemHolder h, Row r) {
        head(h, AiChatRules.who(r.name, r.email), r.email, "");
        boolean full = open.contains(r.id);
        h.b.body.removeAllViews();
        ItemAiChatMsgBinding m = addMsg(h.b.body, r, full);
        cutHint(h, m, full);
        h.b.getRoot().setOnClickListener(v -> toggle(h, r.id));
    }

    /* ---------- grouped: one user ---------- */

    private void bindGroup(ItemHolder h, Group g) {
        if (g.messages.size() == 1) {                                  // nothing to open: behaves like a flat row
            Row r = g.latest();
            head(h, g.name, g.email, AiChatRules.ago(g.latestIso, now));
            boolean full = open.contains(r.id);
            h.b.body.removeAllViews();
            ItemAiChatMsgBinding m = addMsg(h.b.body, r, full);
            cutHint(h, m, full);
            h.b.getRoot().setOnClickListener(v -> toggle(h, r.id));
            return;
        }
        head(h, g.name, g.email, AiChatRules.ago(g.latestIso, now));
        boolean all = open.contains(g.key);
        h.b.body.removeAllViews();
        if (all) for (Row r : g.messages) addMsg(h.b.body, r, true);          // oldest first, like reading a chat
        else addMsg(h.b.body, g.latest(), false);
        String count = AiChatRules.countText(g.messages.size());
        h.b.hint.setText(h.itemView.getContext().getString(all ? R.string.ai_chats_close_all : R.string.ai_chats_open_all, count));
        h.b.hint.setVisibility(View.VISIBLE);
        h.b.getRoot().setOnClickListener(v -> toggle(h, g.key));
    }

    /* ---------- shared bits ---------- */

    private void head(ItemHolder h, String name, String email, String ago) {
        h.b.name.setText(name);
        h.b.email.setText(email);
        h.b.email.setVisibility(email == null || email.isEmpty() ? View.GONE : View.VISIBLE);
        h.b.ago.setText(ago);
        h.b.ago.setVisibility(ago == null || ago.isEmpty() ? View.GONE : View.VISIBLE);
        h.b.hint.setVisibility(View.GONE);
    }

    private ItemAiChatMsgBinding addMsg(ViewGroup parent, Row r, boolean full) {
        ItemAiChatMsgBinding m = ItemAiChatMsgBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        m.when.setText(AiChatRules.whenLine(r.createdAt, now, zone));
        m.question.setText(r.question);
        m.answer.setText(r.answer);
        if (full) { m.answer.setMaxLines(Integer.MAX_VALUE); m.answer.setEllipsize(null); }
        else { m.answer.setMaxLines(2); m.answer.setEllipsize(TextUtils.TruncateAt.END); }
        parent.addView(m.getRoot());
        return m;
    }

    /** "Tap to read the full answer" only when the 2 lines really cut the answer; "Tap to collapse" only when the open answer is longer than 2 lines. */
    private void cutHint(ItemHolder h, ItemAiChatMsgBinding m, boolean full) {
        m.answer.post(() -> {
            if (!m.answer.isAttachedToWindow()) return;                // the row was rebound for another message meanwhile
            Layout l = m.answer.getLayout();
            if (l == null || l.getLineCount() == 0) return;
            boolean cut = full ? l.getLineCount() > 2 : l.getEllipsisCount(l.getLineCount() - 1) > 0;
            h.b.hint.setText(full ? R.string.ai_chats_tap_close : R.string.ai_chats_tap_open);
            h.b.hint.setVisibility(cut ? View.VISIBLE : View.GONE);
        });
    }

    private void toggle(ItemHolder h, String key) {
        if (!open.remove(key)) open.add(key);
        int p = h.getBindingAdapterPosition();
        if (p != RecyclerView.NO_POSITION) notifyItemChanged(p);
    }

    /** Placeholder cards while the first page loads. */
    static final class Skeleton extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ai_chat_skeleton, parent, false)) { };
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) { }
        @Override public int getItemCount() { return 6; }
    }
}
