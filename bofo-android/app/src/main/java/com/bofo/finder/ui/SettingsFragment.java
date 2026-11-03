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
        b.pushEnabled.setChecked(AppPrefs.pushEnabled(requireContext()));
        b.pushEnabled.setOnCheckedChangeListener((btn, on) -> setPush(on));
        // v2.3.2: admin only: switch the BOFO AI on/off for everyone (saved on the server, app_flags 'ai_enabled')
        b.aiEnabled.setChecked(com.bofo.finder.util.AiFlag.on());
        b.aiEnabled.setOnCheckedChangeListener((btn, on) -> setAi(on));
        com.bofo.finder.util.AiFlag.refresh(v -> {
            if (b == null) return;
            b.aiEnabled.setOnCheckedChangeListener(null);
            b.aiEnabled.setChecked(v);
            b.aiEnabled.setOnCheckedChangeListener((btn, on) -> setAi(on));
        });
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

    private void applyRole() {
        b.switchRole.setVisibility(role == Role.ADMIN ? View.GONE : View.VISIBLE);
        b.switchRole.setText(role == Role.OWNER ? R.string.settings_switch_student : R.string.settings_switch_owner);
        b.ownerAgreement.setVisibility(role == Role.OWNER ? View.VISIBLE : View.GONE);
        b.adminMfa.setVisibility(role == Role.ADMIN ? View.VISIBLE : View.GONE);
        b.adminViewAs.setVisibility(role == Role.ADMIN ? View.VISIBLE : View.GONE);
        b.adminAiRow.setVisibility(role == Role.ADMIN ? View.VISIBLE : View.GONE);
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
                if (granted) PushService.syncAsync(requireContext(), AuthRouter.realRole.db());
                else message(getString(R.string.push_blocked_hint));
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

    private void setPush(boolean on) {
        final android.content.Context app = requireContext().getApplicationContext();
        AppPrefs.setPushEnabled(app, on);
        if (!on) { PushService.disableAsync(app); return; }
        if (!PushService.firebaseReady(app)) { message(getString(R.string.push_unavailable_hint)); return; }
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        if (!androidx.core.app.NotificationManagerCompat.from(app).areNotificationsEnabled()) message(getString(R.string.push_blocked_hint));
        PushService.syncAsync(app, AuthRouter.realRole.db());
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
