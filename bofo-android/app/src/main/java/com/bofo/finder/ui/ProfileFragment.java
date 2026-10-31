package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.BuildConfig;
import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentProfileBinding;
import com.bofo.finder.model.Profile;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.ProfileService;
import com.bofo.finder.util.Async;
import com.bumptech.glide.Glide;

/** Profile tab: account card (photo, name, e-mail, role), edit profile, settings and the About card. */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        // Never hard-code the version: it is versionName from app/build.gradle.
        binding.version.setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));
        binding.accountEmail.setText(AuthService.get().email());
        binding.openEdit.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean(ProfileSetupFragment.ARG_EDIT, true);
            NavHostFragment.findNavController(this).navigate(R.id.profileSetupFragment, args);
        });
        binding.openSettings.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.settingsFragment));
        if (BuildConfig.DEBUG) {
            binding.debugOpen.setVisibility(View.VISIBLE);
            binding.debugOpen.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigate(R.id.debugFragment));
        }
        return binding.getRoot();
    }

    /** Reloaded on every return from the edit screen. */
    @Override
    public void onStart() {
        super.onStart();
        Async.run(() -> new ProfileService().fetchMe(), (Profile p) -> {
            if (binding == null || p == null) return;
            binding.accountName.setText(p.fullName());
            binding.accountRole.setText(p.role() == com.bofo.finder.model.Role.OWNER ? R.string.account_role_owner
                    : p.role() == com.bofo.finder.model.Role.ADMIN ? R.string.account_role_admin : R.string.account_role_student);
            if (p.photoUrl() != null && !p.photoUrl().isEmpty())
                Glide.with(this).load(p.photoUrl()).circleCrop().placeholder(R.drawable.mascot_happy).into(binding.avatar);
        }, e -> { /* offline and nothing saved yet: the e-mail line is enough */ });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
