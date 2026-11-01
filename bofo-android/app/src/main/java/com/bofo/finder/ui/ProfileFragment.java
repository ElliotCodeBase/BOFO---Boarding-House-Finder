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
        return binding.getRoot();
    }

    private boolean loaded;      // v2.0.90: true once a profile has been drawn; until then a failed load is retried by itself
    private int token;

    /** Reloaded on every return from the edit screen (onStart) and when the phone comes back online. v2.0.90: a failed load retries by itself (2 s, 5 s, 10 s). */
    @Override
    public void onStart() {
        super.onStart();
        load(0);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        com.bofo.finder.util.Connectivity.OFFLINE.observe(getViewLifecycleOwner(), off -> { if (off != null && !off && !loaded) load(0); });
    }

    private void load(int attempt) {
        final int my = ++token;
        Async.run(() -> new ProfileService().fetchMe(), (Profile p) -> {
            if (binding == null || my != token) return;
            if (p == null) { retry(attempt); return; }
            loaded = true;
            binding.accountName.setText(p.fullName());
            binding.accountRole.setText(p.role() == com.bofo.finder.model.Role.OWNER ? R.string.account_role_owner
                    : p.role() == com.bofo.finder.model.Role.ADMIN ? R.string.account_role_admin : R.string.account_role_student);
            AiChatLogFragment.attachEntry(this, binding.aiChatsOpen, p.role());      // v2.0.91 AI Chats monitor (admin only): REMOVE this line with the feature
            if (p.photoUrl() != null && !p.photoUrl().isEmpty())
                Glide.with(this).load(p.photoUrl()).centerCrop().placeholder(R.drawable.mascot_happy).into(binding.avatar);
        }, e -> { if (binding != null && my == token) retry(attempt); });
    }

    private void retry(int attempt) {
        if (loaded || binding == null || attempt >= 3) return;
        final int[] wait = {2000, 5000, 10000};
        final int my = token;
        binding.getRoot().postDelayed(() -> { if (binding != null && !loaded && my == token) load(attempt + 1); }, wait[attempt]);
    }

    @Override
    public void onDestroyView() {
        token++;
        loaded = false;
        binding = null;
        super.onDestroyView();
    }
}
