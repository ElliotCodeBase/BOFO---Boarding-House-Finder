package com.bofo.finder.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentSplashBinding;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;

/** Shows the mascot briefly, restores the saved session and opens login, role chooser, profile setup or the app. */
public class SplashFragment extends Fragment {

    private static final long SPLASH_MS = 2400;   // one full jump loop of the mascot (v2.0.20; was 1200)

    private FragmentSplashBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable next = this::openNext;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSplashBinding.inflate(inflater, container, false);
        binding.retry.setOnClickListener(v -> {
            binding.retry.setVisibility(View.GONE);
            binding.status.setText(R.string.splash_status);
            openNext();
        });
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        lightBars(false);                                   // dark screen: status / navigation bar icons turn white
        handler.postDelayed(next, SPLASH_MS);
    }

    @Override
    public void onStop() {
        handler.removeCallbacks(next);
        lightBars(true);                                    // the rest of the app has a light background
        super.onStop();
    }

    private void lightBars(boolean light) {
        if (!isAdded() || getActivity() == null) return;
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(requireActivity().getWindow(), requireActivity().getWindow().getDecorView());
        c.setAppearanceLightStatusBars(light);
        c.setAppearanceLightNavigationBars(light);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void openNext() {
        if (!isAdded() || binding == null) return;
        NavController nav = NavHostFragment.findNavController(this);
        if (!AuthRouter.isSplash(nav)) return;
        if (AuthRouter.consumeLink(requireContext(), nav, this::fail)) return;
        AuthRouter.route(nav, this::fail);
    }

    private void fail(Throwable e) {
        if (!isAdded() || binding == null) return;
        binding.status.setText(Connectivity.isNetworkError(e) ? getString(R.string.splash_offline) : ErrorText.full("Could not open the app", e));
        binding.retry.setVisibility(View.VISIBLE);
    }
}
