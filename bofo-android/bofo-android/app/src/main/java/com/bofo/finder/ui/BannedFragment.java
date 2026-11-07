package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentBannedBinding;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.util.AppPrefs;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.ErrorText;

/** v2.1.9: shown instead of the app for a banned account: "You are banned.", the reason, and Sign out. Nothing else. */
public class BannedFragment extends Fragment {

    private FragmentBannedBinding b;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentBannedBinding.inflate(inflater, container, false);
        String why = AuthRouter.bannedReason == null ? "" : AuthRouter.bannedReason.trim();
        b.reason.setText(getString(R.string.ban_reason, why.isEmpty() ? getString(R.string.rep_no_reason) : why));
        AppealHelper.wire(this, b.appeal, b.appealStatus);
        b.signOut.setOnClickListener(v -> {
            final android.content.Context app = requireContext().getApplicationContext();
            final NavController nav = NavHostFragment.findNavController(this);
            Async.run(() -> { AuthService.get().signOut(); return Boolean.TRUE; }, ok -> {
                AppPrefs.clear(app);
                AuthRouter.reset();
                AuthRouter.go(nav, R.id.action_global_login);
            }, e -> Toast.makeText(app, ErrorText.full("Could not sign out", e), Toast.LENGTH_LONG).show());
        });
        return b.getRoot();
    }

    @Override
    public void onDestroyView() { b = null; super.onDestroyView(); }
}
