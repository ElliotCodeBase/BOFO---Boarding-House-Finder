package com.bofo.finder.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentVerificationBinding;
import com.bofo.finder.model.Profile;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.ProfileService;
import com.bofo.finder.service.VerificationService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.Images;
import com.bofo.finder.util.VerificationRules;
import com.bofo.finder.util.VerificationRules.State;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.File;

/**
 * Owner > Verification (8d). Shows where the request stands (not verified / waiting / verified / not approved + reason) and, when the owner may send one,
 * the form: type of ID, ID front and back (camera or gallery), a live selfie (camera only), a consent tick, Send for review
 * ({@link VerificationService#submit}: private bucket id-verification, user_agreements row, owner_verifications row, status pending).
 * Photos are shrunk to 1600 px JPEG; a cheap blur check ({@link VerificationService#looksSharp}) only WARNS, it never blocks.
 * Photos live in memory only (a ViewModel keeps them over rotation and while another screen is open; they are wiped when the screen is left or after sending).
 * If Android kills the app while the camera is open, the picture the camera app saved is still picked up when it returns.
 */
public class VerificationFragment extends Fragment {

    private static final String KEY_SLOT = "slot";

    private FragmentVerificationBinding b;
    /** Survives rotation and the back stack; cleared when the screen is popped. */
    public static class Draft extends ViewModel {
        final byte[][] photos = new byte[3][];
        final boolean[] sharp = {true, true, true};
        int idTypeIndex;
        @Override protected void onCleared() { java.util.Arrays.fill(photos, null); }
    }

