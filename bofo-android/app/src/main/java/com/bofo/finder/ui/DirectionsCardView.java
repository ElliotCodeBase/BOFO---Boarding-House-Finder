package com.bofo.finder.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.bofo.finder.R;
import com.bofo.finder.databinding.ItemDirOptionBinding;
import com.bofo.finder.databinding.ViewDirectionsCardBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.College;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.AppPrefs;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.DirectionsRules;
import com.bofo.finder.util.Geo;
import com.bofo.finder.util.LocationHelper;
import com.bofo.finder.util.Router;
import com.bofo.finder.util.Trip;
import com.bofo.finder.util.TripPlanner;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Directions" card on the place screen (port of the JavaFX DirectionsCard). Three tabs pick the trip: me to the BH, BH to the campus,
 * me to the campus. For each trip it lists the real ways to go ({@link TripPlanner}: walk, tricycle, jeepney lines, bus / van when it is
 * far) with time and fare, recommends one, and draws the chosen one on a small map. The rules (which tab means which trip, what is
 * missing, when to plan again) live in {@link DirectionsRules}.
 *
 * <p>Location: never asked when the screen opens. If the permission is already granted (and Settings > Use my location is on) the card
 * follows you and plans again when you moved about 80 m; otherwise a "Use my location" button asks. Call {@link #bind} once the
 * place is loaded; the caller must have created the {@link LocationHelper} in its onCreate.
 */
public class DirectionsCardView extends MaterialCardView {

    private final ViewDirectionsCardBinding b;
    private Fragment host;
    private LocationHelper location;
    private BoardingHouse bh;
    private College campus;
    private double[] me;                       // {lat, lng} of the phone, null until known
    private int tab = -1, token, chosen;
    private Trip.Plan plan;
    private double[] planFrom;                 // where the current plan starts (to notice that you moved)
    private boolean planning, failed, userPicked, moreOpen, observing, stopped;
    private int locationProblem;               // string resource, 0 = none
    private final List<ItemDirOptionBinding> rows = new ArrayList<>();

    public DirectionsCardView(@NonNull Context c) { this(c, null); }

    public DirectionsCardView(@NonNull Context c, @Nullable AttributeSet a) {
        super(c, a);
        b = ViewDirectionsCardBinding.inflate(LayoutInflater.from(c), this, true);
        b.tabMeBh.setOnClickListener(v -> pick(DirectionsRules.TAB_ME_BH));
        b.tabBhCampus.setOnClickListener(v -> pick(DirectionsRules.TAB_BH_CAMPUS));
        b.tabMeCampus.setOnClickListener(v -> pick(DirectionsRules.TAB_ME_CAMPUS));
        b.btnInstead.setOnClickListener(v -> pick(DirectionsRules.TAB_BH_CAMPUS));
        b.btnFarCampus.setOnClickListener(v -> pick(DirectionsRules.TAB_BH_CAMPUS));
        b.btnRetry.setOnClickListener(v -> select(tab));
        b.btnLocate.setOnClickListener(v -> locateNow());
        b.btnOsm.setOnClickListener(v -> openExternal());
        b.btnMore.setOnClickListener(v -> toggleMore());
    }

    private final DefaultLifecycleObserver life = new DefaultLifecycleObserver() {
        @Override public void onStart(@NonNull LifecycleOwner o) { if (stopped) { stopped = false; startWatching(); } }
        @Override public void onStop(@NonNull LifecycleOwner o) { stopped = true; if (location != null) location.stop(); }
        @Override public void onDestroy(@NonNull LifecycleOwner o) { token++; observing = false; o.getLifecycle().removeObserver(this); }
    };

    private final LocationHelper.Callback watchCallback = new LocationHelper.Callback() {
        @Override public void got(double lat, double lng) { onFix(lat, lng); }
        @Override public void off() { }
        @Override public void denied() { }
        @Override public void failed() { }
    };

    // ---------------------------------------------------------------- setup

    /** Shows the card for this place. Safe to call again (the place screen reloads on "Try again"). */
    public void bind(@NonNull Fragment fragment, @NonNull LocationHelper helper, @NonNull BoardingHouse place) {
        host = fragment; location = helper; bh = place;
        token++;
        plan = null; planFrom = null; planning = false; failed = false; userPicked = false; moreOpen = false; me = null; locationProblem = 0;
        campus = BrowseState.campus();                                   // the chosen campus first, else the nearest one to this place (below)

        LifecycleOwner owner = fragment.getViewLifecycleOwner();
        MapCardView map = b.map;
        map.setTapsEnabled(false);                                      // one place on this map: no peek card needed
        map.showLocate(false);
        map.showExpand(false);
        map.setParentScrollBlocked(true);                               // the map sits inside the place screen's ScrollView
        map.setPlaces(Collections.singletonList(place));
        map.setCampusDot(campus == null ? null : Double.valueOf(campus.lat()), campus == null ? null : Double.valueOf(campus.lng()));
        map.setUser(null, null);
        map.activate(owner);
        map.moveTo(place.lat(), place.lng(), 15);
        if (!observing) { owner.getLifecycle().addObserver(life); observing = true; }

        Router.warmUp();
        if (campus == null) findNearestCampus();
        startWatching();
        select(DirectionsRules.defaultTab(false, campus != null));
    }

    private void findNearestCampus() {
        List<College> known = BrowseState.colleges;
        if (known != null && !known.isEmpty()) { onCampus(DirectionsRules.nearestCampus(known, bh.lat(), bh.lng())); return; }
        final int my = token;
        Async.run(() -> new BhService().colleges(), list -> {
            if (my != token) return;
            onCampus(DirectionsRules.nearestCampus(list, bh.lat(), bh.lng()));
        }, e -> { });
    }

    private void onCampus(@Nullable College c) {
        if (c == null) return;
        campus = c;
        b.map.setCampusDot(c.lat(), c.lng());
        if (plan == null && !planning && !failed) {
            // a tab was waiting for the campus; with no position and no choice made yet, BH -> Campus is the one that works
            select(!userPicked && me == null && tab == DirectionsRules.TAB_ME_BH ? DirectionsRules.TAB_BH_CAMPUS : tab);
        } else if (plan != null && plan.straightKm() > TripPlanner.FAR_KM) {
            render(tab);                                                // now the "show it from the campus" button can appear
        }
    }

    // ---------------------------------------------------------------- location

    private void startWatching() {
        Context c = getContext();
        if (host == null || location == null || c == null || !AppPrefs.useLocation(c) || !LocationHelper.granted(c)) return;   // never asks here
        location.startWatching(watchCallback);
    }

    /** The "Use my location" button: this is where the permission dialog may appear. */
    private void locateNow() {
        if (location == null) return;
        location.requestOnce(new LocationHelper.Callback() {
            @Override public void got(double lat, double lng) { onFix(lat, lng); startWatching(); }
            @Override public void off() { problem(R.string.dir_location_off); }
            @Override public void denied() { problem(R.string.dir_location_denied); }
            @Override public void failed() { problem(R.string.dir_location_failed); }
        });
    }

    private void problem(int res) {
        locationProblem = res;
        if (plan == null && !planning && !failed) showMissing(tab);
    }

    private void onFix(double lat, double lng) {
        me = new double[]{lat, lng};
        locationProblem = 0;
        b.map.setUser(lat, lng);
        if (!DirectionsRules.startsAtMe(tab)) return;                   // BH -> Campus does not depend on where you are
        if (plan == null && !planning && !failed) select(tab);          // the first fix arrived while we were waiting for it
        else if (plan != null && !planning && DirectionsRules.movedEnough(planFrom, lat, lng)) select(tab);
    }

    // ---------------------------------------------------------------- trips

    private double[] bhPoint() { return new double[]{bh.lat(), bh.lng()}; }
    private double[] campusPoint() { return campus == null ? null : new double[]{campus.lat(), campus.lng()}; }
    private double[] fromPoint(int t) { return DirectionsRules.from(t, bhPoint(), me); }
    private double[] toPoint(int t) { return DirectionsRules.to(t, bhPoint(), campusPoint()); }
    private String fromName(int t) { return t == DirectionsRules.TAB_BH_CAMPUS ? bh.name() : getContext().getString(R.string.dir_current_location); }
    private String toName(int t) { return t == DirectionsRules.TAB_ME_BH ? bh.name() : campus == null ? getContext().getString(R.string.dir_campus_word) : campus.name(); }

    private void pick(int t) { userPicked = true; select(t); }

    private void select(int t) {
        tab = t;
        final int my = ++token;
        b.tabs.check(t == DirectionsRules.TAB_ME_BH ? R.id.tab_me_bh : t == DirectionsRules.TAB_BH_CAMPUS ? R.id.tab_bh_campus : R.id.tab_me_campus);
        final double[] from = fromPoint(t), to = toPoint(t);
        plan = null; planFrom = null; planning = false; failed = false;
        b.map.clearRoute();                                             // never leave the previous tab's line on the map
        b.fromName.setText(fromName(t));
        b.toName.setText(toName(t));
        if (from == null || to == null) { showMissing(t); return; }

        planning = true;
        planFrom = from;
        showLoading();
        Router.Route known = Router.peek(from[0], from[1], to[0], to[1]);   // instant preview: a saved road, else a straight guide line
        if (known == null) known = Router.estimate(from[0], from[1], to[0], to[1]);
        b.map.setRoute(known.pts(), known.approximate());
        b.map.fitRoute();
        Async.run(() -> TripPlanner.planWithin(from, to, DirectionsRules.PLAN_SECONDS), p -> {
            if (my != token) return;
            planning = false; plan = p; chosen = p.best();
            render(t);
        }, e -> {
            if (my != token) return;
            planning = false; failed = true;
            showError();
        });
    }

    // ---------------------------------------------------------------- what the card shows

    private void showLoading() {
        b.dirLoading.setVisibility(View.VISIBLE);
        b.messageBox.setVisibility(View.GONE);
        b.planBox.setVisibility(View.GONE);
    }

    private void showMissing(int t) {
        DirectionsRules.Missing m = DirectionsRules.missing(t, me != null, campus != null);
        Context c = getContext();
        String text = "";
        boolean locate = false, instead = false;
        switch (m) {
            case NEED_CAMPUS: text = c.getString(R.string.dir_need_campus); break;
            case NEED_CAMPUS_AND_LOCATION: text = c.getString(R.string.dir_need_campus); locate = true; break;
            case NEED_LOCATION:
                text = c.getString(locationProblem != 0 ? locationProblem : R.string.dir_need_location);
                locate = true;
                instead = campus != null;                               // BH -> Campus needs no location
                break;
            default: break;
        }
        showMessage(text, locate, instead, false);
    }

    private void showError() { showMessage(getContext().getString(R.string.dir_error), false, false, true); }

    private void showMessage(String text, boolean locate, boolean instead, boolean retry) {
        b.dirLoading.setVisibility(View.GONE);
        b.planBox.setVisibility(View.GONE);
        b.messageBox.setVisibility(View.VISIBLE);
        b.message.setText(text);
        b.message.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        b.btnLocate.setVisibility(locate ? View.VISIBLE : View.GONE);
        b.btnInstead.setVisibility(instead ? View.VISIBLE : View.GONE);
        b.btnRetry.setVisibility(retry ? View.VISIBLE : View.GONE);
    }

    private void render(int t) {
        Context c = getContext();
        LayoutInflater inf = LayoutInflater.from(c);
        b.dirLoading.setVisibility(View.GONE);
        b.messageBox.setVisibility(View.GONE);
        b.planBox.setVisibility(View.VISIBLE);
        b.notes.removeAllViews();
        b.options.removeAllViews();
        b.moreBox.removeAllViews();
        rows.clear();

        // calm layout: the result, ONE short line, a few small buttons. The fine print waits behind "More info".
        boolean far = plan.straightKm() > TripPlanner.FAR_KM;
        String planNote = plan.note().trim();
        List<String> more = new ArrayList<>();
        if (far) {
            addNote(inf, c.getString(R.string.dir_far_line, Geo.fmt(plan.straightKm())));
            if (!planNote.isEmpty()) more.add(planNote);
        } else if (!planNote.isEmpty()) {
            addNote(inf, planNote);
        }

        int n = plan.options().size();
        b.waysTitle.setVisibility(n > 1 ? View.VISIBLE : View.GONE);
        b.singleDetail.setVisibility(View.GONE);
        if (n > 1) {
            for (int i = 0; i < n; i++) {
                final int idx = i;
                Trip.Option o = plan.options().get(i);
                ItemDirOptionBinding r = ItemDirOptionBinding.inflate(inf, b.options, false);
                r.optIcon.setText(o.mode().icon);
                r.optTitle.setText(o.title());
                r.optDetail.setText(o.detail());
                r.optTime.setText(Geo.fmtTime(o.minutes()));
                String fare = o.fareText();
                r.optFare.setText(fare);
                r.optFare.setVisibility(fare.isEmpty() ? View.GONE : View.VISIBLE);
                r.optBest.setVisibility(i == plan.best() ? View.VISIBLE : View.GONE);
                r.getRoot().setOnClickListener(v -> choose(idx));
                b.options.addView(r.getRoot());
                rows.add(r);
            }
        } else {
            String detail = plan.options().get(0).detail();
            if (far) more.add(detail);                                  // for a far trip this only repeats "too far to walk"
            else { b.singleDetail.setText(detail); b.singleDetail.setVisibility(View.VISIBLE); }
        }
        more.add(c.getString(R.string.dir_src));
        for (String s : more) {
            TextView tv = new TextView(c);
            tv.setTextAppearance(R.style.Text_Bofo_Secondary);
            tv.setTextSize(12);
            tv.setText(s);
            tv.setPadding(0, dp(6), 0, 0);
            b.moreBox.addView(tv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        b.moreBox.setVisibility(moreOpen ? View.VISIBLE : View.GONE);
        b.btnMore.setText(moreOpen ? R.string.dir_less : R.string.dir_more);
        b.btnFarCampus.setVisibility(far && t != DirectionsRules.TAB_BH_CAMPUS && campus != null ? View.VISIBLE : View.GONE);
        choose(chosen);
    }

    private void addNote(LayoutInflater inf, String text) {
        TextView tv = (TextView) inf.inflate(R.layout.item_dir_note, b.notes, false);
        tv.setText(text);
        b.notes.addView(tv);
    }

    /** Picks one way: the big summary, the highlighted row and the line on the map. */
    private void choose(int idx) {
        if (plan == null || idx < 0 || idx >= plan.options().size()) return;
        chosen = idx;
        Trip.Option o = plan.options().get(idx);
        b.heroIcon.setText(o.mode().icon);
        b.heroHead.setText(DirectionsRules.heroHead(o));
        b.heroMeta.setText(DirectionsRules.heroMeta(o));
        for (int i = 0; i < rows.size(); i++) rows.get(i).getRoot().setBackgroundResource(i == idx ? R.drawable.bg_dir_option_on : R.drawable.bg_dir_option);
        b.map.setRoute(o.path(), o.approximate());
        b.map.fitRoute();
    }

    private void toggleMore() {
        moreOpen = !moreOpen;
        b.moreBox.setVisibility(moreOpen ? View.VISIBLE : View.GONE);
        b.btnMore.setText(moreOpen ? R.string.dir_less : R.string.dir_more);
    }

    private void openExternal() {
        double[] a = fromPoint(tab), z = toPoint(tab);
        if (a == null || z == null) return;
        Trip.Mode m = plan == null || chosen >= plan.options().size() ? Trip.Mode.WALK : plan.options().get(chosen).mode();
        try {
            getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(DirectionsRules.osmUrl(m, a, z))));
        } catch (ActivityNotFoundException e) {
            if (host != null) ListArea.toast(host, getContext().getString(R.string.detail_open_failed));
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
