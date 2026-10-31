package com.bofo.finder.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentHomeBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;
import com.bofo.finder.model.Profile;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.AppPrefs;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.CampusMatch;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.Favorites;
import com.bofo.finder.util.ExploreHandoff;
import com.bofo.finder.util.ListRules;
import com.bofo.finder.util.LocationHelper;
import com.bofo.finder.util.PriceRange;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Home (one scrolling page): greeting row, one search bar, the inline map card with its radius pill, New / Hot / Best rated tabs, rent range and the card grid. */
public class HomeFragment extends Fragment {

    private static final BhService.Tab[] TABS = {BhService.Tab.NEW, BhService.Tab.HOT, BhService.Tab.BEST};

    private record Result(List<BoardingHouse> list, PriceRange range) { }

    private FragmentHomeBinding b;
    private BhAdapter adapter;
    private ListArea area;
    private HomeSearchBar search;
    private String paintedPhoto = "";
    private int token;
    private int lastChange = -1;
    private int tab;
    private final LocationHelper loc = new LocationHelper(this);   // registers the permission launcher: must be created with the Fragment
    private static final long RADIUS_SETTLE_MS = 350;      // the list reloads this long after the radius pill stops moving
    private final Handler ui = new Handler(Looper.getMainLooper());
    private float pendingKm;
    private double loadedRadius = -1;
    private String lastRef;                                 // the map zooms to fit only when the centre changes
    private final Runnable settleRadius = () -> {
        if (b == null || pendingKm == loadedRadius) return;
        BrowseState.radiusKm = pendingKm;
        BrowseState.bump();
    };
    private List<BoardingHouse> current = new ArrayList<>();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentHomeBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        adapter = new BhAdapter(new BhAdapter.Listener() {
            @Override public void open(BoardingHouse x) { BhDetailFragment.open(HomeFragment.this, x.id()); }
            @Override public void heart(BoardingHouse x) { Favorites.toggle(x.id(), msg -> ListArea.toast(HomeFragment.this, msg)); }
        });
        area = new ListArea(this, b.area, adapter, this::reload);

        b.tabs.addTab(b.tabs.newTab().setText(R.string.home_tab_new));
        b.tabs.addTab(b.tabs.newTab().setText(R.string.home_tab_hot));
        b.tabs.addTab(b.tabs.newTab().setText(R.string.home_tab_best));
        ListArea.onTab(b.tabs, pos -> { tab = pos; reload(); });

        // Greeting row + the ONE search bar (Step 3, v2.0.21)
        search = new HomeSearchBar(this, b, new HomeSearchBar.Host() {
            @Override public void nearMe() { askNearMe(); }
            @Override public void searchPlace(String text) { openExplore(text); }
        });
        b.bell.setOnClickListener(v -> say(R.string.home_bell_toast));
        paintGreeting();
        // Inline map card (Step 4): always on the page, sized 42% of the screen (200-440 dp), drag on it never scrolls the page
        float dn = getResources().getDisplayMetrics().density;
        ViewGroup.LayoutParams flp = b.mapFrame.getLayoutParams();
        flp.height = Math.round(Math.max(200 * dn, Math.min(440 * dn, getResources().getDisplayMetrics().heightPixels * 0.42f)));
        b.mapFrame.setLayoutParams(flp);
        if (BrowseState.radiusKm > 5) BrowseState.radiusKm = 5;          // the pill goes 0.5 to 5 km
        b.mapCard.setParentScrollBlocked(true);
        b.mapCard.setListener(new MapCardView.Listener() {
            @Override public void openPlace(String id) { BhDetailFragment.open(HomeFragment.this, id); }
            @Override public void locate() { locateMe(); }
            @Override public void radiusChanged(float km, boolean settled) { onRadiusPill(km, settled); }
        });
        // MapLibre start-up (native libs, style parse) is heavy: do it after the first frame, not inside the same frame as the list
        final View root = b.getRoot();
        root.postDelayed(() -> { if (b != null && isAdded()) b.mapCard.activate(getViewLifecycleOwner()); }, 300);

