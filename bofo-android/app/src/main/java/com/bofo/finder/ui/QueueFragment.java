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
import com.bofo.finder.databinding.FragmentAdminListBinding;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.service.VerificationService;
import com.bofo.finder.util.AdminRules;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BoostRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Admin > Queue (8h): owner verifications waiting for review, oldest first. Tap one to look at the ID photos and approve or reject it. */
public class QueueFragment extends Fragment {

    private FragmentAdminListBinding b;
    private final List<JSONObject> items = new ArrayList<>();
    private final Map<String, String> names = new HashMap<>();
    private ArrayAdapter<String> rows;
    private int token;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentAdminListBinding.inflate(inflater, container, false);
        b.title.setText(R.string.tab_queue);
        rows = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_2, android.R.id.text1, new ArrayList<String>()) {
            @NonNull @Override public View getView(int pos, @Nullable View v, @NonNull ViewGroup p) {
                View r = super.getView(pos, v, p);
                if (pos < 0 || pos >= items.size()) return r;
                JSONObject x = items.get(pos);
                ((TextView) r.findViewById(android.R.id.text1)).setText(AdminRules.who(names.get(x.optString("owner_id"))));
                ((TextView) r.findViewById(android.R.id.text2)).setText(x.optString("id_type") + "  -  " + BoostRules.day(x.optString("submitted_at", ""), ZoneId.systemDefault()));
                return r;
            }
        };
        b.list.setAdapter(rows);
        b.list.setOnItemClickListener((p, v, pos, id) -> { if (pos >= 0 && pos < items.size()) open(items.get(pos)); });
        b.swipe.setColorSchemeResources(R.color.bofo_primary);
        b.swipe.setOnRefreshListener(this::reload);
        return b.getRoot();
    }

    @Override
    public void onResume() { super.onResume(); reload(); }          // also after a decision on the review screen

    private void open(JSONObject x) {
        Bundle a = new Bundle();
        a.putString("vid", x.optString("id"));
        a.putString("ownerName", AdminRules.who(names.get(x.optString("owner_id"))));
        a.putString("idType", x.optString("id_type"));
        a.putString("submitted", x.optString("submitted_at", ""));
        a.putString("front", x.optString("id_front_url"));
        a.putString("back", x.optString("id_back_url"));
        a.putString("selfie", x.optString("selfie_url"));
        NavHostFragment.findNavController(this).navigate(R.id.verificationReviewFragment, a);
    }

    private void reload() {
        if (b == null) return;
        final int my = ++token;
        b.swipe.setRefreshing(true);
        Async.run(() -> {
            JSONArray pend = new VerificationService().pending();
            List<JSONObject> l = new ArrayList<>();
            List<String> owners = new ArrayList<>();
            for (int i = 0; i < pend.length(); i++) { l.add(pend.getJSONObject(i)); owners.add(pend.getJSONObject(i).optString("owner_id")); }
            Map<String, String> m = new HashMap<>();
            String in = AdminRules.inList(owners);
            if (!in.isEmpty()) {
                try {                                          // names are a bonus: a failure here still shows the queue
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
            b.message.setText(R.string.queue_empty);
            b.message.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }, e -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.queue_error), e));
        });
    }

    @Override
    public void onDestroyView() { token++; b = null; super.onDestroyView(); }
}
