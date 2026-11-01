package com.bofo.finder.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.SheetAdminMfaBinding;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.ErrorText;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * v2.0.119: two-step verification for ADMIN accounts (authenticator app, 6-digit code, Supabase TOTP).
 * One sheet, four states, picked from what the server says: nothing set up yet (INTRO, then SETUP with the key),
 * set up but this sign-in has not entered a code (ENTER), set up and verified in this sign-in (ON).
 * The server side is migration 017: once 'require_admin_mfa' is switched on, is_admin() is true only in a verified (aal2) session.
 */
public class AdminMfaSheet extends BottomSheetDialogFragment {

    public interface Done { void changed(); }

    private enum Mode { LOADING, INTRO, SETUP, ENTER, ON }

    private SheetAdminMfaBinding b;
    private Done done;
    private Mode mode = Mode.LOADING;
    private String factorId = "", secret = "", uri = "";
    private boolean busy;

    static void show(Fragment host, Done done) {
        if (!host.isAdded()) return;
        AdminMfaSheet s = new AdminMfaSheet();
        s.done = done;
        s.show(host.getChildFragmentManager(), "admin_mfa");
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = SheetAdminMfaBinding.inflate(inflater, container, false);
        b.mfaPrimary.setOnClickListener(v -> primary());
        b.mfaCopy.setOnClickListener(v -> copyKey());
        b.mfaOpen.setOnClickListener(v -> openAuthenticator());
        b.mfaTurnOff.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.mfa_turn_off_ask)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.mfa_turn_off, (d, w) -> turnOff())
                .show());
        b.mfaCode.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int bf, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) { refreshButton(); }
        });
        render();
        load();
        return b.getRoot();
    }

    @Override
    public void onDestroyView() { b = null; super.onDestroyView(); }

    /** Asks the server whether this admin already has an authenticator and whether this sign-in passed the code check. */
    private void load() {
        Async.run(() -> {
            AuthService a = AuthService.get();
            return new String[]{a.mfaVerifiedFactor(), a.aal()};
        }, r -> {
            if (b == null) return;
            factorId = r[0];
            mode = factorId.isEmpty() ? Mode.INTRO : "aal2".equals(r[1]) ? Mode.ON : Mode.ENTER;
            render();
        }, e -> { if (b != null) error(ErrorText.full("Could not check two-step verification", e)); });
    }

    private void primary() {
        if (busy) return;
        if (mode == Mode.INTRO) startSetup();
        else if (mode == Mode.SETUP || mode == Mode.ENTER) verify();
        else if (mode == Mode.ON) dismissAllowingStateLoss();
    }

    private void startSetup() {
        busy(true);
        Async.run(() -> AuthService.get().mfaEnroll(), r -> {
            busy(false);
            if (b == null) return;
            factorId = r[0]; secret = r[1]; uri = r[2];
            mode = Mode.SETUP;
            render();
        }, e -> { busy(false); error(ErrorText.full("Could not start the setup", e)); });
    }

    private void verify() {
        final String code = b.mfaCode.getText().toString().trim();
        if (code.length() != 6) return;
        busy(true);
        final boolean firstTime = mode == Mode.SETUP;
        Async.run(() -> { AuthService.get().mfaVerify(factorId, code); return Boolean.TRUE; }, ok -> {
            busy(false);
            toast(firstTime ? R.string.mfa_ok_on : R.string.mfa_done);
            if (done != null) done.changed();
            if (b == null) return;
            mode = Mode.ON;
            b.mfaCode.setText("");
            render();
        }, e -> {
            busy(false);
            // 400 / 422 = wrong or expired code: say it plainly instead of printing the server text
            boolean wrong = e instanceof SupabaseClient.ApiException && (((SupabaseClient.ApiException) e).status == 400 || ((SupabaseClient.ApiException) e).status == 422);
            error(wrong ? getString(R.string.mfa_bad_code) : ErrorText.full("Could not verify the code", e));
        });
    }

    private void turnOff() {
        busy(true);
        Async.run(() -> { AuthService.get().mfaRemove(factorId); return Boolean.TRUE; }, ok -> {
            busy(false);
            toast(R.string.mfa_ok_off);
            factorId = "";
            if (done != null) done.changed();
            if (b == null) return;
            mode = Mode.INTRO;
            render();
        }, e -> { busy(false); error(ErrorText.full("Could not turn it off", e)); });
    }

    private void copyKey() {
        Context c = requireContext();
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("BOFO setup key", secret));
        toast(R.string.mfa_copied);
    }

    private void openAuthenticator() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri))); }
        catch (Exception noApp) { toast(R.string.mfa_no_app); }
    }

    /* ---------- screen ---------- */
    private void render() {
        if (b == null) return;
        error(null);
        boolean needCode = mode == Mode.SETUP || mode == Mode.ENTER;
        b.mfaKeyBox.setVisibility(mode == Mode.SETUP ? View.VISIBLE : View.GONE);
        b.mfaCode.setVisibility(needCode ? View.VISIBLE : View.GONE);
        b.mfaTurnOff.setVisibility(mode == Mode.ON ? View.VISIBLE : View.GONE);
        switch (mode) {
            case INTRO:
                b.mfaBody.setText(R.string.mfa_intro); b.mfaPrimary.setText(R.string.mfa_start); break;
            case SETUP:
                b.mfaBody.setText(R.string.mfa_setup_body); b.mfaKey.setText(spaced(secret)); b.mfaPrimary.setText(R.string.mfa_verify_on); break;
            case ENTER:
                b.mfaBody.setText(R.string.mfa_enter_body); b.mfaPrimary.setText(R.string.mfa_verify); break;
            case ON:
                b.mfaBody.setText(R.string.mfa_on_body); b.mfaPrimary.setText(R.string.mfa_done); break;
            default:
                b.mfaBody.setText(""); b.mfaPrimary.setText(R.string.mfa_loading); break;
        }
        refreshButton();
    }

    private void refreshButton() {
        if (b == null) return;
        boolean needCode = mode == Mode.SETUP || mode == Mode.ENTER;
        boolean ready = mode != Mode.LOADING && (!needCode || b.mfaCode.getText().toString().trim().length() == 6);
        b.mfaPrimary.setEnabled(ready && !busy);
    }

    private void busy(boolean on) { busy = on; refreshButton(); if (b != null) b.mfaTurnOff.setEnabled(!on); }

    private void error(String t) {
        if (b == null) return;
        b.mfaMessage.setVisibility(t == null || t.isEmpty() ? View.GONE : View.VISIBLE);
        b.mfaMessage.setText(t == null ? "" : t);
    }

    private void toast(int res) {
        Context c = getContext();
        if (c != null) Toast.makeText(c.getApplicationContext(), res, Toast.LENGTH_SHORT).show();
    }

    /** "ABCDEFGH..." -> "ABCD EFGH ..." so the key is easy to read and type. */
    private static String spaced(String s) {
        StringBuilder o = new StringBuilder();
        for (int i = 0; i < s.length(); i++) { if (i > 0 && i % 4 == 0) o.append(' '); o.append(s.charAt(i)); }
        return o.toString();
    }
}