        // The header scrolls away with the list: pull-to-refresh only at the very top, an open dropdown closes
        b.appBar.addOnOffsetChangedListener((bar, offset) -> {
            if (b == null) return;
            b.area.swipe.setEnabled(offset == 0);
            if (search != null) search.closeOnScroll();
        });
        b.area.emptyBox.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);   // the list area is taller than the screen under the header
        b.area.emptyBox.setPadding(b.area.emptyBox.getPaddingLeft(), Math.round(40 * dn), b.area.emptyBox.getPaddingRight(), b.area.emptyBox.getPaddingBottom());

        Favorites.load(null);
        Favorites.IDS.observe(getViewLifecycleOwner(), ids -> adapter.setFavorites(ids));
        Connectivity.OFFLINE.observe(getViewLifecycleOwner(), off -> b.offlineBanner.setVisibility(Boolean.TRUE.equals(off) ? View.VISIBLE : View.GONE));
        // The first emission is the initial load; later ones come from the campus picker / radius slider.
        BrowseState.CHANGED.observe(getViewLifecycleOwner(), n -> { if (n == null || n == lastChange) return; lastChange = n; reload(); });
    }

    private void reload() {
        if (b == null) return;
        final int my = ++token;
        area.showLoading();
        BrowseState.ensureLoaded(() -> {
            if (my != token || b == null) return;
            paintCampus();
            final Double lat = BrowseState.refLat(), lng = BrowseState.refLng();
            final double radius = BrowseState.radiusKm;
            loadedRadius = radius;
            final BhService.Tab t = TABS[tab];
            Async.run(() -> load(t, lat, lng, radius), (Result r) -> {
                if (my != token || b == null) return;
                adapter.setShowDistance(lat != null);
                paintRange(r.range());
                current = r.list();
                pushMap();
                if (r.list().isEmpty()) area.showMessage(getString(t == BhService.Tab.BEST ? R.string.home_empty_best : R.string.home_empty), false);
                else area.showList(r.list());
            }, e -> fail(my, e));
        }, e -> fail(my, e));
    }

    private void fail(int my, Throwable e) {
        if (my != token || b == null) return;
        paintRange(null);
        area.showMessage(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.list_error), e), true);
    }

    /** Background thread. No campus: the whole list. Campus: nearest first within the radius. Best rated reads the rent range from the unfiltered list. */
    private static Result load(BhService.Tab t, Double lat, Double lng, double radius) throws Exception {
        BhService s = new BhService();
        Double rad = lat == null ? null : radius;
        List<BoardingHouse> list = s.list(t, lat, lng, rad, null);
        List<BoardingHouse> basis = t == BhService.Tab.BEST ? s.list(BhService.Tab.NEW, lat, lng, rad, null) : list;
        return new Result(list, PriceRange.of(ListRules.prices(basis)));
    }

    private void paintCampus() {
        boolean ref = BrowseState.hasRef();
        b.sectionTitle.setText(ref ? R.string.home_nearby : R.string.home_all);
        b.mapCard.setRadiusPill(ref, BrowseState.radiusKm);
        if (search != null) search.syncFromState();       // the search bar shows the chosen campus / Near me
        paintGreeting();                                  // the profile may have arrived with this load
    }

    /** "Good morning" / "Good afternoon" / "Good evening", first name (or "there"), avatar photo or initials. */
    private void paintGreeting() {
        if (b == null || !isAdded()) return;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        b.greetingHello.setText(h < 12 ? R.string.home_good_morning : h < 18 ? R.string.home_good_afternoon : R.string.home_good_evening);
        Profile me = BrowseState.me;
        String full = me == null ? "" : me.fullName();
        String first = CampusMatch.firstName(full);
        b.greetingName.setText(first.isEmpty() ? getString(R.string.home_name_there) : first);
        b.avatarInitials.setText(CampusMatch.initialsOf(full));
        String url = me == null || me.photoUrl() == null ? "" : me.photoUrl().trim();
        if (!url.equals(paintedPhoto)) {
            paintedPhoto = url;
            if (url.isEmpty()) { Glide.with(b.avatarImg).clear(b.avatarImg); b.avatarImg.setImageDrawable(null); }
            else Glide.with(b.avatarImg).load(url).circleCrop().into(b.avatarImg);
        }
    }

    /** Enter on text that is not a campus: Explore opens with it already typed in. */
    private void openExplore(String text) {
        ExploreHandoff.set(text);
        Fragment p = getParentFragment();
        if (p instanceof ShellFragment) ((ShellFragment) p).openExplore();
    }

    // ---- location + map (Stage 5) ----------------------------------------------------------------------------

    /** "Near me" chip: one position, then the list is measured from the phone. */
    private void askNearMe() {
        loc.requestOnce(new LocationHelper.Callback() {
            @Override public void got(double lat, double lng) { if (b != null) BrowseState.setNearMe(lat, lng); }
            @Override public void off() { say(R.string.loc_off); paintCampus(); }
            @Override public void denied() { say(R.string.loc_denied); paintCampus(); }
            @Override public void failed() { say(R.string.loc_failed); paintCampus(); }
        });
    }

    /** Locate button on the map: show the dot, move there, keep the dot live. */
    private void locateMe() {
        loc.requestOnce(new LocationHelper.Callback() {
            @Override public void got(double lat, double lng) {
                if (b == null) return;
                b.mapCard.setUser(lat, lng);
                b.mapCard.moveTo(lat, lng, 15);
                startLive();
            }
            @Override public void off() { say(R.string.loc_off); }
            @Override public void denied() { say(R.string.loc_denied); }
            @Override public void failed() { say(R.string.loc_failed); }
        });
    }

    /** Live dot, only when the permission is already granted and Settings > Use my location is on (never asks by itself). */
    private void startLive() {
        if (getContext() == null || !LocationHelper.granted(requireContext()) || !AppPrefs.useLocation(requireContext())) return;
        loc.startWatching(new LocationHelper.Callback() {
            @Override public void got(double lat, double lng) { if (b != null) b.mapCard.setUser(lat, lng); }
            @Override public void off() { }
            @Override public void denied() { }
            @Override public void failed() { }
        });
    }

    /** Radius pill moved: the circle and the zoom follow live, the list reloads once it has settled. */
    private void onRadiusPill(float km, boolean settled) {
        if (b == null) return;
        pendingKm = km;
        BrowseState.radiusKm = km;
        b.mapCard.setRing(BrowseState.refLat(), BrowseState.refLng(), km);
        b.mapCard.glideToRing(settled);
        ui.removeCallbacks(settleRadius);
        ui.postDelayed(settleRadius, RADIUS_SETTLE_MS);
    }

    /** Hands the current list, ring and campus dot to the map. Zooms to fit only when the centre changed (first load, campus picked, Near me). */
    private void pushMap() {
        if (b == null) return;
        College campus = BrowseState.campus();
        b.mapCard.setPlaces(current);
        b.mapCard.setRing(BrowseState.refLat(), BrowseState.refLng(), BrowseState.radiusKm);
        b.mapCard.setCampusDot(campus == null ? null : campus.lat(), campus == null ? null : campus.lng());
        String key = BrowseState.refLat() + "," + BrowseState.refLng();
        if (!key.equals(lastRef)) { lastRef = key; b.mapCard.fit(); }
    }

    private void say(int res) { ListArea.toast(this, getString(res)); }

    @Override
    public void onStart() { super.onStart(); startLive(); }

    @Override
    public void onStop() { loc.stop(); super.onStop(); }

    private void paintRange(PriceRange r) {
        if (r == null) { b.priceRange.setVisibility(View.GONE); return; }
        StringBuilder sb = new StringBuilder();
        for (String[] cell : r.cells()) { if (sb.length() > 0) sb.append("    "); sb.append(cell[0]).append(' ').append(cell[1]); }
        b.priceRange.setText(sb.toString());
        b.priceRange.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        token++;                       // late results are ignored
        ui.removeCallbacks(settleRadius);
        loc.stop();
        if (area != null) area.destroy();
        search = null;
        b = null;
        super.onDestroyView();
    }
}
