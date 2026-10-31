package com.bofo.finder.ui;

import android.animation.ObjectAnimator;
import android.view.View;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bofo.finder.R;
import com.bofo.finder.databinding.ViewListAreaBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.util.Cache;
import com.google.android.material.tabs.TabLayout;

import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

/** The shared body of Home / Explore / Saved: card grid, skeleton while loading, pull to refresh, message + retry. */
final class ListArea {
    private final ViewListAreaBinding b;
    private final BhAdapter adapter;
    private ObjectAnimator pulse;
    private final Footer footer = new Footer();

    /** Step 7: one full-width "Show more (n)" button after the cards. */
    private static final class Footer extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private String text; private Runnable action;
        void set(String t, Runnable a) { boolean had = text != null; text = t; action = a; if (had && t != null) notifyItemChanged(0); else if (had) notifyItemRemoved(0); else if (t != null) notifyItemInserted(0); }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new RecyclerView.ViewHolder(android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_show_more, parent, false)) { };
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            Button btn = h.itemView.findViewById(R.id.show_more);
            btn.setText(text);
            btn.setOnClickListener(v -> { if (action != null) action.run(); });
        }
        @Override public int getItemCount() { return text == null ? 0 : 1; }
    }

    ListArea(Fragment f, ViewListAreaBinding b, BhAdapter adapter, Runnable reload) {
        this.b = b;
        this.adapter = adapter;
        int span = f.getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
        GridLayoutManager glm = new GridLayoutManager(f.requireContext(), span);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int pos) { return pos >= adapter.getItemCount() ? span : 1; }      // the footer button spans the row
        });
        b.list.setLayoutManager(glm);
        b.list.setAdapter(new ConcatAdapter(new ConcatAdapter.Config.Builder().setIsolateViewTypes(true).build(), adapter, footer));
        b.skeleton.setLayoutManager(new GridLayoutManager(f.requireContext(), span));
        b.skeleton.setAdapter(new BhAdapter.Skeleton());
        b.swipe.setColorSchemeResources(R.color.bofo_primary);
        b.swipe.setOnRefreshListener(() -> { Cache.remove("list:"); reload.run(); });
        b.retry.setOnClickListener(v -> reload.run());
    }

    /** First load: skeleton cards. Later loads: keep the list and show the pull-to-refresh spinner. */
    void showLoading() {
        b.emptyBox.setVisibility(View.GONE);
        if (adapter.getItemCount() > 0) { b.swipe.setRefreshing(true); return; }
        b.list.setVisibility(View.GONE);
        b.skeleton.setVisibility(View.VISIBLE);
        if (pulse == null) {
            pulse = ObjectAnimator.ofFloat(b.skeleton, View.ALPHA, 1f, 0.45f);
            pulse.setDuration(900);
            pulse.setInterpolator(new android.view.animation.PathInterpolator(0.42f, 0f, 0.58f, 1f));
            pulse.setRepeatMode(ObjectAnimator.REVERSE);
            pulse.setRepeatCount(ObjectAnimator.INFINITE);
        }
        if (!pulse.isStarted()) pulse.start();
    }

    /** "Show more (n)" under the cards; null removes it. */
    void setMore(String text, Runnable action) { footer.set(text, action); }

    void showList(List<BoardingHouse> l) {
        stopLoading();
        b.emptyBox.setVisibility(View.GONE);
        b.list.setVisibility(View.VISIBLE);
        adapter.submitList(l);
    }

    /** Empty result or error (retry = true shows the Try again button). */
    void showMessage(String text, boolean retry) {
        stopLoading();
        footer.set(null, null);
        adapter.submitList(Collections.<BoardingHouse>emptyList());
        b.list.setVisibility(View.GONE);
        b.emptyBox.setVisibility(View.VISIBLE);
        b.emptyText.setText(text);
        b.retry.setVisibility(retry ? View.VISIBLE : View.GONE);
    }

    private void stopLoading() {
        b.swipe.setRefreshing(false);
        b.skeleton.setVisibility(View.GONE);
        if (pulse != null) { pulse.cancel(); b.skeleton.setAlpha(1f); }
    }

    /** Call from onDestroyView so no animator keeps the old view alive. */
    void destroy() { if (pulse != null) { pulse.cancel(); pulse = null; } }

    static void toast(Fragment f, String msg) {
        if (f.isAdded() && f.getContext() != null) Toast.makeText(f.requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    /** Tab selection without implementing the three-method listener every time. */
    static void onTab(TabLayout t, IntConsumer c) {
        t.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { c.accept(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }
}
