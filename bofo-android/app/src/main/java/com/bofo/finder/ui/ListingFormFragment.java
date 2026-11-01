package com.bofo.finder.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
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
import com.bofo.finder.model.College;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.DirectionsRules;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.Geo;
import com.bofo.finder.util.Images;
import com.bofo.finder.util.GenderRules;
import com.bofo.finder.util.ListingRules;
import com.bofo.finder.util.LocationHelper;
import com.bofo.finder.util.Router;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Owner > Create (v2.0.44): the JavaFX ListingFormView as four steps: (1) name, address, map pin picker with the route to the nearest campus,
 * (2) price and rooms with - / + steppers, (3) photos (thumbnails you can remove) and the amenity picker (search, catalog, add your own),
 * (4) contact and a review. A bar at the bottom keeps the error text (copyable), the progress of the 6 required details and Back / Next / Publish.
 * With the argument {@code editId} the same wizard edits that place ({@link BhService#updateListing}); the amenities are replaced too.
 * Publishing is refused by the database for owners who are not verified (RLS).
 */
public class ListingFormFragment extends Fragment {

    private static final int REQUIRED = 6;                  // name, address, pin, price, photo, contact
    private static final int COLLAPSED = 10, MAX_HITS = 24;

    private FragmentListingFormBinding b;
    private LocationHelper location;
    private ActivityResultLauncher<PickVisualMediaRequest> picker;
    private final List<byte[]> photos = new ArrayList<>();
    private final List<int[]> rooms = new ArrayList<>();                     // {beds, occupied}
    private final List<View> roomViews = new ArrayList<>();
    private boolean busy;
    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
        if (ok != null && ok) picked(java.util.Collections.singletonList(cameraUri()));
    });
    private String editId;                  // null = new place
    private int existing;                   // editing: photos the place already has
    private boolean loaded = true;          // editing: false until the place is filled in
    private int step;
    private Double pinLat, pinLng;
    private MapCardView map;
    private List<College> colleges = new ArrayList<>();
    private int routeToken;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    // amenity picker state
    private List<Amenity> catalog = new ArrayList<>();
    private final Map<String, Amenity> byId = new HashMap<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private boolean catalogLoaded, catalogFailed, expanded, adding;
    private Set<String> pendingSelected;                // edit mode: ids to tick once the catalog is there
    private final List<String> rules = new ArrayList<>();   // house rules typed by the owner
    private static final int MAX_RULES = 20;
    private boolean genderColumn;                       // edit mode: the place came back with a gender_policy column (012_gender_policy.sql is run)
    private boolean rulesColumn;                        // edit mode: the place came back with a rules column (011_rules.sql is run)
    private boolean[] freeFlags;                        // edit mode, old places: water / electricity / wifi columns

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
        b.back.setOnClickListener(v -> back());
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        for (int i = 0; i < 4; i++) {                                    // the 4 progress segments
            View s = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) lp.setMarginStart(dp(6));
            b.segs.addView(s, lp);
        }
        b.addRoom.setOnClickListener(v -> addRoom(4, 0));
        addRoom(4, 0);
        b.addPhotos.setOnClickListener(v -> {
            if (ListingRules.photoRoom(existing + photos.size()) == 0) { ListArea.toast(this, getString(R.string.form_photos)); return; }
            picker.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
        });
        b.takePhoto.setOnClickListener(v -> {
            if (ListingRules.photoRoom(existing + photos.size()) == 0) { ListArea.toast(this, getString(R.string.form_photos)); return; }
            takePicture.launch(cameraUri());
        });
        b.publish.setOnClickListener(v -> next());
        b.backStep.setOnClickListener(v -> go(step - 1));
        b.setPin.setOnClickListener(v -> setPin());
        b.copyError.setOnClickListener(v -> copyError());
        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int bf, int c) { refresh(); }
            @Override public void afterTextChanged(Editable s) { }
        };
        for (android.widget.EditText e : new android.widget.EditText[]{b.name, b.address, b.price, b.phone, b.facebook, b.email}) e.addTextChangedListener(w);
        b.amenSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int bf, int c) { renderAmenities(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        b.amenSearch.setOnEditorActionListener((v, id, ev) -> { addTyped(); return true; });
        b.amenMore.setOnClickListener(v -> { expanded = !expanded; renderAmenities(); });
        b.ruleAdd.setOnClickListener(v -> addRule());
        b.ruleInput.setOnEditorActionListener((v, id, ev) -> { addRule(); return true; });
        renderRules();
        buildMap();
        loadCatalog();
        photoCount();
        setErrors("");
        if (editId != null) startEdit();
        go(0);
        return b.getRoot();
    }

    private Uri cameraUri() {
        java.io.File dir = new java.io.File(requireContext().getCacheDir(), "camera");
        dir.mkdirs();
        return androidx.core.content.FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".files", new java.io.File(dir, "listing_capture.jpg"));
    }

    private void back() { if (step > 0) go(step - 1); else NavHostFragment.findNavController(this).navigateUp(); }

    // ------------------------------------------------------------------ steps

    private void go(int i) {
        if (b == null) return;
        step = Math.max(0, Math.min(3, i));
        setErrors("");
        View[] pages = {b.page0, b.page1, b.page2, b.page3};
        for (int k = 0; k < 4; k++) pages[k].setVisibility(k == step ? View.VISIBLE : View.GONE);
        for (int k = 0; k < b.segs.getChildCount(); k++) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(3));
            g.setColor(k <= step ? 0xFF5B3BF0 : 0xFFE7E8F2);
            b.segs.getChildAt(k).setBackground(g);
        }
        int[] titles = {R.string.form_step1, R.string.form_step2, R.string.form_step3, R.string.form_step4};
        int[] hints = {R.string.form_step1_hint, R.string.form_step2_hint, R.string.form_step3_hint, R.string.form_step4_hint};
        b.stepCount.setText(getString(R.string.form_step_count, step + 1));
        b.stepTitle.setText(titles[step]);
        b.stepHint.setText(hints[step]);
        b.backStep.setVisibility(step > 0 ? View.VISIBLE : View.GONE);
        b.publish.setText(step < 3 ? getString(R.string.form_next) : getString(editId == null ? R.string.form_publish_listing : R.string.form_save));
        if (step == 3) updateReview();
        b.scroll.scrollTo(0, 0);
        refresh();
    }

    /** Checks only what this step needs, then moves on (or publishes on the last step). */
    private void next() {
        if (busy || !loaded) return;
        String err = null;
        if (step == 0) {
            if (text(b.name).isEmpty()) err = getString(R.string.form_err_name);
            else if (text(b.address).isEmpty()) err = getString(R.string.form_err_address);
            else if (pinLat == null || pinLng == null) err = getString(R.string.form_err_pin);
        } else if (step == 1) {
            Double p = ListingRules.money(text(b.price));
            if (p == null || p <= 0) err = getString(R.string.form_err_price);
        } else if (step == 2) {
            if (photos.isEmpty() && existing == 0) err = getString(R.string.form_err_photo);
        } else if (text(b.phone).isEmpty() && text(b.facebook).isEmpty() && text(b.email).isEmpty()) {
            err = getString(R.string.form_err_contact);
        }
        if (err != null) { setErrors(err); return; }
        if (step < 3) go(step + 1); else publish();
    }

    private void setErrors(String s) {
        if (b == null) return;
        boolean show = s != null && !s.trim().isEmpty();
        b.errors.setText(s == null ? "" : s);
        b.errorBox.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void copyError() {
        try {
            ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("BOFO error", b.errors.getText()));
            ListArea.toast(this, getString(R.string.form_copied));
        } catch (Exception ignored) { /* nothing to copy to */ }
    }

    /** Counts the required parts that are done and shows it above the Next / Publish button. */
    private void refresh() {
        if (b == null) return;
        int done = 0;
        if (!text(b.name).isEmpty()) done++;
        if (!text(b.address).isEmpty()) done++;
        if (pinLat != null && pinLng != null) done++;
        Double p = ListingRules.money(text(b.price));
        if (p != null && p > 0) done++;
        if (!photos.isEmpty() || existing > 0) done++;
        if (!text(b.phone).isEmpty() || !text(b.facebook).isEmpty() || !text(b.email).isEmpty()) done++;
        b.progressText.setText(done >= REQUIRED ? getString(R.string.form_progress_done) : getString(R.string.form_progress, done));
        b.progressBar.setProgress(Math.min(100, done * 100 / REQUIRED));
    }

    private void updateReview() {
        int beds = 0;
        for (int[] r : rooms) beds += r[0];
        String per = getString(b.modeRoom.isChecked() ? R.string.form_per_room : R.string.form_per_bed).toLowerCase(Locale.ROOT);
        b.review.setText(getString(R.string.form_review_text, text(b.name), text(b.address), text(b.price), per,
                rooms.size(), beds, selected.size(), photos.size() + existing));
    }

    // ------------------------------------------------------------------ map pin picker

    private void buildMap() {
        map = new MapCardView(requireContext());
        map.setParentScrollBlocked(true);
        map.showExpand(false);
        map.setListener(new MapCardView.Listener() {
            @Override public void openPlace(String id) { }
            @Override public void locate() { locateMe(); }
        });
        map.setTapsEnabled(false);
        b.mapFrame.addView(map, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        b.mapFrame.addView(new CornerMaskView(requireContext()), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final MapCardView m = map;
        b.getRoot().postDelayed(() -> {
            if (b == null || map != m || !isAdded()) return;
            m.activate(getViewLifecycleOwner());
            if (editId == null) {                                   // start where the student side starts: the chosen campus, else the first one
                double[] ref = null;
                if (BrowseState.hasRef()) ref = new double[]{BrowseState.refLat(), BrowseState.refLng()};
                else if (BrowseState.colleges != null && !BrowseState.colleges.isEmpty()) ref = new double[]{BrowseState.colleges.get(0).lat(), BrowseState.colleges.get(0).lng()};
                m.moveTo(ref != null ? ref[0] : 14.5995, ref != null ? ref[1] : 120.9842, 15);
                locateMe();                                          // like the JavaFX form: ask once, then fly to the phone
            }
        }, 300);
        Async.run(() -> new BhService().colleges(), (List<College> cs) -> {
            if (b == null) return;
            colleges = cs;
            map.setColleges(cs);
        }, e -> { /* the route to campus is a bonus */ });
    }

    private void locateMe() {
        location.requestOnce(new LocationHelper.Callback() {
            @Override public void got(double lat, double lng) {
                if (b == null || map == null) return;
                map.setUser(lat, lng);
                map.setLocState(LocationHelper.precise(requireContext()) ? MapCardView.Loc.EXACT : MapCardView.Loc.APPROX);
                if (pinLat == null) map.moveTo(lat, lng, 16);        // never pull the map away from a pin that is already set
            }
            @Override public void off() { ListArea.toast(ListingFormFragment.this, getString(R.string.loc_off)); }
            @Override public void denied() { ListArea.toast(ListingFormFragment.this, getString(R.string.loc_denied)); }
            @Override public void failed() { ListArea.toast(ListingFormFragment.this, getString(R.string.loc_failed)); }
        });
    }

    private void setPin() {
        double[] c = map == null ? null : map.center();
        if (c == null) return;
        pinLat = c[0]; pinLng = c[1];
        pinText(String.format(Locale.US, "%.5f, %.5f", pinLat, pinLng), true);
        routeToNearestCollege(c);
        refresh();
    }

    private void pinText(String s, boolean ok) {
        b.pinStatus.setText(getString(R.string.form_pin_set, s));
        b.pinStatus.setTextColor(requireContext().getColor(ok ? R.color.bofo_success : R.color.bofo_text_2));
    }

    /** Draws the walking route from the nearest campus to the dropped pin and shows the distance under the map. */
    private void routeToNearestCollege(double[] pin) {
        if (colleges.isEmpty()) return;
        College c = DirectionsRules.nearestCampus(colleges, pin[0], pin[1]);
        if (c == null) return;
        final int my = ++routeToken;
        final String base = String.format(Locale.US, "%.5f, %.5f", pin[0], pin[1]);
        Router.Route known = Router.peek(c.lat(), c.lng(), pin[0], pin[1]);
        if (known == null) known = Router.estimate(c.lat(), c.lng(), pin[0], pin[1]);
        paintRoute(known, c, pin, base);
        Async.run(() -> Geo.km(c.lat(), c.lng(), pin[0], pin[1]) <= 3 ? Router.walk(c.lat(), c.lng(), pin[0], pin[1]) : Router.drive(c.lat(), c.lng(), pin[0], pin[1]),
                (Router.Route r) -> { if (my == routeToken && b != null && map != null) paintRoute(r, c, pin, base); },
                e -> { /* the estimate stays */ });
    }

    private void paintRoute(Router.Route r, College c, double[] pin, String base) {
        map.setRoute(r.pts(), r.approximate());
        double km = r.meters() / 1000.0;
        map.setRouteLabel(Geo.fmt(km));
        pinText(base + "  ·  " + Geo.fmt(km) + " from " + c.name()
                + (km <= 3 ? " (about " + Geo.fmtTime(Math.max(1, Geo.walkMinutes(km))) + " walk)" : ""), true);
    }

    // ------------------------------------------------------------------ rooms

    private void addRoom(int beds, int occ) {
        final int[] r = {beds, Math.min(occ, beds)};
        rooms.add(r);
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_dir_option);
        card.setPadding(dp(14), dp(10), dp(14), dp(12));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(10);
        card.setLayoutParams(clp);

        LinearLayout head = new LinearLayout(requireContext());
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("", 15, true);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView rm = label("✕", 16, true);
        rm.setPadding(dp(10), dp(4), dp(10), dp(4));
        rm.setOnClickListener(v -> { int i = roomViews.indexOf(card); if (i >= 0 && rooms.size() > 1) { rooms.remove(i); roomViews.remove(i); b.rooms.removeView(card); renumberRooms(); } });
        head.addView(rm);
        card.addView(head);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] occV = new TextView[1];
        View bedsStep = stepper(getString(R.string.form_room_beds_l), 1, 20, r, 0, () -> { if (r[1] > r[0]) r[1] = r[0]; if (occV[0] != null) occV[0].setText(String.valueOf(r[1])); });
        View occStep = stepper(getString(R.string.form_room_occ_l), 0, 20, r, 1, () -> { if (r[1] > r[0]) { r[1] = r[0]; occV[0].setText(String.valueOf(r[1])); } });
        occV[0] = (TextView) ((ViewGroup) occStep).getChildAt(1).findViewWithTag("v");
        row.addView(bedsStep, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(occStep, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row);
        card.setTag(title);
        roomViews.add(card);
        b.rooms.addView(card);
        renumberRooms();
    }

    private void renumberRooms() {
        for (int i = 0; i < roomViews.size(); i++) {
            View card = roomViews.get(i);
            ((TextView) card.getTag()).setText(getString(R.string.form_room_n, i + 1));
            ((ViewGroup) ((ViewGroup) card).getChildAt(0)).getChildAt(1).setVisibility(roomViews.size() > 1 ? View.VISIBLE : View.GONE);
        }
    }

    /** "Caption" over  [-] value [+]; writes into r[idx] (0 = beds, 1 = occupied) and calls changed(). */
    private View stepper(String caption, int min, int max, int[] r, int idx, Runnable changed) {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), dp(8), 0);
        TextView cap = label(caption, 12, false);
        cap.setTextColor(requireContext().getColor(R.color.bofo_text_2));
        box.addView(cap);
        LinearLayout line = new LinearLayout(requireContext());
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = label("−", 22, true), plus = label("+", 22, true), val = label(String.valueOf(r[idx]), 18, true);
        val.setTag("v");
        val.setGravity(Gravity.CENTER);
        minus.setPadding(dp(14), dp(4), dp(14), dp(4));
        plus.setPadding(dp(14), dp(4), dp(14), dp(4));
        minus.setOnClickListener(v -> { int lim = min; if (r[idx] > lim) { r[idx]--; val.setText(String.valueOf(r[idx])); changed.run(); } });
        plus.setOnClickListener(v -> { int lim = idx == 1 ? Math.min(max, r[0]) : max; if (r[idx] < lim) { r[idx]++; val.setText(String.valueOf(r[idx])); changed.run(); } });
        line.addView(minus);
        line.addView(val, new LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT));
        line.addView(plus);
        box.addView(line);
        return box;
    }

    // ------------------------------------------------------------------ photos

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
            for (byte[] jpg : out) { photos.add(jpg); b.thumbs.addView(thumb(jpg)); }
            b.addPhotos.setEnabled(true);
            photoCount();
        }, e -> {
            if (b == null) return;
            b.addPhotos.setEnabled(true);
            ListArea.toast(this, getString(R.string.form_photo_failed));
        });
    }

    /** Square, rounded preview with a small x to remove the photo. */
    private View thumb(byte[] jpg) {
        FrameLayout tile = new FrameLayout(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(76), dp(76));
        lp.setMarginEnd(dp(8));
        tile.setLayoutParams(lp);
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(dp(12));
        card.setCardElevation(0);
        ImageView iv = new ImageView(requireContext());
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 8;                                          // a small preview is enough; the full JPEG is what gets uploaded
        Bitmap bm = BitmapFactory.decodeByteArray(jpg, 0, jpg.length, o);
        if (bm != null) iv.setImageBitmap(bm);
        card.addView(iv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tile.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView x = label("✕", 11, true);
        x.setTextColor(Color.WHITE);
        x.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xB317142A);
        x.setBackground(g);
        FrameLayout.LayoutParams xl = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END);
        xl.setMargins(0, dp(4), dp(4), 0);
        x.setOnClickListener(v -> { photos.remove(jpg); b.thumbs.removeView(tile); photoCount(); });
        tile.addView(x, xl);
        return tile;
    }

    private void photoCount() {
        if (b == null) return;
        String n = getString(R.string.form_photo_count, photos.size());
        b.photoCount.setText(editId == null ? n : getString(R.string.form_edit_photos, existing) + "\n" + n);
        refresh();
    }

    // ------------------------------------------------------------------ amenity picker

    private void loadCatalog() {
        Async.run(() -> new BhService().amenities(), (List<Amenity> l) -> {
            if (b == null) return;
            catalog = new ArrayList<>(l);
            byId.clear();
            for (Amenity a : catalog) byId.put(a.id(), a);
            catalogLoaded = true;
            applyPending();
            renderAmenities();
        }, e -> {
            if (b == null) return;
            catalogFailed = true;                                    // amenities are optional: the form still works without them
            renderAmenities();
        });
    }

    /** Edit mode: tick the place's own amenities (by id, or by the old free water / electricity / Wi-Fi columns) once the catalog is there. */
    private void applyPending() {
        if (!catalogLoaded) return;
        if (pendingSelected != null) selected.addAll(pendingSelected);
        if (freeFlags != null) {
            String[] names = {"free water", "free electricity", "free wi-fi"};
            for (int i = 0; i < 3; i++) if (freeFlags[i]) for (Amenity a : catalog) if (a.name().equalsIgnoreCase(names[i])) selected.add(a.id());
        }
        pendingSelected = null; freeFlags = null;
    }

    private void renderAmenities() {
        if (b == null) return;
        // selected chips (tap one to remove it)
        b.amenSelected.removeAllViews();
        b.amenSelectedLabel.setVisibility(selected.isEmpty() ? View.GONE : View.VISIBLE);
        for (String id : selected) {
            Amenity a = byId.get(id);
            if (a == null) continue;
            Chip c = Chips.create(b.amenSelected, a, "  ✕", false, false);
            c.setClickable(true);
            c.setOnClickListener(v -> { selected.remove(id); renderAmenities(); refresh(); });
            b.amenSelected.addView(c);
        }
        // suggestions
        b.amenities.removeAllViews();
        String q = text(b.amenSearch).toLowerCase(Locale.ROOT);
        List<Amenity> show = new ArrayList<>();
        if (q.isEmpty()) {
            b.amenHeading.setText(R.string.form_amen_popular);
            for (Amenity a : catalog) {
                if (selected.contains(a.id())) continue;
                if (!expanded && show.size() >= COLLAPSED) break;
                show.add(a);
            }
        } else {
            b.amenHeading.setText(R.string.form_amen_results);
            for (Amenity a : catalog) {
                if (selected.contains(a.id()) || !a.name().toLowerCase(Locale.ROOT).contains(q)) continue;
                show.add(a);
                if (show.size() >= MAX_HITS) break;
            }
        }
        for (Amenity a : show) {
            Chip c = Chips.create(b.amenities, a, "", false, false);
            c.setClickable(true);
            c.setOnClickListener(v -> { selected.add(a.id()); b.amenSearch.setText(""); renderAmenities(); refresh(); });
            b.amenities.addView(c);
        }
        String typed = text(b.amenSearch);
        boolean exact = false;
        for (Amenity a : catalog) if (a.name().equalsIgnoreCase(typed.replaceFirst("^[^\\p{L}\\p{N}]+\\s*", ""))) exact = true;
        if (typed.length() >= 2 && !exact && catalogLoaded) {                       // "Add "text"" creates a brand-new amenity
            Chip add = Chips.create(b.amenities, getString(R.string.form_amen_add, typed), false, false);
            add.setClickable(true);
            add.setOnClickListener(v -> addTyped());
            b.amenities.addView(add);
        }
        b.amenMore.setVisibility(q.isEmpty() && catalog.size() > COLLAPSED ? View.VISIBLE : View.GONE);
        b.amenMore.setText(expanded ? R.string.form_amen_less : R.string.form_amen_more);
        b.amenNote.setText(catalogFailed ? getString(R.string.form_amen_missing) : "");
    }

    /** Creates (or finds) the typed amenity; a leading emoji becomes its icon. Needs a verified owner (database function). */
    private void addTyped() {
        String typed = text(b.amenSearch);
        if (adding || typed.length() < 2 || !catalogLoaded) return;
        String icon = null, name = typed;
        int cp = typed.codePointAt(0);
        if (!Character.isLetterOrDigit(cp)) {
            int sp = typed.indexOf(' ');
            if (sp > 0) { icon = typed.substring(0, sp); name = typed.substring(sp + 1).trim(); }
        }
        final String nm = name, ic = icon;
        adding = true;
        Async.run(() -> new BhService().getOrCreateAmenity(nm, ic), (Amenity a) -> {
            adding = false;
            if (b == null) return;
            if (!byId.containsKey(a.id())) { catalog.add(a); byId.put(a.id(), a); }
            selected.add(a.id());
            b.amenSearch.setText("");
            renderAmenities();
            refresh();
        }, e -> {
            adding = false;
            if (b == null) return;
            ListArea.toast(this, getString(R.string.form_amen_failed, e.getMessage() == null ? "" : e.getMessage()));
        });
    }

    private boolean has(String amenityName) {
        for (String id : selected) { Amenity a = byId.get(id); if (a != null && a.name().equalsIgnoreCase(amenityName)) return true; }
        return false;
    }

    // ------------------------------------------------------------------ house rules

    private void addRule() {
        String t = text(b.ruleInput).replaceAll("\\s+", " ");
        if (t.isEmpty()) return;
        if (rules.size() >= MAX_RULES) { ListArea.toast(this, getString(R.string.form_rules_max, MAX_RULES)); return; }
        boolean dup = false;
        for (String r : rules) if (r.equalsIgnoreCase(t)) dup = true;
        if (!dup) rules.add(t);
        b.ruleInput.setText("");
        renderRules();
    }

    private void renderRules() {
        b.rulesList.removeAllViews();
        for (int i = 0; i < rules.size(); i++) {
            final int idx = i;
            android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            android.widget.TextView t = new android.widget.TextView(requireContext());
            t.setText("\u2022  " + rules.get(i));
            t.setTextSize(14f);
            t.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bofo_text));
            row.addView(t, new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            android.widget.TextView x = new android.widget.TextView(requireContext());
            x.setText("\u2715");
            x.setTextSize(16f);
            x.setGravity(android.view.Gravity.CENTER);
            x.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bofo_text_2));
            x.setContentDescription(getString(R.string.form_rules_remove));
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            x.setPadding(pad, pad, pad, pad);
            x.setOnClickListener(v -> { if (idx < rules.size()) rules.remove(idx); renderRules(); });
            row.addView(x);
            b.rulesList.addView(row);
        }
    }

    // ------------------------------------------------------------------ edit mode

    private void startEdit() {
        b.title.setText(R.string.form_edit_title);
        b.publish.setEnabled(false);
        Async.run(() -> new BhService().detail(editId), (JSONObject o) -> {
            if (b == null) return;
            b.name.setText(o.optString("name"));
            b.address.setText(o.optString("address"));
            double price = o.optDouble("price", 0);
            b.price.setText(price > 0 ? (price == Math.rint(price) ? String.valueOf((long) price) : String.valueOf(price)) : "");
            if ("per_room".equals(o.optString("price_mode"))) b.modeRoom.setChecked(true); else b.modeBed.setChecked(true);
            freeFlags = new boolean[]{o.optBoolean("free_water"), o.optBoolean("free_electricity"), o.optBoolean("free_wifi")};
            JSONArray al = o.optJSONArray("amenity_list");
            if (al != null && al.length() > 0) {
                pendingSelected = new LinkedHashSet<>();
                for (int i = 0; i < al.length(); i++) {
                    JSONObject row = al.optJSONObject(i);
                    JSONObject a = row == null ? null : row.optJSONObject("amenities");
                    if (a != null && a.optString("id").length() > 0) pendingSelected.add(a.optString("id"));
                }
                freeFlags = null;                                    // the real amenity rows win over the old columns
            }
            genderColumn = o.has("gender_policy");
            String gp = GenderRules.normalize(o.isNull("gender_policy") ? "" : o.optString("gender_policy", ""));
            if (GenderRules.FEMALE.equals(gp)) b.genderFemale.setChecked(true); else if (GenderRules.MALE.equals(gp)) b.genderMale.setChecked(true); else b.genderAny.setChecked(true);
            JSONArray rl = o.optJSONArray("rules");
            rulesColumn = rl != null;
            rules.clear();
            if (rl != null) for (int i = 0; i < rl.length(); i++) { String r = rl.optString(i, "").trim(); if (!r.isEmpty()) rules.add(r); }
            renderRules();
            applyPending();
            renderAmenities();
            if (!o.isNull("lat") && !o.isNull("lng")) {
                pinLat = o.optDouble("lat"); pinLng = o.optDouble("lng");
                pinText(String.format(Locale.US, "%.5f, %.5f", pinLat, pinLng), true);
                if (map != null) b.getRoot().postDelayed(() -> { if (b != null && map != null) map.moveTo(pinLat, pinLng, 16); }, 600);
            }
            JSONArray rs = o.optJSONArray("rooms");
            if (rs != null && rs.length() > 0) {
                rooms.clear(); roomViews.clear(); b.rooms.removeAllViews();
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
            go(step);
        }, e -> {
            if (b == null) return;
            ListArea.toast(this, getString(R.string.form_edit_load_failed));
            NavHostFragment.findNavController(this).navigateUp();
        });
    }

    // ------------------------------------------------------------------ publish

    private void publish() {
        if (busy || !loaded) return;
        BhService.Draft d = new BhService.Draft();
        d.name = text(b.name); d.address = text(b.address);
        d.price = ListingRules.money(text(b.price));
        d.priceMode = b.modeRoom.isChecked() ? "per_room" : "per_bed";
        d.gender = b.genderFemale.isChecked() ? GenderRules.FEMALE : b.genderMale.isChecked() ? GenderRules.MALE : GenderRules.ANY;
        d.syncGender = editId != null && genderColumn;                // never write a column the database does not have yet
        d.lat = pinLat; d.lng = pinLng;
        d.amenityIds.addAll(selected);
        addRule();                                                    // a rule typed but not yet added is kept
        d.rules.addAll(rules);
        d.syncRules = editId != null && rulesColumn;                  // never write a column the database does not have yet
        d.syncAmenities = catalogLoaded;                              // never wipe a place's amenities when the catalog could not be read
        d.water = has("Free water"); d.electricity = has("Free electricity"); d.wifi = has("Free Wi-Fi");   // old columns stay in sync
        if (!catalogLoaded && editId != null) { d.water = freeFlags != null && freeFlags[0]; d.electricity = freeFlags != null && freeFlags[1]; d.wifi = freeFlags != null && freeFlags[2]; }
        d.phone = text(b.phone); d.facebook = text(b.facebook); d.email = text(b.email);
        for (int[] r : rooms) d.rooms.add(new BhService.RoomDraft(r[0], r[1]));
        d.photos.addAll(photos);
        d.existingPhotos = existing;
        List<String> problems = BhService.validate(d);
        if (!problems.isEmpty()) { setErrors("• " + String.join("\n• ", problems)); return; }
        setErrors("");
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
            b.publish.setText(editId == null ? R.string.form_publish_listing : R.string.form_save);
            String why = Connectivity.isNetworkError(e.getCause() != null ? e.getCause() : e) ? getString(R.string.list_offline)
                    : ErrorText.full(getString(editId == null ? R.string.form_failed : R.string.form_edit_failed, e.getMessage() == null ? "" : e.getMessage()), e);
            setErrors(why + "\n\n" + getString(R.string.form_not_verified));
        });
    }

    // ------------------------------------------------------------------ helpers

    private TextView label(String s, int sp, boolean bold) {
        TextView t = new TextView(requireContext());
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(requireContext().getColor(R.color.bofo_text));
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static String text(android.widget.EditText e) { return e.getText().toString().trim(); }

    @Override
    public void onDestroyView() {
        routeToken++;
        ui.removeCallbacksAndMessages(null);
        map = null;
        b = null;
        rooms.clear();
        roomViews.clear();
        super.onDestroyView();
    }
}
