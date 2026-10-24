package com.bofo.finder.android;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.Executor;

/**
 * Shows Google's account sheet ("Continue as ...") on top of the app and hands the Google ID token back to the
 * JavaFX side through a file in the app's private folder (see NativeGoogle.java). Started by the link
 * bofo://google-signin?client=WEB_CLIENT_ID&nonce=SHA256_HEX_OF_NONCE (declared in AndroidManifest.xml).
 * Android Java compiled by GluonFX at source level 1.7, so: anonymous classes, no lambdas.
 */
public class GoogleSignInActivity extends Activity {
    private static final String RESULT = "google-signin.json", STARTED = "google-signin.started";
    private boolean done;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        write(STARTED, "1");                                       // tells the app this screen is alive
        Uri u = getIntent() == null ? null : getIntent().getData();
        String client = u == null ? null : u.getQueryParameter("client");
        String nonce = u == null ? null : u.getQueryParameter("nonce");
        if (client == null || client.length() == 0) { fail("missing client id"); return; }

        try {
            GetGoogleIdOption.Builder option = new GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)          // also offer accounts that never used BOFO
                    .setServerClientId(client)                     // the WEB client id (same one Supabase has)
                    .setAutoSelectEnabled(false);
            if (nonce != null && nonce.length() > 0) option.setNonce(nonce);
            GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option.build()).build();

            CredentialManager.create(this).getCredentialAsync(this, request, new CancellationSignal(), new Executor() {
                @Override public void execute(Runnable r) { new Handler(Looper.getMainLooper()).post(r); }
            }, new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                @Override public void onResult(GetCredentialResponse response) { handle(response.getCredential()); }
                @Override public void onError(GetCredentialException e) {
                    fail(e instanceof GetCredentialCancellationException ? "cancelled" : e.getType() + ": " + e.getMessage());
                }
            });
        } catch (Throwable t) { fail(String.valueOf(t)); }
    }

    private void handle(Credential c) {
        try {
            if (c instanceof CustomCredential && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                GoogleIdTokenCredential g = GoogleIdTokenCredential.createFrom(((CustomCredential) c).getData());
                JSONObject j = new JSONObject();
                j.put("id_token", g.getIdToken());
                write(RESULT, j.toString());
                finishOnce();
            } else fail("unexpected credential type");
        } catch (Throwable t) { fail(String.valueOf(t)); }
    }

    private void fail(String why) {
        try { write(RESULT, new JSONObject().put("error", why).toString()); } catch (Throwable ignored) { }
        finishOnce();
    }

    private void finishOnce() { if (!done) { done = true; finish(); } }

    /** Writes to a temp file first, then renames, so the app never reads a half-written result. */
    private void write(String name, String text) {
        try {
            File dir = getFilesDir();
            File tmp = new File(dir, name + ".tmp"), dst = new File(dir, name);
            FileOutputStream out = new FileOutputStream(tmp);
            try { out.write(text.getBytes("UTF-8")); } finally { out.close(); }
            if (dst.exists()) dst.delete();
            tmp.renameTo(dst);
        } catch (Throwable ignored) { }
    }
}
