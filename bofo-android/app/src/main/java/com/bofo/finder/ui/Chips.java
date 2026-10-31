package com.bofo.finder.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.bofo.finder.R;
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
}
