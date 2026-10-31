package com.bofo.finder.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.config.AppConfig;
import com.bofo.finder.databinding.FragmentLoginBinding;
import com.bofo.finder.model.Role;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.Validate;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

/**
 * Sign-in screen: Google (native sheet through the Credential Manager, or the browser as a fallback), e-mail code, or e-mail + password.
 * After any success {@link AuthRouter} decides the next screen.
 */
public class LoginFragment extends Fragment {

    private FragmentLoginBinding b;
    private boolean passwordMode;
    private boolean codeSent;
    private boolean busy;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentLoginBinding.inflate(inflater, container, false);
        if (state != null) { passwordMode = state.getBoolean("pw"); codeSent = state.getBoolean("sent"); }

        b.google.setOnClickListener(v -> googleNative());
        b.googleBrowser.setOnClickListener(v -> googleBrowser());
        b.sendCode.setOnClickListener(v -> sendCode());
        b.verify.setOnClickListener(v -> verify());
        b.signIn.setOnClickListener(v -> password(false));
        b.signUp.setOnClickListener(v -> password(true));
        b.toggleMode.setOnClickListener(v -> { passwordMode = !passwordMode; message(null); render(); });
        b.legalNote.setOnClickListener(v -> LegalSheet.show(getParentFragmentManager(), LegalText.Doc.TERMS));
        // DEBUG builds with dev.* test accounts in config.local.properties: Student / Owner / Admin force-login buttons.
        b.devGroup.setVisibility(AppConfig.hasDevAccounts() ? View.VISIBLE : View.GONE);
        b.devStudent.setOnClickListener(v -> forceLogin(Role.STUDENT));
        b.devOwner.setOnClickListener(v -> forceLogin(Role.OWNER));
        b.devAdmin.setOnClickListener(v -> forceLogin(Role.ADMIN));
        render();
        return b.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("pw", passwordMode);
        out.putBoolean("sent", codeSent);
    }

    @Override
    public void onDestroyView() { b = null; super.onDestroyView(); }

    /* ---------- UI state ---------- */
    private void render() {
        b.groupCode.setVisibility(passwordMode ? View.GONE : View.VISIBLE);
        b.groupPassword.setVisibility(passwordMode ? View.VISIBLE : View.GONE);
        b.codeLayout.setVisibility(!passwordMode && codeSent ? View.VISIBLE : View.GONE);
        b.verify.setVisibility(!passwordMode && codeSent ? View.VISIBLE : View.GONE);
        b.sendCode.setText(codeSent ? R.string.login_resend : R.string.login_send_code);
        b.toggleMode.setText(passwordMode ? R.string.login_use_code : R.string.login_use_password);
    }

    private void setBusy(boolean on) {
        busy = on;
        if (b == null) return;
        b.progress.setVisibility(on ? View.VISIBLE : View.GONE);
        for (View v : new View[]{b.google, b.googleBrowser, b.sendCode, b.verify, b.signIn, b.signUp, b.toggleMode, b.devStudent, b.devOwner, b.devAdmin}) v.setEnabled(!on);
    }

    private void message(String text) {
        if (b == null) return;
        b.message.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
        b.message.setText(text == null ? "" : text);
        if (text != null && !text.isEmpty()) showMessageNow();
    }

    /** The message sits below the buttons: close the keyboard and scroll to it so an error is never hidden. */
    private void showMessageNow() {
        View root = b.getRoot();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        root.post(() -> {
            if (b != null && b.getRoot() instanceof android.widget.ScrollView)
                ((android.widget.ScrollView) b.getRoot()).smoothScrollTo(0, b.message.getBottom());
        });
    }

    private String email() { return b.email.getText() == null ? "" : b.email.getText().toString().trim(); }

    private boolean emailOk() {
        boolean ok = Validate.email(email());
        b.emailLayout.setError(ok ? null : getString(R.string.login_need_email));
        return ok;
    }

    /* ---------- e-mail code ---------- */
    private void sendCode() {
        if (busy || !emailOk()) return;
        setBusy(true); message(null);
        final String mail = email();
        Async.run(() -> { AuthService.get().sendEmailCode(mail); return Boolean.TRUE; }, ok -> {
            setBusy(false);
            if (b == null) return;
            codeSent = true; render();
            message(getString(R.string.login_code_sent, mail));
        }, e -> fail("Could not send the code", e));
    }

    private void verify() {
        if (busy || !emailOk()) return;
        final String code = b.code.getText() == null ? "" : b.code.getText().toString().trim();
        if (code.length() < 4) { b.codeLayout.setError(getString(R.string.login_need_code)); return; }
        b.codeLayout.setError(null);
        setBusy(true); message(null);
        final String mail = email();
        Async.run(() -> { AuthService.get().verifyEmailCode(mail, code); return Boolean.TRUE; }, ok -> signedIn(), e -> fail("The code was not accepted", e));
    }

    /* ---------- e-mail + password ---------- */
    private void password(boolean create) {
        if (busy || !emailOk()) return;
        final String pw = b.password.getText() == null ? "" : b.password.getText().toString();
        if (pw.length() < 6) { b.passwordLayout.setError(getString(R.string.login_need_password)); return; }
        b.passwordLayout.setError(null);
        setBusy(true); message(null);
        final String mail = email();
        Async.run(() -> {
            if (create) AuthService.get().signUpPassword(mail, pw); else AuthService.get().signInPassword(mail, pw);
            return Boolean.TRUE;
        }, ok -> signedIn(), e -> {
            if (e instanceof AuthService.ConfirmEmailException) { setBusy(false); message(e.getMessage()); }
            else fail(create ? "Could not create the account" : "Could not sign in", e);
        });
    }

    /* ---------- DEBUG: force login with a test account ---------- */
    private void forceLogin(Role role) {
        if (busy) return;
        setBusy(true);
        message(getString(R.string.login_dev_signing, role.db()));
        Async.run(() -> { AuthService.get().devSignIn(role); return Boolean.TRUE; }, ok -> signedIn(),
                e -> fail("Test sign-in as " + role.db() + " failed", e));
    }

    /* ---------- Google ---------- */
    private void googleNative() {
        if (busy) return;
        if (!AppConfig.googleNative()) { message(getString(R.string.login_google_missing)); return; }
        setBusy(true); message(null);
        CredentialManager cm = CredentialManager.create(requireContext());
        GetSignInWithGoogleOption opt = new GetSignInWithGoogleOption.Builder(AppConfig.googleWebClientId()).build();
        GetCredentialRequest req = new GetCredentialRequest.Builder().addCredentialOption(opt).build();
        cm.getCredentialAsync(requireActivity(), req, new CancellationSignal(), ContextCompat.getMainExecutor(requireContext()),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(GetCredentialResponse r) {
                        if (b == null) return;
                        Credential c = r.getCredential();
                        if (c instanceof CustomCredential && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                            try {
                                final String idToken = GoogleIdTokenCredential.createFrom(c.getData()).getIdToken();
                                Async.run(() -> { AuthService.get().signInGoogleIdToken(idToken); return Boolean.TRUE; },
                                        ok -> signedIn(), e -> fail("Google sign-in failed", e));
                            } catch (Exception e) { fail("Google sign-in failed", e); }
                        } else {
                            setBusy(false);
                            message("Google sign-in returned something unexpected. Try the browser option below.");
                        }
                    }
                    @Override public void onError(GetCredentialException e) {
                        if (b == null) return;
                        android.util.Log.e("BOFO", "Google credential error", e);
                        setBusy(false);
                        if (e instanceof GetCredentialCancellationException) { message(getString(R.string.login_google_cancelled)); return; }
                        // NoCredentialException = no Google account on the phone, or the Android OAuth client / SHA-1 is not set up.
                        message("Google sign-in did not work on this phone (" + e.getClass().getSimpleName() + ": " + e.getMessage()
                                + ").\nUse \"Continue in the browser\" or e-mail instead.");
                    }
                });
    }

    private void googleBrowser() {
        if (busy) return;
        try {
            String url = AuthService.get().beginBrowserGoogle();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            message("Finish the Google sign-in in the browser. It brings you back here.");
        } catch (ActivityNotFoundException e) {
            message("No web browser was found on this phone.");
        } catch (Exception e) {
            message(ErrorText.full("Could not start the browser sign-in", e));
        }
    }

    /* ---------- results ---------- */
    private void signedIn() {
        if (b == null) return;
        AuthRouter.route(NavHostFragment.findNavController(this), e -> fail("Signed in, but the profile could not be loaded", e));
    }

    private void fail(String headline, Throwable e) {
        android.util.Log.e("BOFO", headline, e);
        setBusy(false);
        message(ErrorText.full(headline, e));
    }
}