    private Draft draft;
    private byte[][] photos;
    private int pendingSlot = -1;                 // the slot the camera / picker was opened for
    private int token;                            // drops answers that arrive after a newer load or after the view is gone
    private boolean busy;
    private State state = State.UNVERIFIED;

    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
        if (ok != null && ok && pendingSlot >= 0) use(pendingSlot, cameraUri(pendingSlot));
    });
    private final ActivityResultLauncher<PickVisualMediaRequest> pickPicture = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
        if (uri != null && pendingSlot >= 0) use(pendingSlot, uri);
    });

    private File cameraFile(int slot) {
        File dir = new File(requireContext().getCacheDir(), "camera");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "verify_" + slot + ".jpg");
    }

    private Uri cameraUri(int slot) {
        return FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".files", cameraFile(slot));
    }

    @Override
    public void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        draft = new ViewModelProvider(this).get(Draft.class);
        photos = draft.photos;
        if (saved != null) pendingSlot = saved.getInt(KEY_SLOT, -1);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(KEY_SLOT, pendingSlot);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle saved) {
        b = FragmentVerificationBinding.inflate(inflater, container, false);
        b.back.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        b.retry.setOnClickListener(v -> load());
        b.idType.setText(VerificationRules.idType(draft.idTypeIndex));
        b.idType.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.verify_id_type)
                .setItems(VerificationRules.ID_TYPES, (d, which) -> { draft.idTypeIndex = which; b.idType.setText(VerificationRules.idType(which)); }).show());
        b.frontTake.setOnClickListener(v -> take(VerificationRules.FRONT));
        b.backTake.setOnClickListener(v -> take(VerificationRules.BACK));
        b.selfieTake.setOnClickListener(v -> take(VerificationRules.SELFIE));
        b.frontPick.setOnClickListener(v -> pick(VerificationRules.FRONT));
        b.backPick.setOnClickListener(v -> pick(VerificationRules.BACK));
        b.submit.setOnClickListener(v -> submit());
        for (int i = 0; i < 3; i++) if (photos[i] != null) show(i, photos[i], draft.sharp[i]);      // the view was rebuilt (back from another screen)
        load();
        return b.getRoot();
    }

    private void take(int slot) {
        pendingSlot = slot;
        takePicture.launch(cameraUri(slot));
    }

    private void pick(int slot) {
        pendingSlot = slot;
        pickPicture.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
    }

    /* ---------- status ---------- */

    private void load() {
        if (b == null) return;
        final int my = ++token;
        b.message.setText(R.string.verify_loading);
        b.message.setVisibility(View.VISIBLE);
        b.retry.setVisibility(View.GONE);
        String me = AuthService.get().userId();
        Async.run(() -> {
            Profile p = new ProfileService().fetchMe();
            JSONObject last = new VerificationService().myLatest(me);
            return new Object[]{VerificationRules.effective(p != null && p.verified(), last.optString("status", "unverified")), last.optString("rejection_reason", "")};
        }, (Object[] r) -> {
            if (my != token || b == null) return;
            state = (State) r[0];
            showState(state, (String) r[1]);
        }, e -> {
            if (my != token || b == null) return;
            b.status.setVisibility(View.GONE);
            b.form.setVisibility(View.GONE);
            b.message.setText(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.verify_load_failed), e));
            b.message.setVisibility(View.VISIBLE);
            b.retry.setVisibility(View.VISIBLE);
        });
    }

    private void showState(State s, String reason) {
        b.message.setVisibility(View.GONE);
        b.retry.setVisibility(View.GONE);
        int text, tint;
        switch (s) {
            case PENDING: text = R.string.verify_state_pending; tint = R.color.bofo_warn_tint; break;
            case VERIFIED: text = R.string.verify_state_verified; tint = R.color.bofo_success_tint; break;
            case REJECTED: text = R.string.verify_state_rejected; tint = R.color.bofo_error_tint; break;
            default: text = R.string.verify_state_unverified; tint = R.color.bofo_primary_tint; break;
        }
        String msg = getString(text);
        if (s == State.REJECTED && reason != null && !reason.trim().isEmpty()) msg += "\n\n" + getString(R.string.verify_reason, reason.trim());
        b.status.setText(msg);
        b.status.setBackgroundColor(ContextCompat.getColor(requireContext(), tint));
        b.status.setVisibility(View.VISIBLE);
        b.form.setVisibility(VerificationRules.canSubmit(s) ? View.VISIBLE : View.GONE);
    }

    /* ---------- photos ---------- */

    private void use(int slot, Uri uri) {
        if (b == null) return;
        Async.run(() -> {
            byte[] jpeg = Images.listingJpeg(requireContext().getContentResolver(), uri);
            Bitmap bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            return new Object[]{jpeg, VerificationService.looksSharp(bmp)};
        }, (Object[] r) -> {
            photos[slot] = (byte[]) r[0];
            draft.sharp[slot] = (Boolean) r[1];
            if (b != null) show(slot, photos[slot], draft.sharp[slot]);
        }, e -> {
            if (b == null) return;
            ListArea.toast(this, getString(R.string.verify_photo_failed));
        });
    }

    /** Thumbnail (a quarter of the size) and the note under it. */
    private void show(int slot, byte[] jpeg, boolean sharp) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 4;
        Bitmap thumb = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
        ImageView iv = slot == VerificationRules.FRONT ? b.frontThumb : slot == VerificationRules.BACK ? b.backThumb : b.selfieThumb;
        TextView note = slot == VerificationRules.FRONT ? b.frontNote : slot == VerificationRules.BACK ? b.backNote : b.selfieNote;
        iv.setImageBitmap(thumb);
        note.setText(sharp ? R.string.verify_ready : R.string.verify_blurry);
    }

    /* ---------- send ---------- */

    private void submit() {
        if (busy || !VerificationRules.canSubmit(state)) return;
        int missing = VerificationRules.firstMissing(photos[0], photos[1], photos[2]);
        String problem = missing == VerificationRules.FRONT ? getString(R.string.verify_missing_front)
                : missing == VerificationRules.BACK ? getString(R.string.verify_missing_back)
                : missing == VerificationRules.SELFIE ? getString(R.string.verify_missing_selfie)
                : !b.consent.isChecked() ? getString(R.string.verify_need_consent) : null;
        if (problem != null) { error(problem); return; }
        b.errors.setVisibility(View.GONE);
        busy = true;
        b.submit.setEnabled(false);
        b.submit.setText(R.string.verify_submitting);
        String me = AuthService.get().userId(), type = VerificationRules.idType(draft.idTypeIndex);
        byte[] f = photos[0], k = photos[1], s = photos[2];
        Async.run(() -> { new VerificationService().submit(me, type, f, k, s, VerificationRules.consentVersion()); return Boolean.TRUE; }, ok -> {
            busy = false;
            if (b == null) return;
            b.submit.setEnabled(true);
            b.submit.setText(R.string.verify_submit);
            java.util.Arrays.fill(photos, null);                                   // the ID photos do not stay in memory
            state = State.PENDING;
            showState(State.PENDING, "");
            ListArea.toast(this, getString(R.string.verify_sent));
        }, e -> {
            busy = false;
            if (b == null) return;
            b.submit.setEnabled(true);
            b.submit.setText(R.string.verify_submit);
            error(Connectivity.isNetworkError(e.getCause() != null ? e.getCause() : e) ? getString(R.string.list_offline)
                    : ErrorText.full(getString(R.string.verify_failed, e.getMessage() == null ? "" : e.getMessage()), e));
        });
    }

    private void error(String msg) {
        b.errors.setText(msg);
        b.errors.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        token++;
        b = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        // leaving the screen for good: the camera copies of the ID do not stay in the cache (a rotation keeps them)
        if (getActivity() != null && !getActivity().isChangingConfigurations() && getContext() != null)
            for (int i = 0; i < 3; i++) cameraFile(i).delete();
        super.onDestroy();
    }
}
