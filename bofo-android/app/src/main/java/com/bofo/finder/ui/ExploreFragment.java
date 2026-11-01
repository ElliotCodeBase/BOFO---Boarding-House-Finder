package com.bofo.finder.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bofo.finder.R;
import com.bumptech.glide.Glide;
import com.bofo.finder.databinding.FragmentExploreBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.ExploreRules;
import com.bofo.finder.util.Favorites;
import com.bofo.finder.util.LocationHelper;
import com.bofo.finder.util.ListRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Step 9: Explore. Title + subtitle, one search pill with a square filter button, List | Map control, results.
 * Sort (Nearest / Lowest price / Most beds), max price and amenities live in the filter sheet. Distance is measured from the chosen campus when there is one.
 */
public class ExploreFragment extends Fragment {

    private static final long DEBOUNCE_MS = 400;

    private FragmentExploreBinding b;
    private BhAdapter adapter;
    private ListArea area;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private int token;
    private int lastChange = -1;
    private String query = "";
    private boolean mapMode, mapReady, finding;
    private final LocationHelper loc = new LocationHelper(this);       // v2.0.80: registers the permission launcher, so it is created with the Fragment
    /** v2.0.80: {south, west, north, east} after "Search this area"; the list and the count only keep houses inside it. Cleared by any new search, filter or campus. */
    private double[] areaBox;
    private List<BoardingHouse> lastItems = new ArrayList<>();
    /** v2.0.82: quick chips. keepArea / soft are set by a chip tap: the "Search this area" box and the camera stay, the strip fades. */
    private boolean keepArea, soft;
    private TextView[] quickChips;
    private TextView chipReset;
    /** v2.0.82: compare tray (max 3, in the order they were added). Lives as long as this Explore tab: a new tab starts empty. */
    private static final int COMPARE_MAX = 3;
    private final Map<String, BoardingHouse> compare = new LinkedHashMap<>();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentExploreBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        adapter = new BhAdapter(new BhAdapter.Listener() {
            @Override public void open(BoardingHouse x) { BhDetailFragment.open(ExploreFragment.this, x.id()); }
            @Override public void heart(BoardingHouse x) { Favorites.toggle(x.id(), msg -> ListArea.toast(ExploreFragment.this, msg)); }
        });
        area = new ListArea(this, b.area, adapter, this::reload);

