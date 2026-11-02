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
import com.bofo.finder.databinding.FragmentSuspendedBinding;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.AppPrefs;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.SuspendRules;

import java.time.ZoneId;

/** v2.1.8: shown instead of the app while the account is suspended: the reason, the end date, Check again, Sign out. */
public class SuspendedFragment extends Fragment {

    private FragmentSuspendedBinding b;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentSuspendedBinding.inflate(inflater, container, false);
        String until = SuspendRules.when(AuthRouter.suspendedUntil, ZoneId.systemDefault());
        b.until.setText(until.isEmpty() ? getString(R.string.susp_until_unknown) : getString(R.string.susp_until, until));
        String why = AuthRouter.suspendedReason == null ? "" : AuthRouter.suspendedReason.trim();
        b.reason.setText(getString(R.string.susp_reason, why.isEmpty() ? getString(R.string.rep_no_reason) : why));
        b.again.setOnClickListener(v -> {
            b.again.setEnabled(false);
            AuthRouter.route(nav(), e -> {
                if (b == null) return;
                b.again.setEnabled(true);
                Toast.makeText(requireContext().getApplicationContext(), ErrorText.full(getString(R.string.susp_check_error), e), Toast.LENGTH_LONG).show();
            });
        });
        b.signOut.setOnClickListener(v -> {
            final android.content.Context app = requireContext().getApplicationContext();
            final NavController nav = nav();
            Async.run(() -> { AuthService.get().signOut(); return Boolean.TRUE; }, ok -> {
                AppPrefs.clear(app);
                AuthRouter.reset();
                AuthRouter.go(nav, R.id.action_global_login);
            }, e -> Toast.makeText(app, ErrorText.full("Could not sign out", e), Toast.LENGTH_LONG).show());
        });
        return b.getRoot();
    }

    private NavController nav() { return NavHostFragment.findNavController(this); }

    @Override
    public void onDestroyView() { b = null; super.onDestroyView(); }
}
