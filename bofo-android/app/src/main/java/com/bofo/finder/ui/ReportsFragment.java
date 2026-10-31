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
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.AdminRules;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BoostRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin > Reports (8h): the latest 100 rows of blocks_reports (reports and blocks, newest first; admins can read all of them).
 * Tap a row: who reported whom and why, and Take down a place of the reported user (admin_takedown(bh, reason), reason required).
 * There is no "resolved" state in the database, so nothing is marked as handled here.
 */
public class ReportsFragment extends Fragment {

    private FragmentAdminListBinding b;
    private final List<JSONObject> items = new ArrayList<>();
    private final Map<String, String> names = new HashMap<>();
    private ArrayAdapter<String> rows;
    private int token;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentAdminListBinding.inflate(inflater, container, false);
        b.title.setText(R.string.tab_reports);
        rows = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_2, android.R.id.text1, new ArrayList<String>()) {
            @NonNull @Override public View getView(int pos, @Nullable View v, @NonNull ViewGroup p) {
                View r = super.getView(pos, v, p);
                if (pos < 0 || pos >= items.size()) return r;
                JSONObject x = items.get(pos);
                String kind = getString(AdminRules.isReport(x.optBoolean("is_block")) ? R.string.rep_kind_report : R.string.rep_kind_block);
                ((TextView) r.findViewById(android.R.id.text1)).setText(kind + ": " + name(x.optString("reported_user_id")));
                String why = x.optString("reason", "");
                ((TextView) r.findViewById(android.R.id.text2)).setText(BoostRules.day(x.optString("created_at", ""), ZoneId.systemDefault())
                        + (why.isEmpty() || "null".equals(why) ? "" : "  -  " + why));
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

    private String name(String id) { return AdminRules.who(names.get(id)); }

    private void reload() {
        if (b == null) return;
        final int my = ++token;
        b.swipe.setRefreshing(true);
        Async.run(() -> {
            JSONArray a = SupabaseClient.get().select("blocks_reports", "order=created_at.desc&limit=100&select=id,reporter_id,reported_user_id,reason,is_block,created_at");
            List<JSONObject> l = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                l.add(o); ids.add(o.optString("reporter_id")); ids.add(o.optString("reported_user_id"));
            }
            Map<String, String> m = new HashMap<>();
            String in = AdminRules.inList(ids);
            if (!in.isEmpty()) {
                try {
                    JSONArray u = SupabaseClient.get().select("users", "id=in.(" + in + ")&select=id,full_name");
                    for (int i = 0; i < u.length(); i++) m.put(u.getJSONObject(i).getString("id"), u.getJSONObject(i).optString("full_name", ""));
                } catch (Exception ignored) { /* shown as Unknown user */ }
            }
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
            b.message.setText(R.string.rep_empty);
            b.message.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }, e -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.rep_error), e));
        });
    }

    private void details(JSONObject x) {
        String why = x.optString("reason", "");
        String msg = getString(R.string.rep_detail, name(x.optString("reporter_id")), name(x.optString("reported_user_id")),
                why.isEmpty() || "null".equals(why) ? getString(R.string.rep_no_reason) : why,
                BoostRules.day(x.optString("created_at", ""), ZoneId.systemDefault()));
        String target = x.optString("reported_user_id");
        new MaterialAlertDialogBuilder(requireContext()).setTitle(getString(AdminRules.isReport(x.optBoolean("is_block")) ? R.string.rep_kind_report : R.string.rep_kind_block))
                .setMessage(msg)
                .setNegativeButton(R.string.common_close, null)
                .setPositiveButton(R.string.rep_takedown, (d, w) -> pickPlace(target)).show();
    }

    /** The reported user's places that can still be taken down. */
    private void pickPlace(String userId) {
        if (AdminRules.inList(java.util.Collections.singletonList(userId)).isEmpty()) return;
        Async.run(() -> SupabaseClient.get().select("bh_cards", "owner_id=eq." + userId + "&select=id,name,status&order=created_at.desc"), (JSONArray a) -> {
            if (b == null) return;
            List<String> labels = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || !AdminRules.canTakeDown(o.optString("status", "active"))) continue;
                labels.add(o.optString("name")); ids.add(o.optString("id"));
            }
            if (ids.isEmpty()) { ListArea.toast(this, getString(R.string.rep_no_places)); return; }
            new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rep_pick_place)
                    .setItems(labels.toArray(new String[0]), (d, which) -> takeDown(ids.get(which), labels.get(which))).show();
        }, e -> ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.rep_error), e)));
    }

    private void takeDown(String bhId, String bhName) {
        ReasonDialog.ask(this, R.string.rep_takedown_title, R.string.rep_takedown_do, true, reason -> {
            if (!AdminRules.takedownOk(reason)) return;
            Async.run(() -> SupabaseClient.get().rpc("admin_takedown", new JSONObject().put("bh", bhId).put("reason", reason)), r ->
                    ListArea.toast(this, getString(R.string.rep_done, bhName)),
                    e -> ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.rep_takedown_error), e)));
        });
    }

    @Override
    public void onDestroyView() { token++; b = null; super.onDestroyView(); }
}
