package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.BuildConfig;
import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentSettingsBinding;
import com.bofo.finder.model.Profile;
import com.bofo.finder.model.Role;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.ProfileService;
import com.bofo.finder.service.PushService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.AppPrefs;
import com.bofo.finder.util.Cache;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.ThemeMode;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Settings: edit profile, switch student / owner, location switch, agreements, sign out, delete account. */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding b;
    private Role role = Role.STUDENT;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentSettingsBinding.inflate(inflater, container, false);
        b.editProfile.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean(ProfileSetupFragment.ARG_EDIT, true);
            nav().navigate(R.id.profileSetupFragment, args);
        });
        b.version.setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));
        b.switchRole.setOnClickListener(v -> switchRole());
        b.adminViewAs.setOnClickListener(v -> pickView());                          // v2.0.120: admins only
        b.adminMfa.setOnClickListener(v -> AdminMfaSheet.show(this, null));      // v2.0.119: admins only (row is hidden for everyone else)
        b.useLocation.setChecked(AppPrefs.useLocation(requireContext()));
        b.useLocation.setOnCheckedChangeListener((btn, on) -> AppPrefs.setUseLocation(requireContext(), on));
        // v2.3.0: Notifications. Checked state first, listener second. On = ask the system permission if needed, then register this phone; off = remove it from the server.
        b.pushEnabled.setChecked(AppPrefs.pushEnabled(requireContext()) && androidx.core.app.NotificationManagerCompat.from(requireContext()).areNotificationsEnabled());
        b.pushEnabled.setOnCheckedChangeListener((btn, on) -> setPush(on));
        b.pushTest.setOnClickListener(v -> sendTest());
        // v2.3.2: admin only: switch the BOFO AI on/off for everyone (saved on the server, app_flags 'ai_enabled')
        b.aiEnabled.setChecked(com.bofo.finder.util.AiFlag.on());
        b.aiEnabled.setOnCheckedChangeListener((btn, on) -> setAi(on));
        com.bofo.finder.util.AiFlag.refresh(v -> {
            if (b == null) return;
            b.aiEnabled.setOnCheckedChangeListener(null);
            b.aiEnabled.setChecked(v);
            b.aiEnabled.setOnCheckedChangeListener((btn, on) -> setAi(on));
        });
        b.adminLock.setChecked(AppPrefs.adminAutoLock(requireContext()));          // v2.9.0: admin only (row hidden for everyone else)
        b.adminLock.setOnCheckedChangeListener((btn, on) -> AppPrefs.setAdminAutoLock(requireContext(), on));
        b.pathFade.setChecked(AppPrefs.seeThroughPath(requireContext()));
        b.pathFade.setOnCheckedChangeListener((btn, on) -> AppPrefs.setSeeThroughPath(requireContext(), on));
        // v2.0.117: Orange theme. Checked state first, listener second (so building the screen never flips the theme). The activity recreates itself on this same screen.
        b.orangeTheme.setChecked(AppPrefs.orangeTheme(requireContext()));
        b.orangeTheme.setOnCheckedChangeListener((btn, on) -> ThemeMode.set(requireActivity(), on));
        b.terms.setOnClickListener(v -> LegalSheet.show(getParentFragmentManager(), LegalText.Doc.TERMS));
        b.privacy.setOnClickListener(v -> LegalSheet.show(getParentFragmentManager(), LegalText.Doc.PRIVACY));
        b.ownerAgreement.setOnClickListener(v -> LegalSheet.show(getParentFragmentManager(), LegalText.Doc.OWNER));
        b.signOut.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.settings_sign_out_ask)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.settings_sign_out, (d, w) -> signOut())
                .show());
        b.deleteAccount.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_delete)
                .setMessage(R.string.settings_delete_ask)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.settings_delete_confirm, (d, w) -> deleteAccount())
                .show());
        applyRole();
        Async.run(() -> new ProfileService().fetchMe(), (Profile p) -> {
            if (b == null || p == null) return;
            role = p.role();
            applyRole();
        }, e -> { /* the screen still works with the default role */ });
        return b.getRoot();
    }

    @Override
    public void onDestroyView() { b = null; super.onDestroyView(); }

    private NavController nav() { return NavHostFragment.findNavController(this); }

    /** Title on the first line, a smaller grey sentence under it. */
    private CharSequence withHint(String title, String hint) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(title).append('\n').append(hint);
        int from = title.length() + 1;
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.85f), from, sb.length(), 0);
        sb.setSpan(new android.text.style.ForegroundColorSpan(requireContext().getColor(R.color.bofo_text_2)), from, sb.length(), 0);
        return sb;
    }

    private void applyRole() {
        b.switchRole.setVisibility(role == Role.ADMIN ? View.GONE : View.VISIBLE);
        b.switchRole.setText(role == Role.OWNER ? R.string.settings_switch_student : R.string.settings_switch_owner);
        b.ownerAgreement.setVisibility(role == Role.OWNER ? View.VISIBLE : View.GONE);
        b.adminMfa.setVisibility(role == Role.ADMIN ? View.VISIBLE : View.GONE);
        b.pushTest.setVisibility(isAdmin() ? View.VISIBLE : View.GONE);        // v2.9.5: test + server log text are admin only
        b.adminViewAs.setVisibility(role == Role.ADMIN ? View.VISIBLE : View.GONE);
        b.adminAiRow.setVisibility(role == Role.ADMIN ? View.VISIBLE : View.GONE);
        b.adminLockRow.setVisibility(role == Role.ADMIN ? View.VISIBLE : View.GONE);
        if (role == Role.ADMIN) {                                       // v2.4.14: one plain line under each admin switch
            b.adminMfa.setText(withHint(getString(R.string.mfa_row), getString(R.string.hint_admin_mfa)));
            b.adminViewAs.setText(withHint(getString(R.string.view_as_row), getString(R.string.hint_admin_view_as)));
            b.adminAiTitle.setText(withHint(getString(R.string.ai_switch_title), getString(R.string.hint_admin_ai)));
            b.adminLockTitle.setText(withHint(getString(R.string.admin_lock_switch), getString(R.string.hint_admin_lock)));
        }
        b.orangeThemeRow.setVisibility(role == Role.ADMIN ? View.VISIBLE : View.GONE);   // v2.3.2: theme switch is admin only
    }

    /** v2.0.120: an admin looks at the app as admin, owner or student. Only the screens change: the account stays admin on the server, nothing is written. */
    private void pickView() {
        final Role[] opts = {Role.ADMIN, Role.OWNER, Role.STUDENT};
        final String[] names = {getString(R.string.view_as_admin), getString(R.string.view_as_owner), getString(R.string.view_as_student)};
        int cur = AuthRouter.role == Role.OWNER ? 1 : AuthRouter.role == Role.STUDENT ? 2 : 0;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.view_as_title)
                .setSingleChoiceItems(names, cur, (d, which) -> {
                    d.dismiss();
                    if (opts[which] == AuthRouter.role || b == null) return;
                    Cache.clear();
                    AuthRouter.role = opts[which];            // the shell builds its tabs from this when it opens
                    android.widget.Toast.makeText(requireContext().getApplicationContext(), getString(R.string.view_as_done, names[which]), android.widget.Toast.LENGTH_SHORT).show();
                    AuthRouter.go(nav(), R.id.action_global_shell);
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void message(String t) {
        if (b == null) return;
        b.message.setVisibility(t == null || t.isEmpty() ? View.GONE : View.VISIBLE);
        b.message.setText(t == null ? "" : t);
    }

    private void switchRole() {
        final Role target = role == Role.OWNER ? Role.STUDENT : Role.OWNER;
        message(null);
        Async.run(() -> {
            ProfileService ps = new ProfileService();
            ps.switchRole(target);
            ps.recordAgreements(target);
            ps.fetchMe();                               // refreshes the saved offline copy of the profile (role comes from the server)
            return Boolean.TRUE;
        }, ok -> {
            Cache.clear();
            // v2.0.92: the tabs are built from AuthRouter.role when the shell opens, so set it and reopen the shell now (no re-login needed)
            AuthRouter.role = target;
            if (b == null) return;
            role = target; applyRole();
            android.widget.Toast.makeText(requireContext().getApplicationContext(), R.string.settings_switch_done, android.widget.Toast.LENGTH_SHORT).show();
            AuthRouter.go(nav(), R.id.action_global_shell);
        }, e -> message(ErrorText.full("Could not switch the account", e)));
    }

    private final androidx.activity.result.ActivityResultLauncher<String> notifPermission = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded()) return;
                if (granted) registerAndReport();
                else { pushFailed(getString(R.string.push_blocked_hint)); openOsNotificationSettings(); }
            });

    private void setAi(boolean on) {
        Async.run(() -> { com.bofo.finder.service.SupabaseClient.get().rpc("set_ai_enabled", new org.json.JSONObject().put("on_", on)); return on; },
                v -> { com.bofo.finder.util.AiFlag.set(v); message(getString(v ? R.string.ai_switch_now_on : R.string.ai_switch_now_off)); },
                e -> {
                    if (b == null) return;
                    b.aiEnabled.setOnCheckedChangeListener(null);
                    b.aiEnabled.setChecked(!on);
                    b.aiEnabled.setOnCheckedChangeListener((btn, o) -> setAi(o));
                    message(ErrorText.full("Could not change the AI switch", e));
                });
    }

    /** v2.9.5: the real account type (an admin who views the app as a student is still an admin). */
    private static boolean isAdmin() { return AuthRouter.realRole == Role.ADMIN; }

    /** Server / registration details (error text, push log) only for admins; everyone else gets one plain line. */
    private String registerError(Throwable e) {
        return isAdmin() ? getString(R.string.push_err_register, ErrorText.full("", e)) : getString(R.string.push_err_generic);
    }

    private void setPushChecked(boolean on) {
        if (b == null) return;
        b.pushEnabled.setOnCheckedChangeListener(null);
        b.pushEnabled.setChecked(on);
        b.pushEnabled.setOnCheckedChangeListener((btn, o) -> setPush(o));
    }

    /** v2.9.5: the switch only stays ON when this phone really got registered; otherwise it flips back and says why. */
    private void pushFailed(String text) {
        AppPrefs.setPushEnabled(requireContext().getApplicationContext(), false);
        setPushChecked(false);
        message(text);
    }

    private void openOsNotificationSettings() {
        try {
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName()));
        } catch (Exception ignored) { }
    }

    private void setPush(boolean on) {
        final android.content.Context app = requireContext().getApplicationContext();
        AppPrefs.setPushEnabled(app, on);
        if (!on) { PushService.disableAsync(app); message(""); return; }
        if (!PushService.firebaseReady(app)) { pushFailed(getString(R.string.push_unavailable_hint)); return; }
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        registerAndReport();
    }

    private void registerAndReport() {
        final android.content.Context app = requireContext().getApplicationContext();
        Async.run(() -> PushService.registerNowBlocking(app, AuthRouter.realRole.db()), (PushService.Reg r) -> {
            if (b == null) return;
            switch (r) {
                case OK: message(getString(R.string.push_on_ok)); break;
                case OS_BLOCKED: pushFailed(getString(R.string.push_blocked_hint)); openOsNotificationSettings(); break;
                case NO_SESSION: pushFailed(getString(R.string.push_err_session)); break;
                case NO_FIREBASE: pushFailed(getString(R.string.push_unavailable_hint)); break;
                default: break;
            }
        }, e -> { if (b != null) pushFailed(registerError(e)); });
    }

    private void sendTest() {
        final android.content.Context app = requireContext().getApplicationContext();
        if (!isAdmin()) return;
        if (!AppPrefs.pushEnabled(app)) { message(getString(R.string.push_test_off)); return; }
        message(getString(R.string.push_testing));
        Async.run(() -> {
            PushService.Reg r = PushService.registerNowBlocking(app, AuthRouter.realRole.db());      // the test also repairs a missing registration
            if (r != PushService.Reg.OK) return "reg:" + r.name();
            return PushService.testBlocking();
        }, (String res) -> {
            if (b == null) return;
            if (res.startsWith("reg:")) {
                PushService.Reg r = PushService.Reg.valueOf(res.substring(4));
                message(getString(r == PushService.Reg.OS_BLOCKED ? R.string.push_blocked_hint : r == PushService.Reg.NO_SESSION ? R.string.push_err_session : r == PushService.Reg.NO_FIREBASE ? R.string.push_unavailable_hint : R.string.push_test_off));
                if (r == PushService.Reg.OS_BLOCKED) openOsNotificationSettings();
            } else if ("sent".equals(res)) message(getString(R.string.push_test_sent));
            else if ("not_set_up".equals(res)) message(getString(R.string.push_test_server));
            else if ("no_device".equals(res)) message(getString(R.string.push_test_nodevice));
            else if ("no_answer".equals(res)) message(getString(R.string.push_test_unknown));
            else message(getString(R.string.push_test_failed, res.startsWith("failed:") ? res.substring(7) : res));
        }, e -> { if (b != null) message(registerError(e)); });
    }

    private void signOut() {
        final android.content.Context app = requireContext().getApplicationContext();
        final NavController nav = nav();
        Async.run(() -> { AuthService.get().signOut(); return Boolean.TRUE; }, ok -> {
            AppPrefs.clear(app);
            AuthRouter.reset();                       // v2.0.126: the next account must not inherit this role
            AuthRouter.go(nav, R.id.action_global_login);
        }, e -> message(ErrorText.full("Could not sign out", e)));
    }

    private void deleteAccount() {
        final android.content.Context app = requireContext().getApplicationContext();
        final NavController nav = nav();
        Async.run(() -> { AuthService.get().deleteAccount(); return Boolean.TRUE; }, ok -> {
            AppPrefs.clear(app);
            AuthRouter.reset();                       // v2.0.126: the next account must not inherit this role
            AuthRouter.go(nav, R.id.action_global_login);
        }, e -> message(ErrorText.full("Could not delete the account", e)));
    }
}
