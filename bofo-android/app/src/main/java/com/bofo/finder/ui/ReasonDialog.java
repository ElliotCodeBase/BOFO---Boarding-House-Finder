package com.bofo.finder.ui;

import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.bofo.finder.R;
import com.bofo.finder.util.AdminRules;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.function.Consumer;

/** Small "type a reason" dialog for the admin screens (reject, take down, refund). The reason is cleaned (trimmed, max 300) before it is handed back. */
final class ReasonDialog {
    private ReasonDialog() {}

    static void ask(Fragment f, @StringRes int title, @StringRes int ok, boolean required, Consumer<String> done) {
        if (!f.isAdded()) return;
        EditText in = new EditText(f.requireContext());
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        in.setHint(required ? R.string.admin_reason_hint : R.string.admin_reason_hint_optional);
        in.setMaxLines(4);
        FrameLayout box = new FrameLayout(f.requireContext());
        int m = Math.round(20 * f.getResources().getDisplayMetrics().density);
        box.setPadding(m, m / 2, m, 0);
        box.addView(in);
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(f.requireContext()).setTitle(title).setView(box)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(ok, null).create();
        d.setOnShowListener(x -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String r = AdminRules.clean(in.getText().toString());
            if (required && r.isEmpty()) { in.setError(f.getString(R.string.admin_reason_needed)); return; }
            d.dismiss();
            done.accept(r);
        }));
        d.show();
    }
}
