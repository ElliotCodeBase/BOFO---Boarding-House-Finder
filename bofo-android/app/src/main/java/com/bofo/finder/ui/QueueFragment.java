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
import com.bofo.finder.util.AdminListRules;
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

/** Admin > Queue: owner ID requests. Chips Waiting (oldest first) / Done / All, search by name, status in words. Tap a row to review it; long-press for Open / Reject. */
public class QueueFragment extends Fragment {

    private FragmentAdminListBinding b;
    private final List<JSONObject> all = new ArrayList<>(), items = new ArrayList<>();
    private final Map<String, String> names = new HashMap<>();
    private AdminRowAdapter rows;
    private AdminListChrome chrome;
    private int token;
    private boolean busy;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentAdminListBinding.inflate(inflater, container, false);
        b.title.setText(R.string.tab_queue);
        rows = new AdminRowAdapter(requireContext());
        b.list.setAdapter(rows);
        int start = getArguments() == null ? AdminListRules.WAITING : getArguments().getInt("filter", AdminListRules.WAITING);     // Admin Home preselects the chip
        chrome = new AdminListChrome(b, start, new AdminListChrome.Host() {
            @Override public void onFilter(int chip) { reload(); }
            @Override public void onSearch(String query) { applyFilter(); }
            @Override public void onRetry() { reload(); }
        });
        b.list.setOnItemClickListener((p, v, pos, id) -> { if (pos >= 0 && pos < items.size()) open(items.get(pos)); });
        b.list.setOnItemLongClickListener((p, v, pos, id) -> { if (pos >= 0 && pos < items.size()) quick(items.get(pos)); return true; });
        b.swipe.setColorSchemeResources(R.color.bofo_primary);
        b.swipe.setOnRefreshListener(this::reload);
        return b.getRoot();
    }

    @Override
    public void onResume() { super.onResume(); reload(); }          // also after a decision on the review screen

    /**
     * v2.4.12: long-press = Open / Reject. There is no Approve here on purpose: approving needs the ID number typed from the photo,
     * so Approve stays on the review screen (Open).
     */
    private void quick(JSONObject x) {
        String who = AdminRules.who(names.get(x.optString("owner_id")));
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext()).setTitle(who)
                .setItems(new String[]{getString(R.string.act_open), getString(R.string.act_reject_quick)}, (d, which) -> {
                    if (which == 0) open(x); else RejectDialog.ask(this, r -> reject(x, r));
                }).show();
    }

    private void reject(JSONObject x, String reason) {
        if (busy) return;
        busy = true;
        Async.run(() -> { new VerificationService().decide(x.optString("id"), false, reason); return Boolean.TRUE; }, ok -> {
            busy = false;
            ListArea.toast(this, getString(R.string.review_done_rejected));
            reload();
        }, e -> {
            busy = false;
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.review_error), e));
            reload();
        });
    }

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
        final int chip = chrome.chip();
        chrome.loading(!items.isEmpty());
        Async.run(() -> {
            JSONArray pend = new VerificationService().list(chip);
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
            @SuppressWarnings("unchecked") List<JSONObject> l = (List<JSONObject>) r[0];
            @SuppressWarnings("unchecked") Map<String, String> m = (Map<String, String>) r[1];
            all.clear(); all.addAll(l);
            names.clear(); names.putAll(m);
            applyFilter();
        }, e -> {
            if (my != token || b == null) return;
            chrome.failed(Connectivity.isNetworkError(e), !items.isEmpty());
        });
    }

    /** Search + rows. The status is a word on a coloured pill (Waiting / Approved / Rejected). */
    private void applyFilter() {
        if (b == null) return;
        items.clear();
        List<AdminRowAdapter.Row> out = new ArrayList<>();
        for (JSONObject x : all) {
            String name = AdminRules.who(names.get(x.optString("owner_id")));
            if (!AdminListRules.matches(chrome.query(), name, x.optString("id_type"))) continue;
            items.add(x);
            String st = x.optString("status");
            String sent = BoostRules.day(x.optString("submitted_at", ""), ZoneId.systemDefault());
            String wait = "pending".equals(st) ? AdminRowAdapter.waiting(requireContext(), x.optString("submitted_at", "")) : "";
            String sub = wait.isEmpty() ? getString(R.string.admin_row_sub_queue, x.optString("id_type"), sent) : wait + " - " + x.optString("id_type");
            out.add(new AdminRowAdapter.Row(name, sub,
                    getString("verified".equals(st) ? R.string.admin_st_approved : "rejected".equals(st) ? R.string.admin_st_rejected : R.string.admin_st_waiting),
                    "verified".equals(st) ? AdminRowAdapter.OK : "rejected".equals(st) ? AdminRowAdapter.BAD : AdminRowAdapter.WAITING, null));
        }
        rows.set(out);
        chrome.loaded(!items.isEmpty(), chrome.chip() == AdminListRules.WAITING ? R.string.queue_empty : R.string.admin_empty_done);
    }

    @Override
    public void onDestroyView() { token++; if (chrome != null) chrome.destroy(); chrome = null; b = null; super.onDestroyView(); }
}
