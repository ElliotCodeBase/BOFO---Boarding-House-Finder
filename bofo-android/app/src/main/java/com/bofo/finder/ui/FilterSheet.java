package com.bofo.finder.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bofo.finder.R;
import com.bofo.finder.databinding.SheetFilterBinding;
import com.bofo.finder.model.Amenity;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.ExploreRules;
import com.bofo.finder.util.Fuzzy;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Step 9: Explore filters (original FilterSheet): maximum price (quick chips + your own number), Sort by, and Amenities
 * (popular chips + a search box for every other amenity). Nothing changes in the list until "Show results".
 * If the amenity catalog cannot be loaded, a built-in popular list is shown instead (filtered by name on the cards) and no error appears.
 */
public class FilterSheet extends BottomSheetDialogFragment {

    private static final int[] PRICES = {1000, 1500, 2000, 3000};
    private static final int POPULAR_SHOWN = 12;

    private SheetFilterBinding b;
    private List<Amenity> amenities = new ArrayList<>();
    private boolean loaded, catalog;
    private final Set<String> selected = new LinkedHashSet<>();     // catalog amenity ids
    private final Set<String> selNames = new LinkedHashSet<>();     // names picked from the built-in list
    private final List<Chip> priceChips = new ArrayList<>();
    private int sort;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = SheetFilterBinding.inflate(inflater, container, false);
        selected.addAll(BrowseState.amenityIds);
        selNames.addAll(BrowseState.amenityNames);
        sort = BrowseState.sort;

        // price: Any + 4 quick chips, the field is the single source of truth
        addPriceChip(getString(R.string.ex_price_any), null);
        for (int p : PRICES) addPriceChip("\u20B1" + String.format(java.util.Locale.US, "%,d", p), p);
        Double max = BrowseState.maxPrice;
        b.priceCustom.setText(max == null ? "" : String.valueOf(max.longValue()));
        b.priceCustom.addTextChangedListener(new SimpleWatcher() { @Override public void afterTextChanged(Editable s) { paintPrice(); } });
        paintPrice();

        // sort
        TextView[] segs = {b.sort0, b.sort1, b.sort2};
        for (int i = 0; i < segs.length; i++) { final int idx = i; segs[i].setOnClickListener(v -> { sort = idx; paintSort(); }); }
        paintSort();

        // amenities
        b.amenitySearch.addTextChangedListener(new SimpleWatcher() { @Override public void afterTextChanged(Editable s) { renderChips(); } });
        Async.run(() -> new BhService().amenities(), (List<Amenity> l) -> {
            if (b == null) return;
            amenities = l; catalog = l != null && !l.isEmpty(); loaded = true;
            renderChips();
        }, e -> {
            if (b == null) return;
            catalog = false; loaded = true;                       // no error text: the built-in list takes over
            renderChips();
        });

