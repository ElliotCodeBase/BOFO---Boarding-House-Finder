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
import com.bofo.finder.service.AdminUndoService;
import com.bofo.finder.service.AdminUndoService.Decision;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.AdminActivityRules;
import com.bofo.finder.util.AdminErrorRules;
import com.bofo.finder.util.AdminRules;
import com.bofo.finder.util.AdminUndoRules;
import com.bofo.finder.util.AdminUndoRules.Entry;
import com.bofo.finder.util.AiChatRules;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v2.5.0: Admin > Home > Recently decided. The last 10 decisions of the whole admin team (what, about whom or what, when, by whom) read from audit_logs
 * through the existing admin-read policy. Reasons and notes are never read. An Undo button shows only where an Undo exists and nothing newer undid it
 * (suspend, take down, dismiss, ban). The server refuses an Undo whose state has changed.
 */
public class RecentDecidedFragment extends Fragment {

    private FragmentAdminListBinding b;
    private AdminRowAdapter rows;
    private AdminListChrome chrome;
    private int token;
    private boolean haveRows, busy;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentAdminListBinding.inflate(inflater, container, false);
        b.title.setText(R.string.rd_title);
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
            List<Decision> l = new AdminUndoService().recent();
            List<String> users = new ArrayList<>(), places = new ArrayList<>();
            for (Decision d : l) {
                if ("boarding_house".equals(d.targetType)) places.add(d.entry.targetId); else users.add(d.entry.targetId);
                users.add(d.actorId);
            }
            Map<String, String> names = new HashMap<>();
            try {                                              // names are a bonus: without them the rows still say what happened
                String uin = AdminRules.inList(users);
                if (!uin.isEmpty()) {
                    JSONArray u = SupabaseClient.get().select("users", "id=in.(" + uin + ")&select=id,full_name");
                    for (int i = 0; i < u.length(); i++) names.put(u.getJSONObject(i).getString("id"), u.getJSONObject(i).optString("full_name", ""));
                }
                String pin = AdminRules.inList(places);
                if (!pin.isEmpty()) {
                    JSONArray p = SupabaseClient.get().select("bh_cards", "id=in.(" + pin + ")&select=id,name");
                    for (int i = 0; i < p.length(); i++) names.put(p.getJSONObject(i).getString("id"), p.getJSONObject(i).optString("name", ""));
                }
            } catch (Exception ignored) { /* no names */ }
            return new Object[]{l, names};
        }, (Object[] r) -> {
            if (my != token || b == null) return;
            @SuppressWarnings("unchecked") List<Decision> l = (List<Decision>) r[0];
            @SuppressWarnings("unchecked") Map<String, String> names = (Map<String, String>) r[1];
            show(l, names);
        }, e -> {
            if (my != token || b == null) return;
            chrome.failed(Connectivity.isNetworkError(e), haveRows);
        });
    }

    private void show(List<Decision> l, Map<String, String> names) {
        String[] labels = getResources().getStringArray(R.array.admin_act_labels);
        String me = AuthService.get().userId();
        List<AdminRowAdapter.Row> out = new ArrayList<>();
        List<Entry> newer = new ArrayList<>();                 // the list is newest first: everything before row i is newer than row i
        for (Decision d : l) {
            int i = AdminActivityRules.index(d.entry.action);
            String title = i >= 0 && i < labels.length ? labels[i] : getString(R.string.admin_act_other);
            String about = names.get(d.entry.targetId);
            String by = d.actorId.isEmpty() ? getString(R.string.rd_an_admin) : d.actorId.equals(me) ? getString(R.string.rd_you)
                    : nameOr(names.get(d.actorId), getString(R.string.rd_an_admin));
            String sub = (about == null || about.trim().isEmpty() ? "" : about.trim() + " - ") + AiChatRules.dateTime(d.createdAt, ZoneId.systemDefault())
                    + " - " + getString(R.string.rd_by, by);
            boolean undone = AdminUndoRules.undone(d.entry, newer);
            if (AdminUndoRules.canUndo(d.entry, newer)) {
                final Entry e = d.entry;
                final String who = about == null || about.trim().isEmpty() ? AdminRules.who(null) : about.trim();
                out.add(new AdminRowAdapter.Row(title, sub, "", AdminRowAdapter.NEUTRAL, null, getString(R.string.rd_undo_btn), () -> confirm(e, who)));
            } else {
                out.add(new AdminRowAdapter.Row(title, sub, undone ? getString(R.string.rd_undone) : "", AdminRowAdapter.NEUTRAL, null));
            }
            newer.add(d.entry);
        }
        haveRows = !out.isEmpty();
        rows.set(out);
        chrome.loaded(haveRows, R.string.rd_empty);
    }

    private static String nameOr(@Nullable String n, String fallback) { return n == null || n.trim().isEmpty() ? fallback : n.trim(); }

    private void confirm(Entry e, String who) {
        if (busy || b == null) return;
        int k = AdminUndoRules.kind(e.action);
        String msg = k == AdminUndoRules.SUSPEND ? getString(R.string.rd_undo_confirm_suspend, who)
                : k == AdminUndoRules.RELIST ? getString(R.string.rd_undo_confirm_relist)
                : k == AdminUndoRules.REOPEN ? getString(R.string.rd_undo_confirm_reopen) : getString(R.string.rd_undo_confirm_unban);
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rd_undo_confirm_title).setMessage(msg)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.rd_undo_btn, (d, w) -> run(e)).show();
    }

    private void run(Entry e) {
        if (busy) return;
        busy = true;
        Async.run(() -> { new AdminUndoService().undo(e); return Boolean.TRUE; }, ok -> {
            busy = false;
            if (b == null) return;
            ListArea.toast(this, getString(R.string.rd_undo_done));
            reload();
        }, err -> {
            busy = false;
            if (b == null) return;
            ListArea.toast(this, Connectivity.isNetworkError(err) ? getString(R.string.list_offline) : AdminErrorRules.plain(getString(R.string.rd_undo_error), err));
            reload();
        });
    }

    @Override
    public void onDestroyView() { token++; if (chrome != null) chrome.destroy(); chrome = null; b = null; super.onDestroyView(); }
}
