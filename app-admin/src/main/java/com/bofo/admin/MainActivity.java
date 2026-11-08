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
 * FLAG_SECURE (v2.12.6) is on only for sign-in, invite code, two-step code / key and the lock screen; the working screens allow screenshots.
 */
public final class MainActivity extends AppCompatActivity {
    private FrameLayout root;
    private Access access;
    private long stoppedAt;
    private boolean locked;
    private String loginNote = "";
    private FrameLayout body0; private BottomNavigationView nav0;
    /** One live screen per tab: switching tabs only shows / hides it, so nothing is thrown away and reloaded. A tab older than 3 minutes is rebuilt when opened again. */
    private final android.util.SparseArray<View> tabs = new android.util.SparseArray<>();
    private final android.util.SparseArray<Long> tabBuilt = new android.util.SparseArray<>();
    private static final long TAB_MAX_AGE_MS = 3 * 60 * 1000L;
    /** A shortcut from Home: open a tab if this role has it (a tab it does not have is simply not there). */
    void goTab(int tab) { if (nav0 != null && nav0.getMenu().findItem(tab) != null) nav0.setSelectedItemId(tab); }
    void showMore() { if (body0 != null) { dropTab(4); open(body0, 4); } }
    private void dropTab(int tab) { View old = tabs.get(tab); if (old != null && body0 != null) body0.removeView(old); tabs.remove(tab); tabBuilt.remove(tab); }
    /** v2.12.6: screenshots are blocked ONLY on sign-in, the invite code, the two-step code, the two-step key and the lock screen. */
    private boolean screenSecure = true, keyVisible;
    private void protect(boolean on) { screenSecure = on; applySecure(); }
    void keyVisible(boolean on) { keyVisible = on; applySecure(); }       // TwoStepScreen shows its secret key while it is visible
    private void applySecure() {
        if (screenSecure || keyVisible) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    private int pendingTab = PushRules.TAB_NONE;      // a notification tap waiting for the shell to be on screen
    private boolean askedNotifications;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        protect(true);                                                  // v2.12.6: starts protected; only the working screens (shell) allow screenshots
        root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.bofo_bg));
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, in) -> {
            androidx.core.graphics.Insets i = in.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return in;
        });
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {   // back inside "More" goes up one level instead of leaving the app
            @Override public void handleOnBackPressed() {
                if (nav0 != null && nav0.getSelectedItemId() == 4 && tabs.get(4) instanceof MoreScreen m && m.up()) return;
                setEnabled(false); getOnBackPressedDispatcher().onBackPressed();
            }
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
        if (nav0.getMenu().findItem(t) != null) {                                // a tab this role does not have is simply not there
            dropTab(t);                                                         // a push means something new is waiting: load that tab fresh
            if (nav0.getSelectedItemId() == t) open(body0, t); else nav0.setSelectedItemId(t);
        }
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

    private void show(View v) {
        root.removeAllViews(); root.addView(v, new FrameLayout.LayoutParams(-1, -1));
        TextView h = firstText(v);                                           // TalkBack: say the screen's first heading when the screen changes
        if (h != null) androidx.core.view.ViewCompat.setAccessibilityPaneTitle(v, h.getText());
    }

    private static TextView firstText(View v) {
        if (v instanceof TextView t && !(v instanceof android.widget.Button) && !(v instanceof EditText) && t.getText().length() > 0) return t;
        if (v instanceof ViewGroup g) for (int i = 0; i < g.getChildCount(); i++) { TextView found = firstText(g.getChildAt(i)); if (found != null) return found; }
        return null;
    }

    private LinearLayout centered() {
        LinearLayout l = Ui.col(this); l.setGravity(Gravity.CENTER); int p = Ui.dp(this, 28); l.setPadding(p, p, p, p); return l;
    }

    /* ---------------- login (Google only) ---------------- */
    private void login() {
        protect(true);
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
                        @Override public void onError(GetCredentialException e) { Async.post(() -> { btn.setEnabled(true); Ui.toast(MainActivity.this, "Google sign-in was cancelled or failed. Reason: " + e.getClass().getSimpleName() + " - " + String.valueOf(e.getMessage()) + " (app " + getPackageName() + ")"); }); }
                    });
        } catch (Exception e) { btn.setEnabled(true); Ui.toast(this, "Google sign-in failed."); }
    }

    /* ---------------- gate: two-step code, then ask the server for the role ---------------- */
    private void gate() {
        protect(true);
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
        protect(true);
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
        protect(true);
        LinearLayout l = Ui.col(this);
        android.widget.Button back = Ui.button(this, "< Back", false); back.setOnClickListener(v -> noAccess());
        l.addView(back);
        l.addView(new TwoStepScreen(this, this::gate), new LinearLayout.LayoutParams(-1, 0, 1f));
        show(l);
    }

    private void mfa(String factorId) {
        protect(true);
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
        locked = true; Reauth.forget(); protect(true);
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
        protect(false);                                                 // working screens: screenshots allowed (the ID photo window stays protected)
        LinearLayout page = Ui.col(this);
        LinearLayout top = Ui.row(this); top.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 8));
        LinearLayout who = Ui.col(this);                                              // role pill on top, e-mail below (one line, cut with ...)
        who.addView(Ui.pill(this, access.label, R.color.bofo_primary, R.color.bofo_primary_tint), new LinearLayout.LayoutParams(-2, -2));
        TextView mail = Ui.text(this, AdminAuth.get().email(), R.style.Text_Bofo_Caption);
        mail.setSingleLine(); mail.setEllipsize(android.text.TextUtils.TruncateAt.END); mail.setPadding(0, Ui.dp(this, 4), 0, 0);
        who.addView(mail, new LinearLayout.LayoutParams(-1, -2));
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
        nav.setLabelVisibilityMode(com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);     // names always visible
        android.content.res.ColorStateList navTint = new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{getColor(R.color.bofo_primary), getColor(R.color.bofo_text_2)});
        nav.setItemIconTintList(navTint); nav.setItemTextColor(navTint);
        nav.setItemActiveIndicatorColor(android.content.res.ColorStateList.valueOf(getColor(R.color.bofo_primary_tint)));
        View hairline = new View(this); hairline.setBackgroundColor(getColor(R.color.bofo_border));
        page.addView(hairline, new LinearLayout.LayoutParams(-1, Ui.dp(this, 1)));
        page.addView(nav, new LinearLayout.LayoutParams(-1, -2));

        int id = 0; int first = -1;
        String[] titles = {"Home", "IDs", "Reports", "Approvals", "More"};
        boolean more = access.can(PermissionRules.AUDIT_VIEW_OWN) || access.can(PermissionRules.MEMBERS_MANAGE) || access.can(PermissionRules.BANNED_VIEW) || access.can(PermissionRules.PAYMENTS_VIEW) || access.can(PermissionRules.APPROVALS_DECIDE) || access.can(PermissionRules.AUDIT_VIEW_ALL);
        boolean[] show = {access.can(PermissionRules.DASHBOARD_VIEW), access.can(PermissionRules.ID_REVIEW), access.can(PermissionRules.REPORTS_VIEW),
                access.can(PermissionRules.APPROVALS_DECIDE) || access.can(PermissionRules.APPROVALS_REQUEST), more};
        int[] icons = {R.drawable.ic_nav_home, R.drawable.ic_nav_ids, R.drawable.ic_nav_reports, R.drawable.ic_nav_approvals, R.drawable.ic_nav_more};
        for (int i = 0; i < 5; i++) {
            if (!show[i]) continue;                                 // hidden, and the server rejects it anyway
            nav.getMenu().add(0, i, i, titles[i]).setIcon(icons[i]);
            if (first < 0) first = i;
        }
        body0 = body; nav0 = nav; tabs.clear(); tabBuilt.clear();
        if (first < 0) { body.addView(Ui.text(this, "Your role has no screens in this version yet.", R.style.Text_Bofo_Body)); show(page); return; }
        nav.setOnItemSelectedListener(it -> { open(body, it.getItemId()); return true; });
        show(page);
        nav.setSelectedItemId(first);
        applyPendingTab();
    }

    private void open(FrameLayout body, int tab) {
        Long built = tabBuilt.get(tab);
        if (built != null && System.currentTimeMillis() - built > TAB_MAX_AGE_MS && tab != 4) { body.removeView(tabs.get(tab)); tabs.remove(tab); tabBuilt.remove(tab); }
        View v = tabs.get(tab);
        if (v == null) {
            v = tab == 0 ? new HomeScreen(this, access) : tab == 1 ? new IdScreen(this, access) : tab == 2 ? new ReportsScreen(this, access)
                    : tab == 3 ? new ApprovalsScreen(this, access) : new MoreScreen(this, access);
            tabs.put(tab, v); tabBuilt.put(tab, System.currentTimeMillis());
            body.addView(v, new FrameLayout.LayoutParams(-1, -1));
        }
        for (int i = 0; i < body.getChildCount(); i++) { View c = body.getChildAt(i); c.setVisibility(c == v ? View.VISIBLE : View.GONE); }
    }

    /** Called by screens when the server says the session is dead. */
    void sessionEnded() { Async.run(() -> { AdminAuth.get().signOut(); return 1; }, ok -> login()); }
    static ScrollView scroll(android.content.Context c, View content) { ScrollView s = new ScrollView(c); s.addView(content, new ViewGroup.LayoutParams(-1, -2)); return s; }
}
