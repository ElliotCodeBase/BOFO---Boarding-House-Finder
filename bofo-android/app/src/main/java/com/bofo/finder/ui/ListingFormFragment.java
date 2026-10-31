package com.bofo.finder.ui;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentListingFormBinding;
import com.bofo.finder.model.Amenity;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.Images;
import com.bofo.finder.util.ListingRules;
import com.bofo.finder.util.LocationHelper;
import com.google.android.material.chip.Chip;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owner > Add a place (8c). Builds a {@link BhService.Draft}, checks it with {@link BhService#validate}, then {@link BhService#publish}
 * (listing, rooms, amenities, contact, photos; a half-made listing is removed on failure). Creating only: editing a place is not ported yet.
 * Location: typed numbers or "Use my location"; a map picker comes later. The database refuses unverified owners (RLS).
 * 8e: with the argument {@code editId} the same form edits that place ({@link BhService#updateListing}): fields are filled from the place, rooms and
 * contact are replaced, NEW photos are added after the existing ones (removing photos and amenities are not ported).
 */
public class ListingFormFragment extends Fragment {

    private FragmentListingFormBinding b;
    private LocationHelper location;
    private ActivityResultLauncher<PickVisualMediaRequest> picker;
    private final List<byte[]> photos = new ArrayList<>();
    private final Map<String, Chip> amenityChips = new LinkedHashMap<>();       // amenity id -> chip
    private final List<EditText[]> roomRows = new ArrayList<>();
    private boolean busy;
    private String editId;                  // null = new place
    private int existing;                   // editing: photos the place already has
    private boolean loaded = true;          // editing: false until the place is filled in

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        editId = getArguments() == null ? null : getArguments().getString("editId");
        loaded = editId == null;
        location = new LocationHelper(this);              // registers the permission launcher: must be made in onCreate
        picker = registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(ListingRules.MAX_PHOTOS), this::picked);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentListingFormBinding.inflate(inflater, container, false);
        b.back.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        b.addRoom.setOnClickListener(v -> addRoom(2, 0));
        addRoom(2, 0);
        b.addPhotos.setOnClickListener(v -> {
            if (ListingRules.photoRoom(existing + photos.size()) == 0) { ListArea.toast(this, getString(R.string.form_photos)); return; }
            picker.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
        });
        b.useLocation.setOnClickListener(v -> locate());
        b.publish.setOnClickListener(v -> publish());
        photoCount();
        if (editId != null) startEdit();
        else Async.run(() -> new BhService().amenities(), (List<Amenity> l) -> {
            if (b == null) return;
            for (Amenity a : l) {
                Chip c = Chips.create(b.amenities, a.label(), true, false);
                b.amenities.addView(c);
                amenityChips.put(a.id(), c);
            }
        }, e -> { /* amenities are optional: the form still works without them */ });
        return b.getRoot();
    }

    private void addRoom(int beds, int taken) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, 8);
        EditText be = number(getString(R.string.form_room_beds), beds), oc = number(getString(R.string.form_room_occ), taken);
        Button rm = new Button(requireContext(), null, 0, R.style.Widget_Bofo_Button_Tint);
        rm.setText(R.string.form_remove);
        EditText[] pair = {be, oc};
        rm.setOnClickListener(v -> { if (roomRows.size() > 1) { roomRows.remove(pair); b.rooms.removeView(row); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(8);
        row.addView(be, lp);
        row.addView(oc, new LinearLayout.LayoutParams(lp));
        row.addView(rm);
        roomRows.add(pair);
        b.rooms.addView(row);
    }

    private EditText number(String hint, int v) {
        EditText e = new EditText(requireContext());
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setHint(hint);
        e.setText(String.valueOf(v));
        e.setBackgroundResource(R.drawable.bg_chat_input);
        e.setPadding(32, 24, 32, 24);
        e.setTextSize(15f);
        return e;
    }

    private void picked(List<Uri> uris) {
        if (uris == null || uris.isEmpty() || b == null) return;
        int room = ListingRules.photoRoom(existing + photos.size());
        List<Uri> take = uris.subList(0, Math.min(room, uris.size()));
        b.addPhotos.setEnabled(false);
        Async.run(() -> {
            List<byte[]> out = new ArrayList<>();
            for (Uri u : take) out.add(Images.listingJpeg(requireContext().getContentResolver(), u));
            return out;
        }, (List<byte[]> out) -> {
            if (b == null) return;
            photos.addAll(out);
            b.addPhotos.setEnabled(true);
            photoCount();
        }, e -> {
            if (b == null) return;
            b.addPhotos.setEnabled(true);
            ListArea.toast(this, getString(R.string.form_photo_failed));
        });
    }

    private void photoCount() {
        String n = getString(R.string.form_photo_count, photos.size());
        b.photoCount.setText(editId == null ? n : getString(R.string.form_edit_photos, existing) + "\n" + n);
    }

    /** Edit mode: titles, no amenity chips (the live project has no amenities tables), then fill the form from the place. */
    private void startEdit() {
        b.title.setText(R.string.form_edit_title);
        b.publish.setText(R.string.form_save);
        b.publish.setEnabled(false);
        b.amenitiesLabel.setVisibility(View.GONE);
        b.amenities.setVisibility(View.GONE);
        Async.run(() -> new BhService().detail(editId), (JSONObject o) -> {
            if (b == null) return;
            b.name.setText(o.optString("name"));
            b.address.setText(o.optString("address"));
            double price = o.optDouble("price", 0);
            b.price.setText(price > 0 ? (price == Math.rint(price) ? String.valueOf((long) price) : String.valueOf(price)) : "");
            if ("per_room".equals(o.optString("price_mode"))) b.modeRoom.setChecked(true); else b.modeBed.setChecked(true);
            b.freeWater.setChecked(o.optBoolean("free_water")); b.freeElectricity.setChecked(o.optBoolean("free_electricity")); b.freeWifi.setChecked(o.optBoolean("free_wifi"));
            if (!o.isNull("lat") && !o.isNull("lng")) {
                b.lat.setText(String.format(java.util.Locale.US, "%.6f", o.optDouble("lat")));
                b.lng.setText(String.format(java.util.Locale.US, "%.6f", o.optDouble("lng")));
            }
            JSONArray rs = o.optJSONArray("rooms");
            if (rs != null && rs.length() > 0) {
                roomRows.clear(); b.rooms.removeAllViews();
                for (int i = 0; i < rs.length(); i++) { JSONObject r = rs.optJSONObject(i); if (r != null) addRoom(r.optInt("beds", 1), r.optInt("occupants", 0)); }
            }
            JSONArray cs = o.optJSONArray("contact");
            JSONObject c = cs != null && cs.length() > 0 ? cs.optJSONObject(0) : null;
            if (c != null) {
                b.phone.setText(c.isNull("phone") ? "" : c.optString("phone"));
                b.facebook.setText(c.isNull("facebook") ? "" : c.optString("facebook"));
                b.email.setText(c.isNull("email") ? "" : c.optString("email"));
            }
            JSONArray ps = o.optJSONArray("photos");
            existing = ps == null ? 0 : ps.length();
            photoCount();
            loaded = true;
            b.publish.setEnabled(true);
        }, e -> {
            if (b == null) return;
            ListArea.toast(this, getString(R.string.form_edit_load_failed));
            NavHostFragment.findNavController(this).navigateUp();
        });
    }

    private void locate() {
        location.requestOnce(new LocationHelper.Callback() {
            @Override public void got(double lat, double lng) {
                if (b == null) return;
                b.lat.setText(String.format(java.util.Locale.US, "%.6f", lat));
                b.lng.setText(String.format(java.util.Locale.US, "%.6f", lng));
            }
            @Override public void off() { ListArea.toast(ListingFormFragment.this, getString(R.string.loc_off)); }
            @Override public void denied() { ListArea.toast(ListingFormFragment.this, getString(R.string.loc_denied)); }
            @Override public void failed() { ListArea.toast(ListingFormFragment.this, getString(R.string.loc_failed)); }
        });
    }

    private void publish() {
        if (busy || !loaded) return;
        BhService.Draft d = new BhService.Draft();
        d.name = text(b.name); d.address = text(b.address);
        d.price = ListingRules.money(text(b.price));
        d.priceMode = b.modeRoom.isChecked() ? "per_room" : "per_bed";
        double[] c = ListingRules.coords(text(b.lat), text(b.lng));
        if (c != null) { d.lat = c[0]; d.lng = c[1]; }
        d.water = b.freeWater.isChecked(); d.electricity = b.freeElectricity.isChecked(); d.wifi = b.freeWifi.isChecked();
        d.phone = text(b.phone); d.facebook = text(b.facebook); d.email = text(b.email);
        for (EditText[] r : roomRows) d.rooms.add(new BhService.RoomDraft(ListingRules.intOr(text(r[0]), 0), ListingRules.intOr(text(r[1]), 0)));
        for (Map.Entry<String, Chip> e : amenityChips.entrySet()) if (e.getValue().isChecked()) d.amenityIds.add(e.getKey());
        d.photos.addAll(photos);
        d.existingPhotos = existing;
        List<String> problems = BhService.validate(d);
        if (!problems.isEmpty()) {
            b.errors.setText("• " + String.join("\n• ", problems));
            b.errors.setVisibility(View.VISIBLE);
            return;
        }
        b.errors.setVisibility(View.GONE);
        busy = true;
        b.publish.setEnabled(false);
        b.publish.setText(editId == null ? R.string.form_publishing : R.string.form_saving);
        String me = AuthService.get().userId();
        Async.run(() -> { if (editId == null) return new BhService().publish(me, d); new BhService().updateListing(me, editId, d); return editId; }, (String id) -> {
            busy = false;
            if (b == null) return;
            ListArea.toast(this, getString(editId == null ? R.string.form_done : R.string.form_saved));
            NavHostFragment.findNavController(this).navigateUp();
        }, e -> {
            busy = false;
            if (b == null) return;
            b.publish.setEnabled(true);
            b.publish.setText(editId == null ? R.string.form_publish : R.string.form_save);
            String why = Connectivity.isNetworkError(e.getCause() != null ? e.getCause() : e) ? getString(R.string.list_offline)
                    : ErrorText.full(getString(editId == null ? R.string.form_failed : R.string.form_edit_failed, e.getMessage() == null ? "" : e.getMessage()), e);
            b.errors.setText(why + "\n\n" + getString(R.string.form_not_verified));
            b.errors.setVisibility(View.VISIBLE);
        });
    }

    private static String text(EditText e) { return e.getText().toString().trim(); }

    @Override
    public void onDestroyView() {
        b = null;
        amenityChips.clear();
        roomRows.clear();
        super.onDestroyView();
    }
}
