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
import com.bofo.finder.databinding.FragmentOwnerBoardingBinding;
import com.bofo.finder.databinding.ItemConversationBinding;
import com.bofo.finder.model.BhGroup;
import com.bofo.finder.service.BhGroupService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BoardingRules;
import com.bofo.finder.util.ChatRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * v2.4.8: this is the owner's "Groups" tab in the bottom bar (it used to open from Profile). The header icon opens the direct chats.
 * v2.2.0 step 4: the owner's Boarding groups, one row per listed house (name, members, pinned, last message, unread). A tap opens the group
 * chat ({@link BoardingChatFragment} with myRole = owner), Announce opens the form for one or several houses. Reloaded every time the screen
 * comes back, so unread counts are fresh after reading a chat.
 */
public class OwnerBoardingFragment extends Fragment {

    private FragmentOwnerBoardingBinding b;
    private final BhGroupService svc = new BhGroupService();
    private final Adapter adapter = new Adapter();
    private int token;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentOwnerBoardingBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        b.back.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        b.back.setVisibility(getParentFragment() instanceof ShellFragment ? View.GONE : View.VISIBLE);      // v2.4.8: now a bottom-bar tab, so no Back arrow
        b.messages.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.ownerChatsFragment));
        b.announce.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.announceFragment));
        b.retry.setOnClickListener(v -> load());
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        adapter.listener = g -> {
            Bundle a = BoardingChatFragment.args(g.bhId(), g.bhName(), g.memberCount(), GroupMessageRoles.OWNER, true, false);
            NavHostFragment.findNavController(this).navigate(R.id.boardingChatFragment, a);
        };
    }

    @Override
    public void onStart() {
        super.onStart();
        load();
    }

    private void load() {
        final int my = ++token;
        if (adapter.getItemCount() == 0) { b.loading.setVisibility(View.VISIBLE); b.messageBox.setVisibility(View.GONE); }
        Async.run(() -> svc.myGroups(), (List<BhGroup> groups) -> {
            if (my != token || b == null) return;
            List<BhGroup> mine = new ArrayList<>();
            for (BhGroup g : groups) if (GroupMessageRoles.OWNER.equals(g.myRole())) mine.add(g);
            b.loading.setVisibility(View.GONE);
            adapter.submit(mine);
            b.announce.setEnabled(!mine.isEmpty());
            if (mine.isEmpty()) message(getString(R.string.owner_boarding_empty_title), getString(R.string.owner_boarding_empty_sub), false);
            else b.messageBox.setVisibility(View.GONE);
        }, e -> {
            if (my != token || b == null) return;
            b.loading.setVisibility(View.GONE);
            if (adapter.getItemCount() > 0) { ListArea.toast(this, getString(R.string.owner_boarding_load_error)); return; }
            message("", Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.owner_boarding_load_error), e), true);
        });
    }

    private void message(String title, String text, boolean retry) {
        b.messageTitle.setText(title);
        b.messageTitle.setVisibility(title.isEmpty() ? View.GONE : View.VISIBLE);
        b.messageText.setText(text);
        b.retry.setVisibility(retry ? View.VISIBLE : View.GONE);
        b.messageBox.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        token++;
        b = null;
        super.onDestroyView();
    }

    /** The two role words used by the group chat (kept in one place so this screen reads like the rest). */
    private static final class GroupMessageRoles { static final String OWNER = com.bofo.finder.model.GroupMessage.OWNER; }

    private static final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        interface Listener { void open(BhGroup g); }
        Listener listener;
        private List<BhGroup> items = new ArrayList<>();

        void submit(List<BhGroup> l) { items = l; notifyDataSetChanged(); }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup p, int type) {
            return new Holder(ItemConversationBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int pos) {
            BhGroup g = items.get(pos);
            android.content.Context c = h.v.getRoot().getContext();
            h.v.initial.setText(BoardingRules.initial(g.bhName()));
            h.v.name.setText(g.bhName());
            String members = c.getResources().getQuantityString(R.plurals.boarding_members, g.memberCount(), g.memberCount());
            h.v.place.setText(g.pinnedCount() > 0
                    ? c.getString(R.string.owner_boarding_meta, members, c.getResources().getQuantityString(R.plurals.boarding_pinned_count, g.pinnedCount(), g.pinnedCount()))
                    : members);
            String prev = BoardingRules.preview(g.lastSender(), g.lastMessage());
            h.v.last.setText(prev.isEmpty() ? c.getString(R.string.owner_boarding_no_messages) : prev);
            h.v.time.setText(g.lastAt().isEmpty() ? "" : ChatRules.timeLabel(g.lastAt(), System.currentTimeMillis(), ZoneId.systemDefault()));
            h.v.badge.setVisibility(g.unread() > 0 && !g.muted() ? View.VISIBLE : View.GONE);
            h.v.badge.setText(g.unread() > 99 ? "99+" : String.valueOf(g.unread()));
            h.v.getRoot().setOnClickListener(v -> { if (listener != null) listener.open(g); });
        }

        @Override public int getItemCount() { return items.size(); }

        static final class Holder extends RecyclerView.ViewHolder {
            final ItemConversationBinding v;
            Holder(ItemConversationBinding v) { super(v.getRoot()); this.v = v; }
        }
    }
}
