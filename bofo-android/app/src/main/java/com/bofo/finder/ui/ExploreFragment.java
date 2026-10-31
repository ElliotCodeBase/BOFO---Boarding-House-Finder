package com.bofo.finder.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentExploreBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.Favorites;
import com.bofo.finder.util.ListRules;

import java.util.List;

/** Explore: search by name / area, price + amenity filters, sort tabs. Distance is measured from the chosen campus when there is one. */
public class ExploreFragment extends Fragment {

    private static final BhService.Tab[] TABS = {BhService.Tab.NEW, BhService.Tab.HOT, BhService.Tab.BEST};
    private static final long DEBOUNCE_MS = 400;

    private FragmentExploreBinding b;
    private BhAdapter adapter;
    private ListArea area;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private int token;
    private int lastChange = -1;
    private int tab;
    private String query = "";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentExploreBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        adapter = new BhAdapter(new BhAdapter.Listener() {
            @Override public void open(BoardingHouse x) { BhDetailFragment.open(ExploreFragment.this, x.id()); }
            @Override public void heart(BoardingHouse x) { Favorites.toggle(x.id(), msg -> ListArea.toast(ExploreFragment.this, msg)); }
        });
        area = new ListArea(this, b.area, adapter, this::reload);

        b.tabs.addTab(b.tabs.newTab().setText(R.string.home_tab_new));
        b.tabs.addTab(b.tabs.newTab().setText(R.string.home_tab_hot));
        b.tabs.addTab(b.tabs.newTab().setText(R.string.home_tab_best));
        ListArea.onTab(b.tabs, pos -> { tab = pos; reload(); });

        b.search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) {
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                final String q = s.toString().trim();
                pendingSearch = () -> { if (!q.equals(query)) { query = q; reload(); } };
                handler.postDelayed(pendingSearch, DEBOUNCE_MS);
            }
        });
        b.search.setOnEditorActionListener((v, action, ev) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        });
        b.filterBtn.setOnClickListener(v -> new FilterSheet().show(getChildFragmentManager(), "filter"));
        paintFilterButton();

        Favorites.load(null);
        Favorites.IDS.observe(getViewLifecycleOwner(), ids -> adapter.setFavorites(ids));
        BrowseState.CHANGED.observe(getViewLifecycleOwner(), n -> { if (n == null || n == lastChange) return; lastChange = n; paintFilterButton(); reload(); });
    }

    private void paintFilterButton() {
        int n = BrowseState.activeFilters();
        b.filterBtn.setText(n == 0 ? getString(R.string.filters_btn) : getString(R.string.filters_btn_count, n));
    }

    private void reload() {
        if (b == null) return;
        final int my = ++token;
        area.showLoading();
        BrowseState.ensureLoaded(() -> {
            if (my != token || b == null) return;
            final College c = BrowseState.campus();
            final BhService.Tab t = TABS[tab];
            final String q = query;
            final Double min = BrowseState.minPrice, max = BrowseState.maxPrice;
            final BhService.Filter f = BrowseState.filter(q);
            Async.run(() -> {
                Double lat = c == null ? null : c.lat();
                Double lng = c == null ? null : c.lng();
                List<BoardingHouse> l = new BhService().list(t, lat, lng, null, f);       // no radius: search everywhere, nearest first
                return ListRules.inPriceRange(l, min, max);
            }, (List<BoardingHouse> l) -> {
                if (my != token || b == null) return;
                adapter.setShowDistance(c != null);
                b.resultCount.setText(l.size() == 1 ? getString(R.string.explore_result_one) : getString(R.string.explore_results, l.size()));
                if (l.isEmpty()) area.showMessage(getString(R.string.explore_empty), false);
                else area.showList(l);
            }, e -> fail(my, e));
        }, e -> fail(my, e));
    }

    private void fail(int my, Throwable e) {
        if (my != token || b == null) return;
        b.resultCount.setText("");
        area.showMessage(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.list_error), e), true);
    }

    @Override
    public void onDestroyView() {
        token++;
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        if (area != null) area.destroy();
        b = null;
        super.onDestroyView();
    }
}
