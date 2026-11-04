package com.bofo.finder.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bofo.finder.R;
import com.bofo.finder.model.AnnounceType;
import com.bofo.finder.util.AnnounceFormState;
import com.bofo.finder.util.AnnounceTypePickerRules;

import java.util.List;

/**
 * The rows of the announcement types: icon, title, one-line description, Urgent / Important badge, and a check mark on the chosen type.
 * Moved out of AnnounceAiSheet in v2.4.7 so the type dropdown ({@link AnnounceTypeDropdown}) can reuse it; the row look is unchanged.
 */
final class AnnounceTypeAdapter extends RecyclerView.Adapter<AnnounceTypeAdapter.Holder> {
    interface OnPick { void pick(AnnounceType t); }

    private final Context ctx;
    private final OnPick onPick;
    private List<AnnounceType> items = AnnounceType.ALL;
    private AnnounceType selected;

    AnnounceTypeAdapter(Context ctx, OnPick onPick) { this.ctx = ctx; this.onPick = onPick; }

    void submit(List<AnnounceType> l) { items = l; notifyDataSetChanged(); }

    void setSelected(AnnounceType t) { selected = t; notifyDataSetChanged(); }

    /** The index of the chosen type in the shown list, or -1. */
    int selectedIndex() {
        for (int i = 0; i < items.size(); i++) if (AnnounceTypePickerRules.isSelected(selected, items.get(i))) return i;
        return -1;
    }

    /** A string resource by name (the types name their own strings); the fallback when it does not exist. */
    static String res(Context c, String name, String fallback) {
        try {
            int id = c.getResources().getIdentifier(name, "string", c.getPackageName());
            return id == 0 ? fallback : c.getString(id);
        } catch (Exception e) { return fallback; }
    }

    static int drawable(Context c, String name) {
        int id = c.getResources().getIdentifier(name, "drawable", c.getPackageName());
        return id == 0 ? R.drawable.ic_sparkle : id;
    }

    /** The live filter of the dropdown: the existing {@link AnnounceFormState#filter} (title and id words). */
    static List<AnnounceType> filter(Context c, String query) {
        return AnnounceFormState.filter(AnnounceType.ALL, query == null ? "" : query, t -> res(c, t.titleName(), t.id));
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int viewType) {
        return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_announce_type, p, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        final AnnounceType t = items.get(position);
        Context c = h.itemView.getContext();
        String title = res(c, t.titleName(), t.id), desc = res(c, t.descName(), "");
        h.icon.setImageResource(drawable(c, t.icon));
        h.title.setText(title);
        h.desc.setText(desc);
        h.desc.setVisibility(desc.isEmpty() ? View.GONE : View.VISIBLE);
        String badgeName = AnnounceFormState.badgeStringName(t);
        String cd;
        if (badgeName == null) {
            h.badge.setVisibility(View.GONE);
            cd = c.getString(R.string.announce_type_row_cd, title, desc);
        } else {
            String badge = res(c, badgeName, "");
            boolean urgent = t.isUrgent();
            h.badge.setText(badge);
            h.badge.setBackgroundResource(urgent ? R.drawable.bg_ann_badge_urgent : R.drawable.bg_ann_badge_important);
            h.badge.setTextColor(ContextCompat.getColor(c, urgent ? R.color.bofo_error_deep : R.color.bofo_text_2));
            h.badge.setVisibility(View.VISIBLE);
            cd = c.getString(R.string.announce_type_row_urgency_cd, title, badge, desc);
        }
        boolean chosen = AnnounceTypePickerRules.isSelected(selected, t);
        h.check.setVisibility(chosen ? View.VISIBLE : View.GONE);
        h.itemView.setSelected(chosen);
        h.itemView.setContentDescription(chosen ? c.getString(R.string.announce_type_selected_cd, cd) : cd);
        h.itemView.setOnClickListener(v -> onPick.pick(t));
    }

    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon, check; final TextView title, desc, badge;
        Holder(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.ann_row_icon); title = v.findViewById(R.id.ann_row_title);
            desc = v.findViewById(R.id.ann_row_desc); badge = v.findViewById(R.id.ann_row_badge);
            check = v.findViewById(R.id.ann_row_check);
        }
    }
}
