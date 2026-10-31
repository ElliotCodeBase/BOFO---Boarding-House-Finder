package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentPaymentsBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.service.BoostService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BoostRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.Money;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owner > Payments (8g): your last 30 boost payments, newest first (BoostService.myPayments; RLS shows an owner only their own rows).
 * Row = place name + plan, amount, status and date. Tap a row: full details with the reference number. Pull down to refresh.
 */
public class PaymentsFragment extends Fragment {

    private FragmentPaymentsBinding b;
    private final List<JSONObject> items = new ArrayList<>();
    private final Map<String, String> names = new HashMap<>();
    private ArrayAdapter<String> rows;
    private int token;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentPaymentsBinding.inflate(inflater, container, false);
        b.back.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        rows = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_2, android.R.id.text1, new ArrayList<String>()) {
            @NonNull @Override public View getView(int pos, @Nullable View v, @NonNull ViewGroup p) {
                View r = super.getView(pos, v, p);
                if (pos < 0 || pos >= items.size()) return r;
                JSONObject x = items.get(pos);
                ((TextView) r.findViewById(android.R.id.text1)).setText(Money.peso(x.optDouble("amount_php", 0)) + "  -  " + statusText(x.optString("status")));
                ((TextView) r.findViewById(android.R.id.text2)).setText(place(x) + "  -  " + BoostRules.day(x.optString("created_at", ""), ZoneId.systemDefault()));
                return r;
            }
        };
        b.list.setAdapter(rows);
        b.list.setOnItemClickListener((p, v, pos, id) -> { if (pos >= 0 && pos < items.size()) details(items.get(pos)); });
        b.swipe.setColorSchemeResources(R.color.bofo_primary);
        b.swipe.setOnRefreshListener(this::reload);
        reload();
        return b.getRoot();
    }

    private String place(JSONObject x) {
        String n = names.get(x.optString("boarding_house_id"));
        return n == null ? getString(R.string.pay_place_gone) : n;
    }

    private String statusText(String s) {
        switch (BoostRules.pay(s)) {
            case PENDING: return getString(R.string.pay_st_pending);
            case PAID: return getString(R.string.pay_st_paid);
            case FAILED: return getString(R.string.pay_st_failed);
            case EXPIRED: return getString(R.string.pay_st_expired);
            case REFUNDED: return getString(R.string.pay_st_refunded);
            default: return s == null || s.isEmpty() ? "?" : s;
        }
    }

    private void reload() {
        if (b == null) return;
        final int my = ++token;
        b.swipe.setRefreshing(true);
        String me = AuthService.get().userId();
        Async.run(() -> {
            JSONArray pay = new BoostService().myPayments();
            Map<String, String> m = new HashMap<>();
            try {                                              // names are a bonus: a failure here still shows the payments
                for (BoardingHouse h : new BhService().myListings(me)) m.put(h.id(), h.name());
            } catch (Exception ignored) { /* shown as "a removed place" */ }
            List<JSONObject> l = new ArrayList<>();
            for (int i = 0; i < pay.length(); i++) l.add(pay.getJSONObject(i));
            return new Object[]{l, m};
        }, (Object[] r) -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            @SuppressWarnings("unchecked") List<JSONObject> l = (List<JSONObject>) r[0];
            @SuppressWarnings("unchecked") Map<String, String> m = (Map<String, String>) r[1];
            items.clear(); items.addAll(l);
            names.clear(); names.putAll(m);
            rows.clear();
            for (int i = 0; i < items.size(); i++) rows.add("");
            rows.notifyDataSetChanged();
            b.message.setText(R.string.pay_empty);
            b.message.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }, e -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.pay_error), e));
        });
    }

    private void details(JSONObject x) {
        String msg = getString(R.string.pay_detail, place(x), x.optString("plan_id"), Money.peso(x.optDouble("amount_php", 0)), statusText(x.optString("status")),
                BoostRules.day(x.optString("created_at", ""), ZoneId.systemDefault()), x.optString("reference_number", "-"));
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.pay_detail_title).setMessage(msg)
                .setPositiveButton(R.string.common_close, null).show();
    }

    @Override
    public void onDestroyView() { token++; b = null; super.onDestroyView(); }
}
