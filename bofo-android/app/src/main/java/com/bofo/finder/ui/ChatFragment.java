package com.bofo.finder.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.bofo.finder.databinding.FragmentChatBinding;
import com.bofo.finder.databinding.ItemMsgMineBinding;
import com.bofo.finder.databinding.ItemMsgTheirsBinding;
import com.bofo.finder.model.Message;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.ChatRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.Outbox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One chat. Messages load newest-first (30 per page, older pages when you scroll to the top). The poll loop is a Handler.postDelayed chain:
 * 3 s, slower after quiet ticks, never more than 12 s; it starts in onResume and is cut in onPause (no background polling).
 * Every message goes through the Outbox first (saved on the device), so offline messages are sent by themselves later.
 */
public class ChatFragment extends Fragment {

    public static final String ARG_CONV = "convId", ARG_TITLE = "title", ARG_PLACE = "place";

    /** Opens a chat on top of the shell (Back returns to the same tab). */
    public static void open(Fragment from, String convId, String title, String place) {
        Bundle a = new Bundle();
        a.putString(ARG_CONV, convId);
        a.putString(ARG_TITLE, title);
        a.putString(ARG_PLACE, place == null ? "" : place);
        NavHostFragment.findNavController(from).navigate(R.id.chatFragment, a);
    }

    private FragmentChatBinding b;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ChatService chat = new ChatService();
    private Adapter adapter;
    private LinearLayoutManager lm;
    private String convId, me;
    private final List<ChatRules.Item> server = new ArrayList<>();        // what the server has told us (oldest first after merge)
    private final List<ChatRules.Item> failed = new ArrayList<>();        // messages the server refused (kept on screen, tap to send again)
    private List<ChatRules.Item> queued = new ArrayList<>();             // waiting in the Outbox
    private boolean loaded, resumed, polling, loadingOlder, noMoreOlder;
    private int quiet, token;

    private final Runnable pollTask = this::tick;

