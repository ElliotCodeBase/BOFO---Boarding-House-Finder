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

import android.os.CancellationSignal;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.bofo.finder.BuildConfig;
import com.bofo.finder.config.AppConfig;
import com.bofo.finder.util.Nonce;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
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
        // v2.3.0: Notifications. Checked state first, listener second. On = ask the system permission if needed, then register this phone; off = remove it from the server.
        b.pushEnabled.setChecked(AppPrefs.pushEnabled(requireContext()) && androidx.core.app.NotificationManagerCompat.from(requireContext()).areNotificationsEnabled());
        b.pushEnabled.setOnCheckedChangeListener((btn, on) -> setPush(on));
        b.pathFade.setChecked(AppPrefs.seeThroughPath(requireContext()));
        b.pathFade.setOnCheckedChangeListener((btn, on) -> AppPrefs.setSeeThroughPath(requireContext(), on));
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
                .setPositiveButton(R.string.settings_delete_confirm, (d, w) -> reauthThenDelete())
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

    private final androidx.activity.result.ActivityResultLauncher<String> notifPermission = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded()) return;
                if (granted) registerAndReport();
                else { pushFailed(getString(R.string.push_blocked_hint)); openOsNotificationSettings(); }
            });

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
        android.widget.Toast.makeText(requireContext().getApplicationContext(), text, android.widget.Toast.LENGTH_LONG).show();
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
                case OK: message(getString(R.string.push_on_ok)); android.widget.Toast.makeText(app, R.string.push_on_ok, android.widget.Toast.LENGTH_SHORT).show(); break;
                case OS_BLOCKED: pushFailed(getString(R.string.push_blocked_hint)); openOsNotificationSettings(); break;
                case NO_SESSION: pushFailed(getString(R.string.push_err_session)); break;
                case NO_FIREBASE: pushFailed(getString(R.string.push_unavailable_hint)); break;
                default: break;
            }
        }, e -> { if (b != null) pushFailed(getString(R.string.push_err_generic)); });
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

    /** v2.12.1 (S-14): a stolen or borrowed unlocked phone must not be able to delete the account. Google accounts sign in with Google again; others get an e-mail code. */
    private void reauthThenDelete() {
        if ("google".equals(AuthService.get().provider()) && AppConfig.googleNative()) reauthWithGoogle(); else reauthWithEmailCode();
    }

    private void reauthWithGoogle() {
        final String raw = Nonce.newRaw();
        GetSignInWithGoogleOption opt = new GetSignInWithGoogleOption.Builder(AppConfig.googleWebClientId()).setNonce(Nonce.sha256Hex(raw)).build();
        GetCredentialRequest req = new GetCredentialRequest.Builder().addCredentialOption(opt).build();
        CredentialManager.create(requireContext()).getCredentialAsync(requireActivity(), req, new CancellationSignal(), ContextCompat.getMainExecutor(requireContext()),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(GetCredentialResponse r) {
                        if (b == null) return;
                        Credential c = r.getCredential();
                        if (!(c instanceof CustomCredential) || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) { message("Google did not confirm. Nothing was deleted."); return; }
                        try {
                            final String idToken = GoogleIdTokenCredential.createFrom(c.getData()).getIdToken();
                            Async.run(() -> { AuthService.get().reauthGoogle(idToken, raw); return Boolean.TRUE; }, ok -> deleteAccount(), e -> message(ErrorText.full("Could not confirm it is you", e)));
                        } catch (Exception e) { message(ErrorText.full("Could not confirm it is you", e)); }
                    }
                    @Override public void onError(@NonNull GetCredentialException e) { if (b != null) message("Not confirmed. Nothing was deleted."); }
                });
    }

    private void reauthWithEmailCode() {
        Async.run(() -> { AuthService.get().sendReauthCode(); return Boolean.TRUE; }, ok -> {
            if (b == null) return;
            final android.widget.EditText in = new android.widget.EditText(requireContext());
            in.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            in.setHint(R.string.settings_delete_code_hint);
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.settings_delete_code_title)
                    .setMessage(R.string.settings_delete_code_msg)
                    .setView(in)
                    .setNegativeButton(R.string.common_cancel, null)
                    .setPositiveButton(R.string.settings_delete_confirm, (d, w) -> {
                        final String code = in.getText().toString();
                        Async.run(() -> { AuthService.get().reauthEmailCode(code); return Boolean.TRUE; }, ok2 -> deleteAccount(), e -> message(ErrorText.full("Could not confirm it is you", e)));
                    }).show();
        }, e -> message(ErrorText.full("Could not send the code", e)));
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
