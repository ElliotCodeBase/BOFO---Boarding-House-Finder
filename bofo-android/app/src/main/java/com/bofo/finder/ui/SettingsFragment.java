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
        b.useLocation.setChecked(AppPrefs.useLocation(requireContext()));
        b.useLocation.setOnCheckedChangeListener((btn, on) -> AppPrefs.setUseLocation(requireContext(), on));
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

    private void signOut() {
        final android.content.Context app = requireContext().getApplicationContext();
        final NavController nav = nav();
        Async.run(() -> { AuthService.get().signOut(); return Boolean.TRUE; }, ok -> {
            AppPrefs.clear(app);
            AuthRouter.go(nav, R.id.action_global_login);
        }, e -> message(ErrorText.full("Could not sign out", e)));
    }

    private void deleteAccount() {
        final android.content.Context app = requireContext().getApplicationContext();
        final NavController nav = nav();
        Async.run(() -> { AuthService.get().deleteAccount(); return Boolean.TRUE; }, ok -> {
            AppPrefs.clear(app);
            AuthRouter.go(nav, R.id.action_global_login);
        }, e -> message(ErrorText.full("Could not delete the account", e)));
    }
}