    private final Outbox.Listener outboxListener = new Outbox.Listener() {          // called from a background thread
        @Override public void sent(String id, Message m) {
            Async.post(() -> {
                if (b == null || !convId.equals(m.conversationId())) return;
                server.add(ChatRules.fromMessage(m, me));
                syncQueued();
                render(true);
            });
        }
        @Override public void failed(String id, Throwable why) {
            Async.post(() -> {
                if (b == null) return;
                for (ChatRules.Item q : queued) if (q.id.equals(id)) { failed.add(q.withState(ChatRules.State.FAILED)); break; }
                syncQueued();
                render(true);
                ListArea.toast(ChatFragment.this, getString(R.string.chat_send_failed, why.getMessage() == null ? why.getClass().getSimpleName() : why.getMessage()));
            });
        }
        @Override public void waiting(String id) { /* still queued: the bubble already shows the clock */ }
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentChatBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        Bundle args = requireArguments();
        convId = args.getString(ARG_CONV, "");
        me = AuthService.get().userId();
        b.title.setText(args.getString(ARG_TITLE, ""));
        String place = args.getString(ARG_PLACE, "");
        String who = args.getString(ARG_TITLE, "").trim();
        b.avatar.setText(who.isEmpty() ? "?" : who.substring(0, 1).toUpperCase());
        b.back.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        adapter = new Adapter(new Adapter.Listener() {
            @Override public void tap(ChatRules.Item i) { if (i.state == ChatRules.State.FAILED) resend(i); }
            @Override public void hold(ChatRules.Item i) { options(i); }
        });
        lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);                                  // short chats sit at the bottom, like every chat app
        b.list.setLayoutManager(lm);
        b.list.setAdapter(adapter);
        b.list.setItemAnimator(null);
        b.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy < 0 && lm.findFirstVisibleItemPosition() <= 1) loadOlder();
            }
        });
        b.send.setOnClickListener(v -> send());
        b.input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int n, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int n, int d) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                if (b == null) return;
                boolean has = e.toString().trim().length() > 0;      // purple only when there is text
                b.send.setEnabled(has);
                b.send.setBackgroundResource(has ? R.drawable.bg_send_button : R.drawable.bg_send_off);
            }
        });
        View.OnClickListener quick = v -> { b.input.setText(((TextView) v).getText()); send(); };
        b.quick1.setOnClickListener(quick);
        b.quick2.setOnClickListener(quick);
        b.retry.setOnClickListener(v -> load());
        Connectivity.OFFLINE.observe(getViewLifecycleOwner(), off -> {
            if (b == null) return;
            b.offline.setVisibility(off != null && off ? View.VISIBLE : View.GONE);
            if (off != null && !off) Outbox.flushAsync();           // connection is back: send what is waiting
        });
        load();
    }

    // ---- loading ---------------------------------------------------------------------------------------------

    private void load() {
        final int my = ++token;
        b.loading.setVisibility(View.VISIBLE);
        b.messageBox.setVisibility(View.GONE);
        Async.run(() -> chat.page(convId, null), (List<Message> page) -> {
            if (my != token || b == null) return;
            server.clear();
            for (int i = page.size() - 1; i >= 0; i--) server.add(ChatRules.fromMessage(page.get(i), me));
            noMoreOlder = page.size() < ChatService.PAGE;
            loaded = true;
            b.loading.setVisibility(View.GONE);
            syncQueued();
            render(true);
            markRead();
            if (resumed) schedule(ChatRules.POLL_MIN_MS);
        }, e -> {
            if (my != token || b == null) return;
            b.loading.setVisibility(View.GONE);
            b.helloMascot.setVisibility(View.GONE);
            b.helloTitle.setVisibility(View.GONE);
            b.messageText.setText(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.chat_error), e));
            b.retry.setVisibility(View.VISIBLE);
            b.messageBox.setVisibility(View.VISIBLE);
        });
    }

    private void loadOlder() {
        if (!loaded || loadingOlder || noMoreOlder) return;
        String before = ChatRules.oldestServerTime(server);
        if (before == null) return;
        loadingOlder = true;
        final int my = token;
        Async.run(() -> chat.page(convId, before), (List<Message> page) -> {
            loadingOlder = false;
            if (my != token || b == null) return;
            if (page.size() < ChatService.PAGE) noMoreOlder = true;
            if (page.isEmpty()) return;
            // keep the bubble the person is looking at where it is
            int first = lm.findFirstVisibleItemPosition();
            ChatRules.Item anchor = first >= 0 && first < adapter.getItemCount() ? adapter.getCurrentList().get(first) : null;
            View v = first >= 0 ? lm.findViewByPosition(first) : null;
            int offset = v == null ? 0 : v.getTop();
            for (Message m : page) server.add(ChatRules.fromMessage(m, me));
            List<ChatRules.Item> all = ChatRules.merge(server, allQueued());
            final ChatRules.Item keep = anchor;
            adapter.submitList(all, () -> {
                if (b == null || keep == null) return;
                for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(keep.id)) { lm.scrollToPositionWithOffset(i, offset); break; }
            });
        }, e -> {
            loadingOlder = false;
            if (my == token && b != null) ListArea.toast(this, getString(R.string.chat_older_failed));
        });
    }

    private void markRead() {
        Async.bg(() -> { try { chat.markRead(convId); } catch (Exception ignored) { /* offline: tried again on the next incoming message */ } });
    }

    // ---- polling (Handler.postDelayed, tied to onResume / onPause) --------------------------------------------

    @Override
    public void onResume() {
        super.onResume();
        resumed = true;
        Outbox.LISTENERS.add(outboxListener);
        if (loaded) {
            syncQueued();
            render(false);
            quiet = 0;
            schedule(500);                 // catch up at once, then back to the normal rhythm
        }
        Outbox.flushAsync();
    }

    @Override
    public void onPause() {
        resumed = false;
        polling = false;
        handler.removeCallbacks(pollTask);
        Outbox.LISTENERS.remove(outboxListener);
        super.onPause();
    }

    private void schedule(long ms) {
        handler.removeCallbacks(pollTask);
        polling = true;
        handler.postDelayed(pollTask, ms);
    }

    private void tick() {
        if (!resumed || b == null || !polling) return;
        Outbox.flushAsync();
        final int my = token;
        final String after = ChatRules.newestServerTime(server);
        boolean askRead = false;
        for (ChatRules.Item i : server) if (i.mine && i.state == ChatRules.State.SENT) { askRead = true; break; }
        final boolean wantRead = askRead;
        Async.run(() -> {
            List<Message> fresh = after == null ? chat.page(convId, null) : chat.fetchNewer(convId, after);
            List<Message> read = wantRead ? chat.fetchMyRead(convId, me) : Collections.<Message>emptyList();
            return new Object[]{fresh, read};
        }, (Object[] r) -> {
            if (my != token || b == null) return;
            @SuppressWarnings("unchecked") List<Message> fresh = (List<Message>) r[0];
            @SuppressWarnings("unchecked") List<Message> read = (List<Message>) r[1];
            boolean changed = false, incoming = false, mineNew = false;
            if (after == null) { Collections.reverse(fresh); }               // first page arrives newest-first
            for (Message m : fresh) {
                ChatRules.Item it = ChatRules.fromMessage(m, me);
                boolean known = false;
                for (ChatRules.Item s : server) if (s.id.equals(it.id)) { known = true; break; }
                if (known) continue;
                server.add(it); changed = true;
                if (it.mine) mineNew = true; else incoming = true;
            }
            for (Message m : read) {
                for (int i = 0; i < server.size(); i++) {
                    ChatRules.Item s = server.get(i);
                    if (s.id.equals(m.id()) && s.state != ChatRules.State.READ) { server.set(i, s.withState(ChatRules.State.READ)); changed = true; }
                }
            }
            syncQueued();
            if (changed) render(mineNew || nearBottom());
            if (incoming) markRead();
            quiet = changed ? 0 : quiet + 1;
            if (resumed) schedule(ChatRules.nextDelayMs(quiet));
        }, e -> {
            if (my != token || b == null) return;
            quiet++;                                                          // offline or server busy: slow down, never stop
            if (resumed) schedule(ChatRules.nextDelayMs(quiet));
        });
    }

    // ---- sending ---------------------------------------------------------------------------------------------

    private void send() {
        String text = ChatRules.outgoing(b.input.getText().toString(), ChatService.MAX_LEN);
        if (text.isEmpty() || !loaded) return;
        try {
            Outbox.add(convId, text);
        } catch (Exception bad) {
            ListArea.toast(this, getString(R.string.chat_send_failed, String.valueOf(bad.getMessage())));
            return;
        }
        b.input.setText("");
        quiet = 0;
        syncQueued();
        render(true);
        Outbox.flushAsync();
        if (resumed) schedule(ChatRules.POLL_MIN_MS);
    }

    private void resend(ChatRules.Item i) {
        failed.remove(i);
        try { Outbox.add(convId, i.body); } catch (Exception bad) { failed.add(i); return; }
        syncQueued();
        render(true);
        Outbox.flushAsync();
    }

    // ---- long press: copy / delete ---------------------------------------------------------------------------

    private void options(ChatRules.Item i) {
        if (i.state == ChatRules.State.PENDING) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        labels.add(getString(R.string.chat_copy));
        acts.add(() -> {
            ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) { cm.setPrimaryClip(ClipData.newPlainText("message", i.body)); ListArea.toast(this, getString(R.string.chat_copied)); }
        });
        if (i.state == ChatRules.State.FAILED) {
            labels.add(getString(R.string.chats_delete));
            acts.add(() -> { failed.remove(i); render(false); });
        } else {
            labels.add(getString(R.string.chat_delete_me));
            acts.add(() -> delete(i, false));
            if (i.mine) { labels.add(getString(R.string.chat_delete_all)); acts.add(() -> delete(i, true)); }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setItems(labels.toArray(new String[0]), (d, which) -> acts.get(which).run())
                .show();
    }

    private void delete(ChatRules.Item i, boolean everyone) {
        Async.run(() -> { chat.deleteMessage(convId, i.id, everyone); return Boolean.TRUE; }, ok -> {
            if (b == null) return;
            server.removeIf(s -> s.id.equals(i.id));
            render(false);
        }, e -> ListArea.toast(this, getString(R.string.chat_delete_failed)));
    }

    // ---- rendering -------------------------------------------------------------------------------------------

    /** Queued (waiting) messages of this chat from the Outbox file, as bubbles. */
    private void syncQueued() {
        List<ChatRules.Item> q = new ArrayList<>();
        try {
            for (JSONObject e : Outbox.pending(convId))
                q.add(new ChatRules.Item(e.getString("id"), e.getString("body"), String.valueOf(e.optLong("at")), true, ChatRules.State.PENDING));
        } catch (Exception bad) { /* unreadable queue file: nothing waiting */ }
        queued = q;
    }

    private List<ChatRules.Item> allQueued() {
        List<ChatRules.Item> l = new ArrayList<>(failed);
        l.addAll(queued);
        return l;
    }

    private boolean nearBottom() {
        int n = adapter.getItemCount();
        return n == 0 || lm.findLastVisibleItemPosition() >= n - 2;
    }

    private void render(boolean scrollDown) {
        if (b == null) return;
        List<ChatRules.Item> all = ChatRules.merge(server, allQueued());
        adapter.submitList(all, () -> { if (b != null && scrollDown && !all.isEmpty()) b.list.scrollToPosition(all.size() - 1); });
        boolean empty = loaded && all.isEmpty();
        b.retry.setVisibility(View.GONE);
        b.helloMascot.setVisibility(View.VISIBLE);
        b.helloTitle.setVisibility(View.VISIBLE);
        b.messageText.setText(R.string.chat_hello_sub);
        b.messageBox.setVisibility(empty ? View.VISIBLE : View.GONE);
        b.quickScroll.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        token++;
        polling = false;
        handler.removeCallbacks(pollTask);
        Outbox.LISTENERS.remove(outboxListener);
        b = null;
        super.onDestroyView();
    }

    // ---- adapter ---------------------------------------------------------------------------------------------

    static final class Adapter extends ListAdapter<ChatRules.Item, RecyclerView.ViewHolder> {
        interface Listener { void tap(ChatRules.Item i); void hold(ChatRules.Item i); }
        private final Listener listener;

        Adapter(Listener l) {
            super(new DiffUtil.ItemCallback<ChatRules.Item>() {
                @Override public boolean areItemsTheSame(@NonNull ChatRules.Item a, @NonNull ChatRules.Item c) { return a.id.equals(c.id); }
                @Override public boolean areContentsTheSame(@NonNull ChatRules.Item a, @NonNull ChatRules.Item c) {
                    return a.state == c.state && a.body.equals(c.body) && a.createdAt.equals(c.createdAt);
                }
            });
            this.listener = l;
        }

        @Override public int getItemViewType(int position) { return getItem(position).mine ? 1 : 0; }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int type) {
            LayoutInflater in = LayoutInflater.from(p.getContext());
            if (type == 1) {
                ItemMsgMineBinding v = ItemMsgMineBinding.inflate(in, p, false);
                return new Holder(v.getRoot(), v.body, v.meta, v.day);
            }
            ItemMsgTheirsBinding v = ItemMsgTheirsBinding.inflate(in, p, false);
            return new Holder(v.getRoot(), v.body, v.meta, v.day);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int position) {
            ChatRules.Item i = getItem(position);
            Holder h = (Holder) vh;
            h.body.setText(i.body);
            long now = System.currentTimeMillis();
            String dl = ChatRules.dayLabel(i.createdAt, now, ZoneId.systemDefault());
            String before = position > 0 ? ChatRules.dayLabel(getItem(position - 1).createdAt, now, ZoneId.systemDefault()) : "";
            h.day.setText(dl);
            h.day.setVisibility(!dl.isEmpty() && !dl.equals(before) ? View.VISIBLE : View.GONE);
            String time = i.state == ChatRules.State.PENDING ? h.meta.getResources().getString(R.string.chat_sending)
                    : i.state == ChatRules.State.FAILED ? h.meta.getResources().getString(R.string.chat_failed_tap)
                    : ChatRules.timeLabel(i.createdAt, System.currentTimeMillis(), ZoneId.systemDefault());
            String tick = ChatRules.tick(i);
            h.meta.setText(tick.isEmpty() ? time : time + "  " + tick);
            h.meta.setTextColor(h.meta.getResources().getColor(i.state == ChatRules.State.READ ? R.color.bofo_primary
                    : i.state == ChatRules.State.FAILED ? R.color.bofo_error : R.color.bofo_muted, null));
            h.root.setOnClickListener(v -> listener.tap(i));
            h.root.setOnLongClickListener(v -> { listener.hold(i); return true; });
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final View root; final TextView body, meta, day;
            Holder(View root, TextView body, TextView meta, TextView day) { super(root); this.root = root; this.body = body; this.meta = meta; this.day = day; }
        }
    }
}
