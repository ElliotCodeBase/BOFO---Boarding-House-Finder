package com.bofo.finder.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentProfileSetupBinding;
import com.bofo.finder.model.College;
import com.bofo.finder.model.Profile;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.service.ProfileService;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.Images;
import com.bofo.finder.util.Regions;
import com.bofo.finder.util.Validate;
import com.bumptech.glide.Glide;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Profile form: photo (camera or gallery), name, username, phone, age, sex, college, region. First run (edit = false) ends in the app;
 * opened from the Profile tab / Settings (edit = true) it just goes back. Only the name is required.
 */
public class ProfileSetupFragment extends Fragment {
    public static final String ARG_EDIT = "edit";

    private static final String[] SEX_KEYS = {"male", "female", "other"};

    private FragmentProfileSetupBinding b;
    private boolean edit;
    private boolean busy;
    private boolean hasPending;                 // a new avatar waiting in cache/avatar_pending.jpg
    private String existingPhotoUrl = "";
    private String sexKey = "", collegeId = "", regionKey = "";
    private List<College> colleges = new ArrayList<>();

    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
        if (ok != null && ok) useImage(cameraUri());
    });
    private final ActivityResultLauncher<PickVisualMediaRequest> pickPicture = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
        if (uri != null) useImage(uri);
    });

    private File cameraFile() {
        File dir = new File(requireContext().getCacheDir(), "camera");
        dir.mkdirs();
        return new File(dir, "avatar_capture.jpg");
    }
    private Uri cameraUri() {
        return FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".files", cameraFile());
    }
    private File pendingFile() { return new File(requireContext().getCacheDir(), "avatar_pending.jpg"); }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentProfileSetupBinding.inflate(inflater, container, false);
        edit = getArguments() != null && getArguments().getBoolean(ARG_EDIT, false);
        if (state != null) hasPending = state.getBoolean("pending");
        b.title.setText(edit ? R.string.setup_edit_title : R.string.setup_title);

        String[] sexLabels = {getString(R.string.sex_male), getString(R.string.sex_female), getString(R.string.sex_other)};
        b.sex.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, sexLabels));
        b.sex.setOnItemClickListener((p, v, pos, id) -> sexKey = SEX_KEYS[pos]);

        List<String> regionNames = new ArrayList<>();
        for (Regions.Region r : Regions.ALL) regionNames.add(r.name());
        b.region.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, regionNames));
        b.region.setOnItemClickListener((p, v, pos, id) -> regionKey = Regions.ALL.get(pos).key());

        b.college.setOnItemClickListener((p, v, pos, id) -> { if (pos < colleges.size()) collegeId = colleges.get(pos).id(); });

        b.photoTake.setOnClickListener(v -> takePicture.launch(cameraUri()));
        b.photoChoose.setOnClickListener(v -> pickPicture.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        b.save.setOnClickListener(v -> save());

        if (hasPending && pendingFile().exists()) showPending();
        load();
        return b.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("pending", hasPending);
    }

    @Override
    public void onDestroyView() { b = null; super.onDestroyView(); }

    /* ---------- load ---------- */
    private void load() {
        Async.run(() -> {
            Profile p = new ProfileService().fetchMe();
            List<College> cl = new ArrayList<>();
            try { cl = new BhService().colleges(); } catch (Exception ignored) { /* the college list is optional */ }
            return new Object[]{p, cl};
        }, r -> {
            if (b == null) return;
            @SuppressWarnings("unchecked") List<College> cl = (List<College>) r[1];
            colleges = cl;
            List<String> names = new ArrayList<>();
            for (College c : cl) names.add(c.name());
            b.college.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, names));
            fill((Profile) r[0]);
        }, e -> { if (b != null) message(ErrorText.full("Could not load your profile", e)); });
    }

    /** Fills only empty fields, so text typed before the load finished (or kept over a rotation) is not overwritten. */
    private void fill(@Nullable Profile p) {
        AuthService au = AuthService.get();
        String name = p != null && !Validate.blank(p.fullName()) ? p.fullName() : au.metaName();
        if (empty(b.fullName)) b.fullName.setText(name);
        existingPhotoUrl = p != null && !Validate.blank(p.photoUrl()) ? p.photoUrl() : au.metaPhoto();
        if (!hasPending && !existingPhotoUrl.isEmpty()) Glide.with(this).load(existingPhotoUrl).circleCrop().placeholder(R.drawable.mascot_happy).into(b.avatar);
        if (p == null) return;
        if (empty(b.username)) b.username.setText(p.username());
        if (empty(b.phone)) b.phone.setText(p.phone());
        if (empty(b.age) && p.age() != null) b.age.setText(String.valueOf(p.age()));
        if (sexKey.isEmpty() && !p.sex().isEmpty()) {
            for (int i = 0; i < SEX_KEYS.length; i++) if (SEX_KEYS[i].equals(p.sex())) { sexKey = p.sex(); b.sex.setText(b.sex.getAdapter().getItem(i).toString(), false); }
        }
        if (regionKey.isEmpty() && Regions.byKey(p.region()) != null) { regionKey = p.region(); b.region.setText(Regions.byKey(p.region()).name(), false); }
        if (collegeId.isEmpty() && p.defaultCollegeId() != null) {
            for (College c : colleges) if (c.id().equals(p.defaultCollegeId())) { collegeId = c.id(); b.college.setText(c.name(), false); }
        }
    }

    private static boolean empty(android.widget.EditText e) { return e.getText() == null || e.getText().toString().trim().isEmpty(); }
    private static String text(android.widget.EditText e) { return e.getText() == null ? "" : e.getText().toString().trim(); }

    /* ---------- photo ---------- */
    private void useImage(Uri uri) {
        final android.content.ContentResolver cr = requireContext().getContentResolver();
        final File out = pendingFile();
        Async.run(() -> {
            byte[] jpeg = Images.avatarJpeg(cr, uri);
            try (FileOutputStream f = new FileOutputStream(out)) { f.write(jpeg); }
            return Boolean.TRUE;
        }, ok -> { hasPending = true; if (b != null) showPending(); },
           e -> { if (b != null) message(ErrorText.full("Could not use that picture", e)); });
    }

    private void showPending() {
        Bitmap bmp = BitmapFactory.decodeFile(pendingFile().getAbsolutePath());
        if (bmp != null) Glide.with(this).load(bmp).circleCrop().into(b.avatar);
    }

    /* ---------- save ---------- */
    private void message(String t) {
        if (b == null) return;
        b.message.setVisibility(t == null || t.isEmpty() ? View.GONE : View.VISIBLE);
        b.message.setText(t == null ? "" : t);
    }

    private void save() {
        if (busy) return;
        String name = Validate.clean(text(b.fullName), 80);
        String username = text(b.username), phone = text(b.phone), ageText = text(b.age);
        boolean ok = true;
        b.fullNameLayout.setError(name.length() >= 2 ? null : getString(R.string.setup_err_name));
        ok &= name.length() >= 2;
        boolean uOk = username.isEmpty() || Validate.username(username);
        b.usernameLayout.setError(uOk ? null : getString(R.string.setup_err_username)); ok &= uOk;
        boolean pOk = phone.isEmpty() || Validate.phone(phone);
        b.phoneLayout.setError(pOk ? null : getString(R.string.setup_err_phone)); ok &= pOk;
        Integer age = ageText.isEmpty() ? null : Validate.age(ageText);
        boolean aOk = ageText.isEmpty() || age != null;
        b.ageLayout.setError(aOk ? null : getString(R.string.setup_err_age)); ok &= aOk;
        if (!ok) return;

        busy = true; b.save.setEnabled(false); b.progress.setVisibility(View.VISIBLE); message(null);
        final boolean uploadAvatar = hasPending && pendingFile().exists();
        final File pending = pendingFile();
        final String fSex = sexKey, fCollege = collegeId, fRegion = regionKey;
        Async.run(() -> {
            JSONObject patch = new JSONObject();
            patch.put("full_name", name);
            patch.put("username", username.isEmpty() ? JSONObject.NULL : username);
            patch.put("phone", phone.isEmpty() ? JSONObject.NULL : Validate.clean(phone, 30));
            patch.put("age", age == null ? JSONObject.NULL : age);
            patch.put("sex", fSex.isEmpty() ? JSONObject.NULL : fSex);
            patch.put("default_college_id", fCollege.isEmpty() ? JSONObject.NULL : fCollege);
            patch.put("region", fRegion.isEmpty() ? JSONObject.NULL : fRegion);
            ProfileService ps = new ProfileService();
            if (uploadAvatar) {
                byte[] data = new byte[(int) pending.length()];
                try (java.io.FileInputStream in = new java.io.FileInputStream(pending)) {
                    int off = 0, n;
                    while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
                }
                patch.put("photo_url", ps.uploadAvatar(data));
            } else if (!existingPhotoUrl.isEmpty()) {
                patch.put("photo_url", existingPhotoUrl);        // first run: keep the Google picture
            }
            return ps.update(patch);
        }, saved -> {
            //noinspection ResultOfMethodCallIgnored
            pendingFile().delete();
            hasPending = false;
            if (b == null) return;
            if (edit) NavHostFragment.findNavController(this).popBackStack();
            else NavHostFragment.findNavController(this).navigate(R.id.action_global_shell);
        }, e -> {
            busy = false;
            if (b == null) return;
            b.save.setEnabled(true); b.progress.setVisibility(View.GONE);
            if (e instanceof SupabaseClient.ApiException && ((SupabaseClient.ApiException) e).body.contains("username")
                    && ((SupabaseClient.ApiException) e).body.contains("23505")) {
                b.usernameLayout.setError(getString(R.string.setup_username_taken));
            } else message(ErrorText.full("Could not save your profile", e));
        });
    }
}
