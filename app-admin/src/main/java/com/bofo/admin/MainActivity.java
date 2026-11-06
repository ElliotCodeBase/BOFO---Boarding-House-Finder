package com.bofo.admin;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.bofo.shared.Async;
import com.bofo.shared.AutoLockRules;
import com.bofo.shared.PermissionRules;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.Executors;

/**
 * One activity, no fragments: Login -> (two-step code) -> role check -> shell with tabs the role may use.
 * FLAG_SECURE is on for the WHOLE app (screenshots, recents thumbnail and screen recording are blocked): every screen shows people.
 */
public final class MainActivity extends AppCompatActivity {
    private FrameLayout root;
    private Access access;
    private long stoppedAt;
    private boolean locked;
    private String loginNote = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.bofo_bg));
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, in) -> {
            androidx.core.graphics.Insets i = in.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return in;
        });
        if (AdminAuth.get().hasSession()) gate(); else login();
    }

    @Override protected void onStop() { super.onStop(); stoppedAt = System.currentTimeMillis(); }

    @Override protected void onStart() {
        super.onStart();
        if (access != null && !locked && AutoLockRules.shouldLock(true, stoppedAt, System.currentTimeMillis())) lock();
    }

    private void show(View v) { root.removeAllViews(); root.addView(v, new FrameLayout.LayoutParams(-1, -1)); }

    private LinearLayout centered() {
        LinearLayout l = Ui.col(this); l.setGravity(Gravity.CENTER); int p = Ui.dp(this, 28); l.setPadding(p, p, p, p); return l;
    }

    /* ---------------- login (Google only) ---------------- */
    private void login() {
        access = null;
        LinearLayout l = centered();
        l.addView(Ui.text(this, "BOFO Admin", R.style.Text_Bofo_Title));
        l.addView(Ui.text(this, "Sign in with the Google account your owner invited.", R.style.Text_Bofo_Caption));
        if (!loginNote.isEmpty()) { TextView n = Ui.text(this, loginNote, R.style.Text_Bofo_Body); n.setTextColor(getColor(R.color.bofo_error)); n.setPadding(0, Ui.dp(this, 16), 0, 0); l.addView(n); }
        l.addView(Ui.space(this, 24));
        android.widget.Button g = Ui.button(this, "Continue with Google", true);
        g.setOnClickListener(v -> google(g));
        l.addView(g);
        show(l);
    }

    private void google(View btn) {
        btn.setEnabled(false);
        try {
            byte[] r = new byte[32]; new SecureRandom().nextBytes(r);
            String raw = android.util.Base64.encodeToString(r, android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
            StringBuilder hex = new StringBuilder();
            for (byte x : MessageDigest.getInstance("SHA-256").digest(raw.getBytes("UTF-8"))) hex.append(String.format("%02x", x));
            GetGoogleIdOption o = new GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(hex.toString()).build();
            CredentialManager.create(this).getCredentialAsync(this, new GetCredentialRequest.Builder().addCredentialOption(o).build(), null,
                    Executors.newSingleThreadExecutor(), new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override public void onResult(GetCredentialResponse res) {
                            try {
                                if (!(res.getCredential() instanceof CustomCredential c) || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) throw new IllegalStateException();
                                String token = GoogleIdTokenCredential.createFrom(c.getData()).getIdToken();
                                Async.run(() -> { AdminAuth.get().signInGoogle(token, raw); return Boolean.TRUE; }, ok -> gate(),
                                        e -> { btn.setEnabled(true); Ui.toast(MainActivity.this, Ui.err(e)); });
                            } catch (Exception e) { Async.post(() -> { btn.setEnabled(true); Ui.toast(MainActivity.this, "Google sign-in failed."); }); }
                        }
                        @Override public void onError(GetCredentialException e) { Async.post(() -> { btn.setEnabled(true); Ui.toast(MainActivity.this, "Google sign-in was cancelled or failed."); }); }
                    });
        } catch (Exception e) { btn.setEnabled(true); Ui.toast(this, "Google sign-in failed."); }
    }

    /* ---------------- gate: two-step code, then ask the server for the role ---------------- */
    private void gate() {
        LinearLayout l = centered(); l.addView(Ui.text(this, "Checking your access...", R.style.Text_Bofo_Section)); show(l);
        Async.run(() -> {
            AdminAuth a = AdminAuth.get();
            String factor = a.totpFactorId();
            if (factor != null && !a.isAal2()) return new Object[]{"mfa", factor};
            Access ac = new Access(new JSONObject(com.bofo.shared.Api.get().rpc("my_admin_access", new JSONObject())));
            if (ac.active) { try { com.bofo.shared.Api.get().rpc("admin_touch_session", new JSONObject().put("device_", android.os.Build.MODEL)); } catch (Exception ignored) { } }
            return new Object[]{"ok", ac};
        }, r -> {
            if ("mfa".equals(r[0])) { mfa((String) r[1]); return; }
            Access ac = (Access) r[1];
            if (!ac.active) { noAccess(); return; }
            access = ac; locked = false; shell();
        }, e -> {
            if (e instanceof com.bofo.shared.Api.ApiException x && x.status == 401) { AdminAuth.get().signOut(); loginNote = ""; login(); return; }
            LinearLayout l = centered(); l.addView(Ui.text(this, Ui.err(e), R.style.Text_Bofo_Body));
            android.widget.Button retry = Ui.button(this, "Try again", true); retry.setOnClickListener(v -> gate()); l.addView(retry);
            android.widget.Button out = Ui.button(this, "Sign out", false); out.setOnClickListener(v -> { AdminAuth.get().signOut(); login(); }); l.addView(out);
            show(l);
        });
    }

    /** No active role: sign out at once and say so on the login screen. */
    private void noAccess() {
        Async.run(() -> { AdminAuth.get().signOut(); return Boolean.TRUE; }, ok -> {
            loginNote = "No access. This Google account has no active BOFO Admin role. Ask the owner for an invite.";
            login(); loginNote = "";
        });
    }

    private void mfa(String factorId) {
        LinearLayout l = centered();
        l.addView(Ui.text(this, "Two-step code", R.style.Text_Bofo_Title));
        l.addView(Ui.text(this, "Enter the 6-digit code from your authenticator app.", R.style.Text_Bofo_Caption));
        EditText code = new EditText(this); code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); code.setHint("123456");
        code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)}); l.addView(code);
        android.widget.Button go = Ui.button(this, "Verify", true);
        go.setOnClickListener(v -> {
            String c = code.getText().toString().trim();
            if (c.length() != 6) { code.setError("6 digits"); return; }
            go.setEnabled(false);
            Async.run(() -> { AdminAuth.get().verifyTotp(factorId, c); return Boolean.TRUE; }, ok -> gate(), e -> { go.setEnabled(true); Ui.toast(this, "That code did not work. Try the newest one."); });
        });
        l.addView(go);
        android.widget.Button out = Ui.button(this, "Sign out", false); out.setOnClickListener(v -> { Async.run(() -> { AdminAuth.get().signOut(); return 1; }, ok -> login()); }); l.addView(out);
        show(l);
    }

    /* ---------------- auto-lock ---------------- */
    private void lock() {
        locked = true; Reauth.forget();
        LinearLayout l = centered();
        l.addView(Ui.text(this, "BOFO Admin is locked", R.style.Text_Bofo_Title));
        android.widget.Button u = Ui.button(this, "Unlock", true);
        u.setOnClickListener(v -> Reauth.ask(this, "Unlock BOFO Admin", () -> { locked = false; shell(); }, null));
        l.addView(u);
        android.widget.Button out = Ui.button(this, "Sign out", false); out.setOnClickListener(v -> { Async.run(() -> { AdminAuth.get().signOut(); return 1; }, ok -> login()); }); l.addView(out);
        show(l);
        if (Reauth.available(this)) u.performClick();
        else { Ui.toast(this, "No screen lock on this phone: signed out for safety."); Async.run(() -> { AdminAuth.get().signOut(); return 1; }, ok -> login()); }
    }

    /* ---------------- shell: only the tabs this role can use ---------------- */
    private void shell() {
        LinearLayout page = Ui.col(this);
        LinearLayout top = Ui.row(this); top.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 4));
        TextView who = Ui.text(this, access.label + "  |  " + AdminAuth.get().email(), R.style.Text_Bofo_Caption);
        top.addView(who, new LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.Button out = Ui.button(this, "Sign out", false);
        out.setOnClickListener(v -> new MaterialAlertDialogBuilder(this).setTitle("Sign out?").setNegativeButton("Cancel", null)
                .setPositiveButton("Sign out", (d, w) -> Async.run(() -> { AdminAuth.get().signOut(); return 1; }, ok -> login())).show());
        top.addView(out);
        page.addView(top);
        FrameLayout body = new FrameLayout(this);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        BottomNavigationView nav = new BottomNavigationView(this);
        nav.setBackgroundColor(getColor(R.color.bofo_white));
        page.addView(nav, new LinearLayout.LayoutParams(-1, -2));

        int id = 0; int first = -1;
        String[] titles = {"Home", "IDs", "Reports"};
        String[] keys = {PermissionRules.DASHBOARD_VIEW, PermissionRules.ID_REVIEW, PermissionRules.REPORTS_VIEW};
        for (int i = 0; i < 3; i++) {
            if (!access.can(keys[i])) continue;                     // hidden, and the server rejects it anyway
            nav.getMenu().add(0, i, i, titles[i]).setIcon(i == 0 ? android.R.drawable.ic_menu_agenda : i == 1 ? android.R.drawable.ic_menu_gallery : android.R.drawable.ic_menu_report_image);
            if (first < 0) first = i;
        }
        if (first < 0) { body.addView(Ui.text(this, "Your role has no screens in this version yet.", R.style.Text_Bofo_Body)); show(page); return; }
        nav.setOnItemSelectedListener(it -> { open(body, it.getItemId()); return true; });
        show(page);
        nav.setSelectedItemId(first);
    }

    private void open(FrameLayout body, int tab) {
        body.removeAllViews();
        View v = tab == 0 ? new HomeScreen(this, access) : tab == 1 ? new IdScreen(this, access) : new ReportsScreen(this, access);
        body.addView(v, new FrameLayout.LayoutParams(-1, -1));
    }

    /** Called by screens when the server says the session is dead. */
    void sessionEnded() { Async.run(() -> { AdminAuth.get().signOut(); return 1; }, ok -> login()); }
    static ScrollView scroll(android.content.Context c, View content) { ScrollView s = new ScrollView(c); s.addView(content, new ViewGroup.LayoutParams(-1, -2)); return s; }
}
