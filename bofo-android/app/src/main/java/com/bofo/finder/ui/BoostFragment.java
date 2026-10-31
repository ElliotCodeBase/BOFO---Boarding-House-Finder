package com.bofo.finder.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentBoostBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.service.BoostService;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BoostRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Owner > Boost a place (8g): pick one of your active places and a plan, tap Pay. The Edge Function create-boost-checkout (server side) answers with a
 * payment link that opens in a Custom Tab; when you come back, the payment row is read until it is paid / failed / expired (about 2 minutes while the
 * screen is open, then Check again). Prices and activation are decided by the server: the app only starts the checkout and watches the status.
 * Open with a place preselected: arguments bhId (optional).
 */
public class BoostFragment extends Fragment {

    private static final String S_PLACE = "place", S_PLAN = "plan", S_PAY = "pay", S_REF = "ref";

    private FragmentBoostBinding b;
    private final BoostService boosts = new BoostService();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<BoardingHouse> places = new ArrayList<>();
    private final List<JSONObject> plans = new ArrayList<>();
    private String placeId, planId, paymentId, reference;
    private boolean busy, loaded;
    private int token, attempt;
    private final Runnable pollTick = this::checkStatus;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentBoostBinding.inflate(inflater, container, false);
        if (state != null) {
            placeId = state.getString(S_PLACE); planId = state.getString(S_PLAN);
            paymentId = state.getString(S_PAY); reference = state.getString(S_REF);
        } else if (getArguments() != null) {
            placeId = getArguments().getString("bhId");
        }
        b.back.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        b.placeBtn.setOnClickListener(v -> pickPlace());
        b.pay.setOnClickListener(v -> pay());
        b.checkAgain.setOnClickListener(v -> { attempt = 0; checkStatus(); });
        refreshUi();
        load();
        return b.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(S_PLACE, placeId); out.putString(S_PLAN, planId);
        out.putString(S_PAY, paymentId); out.putString(S_REF, reference);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (paymentId != null) { attempt = 0; handler.removeCallbacks(pollTick); handler.post(pollTick); }   // back from the payment page (or the screen turned back on)
    }

    @Override
    public void onPause() { super.onPause(); handler.removeCallbacks(pollTick); }

    /* ---------- loading: active places + plans ---------- */

    private void load() {
        final int my = ++token;
        String me = AuthService.get().userId();
        Async.run(() -> {
            List<BoardingHouse> mine = new BhService().myListings(me);
            JSONArray st = SupabaseClient.get().select("boarding_houses", "owner_id=eq." + me + "&select=id,status");
            List<String> active = new ArrayList<>();
            for (int i = 0; i < st.length(); i++) if ("active".equals(st.getJSONObject(i).optString("status", "active"))) active.add(st.getJSONObject(i).getString("id"));
            List<BoardingHouse> ok = new ArrayList<>();
            for (BoardingHouse x : mine) if (active.contains(x.id())) ok.add(x);          // unlisted / taken-down places cannot be seen by students: boosting them is pointless
            JSONArray pl = boosts.plans();
            List<JSONObject> lp = new ArrayList<>();
            for (int i = 0; i < pl.length(); i++) lp.add(pl.getJSONObject(i));
            return new Object[]{ok, lp};
        }, (Object[] r) -> {
            if (my != token || b == null) return;
            @SuppressWarnings("unchecked") List<BoardingHouse> p = (List<BoardingHouse>) r[0];
            @SuppressWarnings("unchecked") List<JSONObject> l = (List<JSONObject>) r[1];
            places.clear(); places.addAll(p);
            plans.clear(); plans.addAll(l);
            loaded = true;
            if (placeId != null && find(placeId) == null) placeId = null;               // preselected place is not boostable
            if (placeId == null && places.size() == 1) placeId = places.get(0).id();
            if (planId == null && !plans.isEmpty()) planId = plans.get(plans.size() > 1 ? 1 : 0).optString("id");   // the middle plan is a sensible default
            buildPlans();
            refreshUi();
        }, e -> {
            if (my != token || b == null) return;
            loaded = true;
            message(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.boost_load_error), e));
        });
    }

    private BoardingHouse find(String id) {
        for (BoardingHouse x : places) if (x.id().equals(id)) return x;
        return null;
    }

    private void buildPlans() {
        b.plans.removeAllViews();
        b.plans.setOnCheckedChangeListener(null);
        for (JSONObject p : plans) {
            RadioButton rb = new RadioButton(requireContext());
            rb.setId(View.generateViewId());
            rb.setText(BoostRules.planLine(p.optString("name"), p.optInt("days"), p.optDouble("price_php", 0)));
            rb.setTag(p.optString("id"));
            rb.setTextSize(15);
            rb.setPadding(dp(8), dp(10), dp(8), dp(10));
            b.plans.addView(rb);
            if (p.optString("id").equals(planId)) rb.setChecked(true);
        }
        b.plans.setOnCheckedChangeListener((g, id) -> {
            View v = g.findViewById(id);
            planId = v == null ? null : (String) v.getTag();
            refreshUi();
        });
    }

    /* ---------- UI state ---------- */

    private void message(String m) {
        if (b == null) return;
        b.message.setText(m);
        b.message.setVisibility(m == null || m.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void refreshUi() {
        if (b == null) return;
        BoardingHouse p = placeId == null ? null : find(placeId);
        b.placeBtn.setText(p != null ? p.name() : getString(R.string.boost_choose_place));
        b.pay.setEnabled(loaded && BoostRules.canPay(placeId, planId, busy));
        if (loaded && places.isEmpty()) message(getString(R.string.boost_no_places));
        else if (loaded && plans.isEmpty()) message(getString(R.string.boost_no_plans));
    }

    private void pickPlace() {
        if (places.isEmpty()) return;
        String[] names = new String[places.size()];
        for (int i = 0; i < names.length; i++) names[i] = places.get(i).name();
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.boost_place)
                .setItems(names, (d, which) -> { placeId = places.get(which).id(); refreshUi(); }).show();
    }

    /* ---------- checkout ---------- */

    private void pay() {
        if (!BoostRules.canPay(placeId, planId, busy)) return;
        busy = true; message(null); refreshUi();
        final String bh = placeId, plan = planId;
        Async.run(() -> boosts.checkout(bh, plan), (JSONObject r) -> {
            busy = false;
            if (b == null) return;
            refreshUi();
            String url = BoostRules.safeCheckoutUrl(r.optString("checkout_url", null));
            if (url == null) { message(getString(R.string.boost_no_link)); return; }
            paymentId = r.optString("payment_id", "");
            if (paymentId.isEmpty()) paymentId = null;
            reference = r.optString("reference", "");
            showStatus(getString(R.string.boost_waiting) + (reference.isEmpty() ? "" : "\n" + getString(R.string.boost_ref, reference)), false);
            open(url);
        }, e -> {
            busy = false;
            if (b == null) return;
            refreshUi();
            if (Connectivity.isNetworkError(e)) { message(getString(R.string.list_offline)); return; }
            Throwable t = e;
            while (t != null && !(t instanceof SupabaseClient.ApiException)) t = t.getCause();
            if (t instanceof SupabaseClient.ApiException ae && BoostRules.functionMissing(ae.status, ae.body)) { message(getString(R.string.boost_not_ready)); return; }
            message(ErrorText.full(getString(R.string.boost_error), e));
        });
    }

    private void open(String url) {
        try {
            new CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(requireContext(), Uri.parse(url));
        } catch (ActivityNotFoundException | SecurityException e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (ActivityNotFoundException e2) { message(getString(R.string.boost_no_browser)); }
        }
    }

    /* ---------- status polling ---------- */

    private void showStatus(String text, boolean allowCheck) {
        if (b == null) return;
        b.statusCard.setVisibility(View.VISIBLE);
        b.statusText.setText(text);
        b.checkAgain.setVisibility(allowCheck ? View.VISIBLE : View.GONE);
    }

    private void checkStatus() {
        final String pid = paymentId;
        if (pid == null || b == null) return;
        final int my = token;
        Async.run(() -> boosts.status(pid), (String s) -> {
            if (b == null || my != token || !pid.equals(paymentId)) return;
            BoostRules.Pay p = BoostRules.pay(s);
            String ref = reference == null || reference.isEmpty() ? "" : "\n" + getString(R.string.boost_ref, reference);
            switch (p) {
                case PAID: showStatus(getString(R.string.boost_paid) + ref, false); paymentId = null; return;
                case FAILED: showStatus(getString(R.string.boost_failed) + ref, true); paymentId = null; return;
                case EXPIRED: showStatus(getString(R.string.boost_expired) + ref, true); paymentId = null; return;
                case REFUNDED: showStatus(getString(R.string.boost_refunded) + ref, false); paymentId = null; return;
                default: break;
            }
            if (attempt >= BoostRules.MAX_POLLS) { showStatus(getString(R.string.boost_still_waiting) + ref, true); return; }
            showStatus(getString(R.string.boost_waiting) + ref, true);
            handler.postDelayed(pollTick, BoostRules.pollDelaySeconds(attempt++) * 1000L);
        }, e -> {
            if (b == null || my != token) return;
            showStatus(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.boost_status_error), e), true);
        });
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    public void onDestroyView() { token++; handler.removeCallbacks(pollTick); b = null; super.onDestroyView(); }
}
