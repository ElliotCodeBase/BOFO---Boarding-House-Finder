package com.bofo.finder.ui;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.bofo.finder.R;
import com.bofo.finder.service.ReportService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.SuspendRules;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** v2.1.8: "Report this place" dialog: a reason from a list, optional details, Send. */
final class ReportDialog {
    private ReportDialog() {}

    /** Label of a reports.reason key ("scam" -> "Scam or fraud"); the key itself when unknown. */
    static String reasonLabel(Context c, String key) {
        int i = SuspendRules.reasonIndex(key);
        String[] labels = c.getResources().getStringArray(R.array.report_reasons);
        return i >= 0 && i < labels.length ? labels[i] : key;
    }

    static void show(Fragment f, String reportedUserId, String bhId) {
        if (!f.isAdded()) return;
        Context c = f.requireContext();
        float dp = f.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int m = Math.round(20 * dp);
        box.setPadding(m, Math.round(10 * dp), m, 0);

        TextView hint = new TextView(c);
        hint.setText(R.string.report_hint);
        box.addView(hint);

        Spinner reason = new Spinner(c);
        reason.setAdapter(new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, c.getResources().getStringArray(R.array.report_reasons)));
        box.addView(reason);

        EditText details = new EditText(c);
        details.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        details.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
        details.setHint(R.string.report_details_hint);
        details.setMaxLines(5);
        box.addView(details);

        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(c).setTitle(R.string.report_title).setView(box)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.report_send, null).create();
        d.setOnShowListener(x -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener((View v) -> {
            int pos = reason.getSelectedItemPosition();
            if (pos < 0 || pos >= SuspendRules.REASONS.length) return;
            final String key = SuspendRules.REASONS[pos];
            final String text = details.getText().toString();
            d.dismiss();
            Async.run(() -> { new ReportService().submit(reportedUserId, bhId, key, text); return Boolean.TRUE; },
                    ok -> ListArea.toast(f, f.getString(R.string.report_sent)),
                    e -> ListArea.toast(f, ReportService.isDuplicate(e) ? f.getString(R.string.report_duplicate)
                            : Connectivity.isNetworkError(e) ? f.getString(R.string.list_offline) : ErrorText.full(f.getString(R.string.report_error), e)));
        }));
        d.show();
    }
}
