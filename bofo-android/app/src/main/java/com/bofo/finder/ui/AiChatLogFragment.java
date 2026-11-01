package com.bofo.finder.ui;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentAiChatLogBinding;
import com.bofo.finder.model.Role;
import com.bofo.finder.service.AiChatLogService;
import com.bofo.finder.util.AiChatRules;
import com.bofo.finder.util.AiChatRules.Range;
import com.bofo.finder.util.AiChatRules.Row;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * v2.0.91 AI Chats monitor (admin only, read-only): what users asked the BOFO assistant, newest first, 30 at a time.
 * Search (server side), date filter, "Group by user", pull to refresh, skeleton, offline / error messages, and "Delete all stored AI chats".
 * Opened from the admin Profile ({@link #attachEntry}); never reachable for students and owners (role checked here; RLS on ai_chat_log is the real protection).
 * Removed with the rest of the feature: see REMOVE_AI_CHATS.md.
 */
public class AiChatLogFragment extends Fragment {

    private FragmentAiChatLogBinding b;
    private AiChatLogAdapter adapter;
    private final AiChatLogService svc = new AiChatLogService();
    private final List<Row> rows = new ArrayList<>();
    private Range range = Range.ALL;
    private String term = "";                // what the admin typed (raw)
    private String lastTerm = "";            // the cleaned term the list on screen was loaded with
    private boolean grouped, more, moreBusy, off;
    private int offset;                      // server rows asked for so far (not the deduplicated count)
    private int token;
    private ObjectAnimator pulse;
    private final Runnable searchRun = () -> { if (b != null && !AiChatRules.searchTerm(term).equals(lastTerm)) reload(); };

    /**
     * Hook for the admin Profile: shows {@code button} only for an admin while the switch (app_flags) is on, and opens this screen on tap.
     * Any failure (offline, no row, not admin) leaves the button hidden.
     */
    public static void attachEntry(@NonNull Fragment host, @NonNull View button, @Nullable Role role) {
        button.setVisibility(View.GONE);
        if (role != Role.ADMIN) return;
        Async.run(() -> new AiChatLogService().enabled(), (Boolean on) -> {
            if (host.getView() == null || !AiChatRules.canSee(role, Boolean.TRUE.equals(on))) return;
            button.setVisibility(View.VISIBLE);
            button.setOnClickListener(v -> NavHostFragment.findNavController(host).navigate(R.id.aiChatLogFragment));
        }, e -> { /* stays hidden */ });
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentAiChatLogBinding.inflate(inflater, container, false);
        b.subtitle.setText(getString(R.string.ai_chats_sub, AiChatRules.RETENTION_DAYS));

        if (AuthRouter.role != Role.ADMIN) {            // students and owners: nothing is built, nothing is loaded
            b.swipe.setEnabled(false);
            b.searchBox.setVisibility(View.GONE); b.range.setVisibility(View.GONE); b.groupSwitch.setVisibility(View.GONE); b.wipe.setVisibility(View.GONE);
            message(getString(R.string.ai_chats_admins_only), false);
            return b.getRoot();
        }

        adapter = new AiChatLogAdapter();
        adapter.setOnMore(this::loadMore);
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        b.skeleton.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.skeleton.setAdapter(new AiChatLogAdapter.Skeleton());
        b.swipe.setColorSchemeResources(R.color.bofo_primary);
        b.swipe.setOnRefreshListener(this::reload);
        b.retry.setOnClickListener(v -> reload());

        b.search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) {
                term = s == null ? "" : s.toString();
                b.search.removeCallbacks(searchRun);
                b.search.postDelayed(searchRun, 400);                      // wait for the typing to pause
            }
        });
        b.search.setOnEditorActionListener((v, action, ev) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            b.search.removeCallbacks(searchRun);
            if (!AiChatRules.searchTerm(term).equals(lastTerm)) reload();
            return true;
        });
        b.range.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            Range r = id == R.id.range_today ? Range.TODAY : id == R.id.range_week ? Range.WEEK : Range.ALL;
            if (r != range) { range = r; reload(); }
        });
        b.groupSwitch.setOnCheckedChangeListener((v, on) -> { grouped = on; render(); });          // regroups what is loaded; no new request
        b.wipe.setOnClickListener(v -> confirmWipe());
        Connectivity.OFFLINE.observe(getViewLifecycleOwner(), o -> { if (Boolean.FALSE.equals(o) && b != null && rows.isEmpty() && b.emptyBox.getVisibility() == View.VISIBLE && !off) reload(); });
        return b.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (b != null && adapter != null) reload();
    }

    /* ---------- loading ---------- */

    private void reload() {
        if (b == null || adapter == null) return;
        final int my = ++token;
        moreBusy = false;
        lastTerm = AiChatRules.searchTerm(term);
        if (rows.isEmpty()) skeleton(true); else b.swipe.setRefreshing(true);
        final String q = AiChatRules.query(range, term, 0, System.currentTimeMillis(), ZoneId.systemDefault());
        Async.run(() -> {
            if (!svc.enabled()) return new Object[]{Boolean.FALSE, null};
            return new Object[]{Boolean.TRUE, svc.page(q)};
        }, (Object[] r) -> {
            if (my != token || b == null) return;
            skeleton(false);
            b.swipe.setRefreshing(false);
            if (!Boolean.TRUE.equals(r[0])) { off = true; rows.clear(); more = false; render(); message(getString(R.string.ai_chats_off), false); return; }
            off = false;
            @SuppressWarnings("unchecked") List<Row> got = (List<Row>) r[1];
            rows.clear(); rows.addAll(AiChatRules.firstPage(got));
            more = AiChatRules.hasMore(got.size());
            offset = rows.size();
            render();
        }, e -> {
            if (my != token || b == null) return;
            skeleton(false);
            b.swipe.setRefreshing(false);
            boolean net = Connectivity.isNetworkError(e);
            if (!rows.isEmpty()) { ListArea.toast(this, net ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.ai_chats_error), e)); return; }   // keep what is shown
            if (net) offline(); else message(ErrorText.full(getString(R.string.ai_chats_error), e), true);
        });
    }

    private void loadMore() {
        if (b == null || moreBusy || !more) return;
        final int my = ++token;
        moreBusy = true;
        render();
        final String q = AiChatRules.query(range, term, offset, System.currentTimeMillis(), ZoneId.systemDefault());
        Async.run(() -> svc.page(q), (List<Row> got) -> {
            if (my != token || b == null) return;
            moreBusy = false;
            List<Row> page = AiChatRules.firstPage(got);
            offset += page.size();
            more = AiChatRules.hasMore(got.size());
            List<Row> merged = AiChatRules.merge(rows, page);
            rows.clear(); rows.addAll(merged);
            render();
        }, e -> {
            if (my != token || b == null) return;
            moreBusy = false;
            render();
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.ai_chats_error), e));
        });
    }

    /* ---------- drawing ---------- */

    private void render() {
        if (b == null || adapter == null) return;
        adapter.submit(rows, grouped, more, moreBusy);
        if (rows.isEmpty()) {
            boolean filtered = !AiChatRules.searchTerm(term).isEmpty() || range != Range.ALL;
            if (!off) message(getString(filtered ? R.string.ai_chats_empty_filtered : R.string.ai_chats_empty), false);
        } else {
            b.emptyBox.setVisibility(View.GONE);
            b.swipe.setVisibility(View.VISIBLE);
        }
    }

    private void message(String text, boolean retry) {
        b.emptyIcon.setImageResource(R.drawable.mascot_happy);
        b.emptyIcon.setImageTintList(null);
        b.emptyIcon.setPadding(0, 0, 0, 0);
        b.emptyTitle.setVisibility(View.GONE);
        b.emptyText.setText(text);
        b.retry.setVisibility(retry ? View.VISIBLE : View.GONE);
        b.swipe.setVisibility(View.GONE);
        b.emptyBox.setVisibility(View.VISIBLE);
    }

    /** Same screen as the other lists: wifi-off icon, title, one line of help, Try again (the list reloads by itself when the phone is back online). */
    private void offline() {
        float dn = b.emptyIcon.getResources().getDisplayMetrics().density;
        int pad = Math.round(14 * dn);
        b.emptyIcon.setImageResource(R.drawable.ic_wifi_off);
        b.emptyIcon.setImageTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bofo_muted)));
        b.emptyIcon.setPadding(pad, pad, pad, pad);
        b.emptyTitle.setText(R.string.net_offline_title);
        b.emptyTitle.setVisibility(View.VISIBLE);
        b.emptyText.setText(R.string.net_offline_body);
        b.retry.setVisibility(View.VISIBLE);
        b.swipe.setVisibility(View.GONE);
        b.emptyBox.setVisibility(View.VISIBLE);
    }

    private void skeleton(boolean on) {
        if (b == null) return;
        if (on) {
            b.emptyBox.setVisibility(View.GONE);
            b.swipe.setVisibility(View.GONE);
            b.skeleton.setVisibility(View.VISIBLE);
            if (pulse == null) {
                pulse = ObjectAnimator.ofFloat(b.skeleton, View.ALPHA, 1f, 0.45f);
                pulse.setDuration(900);
                pulse.setRepeatMode(ObjectAnimator.REVERSE);
                pulse.setRepeatCount(ObjectAnimator.INFINITE);
            }
            if (!pulse.isStarted()) pulse.start();
        } else {
            b.skeleton.setVisibility(View.GONE);
            if (pulse != null) { pulse.cancel(); b.skeleton.setAlpha(1f); }
            if (b.emptyBox.getVisibility() != View.VISIBLE) b.swipe.setVisibility(View.VISIBLE);
        }
    }

    /* ---------- delete everything ---------- */

    private void confirmWipe() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ai_chats_wipe_title)
                .setMessage(R.string.ai_chats_wipe_msg)
                .setNegativeButton(R.string.ai_chats_cancel, null)
                .setPositiveButton(R.string.ai_chats_wipe_do, (d, w) -> wipe())
                .show();
    }

    private void wipe() {
        if (b == null) return;
        b.wipe.setEnabled(false);
        Async.run(() -> svc.wipeAll(), (Integer n) -> {
            if (b == null) return;
            b.wipe.setEnabled(true);
            ListArea.toast(this, getString(R.string.ai_chats_wipe_done, n == null ? 0 : n));
            rows.clear(); more = false;
            reload();
        }, e -> {
            if (b == null) return;
            b.wipe.setEnabled(true);
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.ai_chats_wipe_error), e));
        });
    }

    @Override
    public void onDestroyView() {
        token++;
        if (b != null) { b.search.removeCallbacks(searchRun); if (pulse != null) { pulse.cancel(); pulse = null; } }
        adapter = null;
        b = null;
        super.onDestroyView();
    }
}
