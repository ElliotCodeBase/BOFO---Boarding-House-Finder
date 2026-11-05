package com.bofo.finder.ui;

import android.animation.ValueAnimator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.StringRes;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentAdminListBinding;
import com.bofo.finder.util.AdminListRules;

/**
 * v2.4.13: the shared top and bottom of the admin lists (fragment_admin_list.xml): filter chips (Waiting / Done / All), search box,
 * offline banner, skeleton while loading, and a friendly message with "Try again". A screen creates one, gives it the callbacks, and calls
 * {@link #loading}, {@link #loaded} or {@link #failed}.
 */
final class AdminListChrome {
    interface Host { void onFilter(int chip); void onSearch(String query); void onRetry(); }

    private final FragmentAdminListBinding b;
    private final Host host;
    private boolean silent;                  // true while the code (not the admin) changes a chip
    private int chip;
    private String query = "";
    private ValueAnimator pulse;

    AdminListChrome(FragmentAdminListBinding b, int startChip, Host host) {
        this.b = b; this.host = host; this.chip = startChip;
        b.filtersScroll.setVisibility(View.VISIBLE);
        b.search.setVisibility(View.VISIBLE);
        setChip(startChip);
        b.filters.setOnCheckedStateChangeListener((g, ids) -> {
            if (silent || ids.isEmpty()) return;
            int id = ids.get(0);
            chip = id == R.id.chipDone ? AdminListRules.DONE : id == R.id.chipAll ? AdminListRules.ALL : AdminListRules.WAITING;
            host.onFilter(chip);
        });
        b.search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable e) { query = e.toString(); host.onSearch(query); }
        });
        b.search.setOnEditorActionListener((v, action, ev) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) { v.clearFocus(); return true; }
            return false;
        });
        b.retry.setOnClickListener(v -> host.onRetry());
    }

    int chip() { return chip; }
    String query() { return query; }

    /** Shows a chip as selected without calling the host. -1 = none (a finer filter chosen elsewhere). */
    void setChip(int c) {
        silent = true;
        chip = c;
        b.filters.setSelectionRequired(false);
        if (c < 0) b.filters.clearCheck();
        else b.filters.check(c == AdminListRules.DONE ? R.id.chipDone : c == AdminListRules.ALL ? R.id.chipAll : R.id.chipWaiting);
        b.filters.setSelectionRequired(c >= 0);
        silent = false;
    }

    /** A load started. The skeleton only shows when there is nothing on screen yet. */
    void loading(boolean haveRows) {
        b.swipe.setRefreshing(haveRows);
        b.message.setVisibility(View.GONE);
        b.retry.setVisibility(View.GONE);
        if (!haveRows) showSkeleton(true);
    }

    /** A load finished. {@code emptyRes} is shown when there are no rows (a different text when a search is active). */
    void loaded(boolean hasRows, @StringRes int emptyRes) {
        b.swipe.setRefreshing(false);
        showSkeleton(false);
        b.offline.setVisibility(View.GONE);
        b.retry.setVisibility(View.GONE);
        b.message.setText(query.trim().isEmpty() ? emptyRes : R.string.admin_no_match);
        b.message.setVisibility(hasRows ? View.GONE : View.VISIBLE);
    }

    /** A load failed. With rows on screen: a banner and the old list. Without: a plain sentence and "Try again". */
    void failed(boolean network, boolean hasRows) {
        b.swipe.setRefreshing(false);
        showSkeleton(false);
        if (hasRows) {
            b.offline.setText(network ? R.string.admin_offline_cached : R.string.admin_refresh_failed_cached);
            b.offline.setVisibility(View.VISIBLE);
            b.message.setVisibility(View.GONE);
            b.retry.setVisibility(View.GONE);
        } else {
            b.offline.setVisibility(View.GONE);
            b.message.setText(network ? R.string.list_offline : R.string.admin_error_friendly);
            b.message.setVisibility(View.VISIBLE);
            b.retry.setVisibility(View.VISIBLE);
        }
    }

    private void showSkeleton(boolean on) {
        b.skeleton.setVisibility(on ? View.VISIBLE : View.GONE);
        if (pulse != null) { pulse.cancel(); pulse = null; }
        b.skeleton.setAlpha(1f);
        if (on && ValueAnimator.areAnimatorsEnabled()) {          // respects "remove animations" in system settings
            pulse = ValueAnimator.ofFloat(1f, 0.45f);
            pulse.setDuration(700);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.addUpdateListener(a -> b.skeleton.setAlpha((float) a.getAnimatedValue()));
            pulse.start();
        }
    }

    void destroy() { if (pulse != null) { pulse.cancel(); pulse = null; } }
}
