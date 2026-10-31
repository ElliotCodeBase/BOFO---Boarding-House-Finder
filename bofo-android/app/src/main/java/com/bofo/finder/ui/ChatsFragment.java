package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentChatsBinding;
import com.bofo.finder.databinding.ItemConversationBinding;
import com.bofo.finder.model.Conversation;
import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.ChatRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Chats tab: the inbox (newest first), unread badges, tap to open, long-press to delete. Loads on every resume (cheap, and the list is also kept on the device). */
public class ChatsFragment extends Fragment {

    private FragmentChatsBinding b;
    private Adapter adapter;
    private int token;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentChatsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        adapter = new Adapter(new Adapter.Listener() {
            @Override public void open(Conversation c) { ChatFragment.open(ChatsFragment.this, c.id(), ChatRules.title(c.otherName(), c.bhName()), c.bhName()); }
            @Override public void ask(Conversation c) { confirmDelete(c); }
        });
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        b.swipe.setColorSchemeResources(R.color.bofo_primary);
        b.swipe.setOnRefreshListener(this::reload);
        b.retry.setOnClickListener(v -> reload());
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();                  // also picks up the unread counts after coming back from a chat
    }

    private void reload() {
        if (b == null) return;
        final int my = ++token;
        if (adapter.getItemCount() == 0) { showSkeleton(true); b.emptyBox.setVisibility(View.GONE); }
        Async.run(() -> new ChatService().inbox(), (List<Conversation> l) -> {
            if (my != token || b == null) return;
            showSkeleton(false);
            b.swipe.setRefreshing(false);
            if (l.isEmpty()) message(getString(R.string.chats_empty_sub), false);
            else { b.emptyBox.setVisibility(View.GONE); b.list.setVisibility(View.VISIBLE); adapter.submitList(l); }
        }, e -> {
            if (my != token || b == null) return;
            showSkeleton(false);
            b.swipe.setRefreshing(false);
            if (adapter.getItemCount() > 0) { ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : getString(R.string.chats_error)); return; }
            message(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.chats_error), e), true);
        });
    }

    private android.animation.ObjectAnimator pulse;

    /** Six grey rows shaped like the chat rows, pulsing like the Home skeleton (900 ms, 1 to 0.45). */
    private void showSkeleton(boolean on) {
        if (b == null) return;
        b.skeleton.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            b.list.setVisibility(View.GONE);
            if (pulse == null) {
                pulse = android.animation.ObjectAnimator.ofFloat(b.skeleton, View.ALPHA, 1f, 0.45f);
                pulse.setDuration(900);
                pulse.setInterpolator(new android.view.animation.PathInterpolator(0.42f, 0f, 0.58f, 1f));
                pulse.setRepeatMode(android.animation.ObjectAnimator.REVERSE);
                pulse.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            }
            if (!pulse.isStarted()) pulse.start();
        } else if (pulse != null) {
            pulse.cancel();
            b.skeleton.setAlpha(1f);
        }
    }

    private void message(String text, boolean retry) {
        adapter.submitList(Collections.<Conversation>emptyList());
        b.list.setVisibility(View.GONE);
        b.emptyBox.setVisibility(View.VISIBLE);
        b.emptyText.setText(text);
        b.emptyTitle.setVisibility(retry ? View.GONE : View.VISIBLE);
        b.retry.setVisibility(retry ? View.VISIBLE : View.GONE);
    }

    private void confirmDelete(Conversation c) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.chats_delete_title)
                .setMessage(R.string.chats_delete_msg)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.chats_delete, (d, w) -> Async.run(() -> { new ChatService().deleteConversation(c.id()); return Boolean.TRUE; },
                        ok -> reload(),
                        e -> ListArea.toast(this, ErrorText.full(getString(R.string.chats_error), e))))
                .show();
    }

    @Override
    public void onDestroyView() {
        token++;
        if (pulse != null) { pulse.cancel(); pulse = null; }
        b = null;
        super.onDestroyView();
    }

    // ---- adapter ---------------------------------------------------------------------------------------------

    static final class Adapter extends ListAdapter<Conversation, Adapter.Holder> {
        interface Listener { void open(Conversation c); void ask(Conversation c); }
        private final Listener listener;

        Adapter(Listener l) {
            super(new DiffUtil.ItemCallback<Conversation>() {
                @Override public boolean areItemsTheSame(@NonNull Conversation a, @NonNull Conversation c) { return a.id().equals(c.id()); }
                @Override public boolean areContentsTheSame(@NonNull Conversation a, @NonNull Conversation c) { return a.equals(c); }
            });
            this.listener = l;
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup p, int type) {
            return new Holder(ItemConversationBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int i) {
            Conversation c = getItem(i);
            String name = ChatRules.title(c.otherName(), c.bhName());
            h.v.initial.setText(name.substring(0, 1).toUpperCase());
            h.v.name.setText(name);
            h.v.place.setText(c.bhName());
            h.v.place.setVisibility(c.bhName().isEmpty() ? View.GONE : View.VISIBLE);
            h.v.last.setText(ChatRules.preview(c.lastMessage(), 80));
            h.v.time.setText(ChatRules.timeLabel(c.lastAt(), System.currentTimeMillis(), ZoneId.systemDefault()));
            String badge = ChatRules.badge(c.unread());
            TextView t = h.v.badge;
            t.setText(badge);
            t.setVisibility(badge.isEmpty() ? View.GONE : View.VISIBLE);
            h.v.name.setTextColor(h.v.getRoot().getResources().getColor(c.unread() > 0 ? R.color.bofo_ink : R.color.bofo_text, null));
            h.v.getRoot().setOnClickListener(v -> listener.open(c));
            h.v.getRoot().setOnLongClickListener(v -> { listener.ask(c); return true; });
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ItemConversationBinding v;
            Holder(ItemConversationBinding v) { super(v.getRoot()); this.v = v; }
        }
    }
}
