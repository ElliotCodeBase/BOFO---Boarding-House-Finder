package com.bofo.admin;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

/** Fingerprint / face / screen-lock check. Fails CLOSED: with no screen lock set, sensitive actions are refused. */
final class Reauth {
    private static final int AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    private static long okAt;                                   // 60 s grace so a run of actions does not ask every time
    private Reauth() {}

    static boolean available(AppCompatActivity a) { return BiometricManager.from(a).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS; }

    static void ask(AppCompatActivity a, String title, Runnable ok, Runnable cancel) {
        if (System.currentTimeMillis() - okAt < 60_000 && okAt > 0) { ok.run(); return; }
        if (!available(a)) { Ui.toast(a, "Set a screen lock (PIN, pattern or fingerprint) on this phone to use BOFO Admin."); if (cancel != null) cancel.run(); return; }
        BiometricPrompt p = new BiometricPrompt(a, ContextCompat.getMainExecutor(a), new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) { okAt = System.currentTimeMillis(); ok.run(); }
            @Override public void onAuthenticationError(int code, CharSequence msg) { if (cancel != null) cancel.run(); }
        });
        p.authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(AUTH).build());
    }
    static void forget() { okAt = 0; }
}
