package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bofo.finder.BuildConfig;
import com.bofo.finder.config.AppConfig;
import com.bofo.finder.databinding.FragmentDebugBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;

import java.util.List;

/**
 * Stage 2 check screen (debug builds only): shows the config the app was built with, signs in with email + password
 * through the real AuthService (the session is kept like a normal login) and fetches boarding houses / colleges through the
 * same SupabaseClient + BhService the final screens will use. The database only answers signed-in users (RLS).
 */
public class DebugFragment extends Fragment {

    private FragmentDebugBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDebugBinding.inflate(inflater, container, false);
        binding.config.setText(configSummary());
        binding.signIn.setOnClickListener(v -> signIn());
        binding.fetchBh.setOnClickListener(v -> fetchBoardingHouses());
        binding.fetchColleges.setOnClickListener(v -> fetchColleges());
        Connectivity.OFFLINE.observe(getViewLifecycleOwner(), off -> binding.config.setText(configSummary()));
        return binding.getRoot();
    }

    private String configSummary() {
        String host = AppConfig.supabaseUrl().replaceFirst("^https?://", "");
        return "version  " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                + "\nsupabase " + (host.isEmpty() ? "MISSING (check config.properties / local.properties)" : host)
                + "\nanon key " + (AppConfig.anonKey().isEmpty() ? "MISSING" : "set (" + AppConfig.anonKey().length() + " chars)")
                + "\ngoogle   " + (AppConfig.googleNative() ? "web client id set" : "web client id missing")
                + "\nnetwork  " + (Connectivity.isOffline() ? "OFFLINE" : "online")
                + "\nsession  " + (!AuthService.get().hasSession() ? "signed out (anon key only)" : "signed in as " + AuthService.get().email());
    }

    private void show(String text) {
        if (binding != null) { binding.output.setText(text); binding.config.setText(configSummary()); }
    }

    private void signIn() {
        final String email = binding.email.getText().toString().trim();
        final String password = binding.password.getText().toString();
        if (email.isEmpty() || password.isEmpty()) { show("Enter email and password first."); return; }
        show("Signing in...");
        Async.run(() -> { AuthService.get().signInPassword(email, password); return Boolean.TRUE; }, ok -> {
            if (binding != null) binding.password.setText("");
            show("Signed in. Now fetch the boarding houses.");
        }, e -> show(ErrorText.full("Sign-in failed", e)));
    }

    private void fetchBoardingHouses() {
        show("Loading boarding houses...");
        final long t0 = System.currentTimeMillis();
        Async.run(() -> new BhService().list(BhService.Tab.NEW, null, null, null, null), (List<BoardingHouse> list) -> {
            StringBuilder sb = new StringBuilder(list.size() + " boarding houses in " + (System.currentTimeMillis() - t0) + " ms");
            if (list.isEmpty() && !AuthService.get().hasSession()) sb.append("\n(0 is expected while signed out: the database only answers signed-in users)");
            for (int i = 0; i < Math.min(8, list.size()); i++) {
                BoardingHouse b = list.get(i);
                sb.append("\n- ").append(b.name()).append("  P").append(Math.round(b.price()))
                        .append("  beds ").append(b.availableBeds()).append(b.featured() ? "  [featured]" : "");
            }
            show(sb.toString());
        }, e -> show(ErrorText.full("Could not load boarding houses", e)));
    }

    private void fetchColleges() {
        show("Loading colleges...");
        Async.run(() -> new BhService().colleges(), (List<College> list) -> {
            StringBuilder sb = new StringBuilder(list.size() + " colleges");
            for (int i = 0; i < Math.min(8, list.size()); i++) sb.append("\n- ").append(list.get(i).name());
            show(sb.toString());
        }, e -> show(ErrorText.full("Could not load colleges", e)));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
