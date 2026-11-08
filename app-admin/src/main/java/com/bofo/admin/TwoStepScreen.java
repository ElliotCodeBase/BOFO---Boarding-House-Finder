package com.bofo.admin;

import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bofo.shared.Async;
import com.bofo.shared.TwoStepRules;

import org.json.JSONObject;

/**
 * Two-step sign-in (authenticator app) set up inside the admin app. Start (fingerprint / screen lock) -> the server makes a secret -> the person adds it
 * to their authenticator app (link or typed key) -> types the 6-digit code -> the session becomes two-step (aal2). Only SETTING UP lives here:
 * removing or resetting someone's two-step stays a server action (admin_reset_mfa, mfa_manage). The whole app is FLAG_SECURE, so the key cannot be screenshotted.
 * {@code done} runs after a successful setup (used from the "No access" screen to retry the role check); null = just show the status again.
 */
final class TwoStepScreen extends FrameLayout {
    private final MainActivity act; private final Runnable done; private final LinearLayout col; private boolean busy;

    TwoStepScreen(MainActivity a, Runnable done) {
        super(a); act = a; this.done = done;
        col = Ui.col(a); int p = Ui.dp(a, 16); col.setPadding(p, p, p, p);
        addView(MainActivity.scroll(a, col), new LayoutParams(-1, -1));
        status();
    }

    @Override protected void onVisibilityAggregated(boolean isVisible) { super.onVisibilityAggregated(isVisible); act.keyVisible(isVisible); }

    private void status() {
        col.removeAllViews();
        col.addView(Ui.text(act, "Two-step sign-in", R.style.Text_Bofo_Title));
        col.addView(Ui.text(act, "Checking...", R.style.Text_Bofo_Caption));
        Async.run(() -> AdminAuth.get().totpFactorId() != null, this::showStatus, e -> { col.removeAllViews(); col.addView(Ui.text(act, Ui.err(e), R.style.Text_Bofo_Body)); });
    }

    private void showStatus(boolean on) {
        col.removeAllViews();
        col.addView(Ui.text(act, "Two-step sign-in", R.style.Text_Bofo_Title));
        col.addView(Ui.pill(act, on ? "ON" : "OFF", on ? R.color.bofo_success : R.color.bofo_error, on ? R.color.bofo_success_tint : R.color.bofo_error_tint));
        if (on) {
            col.addView(Ui.text(act, "You sign in with a 6-digit code from your authenticator app. If you lose that phone, ask an owner to reset it.", R.style.Text_Bofo_Body));
            return;
        }
        col.addView(Ui.text(act, "Add a second lock to your account: a 6-digit code from an authenticator app (Google Authenticator, Microsoft Authenticator, Aegis...).", R.style.Text_Bofo_Body));
        android.widget.Button go = Ui.button(act, "Set up", true);
        go.setOnClickListener(v -> Reauth.ask(act, "Set up two-step sign-in", this::start, null));
        col.addView(go);
    }

    private void start() {
        if (busy) return;
        busy = true;
        Async.run(() -> AdminAuth.get().enrollTotp(), j -> { busy = false; showSetup(j); }, e -> { busy = false; Ui.toast(act, Ui.err(e)); });
    }

    private void showSetup(JSONObject j) {
        final String id = j.optString("id"), secret = j.optString("secret"), uri = j.optString("uri");
        col.removeAllViews();
        col.addView(Ui.text(act, "Set up two-step sign-in", R.style.Text_Bofo_Title));
        col.addView(Ui.text(act, "1. Add this account to your authenticator app.", R.style.Text_Bofo_Section));
        if (TwoStepRules.validUri(uri)) {
            android.widget.Button open = Ui.button(act, "Open authenticator app", true);
            open.setOnClickListener(v -> {
                try { act.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))); }
                catch (android.content.ActivityNotFoundException e) { Ui.toast(act, "No authenticator app found. Install one, or type the key by hand."); }
            });
            col.addView(open);
        }
        col.addView(Ui.text(act, "Or type this key (time based):", R.style.Text_Bofo_Caption));
        TextView key = Ui.text(act, TwoStepRules.groupSecret(secret), R.style.Text_Bofo_Section);
        key.setTypeface(android.graphics.Typeface.MONOSPACE); key.setTextIsSelectable(true);
        col.addView(key);
        col.addView(Ui.text(act, "2. Type the 6-digit code it shows.", R.style.Text_Bofo_Section));
        EditText code = new EditText(act); code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); code.setHint("123456");
        code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(7)});
        col.addView(code);
        android.widget.Button ok = Ui.button(act, "Turn on", true);
        ok.setOnClickListener(v -> {
            String c = TwoStepRules.cleanCode(code.getText().toString());
            if (!TwoStepRules.codeReady(c)) { code.setError("6 digits"); return; }
            if (busy) return;
            busy = true; ok.setEnabled(false);
            Async.run(() -> { AdminAuth.get().verifyTotp(id, c); return Boolean.TRUE; },
                    r -> { busy = false; Ui.toast(act, "Two-step sign-in is on."); if (done != null) done.run(); else status(); },
                    e -> { busy = false; ok.setEnabled(true); Ui.toast(act, "That code did not work. Use the newest code."); });
        });
        col.addView(ok);
        android.widget.Button cancel = Ui.button(act, "Cancel", false);
        cancel.setOnClickListener(v -> status());
        col.addView(cancel);
    }
}
