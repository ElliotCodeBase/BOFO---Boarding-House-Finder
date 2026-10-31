package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentRoleBinding;
import com.bofo.finder.model.Profile;
import com.bofo.finder.model.Role;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.ProfileService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.ErrorText;
import com.google.android.material.card.MaterialCardView;

/** First run after sign-in: student or owner, plus accepting the agreements. Creates the row in public.users. */
public class RoleFragment extends Fragment {

    private FragmentRoleBinding b;
    private Role role;                 // null until a card is tapped
    private boolean busy;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentRoleBinding.inflate(inflater, container, false);
        if (state != null && state.getString("role") != null) role = Role.of(state.getString("role"));
        b.cardStudent.setOnClickListener(v -> pick(Role.STUDENT));
        b.cardOwner.setOnClickListener(v -> pick(Role.OWNER));
        b.readTerms.setOnClickListener(v -> LegalSheet.show(getParentFragmentManager(), LegalText.Doc.TERMS));
        b.readPrivacy.setOnClickListener(v -> LegalSheet.show(getParentFragmentManager(), LegalText.Doc.PRIVACY));
        b.readOwner.setOnClickListener(v -> LegalSheet.show(getParentFragmentManager(), LegalText.Doc.OWNER));
        b.continueBtn.setOnClickListener(v -> save());
        render();
        return b.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (role != null) out.putString("role", role.name());
    }

    @Override
    public void onDestroyView() { b = null; super.onDestroyView(); }

    private void pick(Role r) { role = r; message(null); render(); }

    private void render() {
        style(b.cardStudent, role == Role.STUDENT);
        style(b.cardOwner, role == Role.OWNER);
        b.readOwner.setVisibility(role == Role.OWNER ? View.VISIBLE : View.GONE);
        b.agree.setText(role == Role.OWNER ? R.string.role_agree_owner : R.string.role_agree);
    }

    private void style(MaterialCardView c, boolean on) {
        c.setStrokeColor(ContextCompat.getColor(requireContext(), on ? R.color.bofo_primary : R.color.bofo_border));
        float d = getResources().getDisplayMetrics().density;
        c.setStrokeWidth(Math.round((on ? 2 : 1) * d));
        c.setCardBackgroundColor(ContextCompat.getColor(requireContext(), on ? R.color.bofo_primary_tint : R.color.bofo_white));
    }

    private void message(String text) {
        if (b == null) return;
        b.message.setVisibility(text == null ? View.GONE : View.VISIBLE);
        b.message.setText(text == null ? "" : text);
    }

    private void save() {
        if (busy) return;
        if (role == null) { message(getString(R.string.role_pick_first)); return; }
        if (!b.agree.isChecked()) { message(getString(R.string.role_need_agree)); return; }
        busy = true; b.continueBtn.setEnabled(false); message(null);
        final Role chosen = role;
        Async.run(() -> {
            ProfileService ps = new ProfileService();
            Profile p = ps.fetchMe();
            if (p == null) ps.create(chosen, AuthService.get().metaName(), AuthService.get().metaPhoto());
            else if (p.role() != chosen && p.role() != Role.ADMIN) ps.switchRole(chosen);
            ps.recordAgreements(chosen);
            return Boolean.TRUE;
        }, ok -> {
            if (b == null) return;
            NavHostFragment.findNavController(this).navigate(R.id.action_global_setup);
        }, e -> {
            busy = false;
            if (b == null) return;
            b.continueBtn.setEnabled(true);
            message(ErrorText.full("Could not save your choice", e));
        });
    }
}
