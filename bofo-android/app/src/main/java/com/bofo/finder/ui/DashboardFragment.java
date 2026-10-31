package com.bofo.finder.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentDashboardBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.DashboardRules;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.Geo;
import com.bofo.finder.util.Router;
import com.bofo.finder.util.DirectionsRules;
import com.bofo.finder.util.LocationHelper;
import com.bofo.finder.util.VerificationRules;
import com.bofo.finder.model.College;
import com.bofo.finder.model.Profile;
import com.bofo.finder.service.ProfileService;
import com.bofo.finder.service.VerificationService;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owner > Dashboard (8f): totals over all your places (views, saves, chats, rating, reviews, free beds) and one card per place.
 * Numbers come from the public.bh_activity table (one row per place, kept by the database); boosted places from public.boosts
 * (read failure there is ignored). Built in code: the number of places varies. Pull down to refresh.
 * The JavaFX DashboardView was not available, so the content follows the database, not the desktop screen.
 */
public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding b;
    private int token;
    private LocationHelper location;
    private MapCardView map;                       // owner map (rebuilt on every render)
    private List<College> colleges = new ArrayList<>();
    private List<BoardingHouse> mapPlaces = new ArrayList<>();
    private double[] me;                           // last phone fix
    private int routeToken;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    private static final class Place {
        BoardingHouse bh; DashboardRules.Row row; boolean boosted;
    }

    private static final class Data {
        List<Place> places = new ArrayList<>();
        VerificationRules.State verif;             // null = could not be read (banner stays hidden)
        List<College> colleges = new ArrayList<>();
    }

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        location = new LocationHelper(this);         // registers the permission launcher: must be made in onCreate
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentDashboardBinding.inflate(inflater, container, false);
        b.back.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        if (getParentFragment() instanceof ShellFragment) b.back.setVisibility(View.GONE);      // as an owner tab there is nothing to go back to
        b.swipe.setColorSchemeResources(R.color.bofo_primary);
        b.swipe.setOnRefreshListener(this::reload);
        reload();
        return b.getRoot();
    }

    private void reload() {
        if (b == null) return;
        final int my = ++token;
        b.swipe.setRefreshing(true);
        String me = AuthService.get().userId();
        Async.run(() -> {
            Data data = new Data();
            try {                                      // the banner and the campuses are bonuses: a failure never hides the numbers
                Profile pr = new ProfileService().fetchMe();
                data.verif = VerificationRules.effective(pr != null && pr.verified(), new VerificationService().myLatest(me).optString("status", "unverified"));
            } catch (Exception ignored) { /* unknown */ }
            try { data.colleges = new BhService().colleges(); } catch (Exception ignored) { /* no route start then */ }
            List<BoardingHouse> mine = new BhService().myListings(me);
            List<Place> out = data.places;
            if (mine.isEmpty()) return data;
            StringBuilder ids = new StringBuilder();
            for (BoardingHouse x : mine) ids.append(ids.length() == 0 ? "" : ",").append(x.id());
            JSONArray act = SupabaseClient.get().select("bh_activity", "boarding_house_id=in.(" + ids + ")&select=*");
            Map<String, JSONObject> byId = new HashMap<>();
            for (int i = 0; i < act.length(); i++) byId.put(act.getJSONObject(i).getString("boarding_house_id"), act.getJSONObject(i));
            Set<String> boosted = new HashSet<>();
            try {
                JSONArray bo = SupabaseClient.get().select("boosts", "owner_id=eq." + me + "&ends_at=gt." + java.time.Instant.now() + "&select=boarding_house_id");
                for (int i = 0; i < bo.length(); i++) boosted.add(bo.getJSONObject(i).getString("boarding_house_id"));
            } catch (Exception ignored) { /* the boost marks are a bonus */ }
            for (BoardingHouse x : mine) {
                JSONObject a = byId.get(x.id());
                Place p = new Place();
                p.bh = x;
                p.boosted = boosted.contains(x.id());
                p.row = a == null ? new DashboardRules.Row(0, 0, 0, 0, 0, 0)
                        : new DashboardRules.Row(a.optInt("views"), a.optInt("favorites_count"), a.optInt("chats_count"), a.optInt("review_count"), a.optDouble("avg_rating", 0), a.optInt("available_beds"));
                out.add(p);
            }
            out.sort((p, q) -> Integer.compare(q.row.views(), p.row.views()));
            return data;
        }, (Data d) -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            colleges = d.colleges;
            render(d);
        }, e -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            show(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.dash_error), e));
        });
    }

    private void show(String msg) {
        b.content.removeAllViews();
        b.message.setText(msg);
        b.message.setVisibility(View.VISIBLE);
        b.content.addView(b.message);
    }

    private void render(Data d) {
        List<Place> l = d.places;
        b.content.removeAllViews();
        addBanner(d.verif);
        if (l.isEmpty()) {
            b.message.setText(R.string.dash_empty);
            b.message.setVisibility(View.VISIBLE);
            b.content.addView(b.message);
            return;
        }
        b.message.setVisibility(View.GONE);
        List<DashboardRules.Row> rows = new ArrayList<>();
        int boosted = 0;
        for (Place p : l) { rows.add(p.row); if (p.boosted) boosted++; }
        DashboardRules.Totals t = DashboardRules.totals(rows);

        // hero: places + boosted, and the big total of views
        LinearLayout hero = new LinearLayout(requireContext());
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackgroundResource(R.drawable.bg_dash_hero);
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));
        TextView heroTop = text(getResources().getQuantityString(R.plurals.dash_places, t.places(), t.places())
                + (boosted > 0 ? "  \u00B7  " + getString(R.string.dash_boosted, boosted) : ""), 13, true);
        heroTop.setTextColor(0xCCFFFFFF);
        TextView heroNum = text(String.valueOf(t.views()), 40, true);
        heroNum.setTextColor(Color.WHITE);
        TextView heroLbl = text(getString(R.string.dash_views_total), 13, false);
        heroLbl.setTextColor(0xCCFFFFFF);
        hero.addView(heroTop); hero.addView(heroNum); hero.addView(heroLbl);
        b.content.addView(hero, matchWrap(0, 0));

        b.content.addView(statRow(stat(t.saves(), R.string.dash_saved), stat(t.chats(), R.string.dash_chats), stat(t.beds(), R.string.dash_beds)));
        b.content.addView(statRow(stat(DashboardRules.rating(t.rating()), R.string.dash_rating), stat(t.reviews(), R.string.dash_reviews)));

        addMap(l);

        TextView per = text(getString(R.string.dash_per_place), 18, true);
        per.setPadding(dp(2), dp(24), 0, dp(10));
        b.content.addView(per);
        for (Place p : l) b.content.addView(placeCard(p));
    }

    /** One white card per place: photo, name, price, boost star, and chips for views / saved / chats / free beds / rating. */
    private View placeCard(Place p) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundResource(R.drawable.bg_dash_card);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setClickable(true);
        final String hid = p.bh.id();
        card.setOnClickListener(v -> BhDetailFragment.open(this, hid));
        card.setLayoutParams(matchWrap(0, dp(10)));

        com.google.android.material.imageview.ShapeableImageView img = new com.google.android.material.imageview.ShapeableImageView(requireContext());
        img.setShapeAppearanceModel(img.getShapeAppearanceModel().toBuilder().setAllCornerSizes(dp(14)).build());
        img.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        com.bumptech.glide.Glide.with(img).load(p.bh.photoUrl() == null || p.bh.photoUrl().isEmpty() ? (String) null : p.bh.photoUrl())
                .centerCrop().placeholder(R.color.bofo_skeleton).into(img);
        card.addView(img, new LinearLayout.LayoutParams(dp(84), dp(84)));

        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cl.setMarginStart(dp(12));
        card.addView(col, cl);

        LinearLayout top = new LinearLayout(requireContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(p.bh.name(), 15, true);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (p.boosted) top.addView(chip("\u2605 " + getString(R.string.dash_boost_tag), R.drawable.bg_dash_chip_amber));
        col.addView(top);

        TextView price = text(com.bofo.finder.util.Money.perMonth(p.bh.price()), 13, false);
        price.setTextColor(color(R.color.bofo_primary));
        col.addView(price);

        DashboardRules.Row r = p.row;
        LinearLayout chips = new LinearLayout(requireContext());
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(8), 0, 0);
        chips.addView(chip(getString(R.string.dash_chip_views, r.views()), R.drawable.bg_dash_chip));
        chips.addView(chip(getString(R.string.dash_chip_saved, r.saves()), R.drawable.bg_dash_chip));
        chips.addView(chip(getString(R.string.dash_chip_chats, r.chats()), R.drawable.bg_dash_chip));
        col.addView(chips);

        LinearLayout chips2 = new LinearLayout(requireContext());
        chips2.setOrientation(LinearLayout.HORIZONTAL);
        chips2.setPadding(0, dp(6), 0, 0);
        chips2.addView(chip(getString(R.string.dash_chip_beds, r.beds()), r.beds() > 0 ? R.drawable.bg_dash_chip_green : R.drawable.bg_dash_chip));
        if (r.reviews() > 0) chips2.addView(chip("\u2605 " + DashboardRules.rating(r.rating()) + " (" + r.reviews() + ")", R.drawable.bg_dash_chip_amber));
        col.addView(chips2);
        return card;
    }

    private TextView chip(String s, int bg) {
        TextView c = text(s, 11, true);
        c.setBackgroundResource(bg);
        c.setPadding(dp(8), dp(3), dp(8), dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(6));
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = top; lp.bottomMargin = bottom;
        return lp;
    }

    /** Verification status banner (same words as My places). Hidden when verified or unknown. */
    private void addBanner(VerificationRules.State v) {
        if (v == null || v == VerificationRules.State.VERIFIED) return;
        TextView t = text(getString(v == VerificationRules.State.PENDING ? R.string.verify_banner_pending
                : v == VerificationRules.State.REJECTED ? R.string.verify_banner_rejected : R.string.verify_banner_unverified), 14, true);
        t.setBackgroundResource(R.drawable.bg_dash_chip_amber);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        t.setLayoutParams(lp);
        t.setClickable(true);
        t.setOnClickListener(x -> NavHostFragment.findNavController(this).navigate(R.id.verificationFragment));
        b.content.addView(t);
    }

    /** "Your places on the map": tap a pin to see the route from the nearest campus (as on the JavaFX owner Dashboard). */
    private void addMap(List<Place> l) {
        mapPlaces = new ArrayList<>();
        for (Place p : l) mapPlaces.add(p.bh);
        TextView title = text(getString(R.string.dash_map_title), 18, true);
        title.setPadding(dp(2), dp(24), 0, dp(10));
        b.content.addView(title);
        MaterialCardView frame = new MaterialCardView(requireContext());
        frame.setRadius(dp(28));
        frame.setCardElevation(0);
        frame.setStrokeWidth(dp(1));
        frame.setStrokeColor(0x1F000000);
        frame.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
        map = new MapCardView(requireContext());
        map.setParentScrollBlocked(true);
        map.showExpand(false);
        map.setListener(new MapCardView.Listener() {
            @Override public void openPlace(String id) { BhDetailFragment.open(DashboardFragment.this, id); }
            @Override public void locate() { locateMe(); }
            @Override public void placeSelected(String id) { showRoute(id); }
        });
        frame.addView(map, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(new CornerMaskView(requireContext()), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        b.content.addView(frame);
        map.setPlaces(mapPlaces);
        map.setColleges(colleges);
        final MapCardView m = map;
        b.getRoot().postDelayed(() -> { if (b != null && map == m && isAdded()) { m.activate(getViewLifecycleOwner()); m.fit(); } }, 300);
    }

    private void locateMe() {
        if (me != null && map != null) { map.moveTo(me[0], me[1], 15); return; }
        location.requestOnce(new LocationHelper.Callback() {
            @Override public void got(double lat, double lng) {
                if (b == null || map == null) return;
                me = new double[]{lat, lng};
                map.setUser(lat, lng);
                map.setLocState(LocationHelper.precise(requireContext()) ? MapCardView.Loc.EXACT : MapCardView.Loc.APPROX);
                map.moveTo(lat, lng, 15);
            }
            @Override public void off() { ListArea.toast(DashboardFragment.this, getString(R.string.loc_off)); }
            @Override public void denied() { ListArea.toast(DashboardFragment.this, getString(R.string.loc_denied)); }
            @Override public void failed() { ListArea.toast(DashboardFragment.this, getString(R.string.loc_failed)); }
        });
    }

    /** Pin tapped (id) or closed (null): route from the campus nearest to that place; the real road route replaces the estimate. */
    private void showRoute(String id) {
        if (b == null || map == null) return;
        final int my = ++routeToken;
        BoardingHouse h = null;
        if (id != null) for (BoardingHouse x : mapPlaces) if (id.equals(x.id())) { h = x; break; }
        College c = h == null || colleges.isEmpty() ? null : DirectionsRules.nearestCampus(colleges, h.lat(), h.lng());
        if (h == null || c == null) { map.clearRoute(); return; }
        final BoardingHouse house = h;
        final double[] from = {c.lat(), c.lng()};
        Router.Route known = Router.peek(from[0], from[1], house.lat(), house.lng());
        if (known == null) known = Router.estimate(from[0], from[1], house.lat(), house.lng());
        drawRoute(known, from, house);
        map.fitRoute();
        fetchRoute(my, from, house, 0);
    }

    private static boolean walkable(double[] from, BoardingHouse h) { return Geo.km(from[0], from[1], h.lat(), h.lng()) <= 3; }

    private void fetchRoute(int my, double[] from, BoardingHouse h, int tries) {
        Async.run(() -> walkable(from, h) ? Router.walk(from[0], from[1], h.lat(), h.lng()) : Router.drive(from[0], from[1], h.lat(), h.lng()), r -> {
            if (my != routeToken || b == null || map == null) return;
            drawRoute(r, from, h);
            if (!r.approximate()) map.fitRoute();
            else if (tries < 4) ui.postDelayed(() -> { if (my == routeToken && b != null) fetchRoute(my, from, h, tries + 1); }, 1500L * (tries + 1));
        }, e -> {
            if (my == routeToken && b != null && tries < 4) ui.postDelayed(() -> { if (my == routeToken && b != null) fetchRoute(my, from, h, tries + 1); }, 1500L * (tries + 1));
        });
    }

    private void drawRoute(Router.Route r, double[] from, BoardingHouse h) {
        map.setRoute(r.pts(), r.approximate());
        double km = r.meters() / 1000.0;
        int min = (int) Math.max(1, Math.round(r.meters() / (walkable(from, h) ? 80.0 : 500.0)));
        map.setRouteLabel(Geo.fmt(km) + " \u00B7 " + Geo.fmtTime(min));
    }

    private LinearLayout statRow(View... cells) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(matchWrap(dp(10), 0));
        for (int i = 0; i < cells.length; i++) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) cells[i].getLayoutParams();
            if (i < cells.length - 1) lp.setMarginEnd(dp(10));
            row.addView(cells[i]);
        }
        return row;
    }

    private View stat(int value, int label) { return stat(String.valueOf(value), label); }

    private View stat(String value, int label) {
        LinearLayout cell = new LinearLayout(requireContext());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setBackgroundResource(R.drawable.bg_dash_card);
        cell.setPadding(dp(8), dp(14), dp(8), dp(14));
        cell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text(value, 24, true);
        v.setGravity(Gravity.CENTER);
        TextView l = text(getString(label), 12, false);
        l.setGravity(Gravity.CENTER);
        l.setTextColor(color(R.color.bofo_text_2));
        cell.addView(v);
        cell.addView(l);
        return cell;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(requireContext());
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color(R.color.bofo_text));
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        return t;
    }

    private int color(int res) { return androidx.core.content.ContextCompat.getColor(requireContext(), res); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    public void onDestroyView() { token++; routeToken++; map = null; ui.removeCallbacksAndMessages(null); b = null; super.onDestroyView(); }
}
