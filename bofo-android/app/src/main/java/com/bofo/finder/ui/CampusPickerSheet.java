package com.bofo.finder.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bofo.finder.R;
import com.bofo.finder.databinding.SheetCampusBinding;
import com.bofo.finder.model.College;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.ListRules;
import com.bofo.finder.util.Regions;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pick the campus Home and Explore measure distance from. Saved on the profile as default_college_id. */
public class CampusPickerSheet extends BottomSheetDialogFragment {

    private SheetCampusBinding b;
    private final List<College> shown = new ArrayList<>();      // parallel to the rows; null = "no campus"
    private List<College> ordered = new ArrayList<>();
    private ArrayAdapter<String> rows;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = SheetCampusBinding.inflate(inflater, container, false);
        rows = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new ArrayList<String>());
        b.campusList.setAdapter(rows);
        b.campusList.setOnItemClickListener((p, v, pos, id) -> {
            if (pos < 0 || pos >= shown.size()) return;
            BrowseState.setCampus(shown.get(pos));
            dismiss();
        });
        b.campusSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) { render(); }
        });
        rows.add(getString(R.string.campus_loading));
        BrowseState.ensureLoaded(() -> {
            if (b == null) return;
            College now = BrowseState.campus();
            Double lat = now == null ? null : now.lat(), lng = now == null ? null : now.lng();
            if (now == null && BrowseState.me != null) {
                Regions.Region r = Regions.byKey(BrowseState.me.region());
                if (r != null) { lat = r.lat(); lng = r.lng(); }
            }
            ordered = ListRules.nearestFirst(BrowseState.colleges, lat, lng);
            render();
        }, e -> { if (b != null) { rows.clear(); rows.add(getString(R.string.list_error)); } });
        return b.getRoot();
    }

    private void render() {
        if (b == null) return;
        String q = b.campusSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        String current = BrowseState.collegeId;
        shown.clear();
        rows.clear();
        if (q.isEmpty()) { shown.add(null); rows.add(getString(R.string.campus_none)); }
        for (College c : ordered) {
            if (!q.isEmpty() && !c.name().toLowerCase(Locale.ROOT).contains(q)) continue;
            shown.add(c);
            rows.add((c.id().equals(current) ? "\u2713 " : "") + c.name());
        }
        rows.notifyDataSetChanged();
    }

    @Override
    public void onDestroyView() { b = null; super.onDestroyView(); }
}
