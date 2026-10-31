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

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentAdminListBinding;
import com.bofo.finder.service.BoostService;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.AdminRules;
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
 * Admin > Payments (8h): the latest 100 boost payments of all owners (BoostService.allPayments). Tap one for the details; a PAID payment can be refunded
 * (admin_refund_boost: marks it refunded and ends the boost now). The refund only changes our records: the money itself must be returned in the PayMongo dashboard.
 */
public class AdminPaymentsFragment extends Fragment {

    private FragmentAdminListBinding b;
    private final List<JSONObject> items = new ArrayList<>();
    private final Map<String, String> owners = new HashMap<>(), places = new HashMap<>();
    private ArrayAdapter<String> rows;
    private int token;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentAdminListBinding.inflate(inflater, container, false);
        b.title.setText(R.string.tab_admin_payments);
        rows = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_2, android.R.id.text1, new ArrayList<String>()) {
            @NonNull @Override public View getView(int pos, @Nullable View v, @NonNull ViewGroup p) {
                View r = super.getView(pos, v, p);
                if (pos < 0 || pos >= items.size()) return r;
                JSONObject x = items.get(pos);
                ((TextView) r.findViewById(android.R.id.text1)).setText(Money.peso(x.optDouble("amount_php", 0)) + "  -  " + statusText(x.optString("status")));
                ((TextView) r.findViewById(android.R.id.text2)).setText(AdminRules.who(owners.get(x.optString("owner_id"))) + "  -  "
                        + BoostRules.day(x.optString("created_at", ""), ZoneId.systemDefault()));
                return r;
            }
        };
        b.list.setAdapter(rows);
        b.list.setOnItemClickListener((p, v, pos, id) -> { if (pos >= 0 && pos < items.size()) details(items.get(pos)); });
        b.swipe.setColorSchemeResources(R.color.bofo_primary);
        b.swipe.setOnRefreshListener(this::reload);
        return b.getRoot();
    }

    @Override
    public void onResume() { super.onResume(); reload(); }

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
        Async.run(() -> {
            JSONArray a = new BoostService().allPayments();
            List<JSONObject> l = new ArrayList<>();
            List<String> oid = new ArrayList<>(), pid = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                l.add(o); oid.add(o.optString("owner_id")); pid.add(o.optString("boarding_house_id"));
            }
            Map<String, String> om = new HashMap<>(), pm = new HashMap<>();
            try {                                              // names are a bonus
                String in = AdminRules.inList(oid);
                if (!in.isEmpty()) {
                    JSONArray u = SupabaseClient.get().select("users", "id=in.(" + in + ")&select=id,full_name");
                    for (int i = 0; i < u.length(); i++) om.put(u.getJSONObject(i).getString("id"), u.getJSONObject(i).optString("full_name", ""));
                }
                String pin = AdminRules.inList(pid);
                if (!pin.isEmpty()) {
                    JSONArray u = SupabaseClient.get().select("bh_cards", "id=in.(" + pin + ")&select=id,name");
                    for (int i = 0; i < u.length(); i++) pm.put(u.getJSONObject(i).getString("id"), u.getJSONObject(i).optString("name", ""));
                }
            } catch (Exception ignored) { /* shown without names */ }
            return new Object[]{l, om, pm};
        }, (Object[] r) -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            @SuppressWarnings("unchecked") List<JSONObject> l = (List<JSONObject>) r[0];
            @SuppressWarnings("unchecked") Map<String, String> om = (Map<String, String>) r[1];
            @SuppressWarnings("unchecked") Map<String, String> pm = (Map<String, String>) r[2];
            items.clear(); items.addAll(l);
            owners.clear(); owners.putAll(om);
            places.clear(); places.putAll(pm);
            rows.clear();
            for (int i = 0; i < items.size(); i++) rows.add("");
            rows.notifyDataSetChanged();
            b.message.setText(R.string.adminpay_empty);
            b.message.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }, e -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.pay_error), e));
        });
    }

    private void details(JSONObject x) {
        String pl = places.get(x.optString("boarding_house_id"));
        String msg = getString(R.string.adminpay_detail, AdminRules.who(owners.get(x.optString("owner_id"))), pl == null ? getString(R.string.pay_place_gone) : pl,
                x.optString("plan_id"), Money.peso(x.optDouble("amount_php", 0)), statusText(x.optString("status")),
                BoostRules.day(x.optString("created_at", ""), ZoneId.systemDefault()), x.optString("reference_number", "-"));
        MaterialAlertDialogBuilder d = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.pay_detail_title).setMessage(msg)
                .setNegativeButton(R.string.common_close, null);
        if (AdminRules.canRefund(x.optString("status"))) d.setPositiveButton(R.string.adminpay_refund, (dd, w) -> refund(x.optString("id")));
        d.show();
    }

    private void refund(String paymentId) {
        ReasonDialog.ask(this, R.string.adminpay_refund_title, R.string.adminpay_refund_do, true, reason -> {
            if (!AdminRules.takedownOk(reason)) return;
            Async.run(() -> { new BoostService().refund(paymentId, reason); return Boolean.TRUE; }, ok -> {
                ListArea.toast(this, getString(R.string.adminpay_done));
                reload();
            }, e -> ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.adminpay_error), e)));
        });
    }

    @Override
    public void onDestroyView() { token++; b = null; super.onDestroyView(); }
}
