package com.bofo.finder.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.Locale;

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
import com.bofo.finder.util.DirectionsRules;
import com.bofo.finder.util.Geo;
import com.bofo.finder.util.ListRules;
import com.bofo.finder.util.Router;
import com.bofo.finder.util.LocationHelper;
import com.bofo.finder.util.PriceRange;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Home (one scrolling page): greeting row, one search bar, the inline map card with its radius pill, New / Hot / Best rated tabs, rent range and the card grid. */
public class HomeFragment extends Fragment {

    private static final BhService.Tab[] TABS = {BhService.Tab.NEW, BhService.Tab.HOT, BhService.Tab.BEST};

    private record Result(List<BoardingHouse> list, PriceRange range, List<BoardingHouse> basis) { }

    private FragmentHomeBinding b;
    private BhAdapter adapter;
    private ListArea area;
    private HomeSearchBar search;
    private String paintedPhoto = "";
    private int token;
    private int lastChange = -1;
    private int tab;
    private static final int BATCH = 15;
    private int shown = BATCH;
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
    // Step 5: overview mode (no campus, search empty) + location states
    private int routeToken;
    private double[] me;                                    // last known position of the phone {lat, lng}
    private boolean ovOn = true, ovFit = true, hadRef, finding, haveFix, toTop;

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

        // Step 7: chips New / Hot / Best rated (selected = ink fill) and the rent card
        TextView[] chips = {b.chipNew, b.chipHot, b.chipBest};
        for (int i = 0; i < chips.length; i++) {
            final int pos = i;
            chips[i].setOnClickListener(v -> { if (tab == pos) return; tab = pos; paintChips(); toTop = true; reload(); });
        }
        paintChips();
        b.rentCard.setListener(new RentCardView.Listener() {
            @Override public void glow(String houseId) { if (b != null) b.mapCard.setGlow(houseId); }
            @Override public void choose() {
                if (b == null || search == null) return;
                if (b.appBar.getTop() < 0) { expanding = true; b.appBar.setExpanded(true, true); b.appBar.postDelayed(openAfterExpand, 450); return; }
                search.openDropdown();
            }
        });

        // Greeting row + the ONE search bar (Step 3, v2.0.21)
        search = new HomeSearchBar(this, b, new HomeSearchBar.Host() {
            @Override public void nearMe() { askNearMe(); }
            @Override public void searchPlace(String text) { openExplore(text); }
            @Override public void searchChanged() { onSearchChanged(); }
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
            @Override public void locate() { onLocateTap(); }
            @Override public void radiusChanged(float km, boolean settled) { onRadiusPill(km, settled); }
            @Override public void placeSelected(String id) { showRoute(id); }
            @Override public void campusTapped(String id) {
                for (College c : BrowseState.colleges) if (c.id().equals(id)) { BrowseState.setCampus(c); return; }
            }
        });
        b.mapCard.setLocState(!AppPrefs.useLocation(requireContext()) ? MapCardView.Loc.OFF
                : LocationHelper.granted(requireContext()) ? MapCardView.Loc.FINDING : MapCardView.Loc.NONE);   // the live dot resolves it
        paintLocate();
        // MapLibre start-up (native libs, style parse) is heavy: do it after the first frame, not inside the same frame as the list
        final View root = b.getRoot();
        root.postDelayed(() -> { if (b != null && isAdded()) b.mapCard.activate(getViewLifecycleOwner()); }, 300);

