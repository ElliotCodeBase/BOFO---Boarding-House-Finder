package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentMapBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.AppPrefs;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.LocationHelper;
import com.google.android.material.slider.Slider;

import java.util.List;
import java.util.Locale;

/**
 * Full-screen map: every listing in range as a pin, the campus (or Near me) ring, a campus picker, a radius slider and a locate button.
 * Opened from Home (expand button) and from a place's detail screen ("Show on map", which also focuses and selects that place).
 */
public class MapFragment extends Fragment {

    public static final String ARG_ID = "focusId", ARG_LAT = "focusLat", ARG_LNG = "focusLng";

    /** All listings, centred on the campus ring or the default area. */
    public static void open(Fragment from) {
        NavHostFragment.findNavController(from).navigate(R.id.mapFragment, new Bundle());
    }

    /** Centred on one place, its pin selected. */
    public static void open(Fragment from, String id, double lat, double lng) {
        Bundle a = new Bundle();
        a.putString(ARG_ID, id);
        a.putFloat(ARG_LAT, (float) lat);
        a.putFloat(ARG_LNG, (float) lng);
        NavHostFragment.findNavController(from).navigate(R.id.mapFragment, a);
    }

    private final LocationHelper loc = new LocationHelper(this);    // registers the permission launcher: must be created with the Fragment
    private FragmentMapBinding b;
    private int token;
    private int lastChange = -1;
    private boolean firstLoad = true;
    private String focusId;
    private Double focusLat, focusLng;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentMapBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        Bundle a = getArguments();
        if (a != null && a.getString(ARG_ID) != null) {
            focusId = a.getString(ARG_ID);
            focusLat = (double) a.getFloat(ARG_LAT);
            focusLng = (double) a.getFloat(ARG_LNG);
        }
        b.back.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        b.campusBtn.setOnClickListener(v -> new CampusPickerSheet().show(getChildFragmentManager(), "campus"));
        b.radius.addOnChangeListener((s, value, fromUser) -> paintRadiusLabel(value));
        b.radius.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider s) { }
            @Override public void onStopTrackingTouch(@NonNull Slider s) { BrowseState.radiusKm = s.getValue(); BrowseState.bump(); }
        });
        b.map.setListener(new MapCardView.Listener() {
            @Override public void openPlace(String id) { BhDetailFragment.open(MapFragment.this, id); }
            @Override public void locate() { locateMe(); }
        });
        b.map.activate(getViewLifecycleOwner());
        // The first emission is the initial load; later ones come from the campus picker / radius slider.
        BrowseState.CHANGED.observe(getViewLifecycleOwner(), n -> { if (n == null || n == lastChange) return; lastChange = n; reload(); });
    }

    private void reload() {
        if (b == null) return;
        final int my = ++token;
        b.status.setText(R.string.map_loading);
        b.status.setVisibility(View.VISIBLE);
        BrowseState.ensureLoaded(() -> {
            if (my != token || b == null) return;
            paintTop();
            final Double lat = BrowseState.refLat(), lng = BrowseState.refLng();
            final double radius = BrowseState.radiusKm;
            Async.run(() -> new BhService().list(BhService.Tab.NEW, lat, lng, lat == null ? null : radius, null), (List<BoardingHouse> list) -> {
                if (my != token || b == null) return;
                b.status.setVisibility(View.GONE);
                College campus = BrowseState.campus();
                b.map.setPlaces(list);
                b.map.setRing(lat, lng, radius);
                b.map.setCampusDot(campus == null ? null : campus.lat(), campus == null ? null : campus.lng());
                if (firstLoad && focusId != null) {
                    b.map.moveTo(focusLat, focusLng, 16);
                    b.map.select(focusId);              // no-op when the place is outside the radius (it is then just centred)
                } else {
                    b.map.fit();
                }
                firstLoad = false;
                startLive();
            }, e -> fail(my, e));
        }, e -> fail(my, e));
    }

    private void fail(int my, Throwable e) {
        if (my != token || b == null) return;
        b.status.setText(Connectivity.isNetworkError(e) ? R.string.list_offline : R.string.list_error);
        b.status.setVisibility(View.VISIBLE);
    }

    private void paintTop() {
        College campus = BrowseState.campus();
        boolean ref = BrowseState.hasRef();
        b.campusBtn.setText(campus != null ? getString(R.string.home_campus_set, campus.name())
                : BrowseState.nearMe ? getString(R.string.home_near_you) : getString(R.string.home_campus_none));
        b.radiusBox.setVisibility(ref ? View.VISIBLE : View.GONE);
        if (ref) {
            float v = (float) Math.max(BrowseState.RADIUS_MIN, Math.min(BrowseState.RADIUS_MAX, BrowseState.radiusKm));
            if (b.radius.getValue() != v) b.radius.setValue(v);
            paintRadiusLabel(v);
        }
    }

    private void paintRadiusLabel(float km) {
        College c = BrowseState.campus();
        String n = String.format(Locale.US, km % 1 == 0 ? "%.0f" : "%.1f", km);
        b.radiusLabel.setText(getString(R.string.home_radius, n, c == null ? getString(R.string.map_you) : c.name()));
    }

    private void locateMe() {
        loc.requestOnce(new LocationHelper.Callback() {
            @Override public void got(double lat, double lng) {
                if (b == null) return;
                b.map.setUser(lat, lng);
                b.map.moveTo(lat, lng, 15);
                startLive();
            }
            @Override public void off() { say(R.string.loc_off); }
            @Override public void denied() { say(R.string.loc_denied); }
            @Override public void failed() { say(R.string.loc_failed); }
        });
    }

    /** Live dot, only when the permission is already granted and Settings > Use my location is on (never asks by itself). */
    private void startLive() {
        if (b == null || getContext() == null || !LocationHelper.granted(requireContext()) || !AppPrefs.useLocation(requireContext())) return;
        loc.startWatching(new LocationHelper.Callback() {
            @Override public void got(double lat, double lng) { if (b != null) b.map.setUser(lat, lng); }
            @Override public void off() { }
            @Override public void denied() { }
            @Override public void failed() { }
        });
    }

    private void say(int res) { ListArea.toast(this, getString(res)); }

    @Override
    public void onStart() { super.onStart(); if (!firstLoad) startLive(); }

    @Override
    public void onStop() { loc.stop(); super.onStop(); }

    @Override
    public void onDestroyView() {
        token++;                       // late results are ignored
        loc.stop();
        b = null;
        super.onDestroyView();
    }
}
