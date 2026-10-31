package com.bofo.finder.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import android.content.res.ColorStateList;

import androidx.core.content.ContextCompat;

import com.bofo.finder.R;
import com.bofo.finder.model.Amenity;
import com.google.android.material.chip.Chip;

/** Creates the app's filter-style chips (the style needs XML, so they are inflated, not built in code). */
final class Chips {
    private Chips() {}

    static Chip create(ViewGroup parent, String text, boolean checkable, boolean checked) {
        Chip c = (Chip) LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chip_filter, parent, false);
        c.setText(text);
        c.setCheckable(checkable);
        c.setChecked(checkable && checked);
        if (!checkable) c.setClickable(false);
        return c;
    }

    /** An amenity chip: its name (+ optional suffix) with the app's own line icon in the primary colour. */
    static Chip create(ViewGroup parent, Amenity a, String suffix, boolean checkable, boolean checked) {
        Chip c = create(parent, a.name() + (suffix == null ? "" : suffix), checkable, checked);
        c.setChipIconResource(AmenityIcons.res(a.name()));
        c.setChipIconTint(ColorStateList.valueOf(ContextCompat.getColor(parent.getContext(), R.color.bofo_primary)));
        c.setChipIconSize(18f * parent.getResources().getDisplayMetrics().density);
        c.setChipIconVisible(true);
        return c;
    }
}
