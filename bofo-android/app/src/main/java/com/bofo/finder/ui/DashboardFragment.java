package com.bofo.finder.ui;

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

    private static final class Place {
        BoardingHouse bh; DashboardRules.Row row; boolean boosted;
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
            List<BoardingHouse> mine = new BhService().myListings(me);
            List<Place> out = new ArrayList<>();
            if (mine.isEmpty()) return out;
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
            return out;
        }, (List<Place> l) -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            render(l);
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

    private void render(List<Place> l) {
        if (l.isEmpty()) { show(getString(R.string.dash_empty)); return; }
        b.message.setVisibility(View.GONE);
        b.content.removeAllViews();
        List<DashboardRules.Row> rows = new ArrayList<>();
        int boosted = 0;
        for (Place p : l) { rows.add(p.row); if (p.boosted) boosted++; }
        DashboardRules.Totals t = DashboardRules.totals(rows);

        TextView head = text(getResources().getQuantityString(R.plurals.dash_places, t.places(), t.places())
                + (boosted > 0 ? "  -  " + getString(R.string.dash_boosted, boosted) : ""), 14, false);
        head.setTextColor(color(R.color.bofo_text_2));
        b.content.addView(head);
        b.content.addView(statRow(stat(t.views(), R.string.dash_views), stat(t.saves(), R.string.dash_saved), stat(t.chats(), R.string.dash_chats)));
        b.content.addView(statRow(stat(DashboardRules.rating(t.rating()), R.string.dash_rating), stat(t.reviews(), R.string.dash_reviews), stat(t.beds(), R.string.dash_beds)));

        TextView per = text(getString(R.string.dash_per_place), 16, true);
        per.setPadding(0, dp(20), 0, dp(8));
        b.content.addView(per);
        for (Place p : l) {
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_dir_option);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            card.setLayoutParams(lp);
            card.addView(text(p.bh.name() + (p.boosted ? "  \u2605" : ""), 15, true));
            DashboardRules.Row r = p.row;
            TextView nums = text(getString(R.string.dash_row, r.views(), r.saves(), r.chats(), DashboardRules.rating(r.rating()), r.reviews(), r.beds()), 13, false);
            nums.setTextColor(color(R.color.bofo_text_2));
            card.addView(nums);
            b.content.addView(card);
        }
    }

    private LinearLayout statRow(View... cells) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        row.setLayoutParams(lp);
        for (View c : cells) row.addView(c);
        return row;
    }

    private View stat(int value, int label) { return stat(String.valueOf(value), label); }

    private View stat(String value, int label) {
        LinearLayout cell = new LinearLayout(requireContext());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setBackgroundResource(R.drawable.bg_dir_option);
        cell.setPadding(dp(8), dp(14), dp(8), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(dp(8));
        cell.setLayoutParams(lp);
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
    public void onDestroyView() { token++; b = null; super.onDestroyView(); }
}
