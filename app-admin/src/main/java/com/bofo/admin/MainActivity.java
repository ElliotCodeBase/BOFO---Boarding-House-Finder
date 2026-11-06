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
import com.bofo.shared.PushRules;
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
    private FrameLayout body0; private BottomNavigationView nav0;
    void showMore() { if (body0 != null) open(body0, 4); }
    private int pendingTab = PushRules.TAB_NONE;      // a notification tap waiting for the shell to be on screen
    private boolean askedNotifications;

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
        pushIntent(getIntent());
        if (AdminAuth.get().hasSession()) gate(); else login();
    }

    @Override protected void onNewIntent(android.content.Intent i) {
        super.onNewIntent(i); setIntent(i);
        pushIntent(i);
        if (access != null && !locked) applyPendingTab();      // already inside: jump now; otherwise shell() does it after sign-in / unlock
    }

    /** A tap on an admin notification: remember which tab it wants. Only our own action counts; permissions are still decided by which tabs exist. */
    private void pushIntent(android.content.Intent i) {
        if (i == null || !PushRules.ACTION_OPEN.equals(i.getAction())) return;
        pendingTab = PushRules.tabFor(i.getStringExtra("kind"), i.getStringExtra("place"));
    }

    private void applyPendingTab() {
        int t = pendingTab; pendingTab = PushRules.TAB_NONE;
        if (t == PushRules.TAB_NONE || nav0 == null) return;
        if (nav0.getMenu().findItem(t) != null) nav0.setSelectedItemId(t);      // a tab this role does not have is simply not there
    }

    /** Android 13+: without this the phone never shows our notifications. Asked once per run, only after a staff account is confirmed. */
    private void askNotifications() {
        if (askedNotifications || android.os.Build.VERSION.SDK_INT < 33) return;
        askedNotifications = true;
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
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
        access = null; AdminApp.setAccess(null);
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
            access = ac; locked = false; AdminApp.setAccess(ac); PushRegistrar.registerNow(this); askNotifications(); shell();
        }, e -> {
            if (e instanceof com.bofo.shared.Api.ApiException x && x.status == 401) { AdminAuth.get().signOut(); loginNote = ""; login(); return; }
            LinearLayout el = centered(); el.addView(Ui.text(this, Ui.err(e), R.style.Text_Bofo_Body));
            android.widget.Button retry = Ui.button(this, "Try again", true); retry.setOnClickListener(v -> gate()); el.addView(retry);
            android.widget.Button out = Ui.button(this, "Sign out", false); out.setOnClickListener(v -> { AdminAuth.get().signOut(); login(); }); el.addView(out);
            show(el);
        });
    }

    /** No active role: say so, let a newly invited person enter the invite code, or sign out. */
    private void noAccess() {
        LinearLayout l = centered();
        l.addView(Ui.text(this, "No access", R.style.Text_Bofo_Title));
        l.addView(Ui.text(this, AdminAuth.get().email() + " has no active BOFO Admin role. If the owner sent you an invite code, enter it here.", R.style.Text_Bofo_Caption));
        EditText code = new EditText(this); code.setHint("Invite code"); code.setSingleLine(); l.addView(code);
        android.widget.Button go = Ui.button(this, "Use invite code", true);
        go.setOnClickListener(v -> {
            String t = code.getText().toString().trim();
            if (t.length() < 20) { code.setError("Paste the full code"); return; }
            go.setEnabled(false);
            Async.run(() -> com.bofo.shared.Api.get().rpc("admin_invite_accept", new JSONObject().put("token_", t)), ok -> gate(),
                    e -> { go.setEnabled(true); Ui.toast(this, "This code is not valid for this account. It may be used, expired, or for another e-mail."); });
        });
        l.addView(go);
        l.addView(Ui.text(this, "If the owner requires two-step sign-in, set it up first:", R.style.Text_Bofo_Caption));
        android.widget.Button ts = Ui.button(this, "Set up two-step sign-in", false);
        ts.setOnClickListener(v -> twoStepFromBlocked());
        l.addView(ts);
        android.widget.Button out = Ui.button(this, "Sign out", false);
        out.setOnClickListener(v -> Async.run(() -> { AdminAuth.get().signOut(); return 1; }, ok -> { loginNote = "Signed out."; login(); loginNote = ""; }));
        l.addView(out);
        show(l);
    }

    /** From "No access": set up two-step sign-in, then run the role check again (a server that requires two-step answers "no access" until it exists). */
    private void twoStepFromBlocked() {
        LinearLayout l = Ui.col(this);
        android.widget.Button back = Ui.button(this, "< Back", false); back.setOnClickListener(v -> noAccess());
        l.addView(back);
        l.addView(new TwoStepScreen(this, this::gate), new LinearLayout.LayoutParams(-1, 0, 1f));
        show(l);
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
        String[] titles = {"Home", "IDs", "Reports", "Approvals", "More"};
        boolean more = access.can(PermissionRules.AUDIT_VIEW_OWN) || access.can(PermissionRules.MEMBERS_MANAGE) || access.can(PermissionRules.BANNED_VIEW) || access.can(PermissionRules.PAYMENTS_VIEW);
        boolean[] show = {access.can(PermissionRules.DASHBOARD_VIEW), access.can(PermissionRules.ID_REVIEW), access.can(PermissionRules.REPORTS_VIEW),
                access.can(PermissionRules.APPROVALS_DECIDE) || access.can(PermissionRules.APPROVALS_REQUEST), more};
        int[] icons = {android.R.drawable.ic_menu_agenda, android.R.drawable.ic_menu_gallery, android.R.drawable.ic_menu_report_image, android.R.drawable.ic_menu_send, android.R.drawable.ic_menu_more};
        for (int i = 0; i < 5; i++) {
            if (!show[i]) continue;                                 // hidden, and the server rejects it anyway
            nav.getMenu().add(0, i, i, titles[i]).setIcon(icons[i]);
            if (first < 0) first = i;
        }
        body0 = body; nav0 = nav;
        if (first < 0) { body.addView(Ui.text(this, "Your role has no screens in this version yet.", R.style.Text_Bofo_Body)); show(page); return; }
        nav.setOnItemSelectedListener(it -> { open(body, it.getItemId()); return true; });
        show(page);
        nav.setSelectedItemId(first);
        applyPendingTab();
    }

    private void open(FrameLayout body, int tab) {
        body.removeAllViews();
        View v = tab == 0 ? new HomeScreen(this, access) : tab == 1 ? new IdScreen(this, access) : tab == 2 ? new ReportsScreen(this, access)
                : tab == 3 ? new ApprovalsScreen(this, access) : new MoreScreen(this, access);
        body.addView(v, new FrameLayout.LayoutParams(-1, -1));
    }

    /** Called by screens when the server says the session is dead. */
    void sessionEnded() { Async.run(() -> { AdminAuth.get().signOut(); return 1; }, ok -> login()); }
    static ScrollView scroll(android.content.Context c, View content) { ScrollView s = new ScrollView(c); s.addView(content, new ViewGroup.LayoutParams(-1, -2)); return s; }
}