        b.close.setOnClickListener(v -> dismiss());
        b.reset.setOnClickListener(v -> {                          // clears the sheet only: the list changes at "Show results"
            b.priceCustom.setText(""); sort = 0; selected.clear(); selNames.clear();
            b.amenitySearch.setText("");
            paintSort(); renderChips();
        });
        b.apply.setOnClickListener(v -> apply());
        return b.getRoot();
    }

    private void apply() {
        Double max = ExploreRules.parseMax(b.priceCustom.getText().toString());
        if (max != null && max.isNaN()) { Toast.makeText(requireContext(), R.string.ex_price_bad, Toast.LENGTH_SHORT).show(); return; }
        BrowseState.minPrice = null;                               // the old range slider is gone: only a maximum now
        BrowseState.maxPrice = max;
        BrowseState.sort = sort;
        BrowseState.amenityIds.clear(); BrowseState.amenityIds.addAll(selected);
        BrowseState.amenityNames.clear(); BrowseState.amenityNames.addAll(selNames);
        BrowseState.bump();
        dismiss();
    }

    private void addPriceChip(String text, Integer value) {
        Chip c = Chips.create(b.priceChips, text, true, false);
        c.setOnClickListener(v -> b.priceCustom.setText(value == null ? "" : String.valueOf(value)));
        priceChips.add(c);
        b.priceChips.addView(c);
    }

    private void paintPrice() {
        if (b == null) return;
        String t = b.priceCustom.getText().toString().trim();
        for (int i = 0; i < priceChips.size(); i++) priceChips.get(i).setChecked(i == 0 ? t.isEmpty() : t.equals(String.valueOf(PRICES[i - 1])));
    }

    private void paintSort() {
        TextView[] segs = {b.sort0, b.sort1, b.sort2};
        for (int i = 0; i < segs.length; i++) {
            boolean on = i == sort;
            segs[i].setBackgroundResource(on ? R.drawable.bg_ex_seg_on : 0);
            segs[i].setTextColor(ContextCompat.getColor(requireContext(), on ? R.color.bofo_text : R.color.bofo_text_2));
        }
    }

    /** Popular chips (plus anything already ticked), or the fuzzy matches while typing ("aircon", "wify"...). Ticked ones stay ticked while hidden. */
    private void renderChips() {
        if (b == null || !loaded) return;
        String q = b.amenitySearch.getText().toString().trim();
        b.amenityChips.removeAllViews();
        int noteRes = 0;
        if (catalog) {
            Set<Amenity> list = new LinkedHashSet<>();
            final Map<String, Integer> score = new HashMap<>();
            int best = 0;
            if (q.isEmpty()) {
                for (Amenity a : amenities) if (list.size() < POPULAR_SHOWN) list.add(a);
                for (Amenity a : amenities) if (selected.contains(a.id())) list.add(a);
                if (amenities.size() > POPULAR_SHOWN) noteRes = R.string.ex_amen_show_all;
            } else {
                for (Amenity a : amenities) {
                    int s = Fuzzy.score(q, a.name());
                    if (s > 0) { list.add(a); score.put(a.id(), s); best = Math.max(best, s); }
                }
                if (list.isEmpty()) noteRes = R.string.filter_amenity_none;
                else if (best < 80) noteRes = R.string.filter_amenity_didyoumean;
            }
            List<Amenity> sorted = new ArrayList<>(list);
            if (!q.isEmpty()) sorted.sort((x, y) -> score.get(y.id()) - score.get(x.id()));
            for (Amenity a : sorted) {
                Chip c = Chips.create(b.amenityChips, a, "", true, selected.contains(a.id()));
                c.setOnCheckedChangeListener((v, on) -> { if (on) selected.add(a.id()); else selected.remove(a.id()); });
                b.amenityChips.addView(c);
            }
        } else {
            Set<String> names = new LinkedHashSet<>();
            for (String n : ExploreRules.POPULAR) if (q.isEmpty() || Fuzzy.score(q, n) > 0) names.add(n);
            if (q.isEmpty()) names.addAll(selNames);
            if (names.isEmpty()) noteRes = R.string.filter_amenity_none;
            for (String n : names) {
                Chip c = Chips.create(b.amenityChips, n, true, selNames.contains(n));
                c.setOnCheckedChangeListener((v, on) -> { if (on) selNames.add(n); else selNames.remove(n); });
                b.amenityChips.addView(c);
            }
        }
        if (noteRes == 0) b.amenityNote.setVisibility(View.GONE);
        else { b.amenityNote.setText(noteRes); b.amenityNote.setVisibility(View.VISIBLE); }
    }

    /** Tall sheet (90% of the screen), always opened fully, and it shrinks when the keyboard is up. */
    @Override
    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (!(d instanceof BottomSheetDialog)) return;
        BottomSheetDialog bd = (BottomSheetDialog) d;
        FrameLayout f = bd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (f != null) {
            f.getLayoutParams().height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.9f);
            f.requestLayout();
            BottomSheetBehavior<FrameLayout> bh = BottomSheetBehavior.from(f);
            bh.setSkipCollapsed(true);
            bh.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
        if (bd.getWindow() != null) bd.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    @Override
    public void onDestroyView() { b = null; super.onDestroyView(); }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
        @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
    }
}
