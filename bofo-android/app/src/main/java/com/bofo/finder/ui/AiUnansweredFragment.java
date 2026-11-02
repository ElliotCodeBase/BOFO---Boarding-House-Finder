package com.bofo.finder.ui;

import android.os.Bundle;
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
import com.bofo.finder.databinding.FragmentAiUnansweredBinding;
import com.bofo.finder.databinding.ItemAiUnansweredBinding;
import com.bofo.finder.model.Role;
import com.bofo.finder.service.AiUnansweredService;
import com.bofo.finder.util.AiChatRules;
import com.bofo.finder.util.AiUnansweredRules;
import com.bofo.finder.util.AiUnansweredRules.Reason;
import com.bofo.finder.util.AiUnansweredRules.Row;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v2.1.3 "Needs attention" (admin only, read-only): the questions BOFO could not answer well (no smart reply, asked back, not found, off topic)
 * and the thumbs-down votes, newest first, with a filter by reason. Opened from the admin Profile ({@link #attachEntry}).
 * Students and owners never see the button, and RLS on ai_unanswered returns them 0 rows anyway.
 */
public class AiUnansweredFragment extends Fragment {

    private FragmentAiUnansweredBinding b;
    private final AiUnansweredService svc = new AiUnansweredService();
    private final List<Row> rows = new ArrayList<>();
    private Adapter adapter;
    private Reason reason = Reason.ALL;
    private int token;

    /** Hook for the admin Profile: shows {@code button} only for an admin and opens this screen on tap. */
    public static void attachEntry(@NonNull Fragment host, @NonNull View button, @Nullable Role role) {
        button.setVisibility(View.GONE);
        if (!AiUnansweredRules.canSee(role)) return;
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(v -> NavHostFragment.findNavController(host).navigate(R.id.aiUnansweredFragment));
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentAiUnansweredBinding.inflate(inflater, container, false);
        b.subtitle.setText(getString(R.string.ai_un_sub, AiUnansweredRules.LIMIT));

        if (!AiUnansweredRules.canSee(AuthRouter.role)) {           // students and owners: nothing is built, nothing is loaded
            b.filtersScroll.setVisibility(View.GONE);
            b.swipe.setEnabled(false);
            message(getString(R.string.ai_un_admins_only), false);
            return b.getRoot();
        }

        adapter = new Adapter();
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        b.swipe.setColorSchemeResources(R.color.bofo_primary);
        b.swipe.setOnRefreshListener(this::reload);
        b.retry.setOnClickListener(v -> reload());
        b.filters.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            Reason r = id == R.id.f_degraded ? Reason.DEGRADED : id == R.id.f_clarify ? Reason.CLARIFY : id == R.id.f_not_found ? Reason.NOT_FOUND
                    : id == R.id.f_off_topic ? Reason.OFF_TOPIC : id == R.id.f_thumbs ? Reason.THUMBS_DOWN : Reason.ALL;
            if (r != reason) { reason = r; reload(); }
        });
        Connectivity.OFFLINE.observe(getViewLifecycleOwner(), o -> { if (Boolean.FALSE.equals(o) && b != null && rows.isEmpty() && b.emptyBox.getVisibility() == View.VISIBLE && b.retry.getVisibility() == View.VISIBLE) reload(); });
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
        final Reason asked = reason;
        b.swipe.setRefreshing(true);
        Async.run(() -> svc.latest(asked), (List<Row> got) -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            rows.clear(); rows.addAll(AiUnansweredRules.known(got));
            render();
        }, e -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            if (!rows.isEmpty()) return;                                     // keep what is shown
            message(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.ai_un_error), e), true);
        });
    }

    private void render() {
        adapter.submit(rows);
        if (rows.isEmpty()) {
            b.summary.setVisibility(View.GONE);
            message(getString(reason == Reason.ALL ? R.string.ai_un_empty : R.string.ai_un_empty_filtered), false);
            return;
        }
        b.emptyBox.setVisibility(View.GONE);
        b.swipe.setVisibility(View.VISIBLE);
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Reason, Integer> en : AiUnansweredRules.counts(rows).entrySet())
            if (en.getValue() > 0) parts.add(label(en.getKey()) + " " + en.getValue());
        b.summary.setText(getString(R.string.ai_un_summary, rows.size(), String.join(", ", parts)));
        b.summary.setVisibility(View.VISIBLE);
    }

    private void message(String text, boolean retry) {
        b.emptyText.setText(text);
        b.retry.setVisibility(retry ? View.VISIBLE : View.GONE);
        b.swipe.setVisibility(View.GONE);
        b.emptyBox.setVisibility(View.VISIBLE);
    }

    private String label(Reason r) {
        return getString(switch (r) {
            case DEGRADED -> R.string.ai_un_degraded;
            case CLARIFY -> R.string.ai_un_clarify;
            case NOT_FOUND -> R.string.ai_un_not_found;
            case OFF_TOPIC -> R.string.ai_un_off_topic;
            case THUMBS_DOWN -> R.string.ai_un_thumbs;
            default -> R.string.ai_un_unknown;
        });
    }

    @Override
    public void onDestroyView() {
        token++;
        adapter = null;
        b = null;
        super.onDestroyView();
    }

    /** One card per row. */
    private final class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<Row> items = new ArrayList<>();

        void submit(List<Row> r) { items.clear(); items.addAll(r); notifyDataSetChanged(); }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemAiUnansweredBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Row r = items.get(position);
            h.v.reason.setText(label(r.kind()));
            h.v.ago.setText(AiChatRules.ago(r.createdAt(), System.currentTimeMillis()));
            h.v.message.setText(r.hasText() ? r.message() : getString(r.kind() == Reason.THUMBS_DOWN ? R.string.ai_un_thumb_text : R.string.ai_un_no_text));
            h.v.message.setTextColor(requireContext().getColor(r.hasText() ? R.color.bofo_text : R.color.bofo_muted));
            String intent = r.intent().isEmpty() ? "-" : r.intent(), lang = r.lang().isEmpty() ? "-" : r.lang();
            h.v.meta.setText(getString(R.string.ai_un_meta, intent, lang));
        }

        @Override public int getItemCount() { return items.size(); }

        final class VH extends RecyclerView.ViewHolder {
            final ItemAiUnansweredBinding v;
            VH(ItemAiUnansweredBinding v) { super(v.getRoot()); this.v = v; }
        }
    }
}
