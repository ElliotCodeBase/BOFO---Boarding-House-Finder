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
import com.bofo.finder.util.Geo;
import com.bofo.finder.util.Router;
import com.bofo.finder.util.LocationHelper;
import com.google.android.material.slider.Slider;

import java.util.List;
import java.util.Locale;

/**
 * Full-screen map: every listing in range as a pin, the campus (or Near me) ring, a campus picker, a radius slider and a locate button.
 * Opened from Home (expand button) and from a place's detail screen ("Show on map", which also focuses and selects that place).
 */
public class MapFragment extends Fragment {

    public static final String ARG_ID = "focusId", ARG_LAT = "focusLat", ARG_LNG = "focusLng", ARG_IDS = "focusIds", ARG_ROUTE = "route", ARG_FROM_ME = "routeFromMe";

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

    /** Only these places (Ask BOFO "show them on the map"): pins fitted to the screen, the first one selected, no radius limit. */
    public static void open(Fragment from, String[] ids) {
        Bundle a = new Bundle();
        a.putStringArray(ARG_IDS, ids);
        NavHostFragment.findNavController(from).navigate(R.id.mapFragment, a);
    }

    /**
     * Ask BOFO "show me the path": only this place, with the walking path drawn on THIS map (not the place screen).
     * Starts at the chosen college; fromMe (or no college chosen) starts at the phone.
     */
    public static void openRoute(Fragment from, String id, boolean fromMe) {
        Bundle a = new Bundle();
        a.putStringArray(ARG_IDS, new String[]{id});
        a.putBoolean(ARG_ROUTE, true);
        a.putBoolean(ARG_FROM_ME, fromMe);
        NavHostFragment.findNavController(from).navigate(R.id.mapFragment, a);
    }

    private final LocationHelper loc = new LocationHelper(this);    // registers the permission launcher: must be created with the Fragment
    private FragmentMapBinding b;
    private int token;
    private int lastChange = -1;
    private boolean firstLoad = true;
    private String focusId;
    private Double focusLat, focusLng;
    private String[] focusIds;
    private boolean routeMode, routeFromMe;
    private int routeToken;

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
        if (a != null && a.getStringArray(ARG_IDS) != null && a.getStringArray(ARG_IDS).length > 0) focusIds = a.getStringArray(ARG_IDS);
        if (a != null && focusIds != null && a.getBoolean(ARG_ROUTE)) { routeMode = true; routeFromMe = a.getBoolean(ARG_FROM_ME); }
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
            final String[] only = focusIds;                      // a list of ids ignores the radius
            Async.run(() -> new BhService().list(BhService.Tab.NEW, lat, lng, lat == null || only != null ? null : radius, null), (List<BoardingHouse> all) -> {
                if (my != token || b == null) return;
                b.status.setVisibility(View.GONE);
                List<BoardingHouse> list = all;
                boolean idsOnly = false;
                if (only != null) {
                    List<BoardingHouse> keep = new java.util.ArrayList<>();
                    for (BoardingHouse p : all) for (String id : only) if (p.id().equals(id)) { keep.add(p); break; }
                    if (!keep.isEmpty()) { list = keep; idsOnly = true; }     // none left (e.g. no free beds any more): show everything instead of an empty map
                }
                College campus = BrowseState.campus();
                b.map.setPlaces(list);
                b.map.setRing(idsOnly ? null : lat, idsOnly ? null : lng, radius);
                b.map.setCampusDot(campus == null ? null : campus.lat(), campus == null ? null : campus.lng());
                if (idsOnly) {
                    if (list.size() == 1) b.map.moveTo(list.get(0).lat(), list.get(0).lng(), 16); else b.map.fit();
                    if (firstLoad) b.map.select(list.get(0).id());
                    if (routeMode && list.size() == 1) drawRoute(list.get(0));
                } else if (firstLoad && focusId != null) {
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

    /** Start of the path: the phone when asked (or when no college is chosen), else the chosen college. null = nothing to start from. */
    private double[] routeStart() {
        College c = BrowseState.campus();
        Double la = BrowseState.nearMe && BrowseState.userLat != null ? BrowseState.userLat : BrowseState.deviceLat;
        Double ln = BrowseState.nearMe && BrowseState.userLng != null ? BrowseState.userLng : BrowseState.deviceLng;
        boolean phone = la != null && ln != null;
        if ((routeFromMe || c == null) && phone) { b.map.setUser(la, ln); return new double[]{la, ln}; }
        if (c != null) return new double[]{c.lat(), c.lng()};
        return null;
    }

    /** A straight guide line shows at once, then the real road path replaces it. */
    private void drawRoute(BoardingHouse bh) {
        final double[] from = routeStart();
        if (from == null) { b.map.clearRoute(); say(R.string.bofo_route_no_start); return; }
        final double tl = bh.lat(), tg = bh.lng();
        final int my = ++routeToken;
        Router.Route known = Router.peek(from[0], from[1], tl, tg);
        if (known == null) known = Router.estimate(from[0], from[1], tl, tg);
        showRoute(known);
        Async.run(() -> Router.walk(from[0], from[1], tl, tg), (Router.Route r) -> { if (my == routeToken && b != null) showRoute(r); }, e -> { });
    }

    private void showRoute(Router.Route r) {
        double km = r.meters() / 1000.0;
        b.map.setRoute(r.pts(), r.approximate());
        b.map.setRouteLabel(getString(r.approximate() ? R.string.bofo_route_label_est : R.string.bofo_route_label,
                Geo.fmt(km), Geo.fmtTime(Math.max(1, Geo.walkMinutes(km)))));
        b.map.fitRoute();
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
        routeToken++;
        loc.stop();
        b = null;
        super.onDestroyView();
    }
}
