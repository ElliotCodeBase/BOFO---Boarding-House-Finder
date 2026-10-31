package com.bofo.finder.ui;

import android.animation.ObjectAnimator;
import android.view.View;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

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

    ListArea(Fragment f, ViewListAreaBinding b, BhAdapter adapter, Runnable reload) {
        this.b = b;
        this.adapter = adapter;
        int span = f.getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
        b.list.setLayoutManager(new GridLayoutManager(f.requireContext(), span));
        b.list.setAdapter(adapter);
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

    void showList(List<BoardingHouse> l) {
        stopLoading();
        b.emptyBox.setVisibility(View.GONE);
        b.list.setVisibility(View.VISIBLE);
        adapter.submitList(l);
    }

    /** Empty result or error (retry = true shows the Try again button). */
    void showMessage(String text, boolean retry) {
        stopLoading();
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
