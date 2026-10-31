package com.bofo.finder.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bofo.finder.R;
import com.bofo.finder.databinding.SheetFilterBinding;
import com.bofo.finder.model.Amenity;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.Fuzzy;
import com.bofo.finder.util.Money;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Explore filters: monthly price range + amenities (the place must have ALL ticked ones). */
public class FilterSheet extends BottomSheetDialogFragment {

    private SheetFilterBinding b;
    private List<Amenity> amenities = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = SheetFilterBinding.inflate(inflater, container, false);
        selected.addAll(BrowseState.amenityIds);

        float lo = BrowseState.minPrice == null ? 0f : (float) Math.min(BrowseState.PRICE_MAX, Math.max(0, BrowseState.minPrice));
        float hi = BrowseState.maxPrice == null ? BrowseState.PRICE_MAX : (float) Math.min(BrowseState.PRICE_MAX, Math.max(lo, BrowseState.maxPrice));
        b.priceSlider.setValues(Arrays.asList(lo, hi));
        paintPrice();
        b.priceSlider.addOnChangeListener((s, v, fromUser) -> paintPrice());

        b.amenitySearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) { renderChips(); }
        });
        Async.run(() -> new BhService().amenities(), (List<Amenity> l) -> {
            if (b == null) return;
            amenities = l;
            renderChips();
        }, e -> {
            if (b == null) return;
            b.amenityNote.setText(R.string.filter_amenities_failed);
            b.amenityNote.setVisibility(View.VISIBLE);
        });

        b.reset.setOnClickListener(v -> { BrowseState.clearFilters(); BrowseState.bump(); dismiss(); });
        b.apply.setOnClickListener(v -> {
            List<Float> vals = b.priceSlider.getValues();
            float a = vals.get(0), z = vals.get(1);
            BrowseState.minPrice = a <= 0 ? null : (double) a;
            BrowseState.maxPrice = z >= BrowseState.PRICE_MAX ? null : (double) z;
            BrowseState.amenityIds.clear();
            BrowseState.amenityIds.addAll(selected);
            BrowseState.bump();
            dismiss();
        });
        return b.getRoot();
    }

    private void paintPrice() {
        List<Float> v = b.priceSlider.getValues();
        float a = v.get(0), z = v.get(1);
        String t;
        if (a <= 0 && z >= BrowseState.PRICE_MAX) t = getString(R.string.filter_price_any);
        else if (a <= 0) t = getString(R.string.filter_price_up_to, Money.peso(z));
        else if (z >= BrowseState.PRICE_MAX) t = getString(R.string.filter_price_from, Money.peso(a));
        else t = getString(R.string.filter_price_range, Money.peso(a), Money.peso(z));
        b.priceLabel.setText(getString(R.string.filter_price, t));
    }

    /** All amenities (most used first), or the fuzzy matches while the user types ("aircon", "wify"...). Ticked ones stay ticked while hidden. */
    private void renderChips() {
        if (b == null) return;
        String q = b.amenitySearch.getText().toString().trim();
        List<Amenity> list = new ArrayList<>();
        final java.util.Map<String, Integer> score = new java.util.HashMap<>();
        int best = 0;
        for (Amenity a : amenities) {
            if (q.isEmpty()) { list.add(a); continue; }
            int s = Fuzzy.score(q, a.name());
            if (s > 0) { list.add(a); score.put(a.id(), s); best = Math.max(best, s); }
        }
        if (!q.isEmpty()) list.sort((x, y) -> score.get(y.id()) - score.get(x.id()));
        b.amenityChips.removeAllViews();
        for (Amenity a : list) {
            Chip c = Chips.create(b.amenityChips, a.label(), true, selected.contains(a.id()));
            c.setOnCheckedChangeListener((v, on) -> { if (on) selected.add(a.id()); else selected.remove(a.id()); });
            b.amenityChips.addView(c);
        }
        if (q.isEmpty() || (!list.isEmpty() && best >= 80)) { b.amenityNote.setVisibility(View.GONE); }
        else {
            b.amenityNote.setText(list.isEmpty() ? R.string.filter_amenity_none : R.string.filter_amenity_didyoumean);
            b.amenityNote.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() { b = null; super.onDestroyView(); }
}
