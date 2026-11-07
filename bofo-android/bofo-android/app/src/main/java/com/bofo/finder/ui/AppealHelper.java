package com.bofo.finder.ui;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.bofo.finder.R;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.ErrorText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

/**
 * v2.12.0: "Appeal this decision" on the banned and suspended screens. One appeal per decision (the server refuses a second one: migration 044,
 * submit_appeal / my_appeal). The text is sent only to BOFO staff; nothing is stored on the phone. Pure UI glue, no admin code.
 */
public final class AppealHelper {
    public static final int MIN = 10, MAX = 1000;       // same as appeals.message in 044

    private AppealHelper() {}

    public static boolean messageOk(String s) { int n = s == null ? 0 : s.trim().length(); return n >= MIN && n <= MAX; }

    /** Hides both views, asks the server whether this decision already has an appeal, then shows the button or the status. */
    public static void wire(Fragment f, Button button, TextView status) {
        button.setVisibility(View.GONE); status.setVisibility(View.GONE);
        button.setOnClickListener(v -> ask(f, button, status));
        Async.run(() -> SupabaseClient.get().rpc("my_appeal", new JSONObject()), r -> { if (f.isAdded()) show(f.requireContext(), button, status, r.optBoolean("exists", false), r.optString("status"), r.optString("note", "")); },
                e -> { if (f.isAdded()) show(f.requireContext(), button, status, false, "", ""); });      // cannot tell: allow the try, the server refuses a duplicate
    }

    private static void show(Context c, Button button, TextView status, boolean exists, String state, String noteText) {
        if (!exists) { button.setVisibility(View.VISIBLE); status.setVisibility(View.GONE); return; }
        button.setVisibility(View.GONE); status.setVisibility(View.VISIBLE);
        String note = noteText == null ? "" : noteText.trim();
        if ("pending".equals(state)) status.setText(c.getString(R.string.appeal_pending));
        else status.setText(c.getString(R.string.appeal_rejected) + (note.isEmpty() ? "" : "\n" + c.getString(R.string.appeal_note, note)));
    }

    private static void ask(Fragment f, Button button, TextView status) {
        if (!f.isAdded()) return;
        Context c = f.requireContext();
        EditText in = new EditText(c);
        in.setHint(R.string.appeal_hint); in.setMinLines(4);
        in.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        in.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(MAX)});
        LinearLayout box = new LinearLayout(c); int pad = Math.round(20 * c.getResources().getDisplayMetrics().density); box.setPadding(pad, pad / 2, pad, 0); box.addView(in, new LinearLayout.LayoutParams(-1, -2));
        new MaterialAlertDialogBuilder(c).setTitle(R.string.appeal_title).setMessage(R.string.appeal_body).setView(box)
                .setNegativeButton(R.string.appeal_cancel, null)
                .setPositiveButton(R.string.appeal_send, (d, w) -> {
                    final String msg = in.getText().toString().trim();
                    if (!messageOk(msg)) { Toast.makeText(c.getApplicationContext(), R.string.appeal_too_short, Toast.LENGTH_LONG).show(); return; }
                    button.setEnabled(false);
                    Async.run(() -> SupabaseClient.get().rpc("submit_appeal", new JSONObject().put("msg", msg)), r -> {
                        Toast.makeText(c.getApplicationContext(), R.string.appeal_sent, Toast.LENGTH_LONG).show();
                        if (f.isAdded()) { button.setEnabled(true); show(f.requireContext(), button, status, true, "pending", ""); }
                    }, e -> {
                        Toast.makeText(c.getApplicationContext(), ErrorText.full(c.getString(R.string.appeal_error), e), Toast.LENGTH_LONG).show();
                        if (f.isAdded()) button.setEnabled(true);
                    });
                }).show();
    }
}
