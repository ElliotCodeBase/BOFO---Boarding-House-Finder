package com.bofo.finder.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentBannedUsersBinding;
import com.bofo.finder.databinding.ItemBannedUserBinding;
import com.bofo.finder.model.Role;
import com.bofo.finder.service.BannedUsersService;
import com.bofo.finder.util.AdminRules;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BanRules;
import com.bofo.finder.util.BanRules.Row;
import com.bofo.finder.util.BoostRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.AdminErrorRules;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * v2.1.9 Banned users (admin only): every banned account with name, e-mail, reason, date, who banned it and an "ID blocked" tag,
 * a client-side search, and Unban (confirmation + optional note). Opened from the admin Profile ({@link #attachEntry}).
 * Students and owners never see the button, and the server functions refuse non-admins anyway.
 */
public class BannedUsersFragment extends Fragment {

    private FragmentBannedUsersBinding b;
    private final BannedUsersService svc = new BannedUsersService();
    private final List<Row> all = new ArrayList<>();
    private Adapter adapter;
    private int token;
    private boolean busy;

    /** Hook for the admin Profile: shows {@code button} only for an admin and opens this screen on tap. */
    public static void attachEntry(@NonNull Fragment host, @NonNull View button, @Nullable Role role) {
        button.setVisibility(View.GONE);
        if (!BanRules.canSee(role)) return;
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(v -> NavHostFragment.findNavController(host).navigate(R.id.bannedUsersFragment));
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentBannedUsersBinding.inflate(inflater, container, false);
        if (!BanRules.canSee(AuthRouter.role)) {                      // students and owners: nothing is built, nothing is loaded
            b.searchLayout.setVisibility(View.GONE);
            b.swipe.setEnabled(false);
            message(getString(R.string.ban_list_admins_only), false);
            return b.getRoot();
        }
        adapter = new Adapter();
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        b.swipe.setColorSchemeResources(R.color.bofo_primary);
        b.swipe.setOnRefreshListener(this::reload);
        b.retry.setOnClickListener(v -> reload());
        b.search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { render(); }
        });
        return b.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (b != null && adapter != null) reload();
    }

    private void reload() {
        if (b == null || adapter == null) return;
        final int my = ++token;
        b.swipe.setRefreshing(true);
        Async.run(svc::list, (List<Row> got) -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            all.clear(); all.addAll(got);
            render();
        }, e -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            if (!all.isEmpty()) return;                                 // keep what is shown
            message(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : AdminErrorRules.plain(getString(R.string.ban_list_error), e), true);
        });
    }

    private void render() {
        if (b == null || adapter == null) return;
        String q = b.search.getText() == null ? "" : b.search.getText().toString();
        List<Row> shown = BanRules.filter(all, q);
        adapter.submit(shown);
        if (shown.isEmpty()) {
            b.summary.setVisibility(View.GONE);
            message(getString(all.isEmpty() ? R.string.ban_list_empty : R.string.ban_list_no_match), false);
            return;
        }
        b.emptyBox.setVisibility(View.GONE);
        b.swipe.setVisibility(View.VISIBLE);
        b.summary.setText(getString(R.string.ban_list_summary, shown.size(), all.size()));
        b.summary.setVisibility(View.VISIBLE);
    }

    private void message(String text, boolean retry) {
        b.emptyText.setText(text);
        b.retry.setVisibility(retry ? View.VISIBLE : View.GONE);
        b.swipe.setVisibility(View.GONE);
        b.emptyBox.setVisibility(View.VISIBLE);
    }

    /** v2.4.12: Unban is harmless to undo (Ban again from a report), so ONE dialog: it names the person and takes an optional note. */
    private void confirmUnban(Row r) {
        String who = AdminRules.who(r.name().isEmpty() ? r.email() : r.name());
        ReasonDialog.ask(this, R.string.ban_unban_title, getString(R.string.ban_unban_confirm, who), R.string.ban_unban, false, note -> unban(r, note));
    }

    private void unban(Row r, String note) {
        if (busy) return;
        busy = true;
        Async.run(() -> { svc.unban(r.userId(), note); return Boolean.TRUE; }, ok -> {
            busy = false;
            if (b == null) return;
            ListArea.toast(this, getString(R.string.ban_unbanned));
            reload();
        }, e -> {
            busy = false;
            if (b == null) return;
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : AdminErrorRules.plain(getString(R.string.ban_unban_error), e));
            reload();
        });
    }

    @Override
    public void onDestroyView() {
        token++;
        adapter = null;
        b = null;
        super.onDestroyView();
    }

    /** One card per banned account. */
    private final class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<Row> items = new ArrayList<>();

        void submit(List<Row> r) { items.clear(); items.addAll(r); notifyDataSetChanged(); }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemBannedUserBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Row r = items.get(position);
            h.v.name.setText(AdminRules.who(r.name()));
            h.v.email.setText(r.email().isEmpty() ? "-" : r.email());
            h.v.reason.setText(getString(R.string.ban_reason, r.reason().isEmpty() ? getString(R.string.rep_no_reason) : r.reason()));
            h.v.meta.setText(getString(R.string.ban_list_meta, BoostRules.day(r.bannedAt(), ZoneId.systemDefault()),
                    r.bannedBy().isEmpty() ? "-" : r.bannedBy()));
            h.v.idTag.setVisibility(r.idBlocked() ? View.VISIBLE : View.GONE);
            h.v.unban.setOnClickListener(v -> confirmUnban(r));
        }

        @Override public int getItemCount() { return items.size(); }

        final class VH extends RecyclerView.ViewHolder {
            final ItemBannedUserBinding v;
            VH(ItemBannedUserBinding v) { super(v.getRoot()); this.v = v; }
        }
    }
}