        b.search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) {
                if (b == null) return;
                b.searchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                final String q = s.toString().trim();
                pendingSearch = () -> { if (!q.equals(query)) { query = q; areaBox = null; reload(); } };
                handler.postDelayed(pendingSearch, DEBOUNCE_MS);
            }
        });
        b.search.setOnFocusChangeListener((v, focus) -> { if (b != null) b.searchPill.setActivated(focus); });
        b.search.setOnEditorActionListener((v, action, ev) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        });
        b.searchClear.setOnClickListener(v -> b.search.setText(""));
        b.filterBtn.setOnClickListener(v -> new FilterSheet().show(getChildFragmentManager(), "filter"));
        b.segList.setOnClickListener(v -> setMapMode(false));
        b.segMap.setOnClickListener(v -> setMapMode(true));
        paintFilterButton();
        paintSeg();
        setupChips();
        b.compareClear.setOnClickListener(v -> { compare.clear(); syncCompare(); paintTray(false); });
        b.compareTray.setOnClickListener(v -> openCompare());
        paintTray(false);

        String handoff = com.bofo.finder.util.ExploreHandoff.take();          // typed in the Home search bar (not a campus)
        if (handoff != null) { query = handoff; b.search.setText(handoff); b.search.setSelection(handoff.length()); }

        Favorites.load(null);
        Favorites.IDS.observe(getViewLifecycleOwner(), ids -> { adapter.setFavorites(ids); if (mapReady && b != null) b.mapCard.setFavorites(ids); });
        if (mapMode) { mapMode = false; setMapMode(true); }       // v2.0.82: back from a place page with a new view: the map starts again (mapReady is reset in onDestroyView)
        BrowseState.CHANGED.observe(getViewLifecycleOwner(), n -> { if (n == null || n == lastChange) return; lastChange = n; if (keepArea) keepArea = false; else areaBox = null; paintFilterButton(); paintChips(); reload(); });
    }

    private void paintFilterButton() {
        if (b == null) return;
        int n = BrowseState.activeFilters();
        b.filterBadge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        b.filterBadge.setText(String.valueOf(n));
        b.filterBtn.setBackgroundResource(n > 0 ? R.drawable.bg_ex_filter_btn_on : R.drawable.bg_ex_filter_btn);
        b.filterBtn.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), n > 0 ? R.color.bofo_white : R.color.bofo_ink)));
    }

    private void paintSeg() {
        if (b == null) return;
        paintOne(b.segList, !mapMode);
        paintOne(b.segMap, mapMode);
    }

    private void paintOne(android.widget.TextView t, boolean on) {
        t.setBackgroundResource(on ? R.drawable.bg_ex_seg_on : 0);
        t.setTextColor(ContextCompat.getColor(requireContext(), on ? R.color.bofo_text : R.color.bofo_text_2));
    }

    // ---- v2.0.82: quick chips on the map --------------------------------------------------------------------------

    private float dp() { return getResources().getDisplayMetrics().density; }

    /** Five chips + Reset. They read and write BrowseState (the same values as the filter sheet), so the badge and the sheet stay in sync. */
    private void setupChips() {
        int[] labels = {R.string.map_chip_under2k, R.string.map_chip_wifi, R.string.map_chip_verified, R.string.map_chip_female, R.string.map_chip_beds};
        quickChips = new TextView[labels.length];
        b.mapChipsRow.removeAllViews();
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            quickChips[i] = chip(getString(labels[i]));
            quickChips[i].setOnClickListener(v -> toggleChip(idx));
            b.mapChipsRow.addView(quickChips[i]);
        }
        chipReset = chip(getString(R.string.map_chip_reset));
        chipReset.setOnClickListener(v -> { keepArea = true; soft = true; BrowseState.clearFilters(); BrowseState.bump(); });
        b.mapChipsRow.addView(chipReset);
        paintChips();
    }

    private TextView chip(String text) {
        TextView t = new TextView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(34 * dp()));
        lp.setMarginEnd(Math.round(8 * dp()));
        t.setLayoutParams(lp);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(Math.round(14 * dp()), 0, Math.round(14 * dp()), 0);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(null, Typeface.BOLD);
        t.setMaxLines(1);
        t.setElevation(3 * dp());
        return t;
    }

    private boolean chipOn(int i) {
        switch (i) {
            case 0: return BrowseState.maxPrice != null && BrowseState.maxPrice == BrowseState.QUICK_PRICE;
            case 1: return BrowseState.hasAmenityNamed(BrowseState.AMEN_WIFI);
            case 2: return BrowseState.verifiedOnly;
            case 3: return BrowseState.hasAmenityNamed(BrowseState.AMEN_FEMALE);
            default: return BrowseState.bedsOnly;
        }
    }

    private void toggleChip(int i) {
        boolean on = !chipOn(i);
        switch (i) {
            case 0: BrowseState.minPrice = null; BrowseState.maxPrice = on ? Double.valueOf(BrowseState.QUICK_PRICE) : null; break;
            case 1: BrowseState.setAmenityNamed(BrowseState.AMEN_WIFI, on); break;
            case 2: BrowseState.verifiedOnly = on; break;
            case 3: BrowseState.setAmenityNamed(BrowseState.AMEN_FEMALE, on); break;
            default: BrowseState.bedsOnly = on; break;
        }
        keepArea = true; soft = true;               // the box from "Search this area" and the camera stay; the strip fades
        BrowseState.bump();                         // the observer repaints the badge + chips and reloads
    }

    /** Selected chip = ink fill + white text (same as the Home chips); Reset shows while anything is on. */
    private void paintChips() {
        if (b == null || quickChips == null) return;
        int ink = ContextCompat.getColor(requireContext(), R.color.bofo_ink);
        for (int i = 0; i < quickChips.length; i++) {
            boolean on = chipOn(i);
            quickChips[i].setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip_off);
            quickChips[i].setTextColor(on ? 0xFFFFFFFF : ink);
            quickChips[i].setSelected(on);
        }
        chipReset.setBackgroundResource(R.drawable.bg_chip_off);
        chipReset.setTextColor(ContextCompat.getColor(requireContext(), R.color.bofo_primary));
        chipReset.setVisibility(BrowseState.activeFilters() > 0 ? View.VISIBLE : View.GONE);
    }

    // ---- v2.0.82: compare tray -------------------------------------------------------------------------------------

    /** Long-press on a pin or a strip card: add it (max 3) or take it out again. */
    private void toggleCompare(String id) {
        if (b == null || id == null) return;
        if (compare.containsKey(id)) {
            compare.remove(id);
        } else {
            if (compare.size() >= COMPARE_MAX) { b.mapCard.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS); ListArea.toast(this, getString(R.string.map_compare_max)); return; }
            BoardingHouse h = null;
            for (BoardingHouse x : lastItems) if (x.id().equals(id)) { h = x; break; }
            if (h == null) return;
            compare.put(id, h);
        }
        b.mapCard.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        syncCompare();
        paintTray(true);
    }

    private void syncCompare() { if (b != null && mapReady) b.mapCard.setCompareIds(compare.keySet()); }

    private void paintTray(boolean pop) {
        if (b == null) return;
        final View tray = b.compareTray;
        boolean anim = MapCardView.animationsEnabled(requireContext());
        if (compare.isEmpty()) {
            if (tray.getVisibility() != View.VISIBLE) return;
            if (!anim) { tray.setVisibility(View.GONE); return; }
            tray.animate().alpha(0f).translationY(10 * dp()).setDuration(150).withEndAction(() -> { if (b != null) { b.compareTray.setVisibility(View.GONE); b.compareTray.setAlpha(1f); b.compareTray.setTranslationY(0f); } }).start();
            return;
        }
        b.compareThumbs.removeAllViews();
        int size = Math.round(28 * dp());
        int i = 0;
        for (BoardingHouse h : compare.values()) {
            ImageView iv = new ImageView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i++ > 0) lp.setMarginStart(-Math.round(8 * dp()));
            iv.setLayoutParams(lp);
            iv.setBackgroundResource(R.drawable.bg_compare_thumb);
            iv.setPadding(Math.round(2 * dp()), Math.round(2 * dp()), Math.round(2 * dp()), Math.round(2 * dp()));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setClipToOutline(true);
            iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (h.photoUrl() != null && !h.photoUrl().isEmpty()) Glide.with(this).load(h.photoUrl()).centerCrop().into(iv);
            b.compareThumbs.addView(iv);
        }
        b.compareLabel.setText(getString(R.string.map_compare_n, compare.size()));
        boolean wasShown = tray.getVisibility() == View.VISIBLE;
        tray.animate().cancel();
        tray.setVisibility(View.VISIBLE);
        if (!anim) { tray.setAlpha(1f); tray.setScaleX(1f); tray.setScaleY(1f); tray.setTranslationY(0f); return; }
        if (!wasShown) {                                          // first one: slides up and fades in
            tray.setAlpha(0f); tray.setTranslationY(14 * dp()); tray.setScaleX(1f); tray.setScaleY(1f);
            tray.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        } else if (pop) {                                         // another one added / removed: a small pop
            tray.setAlpha(1f); tray.setTranslationY(0f); tray.setScaleX(.9f); tray.setScaleY(.9f);
            tray.animate().scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
        }
    }

    /** "Compare (n)": the chat card opens with a table of the houses (built on the phone, so it needs no network). */
    private void openCompare() {
        if (b == null) return;
        if (compare.size() < 2) { ListArea.toast(this, getString(R.string.map_compare_need_two)); return; }
        Fragment p = getParentFragment();
        if (p instanceof ShellFragment) ((ShellFragment) p).showCompare(new ArrayList<>(compare.values()));
    }

    // ---- List | Map -----------------------------------------------------------------------------------------------

    private void setMapMode(boolean on) {
        if (b == null || mapMode == on) return;
        mapMode = on;
        paintSeg();
        b.area.getRoot().setVisibility(on ? View.GONE : View.VISIBLE);
        b.mapFrame.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) { ensureMap(); pushMap(true); }
    }

    /** The map only starts when it is first opened (MapLibre start-up is heavy). */
    private void ensureMap() {
        if (mapReady || b == null) return;
        mapReady = true;
        b.mapCard.setListener(new MapCardView.Listener() {
            @Override public void openPlace(String id) { BhDetailFragment.open(ExploreFragment.this, id); }
            @Override public void locate() { locateMe(); }
            @Override public void searchArea(double south, double west, double north, double east) {
                areaBox = new double[]{south, west, north, east};
                reload();
            }
            @Override public void placeLongPressed(String id) { toggleCompare(id); }
            @Override public void campusTapped(String id) {
                for (College c : BrowseState.colleges) if (c.id().equals(id)) { BrowseState.setCampus(c); return; }
            }
        });
        b.mapCard.showLocate(true);                 // v2.0.80: lifted above the floating Ask BOFO button, which sits in the same corner
        b.mapCard.setLocateLift(72);
        b.mapCard.setSearchAreaEnabled(true);
        b.mapCard.setStripEnabled(true);            // v2.0.81: swipeable card strip instead of the single peek card
        b.mapCard.setFavorites(Favorites.IDS.getValue());
        b.mapCard.setCompareIds(compare.keySet());
        b.mapCard.showExpand(false);
        b.mapCard.setLocState(MapCardView.Loc.NONE);
        b.mapCard.activate(getViewLifecycleOwner());
    }

    private void pushMap(boolean fit) {
        if (b == null || !mapReady) return;
        College c = BrowseState.campus();
        b.mapCard.setPlaces(lastItems);
        b.mapCard.setColleges(BrowseState.colleges);
        b.mapCard.setCampusDot(c == null ? null : c.lat(), c == null ? null : c.lng());
        b.mapCard.setRing(null, null, 0);
        b.mapCard.setCampusChip(c == null ? null : c.name());
        if (fit) b.mapCard.fit();
    }

    // ---- data -----------------------------------------------------------------------------------------------------

    private void reload() {
        if (b == null) return;
        final int my = ++token;
        final boolean softNow = soft; soft = false;
        area.showLoading();
        BrowseState.ensureLoaded(() -> {
            if (my != token || b == null) return;
            final College c = BrowseState.campus();
            final String q = query;
            final Double min = BrowseState.minPrice, max = BrowseState.maxPrice;
            final int sort = BrowseState.sort;
            final Set<String> names = new LinkedHashSet<>(BrowseState.amenityNames);
            final boolean onlyVerified = BrowseState.verifiedOnly, onlyBeds = BrowseState.bedsOnly;
            final BhService.Filter f = BrowseState.filter(q);
            final double[] box = areaBox;
            Async.run(() -> {
                Double lat = c == null ? null : c.lat();
                Double lng = c == null ? null : c.lng();
                List<BoardingHouse> l = new BhService().list(BhService.Tab.NEW, lat, lng, null, f);   // no radius: search everywhere, nearest first
                l = ListRules.inPriceRange(l, min, max);
                l = ExploreRules.withAll(l, names);
                l = ExploreRules.quick(l, onlyVerified, onlyBeds);
                l = ExploreRules.sort(l, sort);
                if (box != null) {                                                  // "Search this area": keep the houses inside the visible box (BhService has no bounds query)
                    List<BoardingHouse> in = new ArrayList<>();
                    for (BoardingHouse h : l) if (h.lat() >= box[0] && h.lat() <= box[2] && h.lng() >= box[1] && h.lng() <= box[3]) in.add(h);
                    l = in;
                }
                return l;
            }, (List<BoardingHouse> l) -> {
                if (my != token || b == null) return;
                lastItems = l;
                adapter.setShowDistance(c != null);
                b.resultCount.setText(l.size() == 1 ? getString(R.string.explore_result_one) : getString(R.string.explore_results, l.size()));
                if (l.isEmpty()) area.showMessage(getString(BrowseState.activeFilters() > 0 ? R.string.ex_empty_hint : R.string.explore_empty), false);
                else area.showList(l);
                if (mapMode) { pushMap(box == null && !softNow); if (softNow) b.mapCard.softFade(); }      // after "Search this area" or a quick chip the camera stays where the person put it
            }, e -> fail(my, e));
        }, e -> fail(my, e));
    }

    /** v2.0.80: locate button. Shows the blue dot, flies there, plays the "found you" ring; the dot is not kept live on Explore. */
    private void locateMe() {
        if (b == null || finding) return;
        finding = true;
        b.mapCard.setLocState(MapCardView.Loc.FINDING);
        loc.requestOnce(new LocationHelper.Callback() {
            @Override public void got(double lat, double lng) {
                if (b == null) return;
                finding = false;
                b.mapCard.setUser(lat, lng);
                b.mapCard.setLocState(LocationHelper.precise(requireContext()) ? MapCardView.Loc.EXACT : MapCardView.Loc.APPROX);
                b.mapCard.moveTo(lat, lng, 15);
                handler.postDelayed(() -> { if (b != null) b.mapCard.foundYou(); }, 450);
                handler.postDelayed(() -> { if (b != null && !finding) b.mapCard.setLocState(MapCardView.Loc.NONE); }, 3000);     // the pill steps aside for the campus chip
            }
            @Override public void off() { locFailed(R.string.loc_off); }
            @Override public void denied() { locFailed(R.string.loc_denied); }
            @Override public void failed() { locFailed(R.string.loc_failed); }
            @Override public void servicesOff() { locFailed(R.string.loc_services_off); }
        });
    }

    private void locFailed(int msg) {
        finding = false;
        if (b == null) return;
        b.mapCard.setLocState(MapCardView.Loc.NONE);
        ListArea.toast(this, getString(msg));
    }

    @Override public void onStop() { loc.stop(); super.onStop(); }

    private void fail(int my, Throwable e) {
        if (my != token || b == null) return;
        b.resultCount.setText("");
        if (Connectivity.isNetworkError(e)) area.showOffline();
        else area.showMessage(ErrorText.full(getString(R.string.list_error), e), true);
    }

    @Override
    public void onDestroyView() {
        token++;
        mapReady = false;
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        if (area != null) area.destroy();
        b = null;
        super.onDestroyView();
    }
}
