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
import com.bofo.finder.util.BanRules;
import com.bofo.finder.util.BoostRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.RejectReasonRules;
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
        b.reject.setOnClickListener(v -> askReject());
        b.tabs.check(R.id.tabFront);
        b.tabs.addOnButtonCheckedListener((g, id, checked) -> { if (checked) showPhoto(id); });
        load(a.getString("front"), b.imgFront);
        load(a.getString("back"), b.imgBack);
        load(a.getString("selfie"), b.imgSelfie);
        loadDecision();
        return b.getRoot();
    }

    /** One tap switches between the three photos (all three are already loaded, so the switch is instant). */
    private void showPhoto(int tabId) {
        b.imgFront.setVisibility(tabId == R.id.tabFront ? View.VISIBLE : View.GONE);
        b.imgBack.setVisibility(tabId == R.id.tabBack ? View.VISIBLE : View.GONE);
        b.imgSelfie.setVisibility(tabId == R.id.tabSelfie ? View.VISIBLE : View.GONE);
    }

    /** Reject: quick reason chips + an optional note ("Other" needs a note). The text that goes to the server is built by RejectReasonRules. */
    private void askReject() {
        if (!isAdded() || busy) return;
        final int[] ids = {R.string.vr_reason_blurry, R.string.vr_reason_expired, R.string.vr_reason_face, R.string.vr_reason_other};
        android.content.Context c = requireContext();
        int m = Math.round(20 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout box = new android.widget.LinearLayout(c);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(m, m / 2, m, 0);
        com.google.android.material.chip.ChipGroup group = new com.google.android.material.chip.ChipGroup(c);
        group.setSingleSelection(true);
        for (int i = 0; i < ids.length; i++) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(c, null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setText(ids[i]);
            chip.setCheckable(true);
            chip.setTag(i);
            group.addView(chip);
        }
        android.widget.EditText note = new android.widget.EditText(c);
        note.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setHint(R.string.vr_note_hint);
        note.setMaxLines(4);
        note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(RejectReasonRules.MAX)});
        group.setOnCheckedStateChangeListener((g, checked) -> {
            boolean other = !checked.isEmpty() && (int) g.findViewById(checked.get(0)).getTag() == ids.length - 1;
            note.setHint(other ? R.string.vr_note_hint_other : R.string.vr_note_hint);
        });
        box.addView(group);
        box.addView(note);
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(c).setTitle(R.string.vr_reject_title).setView(box)
                .setNegativeButton(R.string.common_cancel, null).setPositiveButton(R.string.review_reject, null).create();
        d.setOnShowListener(x -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (group.getCheckedChipId() == View.NO_ID) { ListArea.toast(this, getString(R.string.vr_reason_needed)); return; }
            int idx = (int) group.findViewById(group.getCheckedChipId()).getTag();
            boolean other = idx == ids.length - 1;
            String reason = RejectReasonRules.compose(getString(ids[idx]), other, note.getText().toString());
            if (reason == null) { note.setError(getString(R.string.vr_note_needed)); return; }
            d.dismiss();
            decide(false, reason);
        }));
        d.show();
    }

    /** Once a decision exists: who decided and when, and the buttons are hidden. Photos and numbers are never part of this. */
    private void loadDecision() {
        if (vid == null || vid.isEmpty()) return;
        final int my = token;
        Async.run(() -> {
            VerificationService s = new VerificationService();
            org.json.JSONObject row = s.decision(vid);
            String who = row != null && !"pending".equals(row.optString("status")) && !row.isNull("reviewed_by") ? s.reviewerName(row.optString("reviewed_by")) : "";
            return new Object[]{row, who};
        }, (Object[] r) -> {
            if (b == null || my != token) return;
            org.json.JSONObject row = (org.json.JSONObject) r[0];
            if (row == null || "pending".equals(row.optString("status"))) return;
            boolean ok = "verified".equals(row.optString("status"));
            String who = ((String) r[1]).isEmpty() ? getString(R.string.vr_reviewer_unknown) : (String) r[1];
            String when = com.bofo.finder.util.AiChatRules.dateTime(row.optString("reviewed_at", ""), ZoneId.systemDefault());
            StringBuilder sb = new StringBuilder(getString(ok ? R.string.vr_reviewed_approved : R.string.vr_reviewed_rejected, who, when));
            String why = row.isNull("rejection_reason") ? "" : row.optString("rejection_reason", "");
            if (!ok && !why.isEmpty()) sb.append('\n').append(getString(R.string.vr_reviewed_reason, why));
            b.decisionLine.setText(sb);
            b.decisionLine.setVisibility(View.VISIBLE);
            b.bottomBar.setVisibility(View.GONE);
        }, e -> { /* the decision line is a bonus: the screen still works without it */ });
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
            Glide.with(this).load(url).override(2048, 2048).diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true).fitCenter().into(into);
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

    /** v2.1.9: the admin must retype the ID number from the photo; the server compares it with what the owner typed (admin_approve_verification). */
    private void approve() {
        android.widget.EditText in = new android.widget.EditText(requireContext());
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        in.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        in.setHint(R.string.review_id_number_hint);
        in.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(BanRules.ID_MAX)});
        android.widget.FrameLayout box = new android.widget.FrameLayout(requireContext());
        int m = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(m, m / 2, m, 0);
        box.addView(in);
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.review_approve_title)
                .setMessage(R.string.review_approve_msg2).setView(box)
                .setNegativeButton(R.string.common_cancel, null).setPositiveButton(R.string.review_approve, null).create();
        d.setOnShowListener(x -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = in.getText().toString();
            if (!BanRules.validNumber(n)) { in.setError(getString(R.string.verify_id_number_bad)); return; }
            d.dismiss();
            approveWith(n);
        }));
        d.show();
    }

    private void approveWith(String idNumber) {
        if (busy || vid == null) return;
        busy = true;
        b.approve.setEnabled(false); b.reject.setEnabled(false);
        Async.run(() -> { new VerificationService().approve(vid, idNumber); return Boolean.TRUE; }, ok -> {
            busy = false;
            ListArea.toast(this, getString(R.string.review_done_approved));
            if (isAdded() && b != null) NavHostFragment.findNavController(this).navigateUp();
        }, e -> {
            busy = false;
            if (b == null) return;
            b.approve.setEnabled(true); b.reject.setEnabled(true);
            String msg = e instanceof com.bofo.finder.service.SupabaseClient.ApiException api ? approveError(api) : null;
            ListArea.toast(this, msg != null ? msg : Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.review_error), e));
        });
    }

    /** The two answers the admin must be told plainly (the ID number is never put in a message). */
    private String approveError(com.bofo.finder.service.SupabaseClient.ApiException api) {
        String body = api.body == null ? "" : api.body;
        if (body.contains("does not match")) return getString(R.string.review_id_mismatch);
        if (body.contains("this ID is banned")) return getString(R.string.review_id_banned);
        return null;
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
            if (!Connectivity.isNetworkError(e)) loadDecision();      // maybe another admin already decided: show who and when
        });
    }

    @Override
    public void onDestroyView() { token++; b = null; super.onDestroyView(); }
}
