package com.bofo.finder.ui;

import android.view.View;

import androidx.fragment.app.Fragment;

import com.bofo.finder.R;
import com.bofo.finder.util.RejectReasonRules;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** v2.4.12: the "why are you rejecting this?" dialog (quick chips + optional note). Used by the review screen and the Queue long-press menu. */
final class RejectDialog {
    private RejectDialog() {}

    static void ask(Fragment f, java.util.function.Consumer<String> done) {
        if (!f.isAdded()) return;
        final int[] ids = {R.string.vr_reason_blurry, R.string.vr_reason_cropped, R.string.vr_reason_back, R.string.vr_reason_expired, R.string.vr_reason_face,
                R.string.vr_reason_name, R.string.vr_reason_type, R.string.vr_reason_other};
        android.content.Context c = f.requireContext();
        int m = Math.round(20 * f.getResources().getDisplayMetrics().density);
        android.widget.LinearLayout box = new android.widget.LinearLayout(c);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(m, m / 2, m, 0);
        com.google.android.material.chip.ChipGroup group = new com.google.android.material.chip.ChipGroup(c);
        group.setSingleSelection(true);
        for (int i = 0; i < ids.length; i++) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(c, null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setText(ids[i]);
            chip.setCheckable(true);
            chip.setTag(i);
            group.addView(chip);
        }
        android.widget.EditText note = new android.widget.EditText(c);
        note.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setHint(R.string.vr_note_hint);
        note.setMaxLines(4);
        note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(RejectReasonRules.MAX)});
        group.setOnCheckedStateChangeListener((g, checked) -> {
            boolean other = !checked.isEmpty() && (int) g.findViewById(checked.get(0)).getTag() == ids.length - 1;
            note.setHint(other ? R.string.vr_note_hint_other : R.string.vr_note_hint);
        });
        box.addView(group);
        box.addView(note);
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(c).setTitle(R.string.vr_reject_title).setView(box)
                .setNegativeButton(R.string.common_cancel, null).setPositiveButton(R.string.review_reject, null).create();
        d.setOnShowListener(x -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (group.getCheckedChipId() == View.NO_ID) { ListArea.toast(f, f.getString(R.string.vr_reason_needed)); return; }
            int idx = (int) group.findViewById(group.getCheckedChipId()).getTag();
            boolean other = idx == ids.length - 1;
            String reason = RejectReasonRules.compose(f.getString(ids[idx]), other, note.getText().toString());
            if (reason == null) { note.setError(f.getString(R.string.vr_note_needed)); return; }
            d.dismiss();
            done.accept(reason);
        }));
        d.show();
    }
}