        // The header scrolls away with the list: pull-to-refresh only at the very top, an open dropdown closes
        b.appBar.addOnOffsetChangedListener((bar, offset) -> {
            if (b == null) return;
            b.area.swipe.setEnabled(offset == 0);
            if (expanding && offset == 0) { bar.removeCallbacks(openAfterExpand); bar.post(openAfterExpand); } else if (search != null && !expanding) search.closeOnScroll();
        });
        b.area.emptyBox.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);   // the list area is taller than the screen under the header
        b.area.emptyBox.setPadding(b.area.emptyBox.getPaddingLeft(), Math.round(40 * dn), b.area.emptyBox.getPaddingRight(), b.area.emptyBox.getPaddingBottom());

        Favorites.load(null);
        Favorites.IDS.observe(getViewLifecycleOwner(), ids -> adapter.setFavorites(ids));
        Connectivity.OFFLINE.observe(getViewLifecycleOwner(), off -> {
            b.offlineBanner.setVisibility(Boolean.TRUE.equals(off) ? View.VISIBLE : View.GONE);
            paintMapNote();
        });
        area.setSlowListener(slow -> { mapSlow = Boolean.TRUE.equals(slow); paintMapNote(); });
        // The first emission is the initial load; later ones come from the campus picker / radius slider.
        BrowseState.CHANGED.observe(getViewLifecycleOwner(), n -> { if (n == null || n == lastChange) return; lastChange = n; reload(); });
    }

    private boolean mapSlow;

    /** v2.0.48: the map is blank when there is no internet or the network is slow: say so instead of showing nothing. */
    private void paintMapNote() {
        if (b == null) return;
        boolean off = Boolean.TRUE.equals(Connectivity.OFFLINE.getValue());
        b.mapNote.setText(off ? R.string.net_map_offline : R.string.net_map_slow);
        b.mapNote.setVisibility(off || mapSlow ? View.VISIBLE : View.GONE);
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
                paintRent(r.basis(), t == BhService.Tab.BEST);
                current = r.list();
                shown = BATCH;
                pushMap();
                paintSub();
                renderList(t);
            }, e -> fail(my, e));
        }, e -> fail(my, e));
    }

    /** Batches of 15 + "Show more (n)". */
    private void renderList(BhService.Tab t) {
        if (current.isEmpty()) { toTop = false; area.showMessage(getString(t == BhService.Tab.BEST ? R.string.home_empty_best : R.string.home_empty), false); return; }
        int n = Math.min(shown, current.size());
        area.showList(new ArrayList<>(current.subList(0, n)), toTop); toTop = false;
        int left = current.size() - n;
        area.setMore(left > 0 ? getString(R.string.home_show_more, left) : null, () -> { shown += BATCH; renderList(t); });
    }

    /** Tab chips pressed: put the listing back on its first card, so nobody has to scroll up by hand. */
    private void listToTop() {
        if (b == null) return;
        b.area.list.scrollToPosition(0);
    }

    private void paintChips() {
        if (b == null) return;
        TextView[] chips = {b.chipNew, b.chipHot, b.chipBest};
        int ink = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bofo_ink);
        for (int i = 0; i < chips.length; i++) {
            boolean on = i == tab;
            chips[i].setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip_off);
            chips[i].setTextColor(on ? 0xFFFFFFFF : ink);
        }
    }

    /** "Closest to you first · 7 places" / "Within 3.5 km of <campus> · 4 places" / "Choose a campus to see what is close". */
    private void paintSub() {
        if (b == null || !isAdded()) return;
        College c = BrowseState.campus();
        int n = current.size();
        String places = getResources().getQuantityString(R.plurals.home_places, n, n);
        String sub = c != null ? getString(R.string.home_sub_campus, String.format(Locale.US, "%.1f km", BrowseState.radiusKm), c.name(), places)
                : BrowseState.hasRef() ? getString(R.string.home_sub_near, places) : getString(R.string.home_sub_none);
        b.sectionSub.setText(sub);
    }

    /** Rent card: no campus = "Choose a campus to see rent near it"; otherwise Lowest / Middle / Highest over the line. */
    private void paintRent(List<BoardingHouse> basis, boolean best) {
        if (b == null) return;
        b.mapCard.setGlow(null);
        if (!BrowseState.hasRef()) b.rentCard.setNoCampus();
        else b.rentCard.setData(basis, best);
    }

    private void fail(int my, Throwable e) {
        if (my != token || b == null) return;
        paintRent(null, false);
        if (Connectivity.isNetworkError(e)) area.showOffline();
        else area.showMessage(ErrorText.full(getString(R.string.list_error), e), true);
    }

    /** Background thread. No campus: the whole list. Campus: nearest first within the radius. Best rated reads the rent range from the unfiltered list. */
    private static Result load(BhService.Tab t, Double lat, Double lng, double radius) throws Exception {
        BhService s = new BhService();
        Double rad = lat == null ? null : radius;
        List<BoardingHouse> list = s.list(t, lat, lng, rad, null);
        List<BoardingHouse> basis = t == BhService.Tab.BEST ? s.list(BhService.Tab.NEW, lat, lng, rad, null) : list;
        return new Result(list, PriceRange.of(ListRules.prices(basis)), basis);
    }

    private void paintCampus() {
        boolean ref = BrowseState.hasRef();
        b.sectionTitle.setText(ref ? R.string.home_nearby : R.string.home_all);
        b.mapCard.setRadiusPill(ref, BrowseState.radiusKm);
        if (!ref && hadRef) { ovOn = true; ovFit = true; }      // the campus / Near me was cleared: the overview comes back
        hadRef = ref;
        paintLocate();
        paintSub();
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
            @Override public void servicesOff() { say(R.string.loc_services_off); paintCampus(); }
        });
    }

    // ---- Step 5: overview + locate ------------------------------------------------------------------------------

    /** Overview = no campus / Near me and nothing typed: the map shows every pin (and the phone) in ~90 % of the card. */
    private boolean overviewActive() { return ovOn && !BrowseState.hasRef() && (search == null || search.isEmpty()); }

    private void paintLocate() { if (b != null) b.mapCard.setLocateActive(overviewActive()); }

    /** The search text changed or was cleared: clearing it (with no campus) brings the overview back. */
    private void onSearchChanged() {
        atMe = false;
        if (b == null) return;
        if (!BrowseState.hasRef() && search != null && search.isEmpty()) { ovOn = true; b.mapCard.overview(); }
        paintLocate();
    }

    /** Locate button: overview on = fly to me (overview off); overview off with nothing chosen = back to the overview; otherwise fly to me. */
    private void onLocateTap() {
        if (b == null || (finding && android.os.SystemClock.uptimeMillis() - findStart < 8000)) return;                       // extra taps while finding are ignored
        if (atMe) { atMe = false; if (!BrowseState.hasRef() && (search == null || search.isEmpty())) ovOn = true; b.mapCard.overview(); paintLocate(); return; }
        if (overviewActive()) { ovOn = false; paintLocate(); locateMe(); return; }
        if (!BrowseState.hasRef() && search != null && search.isEmpty()) { ovOn = true; b.mapCard.overview(); paintLocate(); return; }
        locateMe();
    }

    private MapCardView.Loc fixState() {
        return LocationHelper.precise(requireContext()) ? MapCardView.Loc.EXACT : MapCardView.Loc.APPROX;
    }

    /** Locate button on the map: show the dot, move there, "found you" ring, keep the dot live. */
    private long findStart; private boolean expanding; private final Runnable openAfterExpand = () -> { expanding = false; if (b != null && search != null) search.openDropdown(); }; private boolean atMe;

    private void locateMe() {
        findStart = android.os.SystemClock.uptimeMillis(); atMe = true;
        if (me != null) b.mapCard.moveTo(me[0], me[1], 15);
        finding = true;
        b.mapCard.setLocState(MapCardView.Loc.FINDING);
        loc.requestOnce(new LocationHelper.Callback() {
            @Override public void got(double lat, double lng) {
                if (b == null) return;
                finding = false; haveFix = true; me = new double[]{lat, lng};
                b.mapCard.setUser(lat, lng);
                b.mapCard.setLocState(fixState());
                b.mapCard.moveTo(lat, lng, 15);
                ui.postDelayed(() -> { if (b != null) b.mapCard.foundYou(); }, 450);
                startLive();
            }
            @Override public void off() { locFailed(R.string.loc_off); }
            @Override public void denied() { locFailed(R.string.loc_denied); }
            @Override public void failed() { locFailed(R.string.loc_failed); }
            @Override public void servicesOff() { locFailed(R.string.loc_services_off); }
        });
    }

    private void locFailed(int msg) {
        atMe = false;
        finding = false;
        if (b != null) b.mapCard.setLocState(MapCardView.Loc.OFF);
        say(msg);
    }

    /** Live dot, only when the permission is already granted and Settings > Use my location is on (never asks by itself). */
    private void startLive() {
        if (getContext() == null || !LocationHelper.granted(requireContext()) || !AppPrefs.useLocation(requireContext())) return;
        loc.startWatching(new LocationHelper.Callback() {
            @Override public void got(double lat, double lng) {
                if (b == null) return;
                boolean first = !haveFix;
                haveFix = true; finding = false; me = new double[]{lat, lng};
                b.mapCard.setUser(lat, lng);
                b.mapCard.setLocState(fixState());
                if (first && overviewActive()) b.mapCard.overview();      // the first fix joins the overview once
            }
            @Override public void off() { if (b != null) b.mapCard.setLocState(MapCardView.Loc.OFF); }
            @Override public void denied() { }
            @Override public void failed() { if (b != null && !haveFix) b.mapCard.setLocState(MapCardView.Loc.OFF); }
        });
    }

    /** Radius pill moved: the circle and the zoom follow live, the list reloads once it has settled. */
    private void onRadiusPill(float km, boolean settled) {
        if (b == null) return;
        pendingKm = km;
        BrowseState.radiusKm = km;
        b.mapCard.setRing(BrowseState.refLat(), BrowseState.refLng(), km);
        b.mapCard.glideToRing(settled);
        paintSub();
        ui.removeCallbacks(settleRadius);
        ui.postDelayed(settleRadius, RADIUS_SETTLE_MS);
    }

    // ---- Step 8: route to the selected pin ---------------------------------------------------------------------

    /** Start = the chosen campus, else the campus nearest to the house, else the phone. */
    private double[] routeStart(BoardingHouse p) {
        College c = BrowseState.campus();
        if (c == null && me != null) return me; if (c == null && BrowseState.colleges != null && !BrowseState.colleges.isEmpty()) c = DirectionsRules.nearestCampus(BrowseState.colleges, p.lat(), p.lng());
        if (c != null) return new double[]{c.lat(), c.lng()};
        return me;
    }

    /** A pin was tapped (id) or the preview closed (null): purple line + blue start + red end + "1.7 km - 21 min", camera fits it above the card. */
    private void showRoute(String id) {
        if (b == null) return;
        final int my = ++routeToken;
        BoardingHouse p = null;
        if (id != null) for (BoardingHouse x : current) if (id.equals(x.id())) { p = x; break; }
        double[] from = p == null ? null : routeStart(p);
        if (from == null) { b.mapCard.clearRoute(); return; }
        final BoardingHouse house = p;
        Router.Route known = Router.peek(from[0], from[1], house.lat(), house.lng());
        if (known == null) known = Router.estimate(from[0], from[1], house.lat(), house.lng());       // instant straight-line guess first
        drawRoute(known, from, house);
        b.mapCard.fitRoute();
        fetchRoute(my, from, house, 0, known.approximate());
    }

    private static boolean walkable(double[] from, BoardingHouse h) { return Geo.km(from[0], from[1], h.lat(), h.lng()) <= 3; }

    /** The real road route; if only a straight line came back it quietly tries again (up to 4 more times). */
    private void fetchRoute(int my, double[] from, BoardingHouse h, int tries, boolean needReal) {
        if (!needReal) return;
        Async.run(() -> walkable(from, h) ? Router.walk(from[0], from[1], h.lat(), h.lng()) : Router.drive(from[0], from[1], h.lat(), h.lng()), r -> {
            if (my != routeToken || b == null) return;
            drawRoute(r, from, h);
            if (my == routeToken && !r.approximate()) b.mapCard.fitRoute();
            else if (tries < 4) ui.postDelayed(() -> { if (my == routeToken && b != null) fetchRoute(my, from, h, tries + 1, true); }, 1500L * (tries + 1));
        }, e -> {
            if (my == routeToken && b != null && tries < 4) ui.postDelayed(() -> { if (my == routeToken && b != null) fetchRoute(my, from, h, tries + 1, true); }, 1500L * (tries + 1));
        });
    }

    private void drawRoute(Router.Route r, double[] from, BoardingHouse h) {
        b.mapCard.setRoute(r.pts(), r.approximate());
        double km = r.meters() / 1000.0;
        int min = (int) Math.max(1, Math.round(r.meters() / (walkable(from, h) ? 80.0 : 500.0)));     // 80 m/min walking, 30 km/h by road
        b.mapCard.setRouteLabel(Geo.fmt(km) + " \u00B7 " + Geo.fmtTime(min));
    }

    /** Hands the current list, ring and campus dot to the map. Zooms to fit only when the centre changed (first load, campus picked, Near me). */
    private void pushMap() {
        if (b == null) return;
        College campus = BrowseState.campus();
        b.mapCard.setPlaces(current);
        if (b.mapCard.selectedId() == null) { routeToken++; b.mapCard.clearRoute(); }          // the selected house is gone: so is its route
        b.mapCard.setColleges(BrowseState.colleges);
        b.mapCard.setRing(BrowseState.refLat(), BrowseState.refLng(), BrowseState.radiusKm);
        b.mapCard.setCampusDot(campus == null ? null : campus.lat(), campus == null ? null : campus.lng());
        String key = BrowseState.refLat() + "," + BrowseState.refLng();
        boolean changed = !key.equals(lastRef);
        lastRef = key;
        if (overviewActive()) {
            if (changed || ovFit) { if (!current.isEmpty()) ovFit = false; b.mapCard.overview(); }
        } else if (changed) b.mapCard.fit();
    }

    private void say(int res) { ListArea.toast(this, getString(res)); }

    @Override
    public void onStart() { super.onStart(); startLive(); }

    @Override
    public void onStop() { loc.stop(); super.onStop(); }

    @Override
    public void onDestroyView() {
        token++;                       // late results are ignored
        ui.removeCallbacks(settleRadius);
        loc.stop();
        if (area != null) area.destroy();
        routeToken++;
        search = null;
        finding = false; haveFix = false;
        b = null;
        super.onDestroyView();
    }
}
