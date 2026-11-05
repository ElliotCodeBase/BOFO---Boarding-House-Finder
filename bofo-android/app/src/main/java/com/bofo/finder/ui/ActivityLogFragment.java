package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentAdminListBinding;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.AdminActivityRules;
import com.bofo.finder.util.AdminRules;
import com.bofo.finder.util.AiChatRules;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v2.4.14: Admin > Home > What I did. The admin's own last 50 actions (action, who or what it was about, when), read from audit_logs
 * (the existing RLS policy lets only an admin read it; we only ask for rows where the actor is this admin). Read-only. Reasons and notes are NOT shown.
 */
public class ActivityLogFragment extends Fragment {

    private FragmentAdminListBinding b;
    private AdminRowAdapter rows;
    private AdminListChrome chrome;
    private int token;
    private boolean haveRows;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentAdminListBinding.inflate(inflater, container, false);
        b.title.setText(R.string.act_log_title);
        b.title.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_back, 0, 0, 0);
        b.title.setCompoundDrawablePadding(Math.round(8 * getResources().getDisplayMetrics().density));
        b.title.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
        b.title.setGravity(android.view.Gravity.CENTER_VERTICAL);
        b.title.setContentDescription(getString(R.string.cd_back));
        b.title.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        rows = new AdminRowAdapter(requireContext());
        b.list.setAdapter(rows);
        chrome = new AdminListChrome(b, 0, new AdminListChrome.Host() {
            @Override public void onFilter(int chip) { }
            @Override public void onSearch(String query) { }
            @Override public void onRetry() { reload(); }
        });
        chrome.hideFilters();
        b.swipe.setColorSchemeResources(R.color.bofo_primary);
        b.swipe.setOnRefreshListener(this::reload);
        reload();
        return b.getRoot();
    }

    private void reload() {
        if (b == null) return;
        final int my = ++token;
        chrome.loading(haveRows);
        Async.run(() -> {
            String me = AuthService.get().userId();
            JSONArray a = SupabaseClient.get().select("audit_logs", "actor_id=eq." + me + "&order=created_at.desc&limit=50&select=action,target_type,target_id,created_at");
            List<JSONObject> l = new ArrayList<>();
            List<String> users = new ArrayList<>(), places = new ArrayList<>(), verifs = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                l.add(o);
                String t = o.optString("target_type"), id = o.optString("target_id", "");
                if ("user".equals(t)) users.add(id); else if ("boarding_house".equals(t)) places.add(id); else if ("owner_verification".equals(t)) verifs.add(id);
            }
            Map<String, String> names = new HashMap<>();
            try {                                              // names are a bonus: without them the rows still say what kind of thing it was
                Map<String, String> owner = new HashMap<>();
                String vin = AdminRules.inList(verifs);
                if (!vin.isEmpty()) {
                    JSONArray v = SupabaseClient.get().select("owner_verifications", "id=in.(" + vin + ")&select=id,owner_id");
                    for (int i = 0; i < v.length(); i++) { owner.put(v.getJSONObject(i).getString("id"), v.getJSONObject(i).optString("owner_id")); users.add(v.getJSONObject(i).optString("owner_id")); }
                }
                String uin = AdminRules.inList(users);
                Map<String, String> un = new HashMap<>();
                if (!uin.isEmpty()) {
                    JSONArray u = SupabaseClient.get().select("users", "id=in.(" + uin + ")&select=id,full_name");
                    for (int i = 0; i < u.length(); i++) un.put(u.getJSONObject(i).getString("id"), u.getJSONObject(i).optString("full_name", ""));
                }
                for (String id : users) if (un.containsKey(id)) names.put(id, un.get(id));
                for (Map.Entry<String, String> e : owner.entrySet()) names.put(e.getKey(), un.getOrDefault(e.getValue(), ""));
                String pin = AdminRules.inList(places);
                if (!pin.isEmpty()) {
                    JSONArray p = SupabaseClient.get().select("bh_cards", "id=in.(" + pin + ")&select=id,name");
                    for (int i = 0; i < p.length(); i++) names.put(p.getJSONObject(i).getString("id"), p.getJSONObject(i).optString("name", ""));
                }
            } catch (Exception ignored) { /* no names */ }
            return new Object[]{l, names};
        }, (Object[] r) -> {
            if (my != token || b == null) return;
            @SuppressWarnings("unchecked") List<JSONObject> l = (List<JSONObject>) r[0];
            @SuppressWarnings("unchecked") Map<String, String> names = (Map<String, String>) r[1];
            String[] labels = getResources().getStringArray(R.array.admin_act_labels);
            List<AdminRowAdapter.Row> out = new ArrayList<>();
            for (JSONObject o : l) {
                int i = AdminActivityRules.index(o.optString("action"));
                String name = names.get(o.optString("target_id", ""));
                String what = name == null || name.trim().isEmpty() ? "" : name.trim() + " - ";
                out.add(new AdminRowAdapter.Row(i >= 0 && i < labels.length ? labels[i] : getString(R.string.admin_act_other),
                        what + AiChatRules.dateTime(o.optString("created_at", ""), ZoneId.systemDefault()), "", AdminRowAdapter.NEUTRAL, null));
            }
            haveRows = !out.isEmpty();
            rows.set(out);
            chrome.loaded(haveRows, R.string.act_log_empty);
        }, e -> {
            if (my != token || b == null) return;
            chrome.failed(Connectivity.isNetworkError(e), haveRows);
        });
    }

    @Override
    public void onDestroyView() { token++; if (chrome != null) chrome.destroy(); chrome = null; b = null; super.onDestroyView(); }
}
