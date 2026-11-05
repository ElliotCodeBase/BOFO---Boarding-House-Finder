package com.bofo.finder.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bofo.finder.R;
import com.bofo.finder.util.AdminHomeRules;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/** v2.4.13: the one row look for the admin lists: title, a line of detail, and a status pill (words + colour, never colour alone). */
final class AdminRowAdapter extends BaseAdapter {
    static final int WAITING = 0, OK = 1, BAD = 2, NEUTRAL = 3;

    static final class Row {
        final String title, sub, status, thumb;
        final int tone;
        final String undoLabel;          // v2.5.0: non-null = the row shows an Undo button
        final Runnable onUndo;
        Row(String title, String sub, String status, int tone, String thumb) { this(title, sub, status, tone, thumb, null, null); }
        Row(String title, String sub, String status, int tone, String thumb, String undoLabel, Runnable onUndo) {
            this.title = title; this.sub = sub; this.status = status; this.tone = tone; this.thumb = thumb; this.undoLabel = undoLabel; this.onUndo = onUndo;
        }
    }

    private final Context ctx;
    private final List<Row> rows = new ArrayList<>();

    AdminRowAdapter(Context ctx) { this.ctx = ctx; }

    void set(List<Row> r) { rows.clear(); rows.addAll(r); notifyDataSetChanged(); }
    @Override public boolean isEmpty() { return rows.isEmpty(); }       // BaseAdapter.isEmpty() is public: a weaker access here does not compile

    @Override public int getCount() { return rows.size(); }
    @Override public Object getItem(int i) { return rows.get(i); }
    @Override public long getItemId(int i) { return i; }

    @Override
    public View getView(int pos, View v, ViewGroup parent) {
        if (v == null) v = LayoutInflater.from(ctx).inflate(R.layout.item_admin_row, parent, false);
        Row r = rows.get(pos);
        v.setTranslationX(0f);                       // v2.5.0: a swiped row may be recycled
        v.setAlpha(1f);
        android.widget.Button undo = v.findViewById(R.id.row_undo);
        if (r.undoLabel != null && r.onUndo != null) {
            undo.setVisibility(View.VISIBLE);
            undo.setText(r.undoLabel);
            undo.setOnClickListener(x -> r.onUndo.run());
        } else {
            undo.setVisibility(View.GONE);
            undo.setOnClickListener(null);
        }
        ((TextView) v.findViewById(R.id.row_title)).setText(r.title);
        ((TextView) v.findViewById(R.id.row_sub)).setText(r.sub);
        TextView st = v.findViewById(R.id.row_status);
        st.setText(r.status);
        st.setVisibility(r.status == null || r.status.isEmpty() ? View.GONE : View.VISIBLE);
        int bg, fg;
        switch (r.tone) {
            case WAITING: bg = R.color.bofo_amber_wash; fg = R.color.bofo_text; break;
            case OK: bg = R.color.bofo_success_tint; fg = R.color.bofo_success_deep; break;
            case BAD: bg = R.color.bofo_error_tint; fg = R.color.bofo_error_deep; break;
            default: bg = R.color.bofo_tile; fg = R.color.bofo_text_2; break;
        }
        st.setBackgroundTintList(ColorStateList.valueOf(ctx.getColor(bg)));
        st.setTextColor(ctx.getColor(fg));
        ImageView t = v.findViewById(R.id.row_thumb);
        if (r.thumb != null && !r.thumb.isEmpty() && !"null".equals(r.thumb)) {
            t.setVisibility(View.VISIBLE);
            Glide.with(t).load(r.thumb).centerCrop().into(t);
        } else {
            Glide.with(t).clear(t);
            t.setVisibility(View.GONE);
        }
        v.setContentDescription(r.title + ". " + r.sub + (r.status == null || r.status.isEmpty() ? "" : ". " + r.status));
        return v;
    }

    /** "Waiting 3 days" for a timestamp; "" when it cannot be read. */
    static String waiting(Context c, String iso) {
        long h = AdminHomeRules.waitingHours(iso, System.currentTimeMillis());
        if (h < 0) return "";
        if (h < 1) return c.getString(R.string.admin_row_waiting_lt_hour);
        if (h < 24) return c.getResources().getQuantityString(R.plurals.admin_row_waiting_hours, (int) h, (int) h);
        int d = (int) (h / 24);
        return c.getResources().getQuantityString(R.plurals.admin_row_waiting_days, d, d);
    }
}
