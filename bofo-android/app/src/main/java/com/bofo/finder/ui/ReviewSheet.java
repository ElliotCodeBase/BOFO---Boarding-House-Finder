package com.bofo.finder.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.SheetReviewBinding;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.ErrorText;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * v2.0.98: a student rates a place (1-5 stars) and may add a comment. One review per student per place:
 * posting again edits it (BhService.upsertReview), and the student can delete it. The place page reloads its reviews through {@link Done}.
 */
public class ReviewSheet extends BottomSheetDialogFragment {

    public interface Done { void changed(); }

    private static final String A_ID = "id", A_NAME = "name", A_RATING = "rating", A_COMMENT = "comment";
    private static final int MAX = 1000;

    private SheetReviewBinding b;
    private Done done;
    private int rating;
    private boolean busy;
    private final TextView[] stars = new TextView[5];

    static void show(Fragment host, String bhId, String bhName, int oldRating, String oldComment, Done done) {
        if (!host.isAdded()) return;
        ReviewSheet s = new ReviewSheet();
        Bundle a = new Bundle();
        a.putString(A_ID, bhId); a.putString(A_NAME, bhName); a.putInt(A_RATING, oldRating); a.putString(A_COMMENT, oldComment);
        s.setArguments(a);
        s.done = done;
        s.show(host.getChildFragmentManager(), "review");
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = SheetReviewBinding.inflate(inflater, container, false);
        Bundle a = requireArguments();
        final boolean editing = a.getInt(A_RATING, 0) > 0;
        rating = state != null ? state.getInt("r", a.getInt(A_RATING, 0)) : a.getInt(A_RATING, 0);
        b.reviewSheetPlace.setText(a.getString(A_NAME, ""));
        b.reviewSheetTitle.setText(editing ? R.string.review_sheet_title_edit : R.string.review_sheet_title);
        b.reviewSubmit.setText(editing ? R.string.review_update : R.string.review_submit);
        b.reviewDelete.setVisibility(editing ? View.VISIBLE : View.GONE);
        if (state == null) b.reviewCommentInput.setText(a.getString(A_COMMENT, ""));

        final float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 5; i++) {
            final int n = i + 1;
            TextView t = new TextView(requireContext());
            t.setText("\u2605");
            t.setTextSize(36);
            t.setGravity(android.view.Gravity.CENTER);
            t.setIncludeFontPadding(false);
            t.setContentDescription(getResources().getQuantityString(R.plurals.review_star_cd, n, n));
            t.setOnClickListener(v -> { rating = n; paint(); });
            b.reviewStarsRow.addView(t, new android.widget.LinearLayout.LayoutParams(Math.round(48 * dp), Math.round(48 * dp)));
            stars[i] = t;
        }
        paint();

        b.reviewCommentInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int bf, int c) { count(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        count();

        b.reviewSubmit.setOnClickListener(v -> submit(a.getString(A_ID), editing));
        b.reviewDelete.setOnClickListener(v -> delete(a.getString(A_ID)));
        return b.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) { super.onSaveInstanceState(out); out.putInt("r", rating); }

    private void paint() {
        int on = ContextCompat.getColor(requireContext(), R.color.bofo_amber);
        int off = ContextCompat.getColor(requireContext(), R.color.bofo_skeleton);
        for (int i = 0; i < 5; i++) stars[i].setTextColor(i < rating ? on : off);
        int[] words = {R.string.review_pick_rating, R.string.review_rating_1, R.string.review_rating_2, R.string.review_rating_3, R.string.review_rating_4, R.string.review_rating_5};
        b.reviewRatingLabel.setText(words[Math.max(0, Math.min(5, rating))]);
        b.reviewSubmit.setEnabled(rating >= 1 && !busy);
    }

    private void count() {
        b.reviewCommentCount.setText(getString(R.string.review_count_left, b.reviewCommentInput.getText().length()));
    }

    private void notifyHost() {
        Fragment p = getParentFragment();
        if (p instanceof BhDetailFragment) ((BhDetailFragment) p).refreshReviews();
        else if (done != null) done.changed();
    }

    private void setBusy(boolean on) {
        busy = on;
        b.reviewSubmit.setEnabled(!on && rating >= 1);
        b.reviewDelete.setEnabled(!on);
        b.reviewCommentInput.setEnabled(!on);
    }

    private void submit(String bhId, boolean editing) {
        final String me = AuthService.get().userId();
        if (me == null || rating < 1 || busy) return;
        final int r = rating;
        final String c = b.reviewCommentInput.getText().toString();
        setBusy(true);
        Async.run(() -> { new BhService().upsertReview(bhId, me, r, c); return Boolean.TRUE; }, ok -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(), editing ? R.string.review_updated : R.string.review_posted, Toast.LENGTH_SHORT).show();
            notifyHost();
            dismissAllowingStateLoss();
        }, e -> {
            if (b == null || !isAdded()) return;
            setBusy(false);
            Toast.makeText(requireContext(), ErrorText.full(getString(R.string.review_failed), e), Toast.LENGTH_LONG).show();
        });
    }

    private void delete(String bhId) {
        final String me = AuthService.get().userId();
        if (me == null || busy) return;
        setBusy(true);
        Async.run(() -> { new BhService().deleteReview(bhId, me); return Boolean.TRUE; }, ok -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(), R.string.review_deleted, Toast.LENGTH_SHORT).show();
            notifyHost();
            dismissAllowingStateLoss();
        }, e -> {
            if (b == null || !isAdded()) return;
            setBusy(false);
            Toast.makeText(requireContext(), ErrorText.full(getString(R.string.review_failed), e), Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onDestroyView() { b = null; super.onDestroyView(); }
}
