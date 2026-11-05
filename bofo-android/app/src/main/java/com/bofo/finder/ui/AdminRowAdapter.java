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
        final String title, sub, status, thumb, meta;
        final int tone;
        Row(String title, String sub, String status, int tone, String thumb) { this(title, sub, status, tone, thumb, null); }
        /** v2.9.8: {@code meta} is a small line under the status pill (a date, or how long it has waited). */
        Row(String title, String sub, String status, int tone, String thumb, String meta) { this.title = title; this.sub = sub; this.status = status; this.tone = tone; this.thumb = thumb; this.meta = meta; }
    }

    private final Context ctx;
    private final List<Row> rows = new ArrayList<>();
    private int placeholder;        // v2.9.8: icon shown in the thumbnail slot when a row has no photo (0 = no slot, the row starts at the text)

    AdminRowAdapter(Context ctx) { this.ctx = ctx; }

    /** Keeps every row's text on the same left edge by showing {@code iconRes} where a photo is missing. */
    AdminRowAdapter withPlaceholder(int iconRes) { this.placeholder = iconRes; return this; }

    void set(List<Row> r) { rows.clear(); rows.addAll(r); notifyDataSetChanged(); }
    @Override public boolean isEmpty() { return rows.isEmpty(); }

    @Override public int getCount() { return rows.size(); }
    @Override public Object getItem(int i) { return rows.get(i); }
    @Override public long getItemId(int i) { return i; }

    @Override
    public View getView(int pos, View v, ViewGroup parent) {
        if (v == null) v = LayoutInflater.from(ctx).inflate(R.layout.item_admin_row, parent, false);
        Row r = rows.get(pos);
        ((TextView) v.findViewById(R.id.row_title)).setText(r.title);
        TextView sub = v.findViewById(R.id.row_sub);
        sub.setText(r.sub);
        sub.setVisibility(r.sub == null || r.sub.isEmpty() ? View.GONE : View.VISIBLE);
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
        TextView meta = v.findViewById(R.id.row_meta);
        boolean hasMeta = r.meta != null && !r.meta.isEmpty();
        meta.setText(hasMeta ? r.meta : "");
        meta.setVisibility(hasMeta ? View.VISIBLE : View.GONE);
        ImageView t = v.findViewById(R.id.row_thumb);
        if (r.thumb != null && !r.thumb.isEmpty() && !"null".equals(r.thumb)) {
            t.setVisibility(View.VISIBLE);
            t.setBackgroundResource(R.drawable.bg_dash_card);
            t.setPadding(0, 0, 0, 0);
            t.setImageTintList(null);
            t.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(t).load(r.thumb).centerCrop().into(t);
        } else {
            Glide.with(t).clear(t);
            if (placeholder != 0) {
                int pad = Math.round(12 * ctx.getResources().getDisplayMetrics().density);
                t.setVisibility(View.VISIBLE);
                t.setBackgroundResource(R.drawable.bg_row_placeholder);
                t.setPadding(pad, pad, pad, pad);
                t.setScaleType(ImageView.ScaleType.FIT_CENTER);
                t.setImageResource(placeholder);
                t.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.bofo_error_deep)));
            } else {
                t.setVisibility(View.GONE);
            }
        }
        v.setContentDescription(r.title + (r.sub == null || r.sub.isEmpty() ? "" : ". " + r.sub)
                + (r.status == null || r.status.isEmpty() ? "" : ". " + r.status) + (hasMeta ? ". " + r.meta : ""));
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
