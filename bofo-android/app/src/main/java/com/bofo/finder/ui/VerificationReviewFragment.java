package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentVerificationReviewBinding;
import com.bofo.finder.service.VerificationService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BoostRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.ZoneId;

/**
 * Admin > Queue > one request (8h): the ID front, ID back and selfie (private bucket, signed links valid 2 minutes), then Approve or Reject (reason required).
 * Privacy: the photos are never written to Glide's disk cache or memory cache, and the screen blocks screenshots / screen recording (FLAG_SECURE) while it is open.
 * The decision is review_verification(vid, approve, reason): admin only, and refused if the request is no longer pending.
 */
public class VerificationReviewFragment extends Fragment {

    private FragmentVerificationReviewBinding b;
    private String vid;
    private boolean busy;
    private int token;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentVerificationReviewBinding.inflate(inflater, container, false);
        Bundle a = requireArguments();
        vid = a.getString("vid");
        b.info.setText(getString(R.string.review_info, a.getString("ownerName", ""), a.getString("idType", ""),
                BoostRules.day(a.getString("submitted", ""), ZoneId.systemDefault())));
        b.back.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        b.approve.setOnClickListener(v -> approve());
        b.reject.setOnClickListener(v -> ReasonDialog.ask(this, R.string.review_reject_title, R.string.review_reject, true, r -> decide(false, r)));
        load(a.getString("front"), b.imgFront);
        load(a.getString("back"), b.imgBack);
        load(a.getString("selfie"), b.imgSelfie);
        return b.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override
    public void onStop() {
        requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.onStop();
    }

    private void load(String path, ImageView into) {
        if (path == null || path.isEmpty()) { note(getString(R.string.review_img_missing)); return; }
        final int my = token;
        Async.run(() -> new VerificationService().signed(path), (String url) -> {
            if (b == null || my != token || !isAdded()) return;
            Glide.with(this).load(url).diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true).fitCenter().into(into);
        }, e -> {
            if (b == null || my != token) return;
            note(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.review_img_error), e));
        });
    }

    private void note(String m) {
        if (b == null) return;
        b.imgMessage.setText(m);
        b.imgMessage.setVisibility(View.VISIBLE);
    }

    private void approve() {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.review_approve_title).setMessage(R.string.review_approve_msg)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.review_approve, (d, w) -> decide(true, "")).show();
    }

    private void decide(boolean approve, String reason) {
        if (busy || vid == null) return;
        busy = true;
        b.approve.setEnabled(false); b.reject.setEnabled(false);
        Async.run(() -> { new VerificationService().decide(vid, approve, reason); return Boolean.TRUE; }, ok -> {
            busy = false;
            ListArea.toast(this, getString(approve ? R.string.review_done_approved : R.string.review_done_rejected));
            if (isAdded() && b != null) NavHostFragment.findNavController(this).navigateUp();
        }, e -> {
            busy = false;
            if (b == null) return;
            b.approve.setEnabled(true); b.reject.setEnabled(true);
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.review_error), e));
        });
    }

    @Override
    public void onDestroyView() { token++; b = null; super.onDestroyView(); }
}
