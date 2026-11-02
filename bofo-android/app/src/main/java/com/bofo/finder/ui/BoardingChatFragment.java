package com.bofo.finder.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import com.bofo.finder.databinding.FragmentBoardingChatBinding;
import com.bofo.finder.databinding.ItemGroupMsgBinding;
import com.bofo.finder.model.GroupMessage;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhGroupService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BoardingRules;
import com.bofo.finder.util.BoardingRules.Row;
import com.bofo.finder.util.BoardingRules.State;
import com.bofo.finder.util.ChatRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.Outbox;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Group chat of ONE boarding house (v2.2.0). Same approach as {@link ChatFragment}: 30 messages per page (older pages on scroll),
 * a Handler poll chain (3 s, slower when quiet, only while resumed) and every message goes through the Outbox first, so it is sent
 * later when offline. Every message shows the sender name, a role badge (Owner / Student) and the time.
 * Step 3: announcements get their own card (title, text, "Got it" button; the owner sees "Seen by N"), the pinned ones sit in a banner
 * under the header (unanswered first, with "Got it" in the banner) and a long press on somebody else's message offers "Report message".
 * Used by the student tab (BoardingFragment, step 2) and by the owner screen (step 4: showBack = true, myRole = owner).
 */
public class BoardingChatFragment extends Fragment {

    public static final String ARG_BH = "bhId", ARG_TITLE = "title", ARG_MEMBERS = "members", ARG_ROLE = "myRole", ARG_BACK = "showBack", ARG_LEAVE = "canLeave";
    /** Fragment result key: the student left the group (BoardingFragment reloads and shows the join screen). */
    public static final String RESULT_LEFT = "boarding_left";

    public static BoardingChatFragment newInstance(String bhId, String title, int members, String myRole, boolean showBack, boolean canLeave) {
        Bundle a = new Bundle();
        a.putString(ARG_BH, bhId);
        a.putString(ARG_TITLE, title == null ? "" : title);
        a.putInt(ARG_MEMBERS, members);
        a.putString(ARG_ROLE, myRole == null ? GroupMessage.STUDENT : myRole);
        a.putBoolean(ARG_BACK, showBack);
        a.putBoolean(ARG_LEAVE, canLeave);
        BoardingChatFragment f = new BoardingChatFragment();
        f.setArguments(a);
        return f;
    }

    private FragmentBoardingChatBinding b;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BhGroupService svc = new BhGroupService();
    private Adapter adapter;
    private LinearLayoutManager lm;
    private String bhId, me, myRole;
    private final List<Row> server = new ArrayList<>();       // what the server told us
    private final List<Row> failed = new ArrayList<>();       // refused by the server (kept on screen, tap to send again)
    private List<Row> queued = new ArrayList<>();             // waiting in the Outbox
    private boolean loaded, resumed, polling, loadingOlder, noMoreOlder;
    private int quiet, token, pinTick;
    private List<Row> pinned = new ArrayList<>();            // pinned announcements (max 3), newest pin first

    private final Runnable pollTask = this::tick;

    private final Outbox.GroupListener outboxListener = new Outbox.GroupListener() {          // called from a background thread
        @Override public void sent(String id, GroupMessage m) {
            Async.post(() -> {
                if (b == null || !bhId.equals(m.bhId())) return;
                addServer(BoardingRules.fromMessage(m, me));
                syncQueued();
                render(true);
            });
        }
        @Override public void failed(String id, Throwable why) {
            Async.post(() -> {
                if (b == null) return;
                for (Row q : queued) if (q.id.equals(id)) { failed.add(q.withState(State.FAILED)); break; }
                syncQueued();
                render(true);
                String text = String.valueOf(why.getMessage());
                ListArea.toast(BoardingChatFragment.this, BoardingRules.sendBlocked(text) ? getString(R.string.boarding_blocked) : getString(R.string.chat_send_failed, text));
            });
        }
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentBoardingChatBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        Bundle args = requireArguments();
        bhId = args.getString(ARG_BH, "");
        myRole = args.getString(ARG_ROLE, GroupMessage.STUDENT);
        me = AuthService.get().userId();
        String title = args.getString(ARG_TITLE, "");
        int members = args.getInt(ARG_MEMBERS, 0);
        b.title.setText(title);
        b.avatar.setText(BoardingRules.initial(title));
        b.subtitle.setText(members > 0 ? getResources().getQuantityString(R.plurals.boarding_members, members, members) : "");
        if (args.getBoolean(ARG_BACK, false)) {
            b.back.setVisibility(View.VISIBLE);
            b.back.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        }
        if (args.getBoolean(ARG_LEAVE, false)) {
            b.more.setVisibility(View.VISIBLE);
            b.more.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                    .setItems(new String[]{getString(R.string.boarding_leave)}, (d, which) -> confirmLeave()).show());
        }

        adapter = new Adapter(myRole, new Adapter.Listener() {
            @Override public void tap(Row r) { if (r.state == State.FAILED) resend(r); }
            @Override public void hold(Row r) { options(r); }
            @Override public void ack(Row r) { acknowledge(r); }
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
        b.retry.setOnClickListener(v -> load());
        b.pinnedBar.setOnClickListener(v -> showPinned());
        b.pinnedGotIt.setOnClickListener(v -> {
            List<Row> order = BoardingRules.bannerOrder(pinned);
            if (!order.isEmpty() && BoardingRules.canAck(order.get(0))) acknowledge(order.get(0));
        });
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
        Async.run(() -> svc.page(bhId, null), (List<GroupMessage> page) -> {
            if (my != token || b == null) return;
            server.clear();
            for (int i = page.size() - 1; i >= 0; i--) server.add(BoardingRules.fromMessage(page.get(i), me));
            noMoreOlder = page.size() < BhGroupService.PAGE;
            loaded = true;
            b.loading.setVisibility(View.GONE);
            syncQueued();
            render(true);
            markRead();
            loadPinned();
            if (resumed) schedule(ChatRules.POLL_MIN_MS);
        }, e -> {
            if (my != token || b == null) return;
            b.loading.setVisibility(View.GONE);
            b.helloMascot.setVisibility(View.GONE);
            b.helloTitle.setVisibility(View.GONE);
            b.messageText.setText(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.boarding_load_error), e));
            b.retry.setVisibility(View.VISIBLE);
            b.messageBox.setVisibility(View.VISIBLE);
        });
    }

    private void loadOlder() {
        if (!loaded || loadingOlder || noMoreOlder) return;
        String before = BoardingRules.oldestServerTime(server);
        if (before == null) return;
        loadingOlder = true;
        final int my = token;
        Async.run(() -> svc.page(bhId, before), (List<GroupMessage> page) -> {
            loadingOlder = false;
            if (my != token || b == null) return;
            if (page.size() < BhGroupService.PAGE) noMoreOlder = true;
            if (page.isEmpty()) return;
            int first = lm.findFirstVisibleItemPosition();                 // keep the bubble the person is looking at where it is
            Row anchor = first >= 0 && first < adapter.getItemCount() ? adapter.getCurrentList().get(first) : null;
            View v = first >= 0 ? lm.findViewByPosition(first) : null;
            int offset = v == null ? 0 : v.getTop();
            for (GroupMessage m : page) addServer(BoardingRules.fromMessage(m, me));
            List<Row> all = BoardingRules.merge(server, allQueued());
            final Row keep = anchor;
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
        Async.bg(() -> { try { svc.markRead(bhId); } catch (Exception ignored) { /* offline: tried again on the next incoming message */ } });
    }

    private void addServer(Row r) {
        for (Row s : server) if (s.id.equals(r.id)) return;
        server.add(r);
    }

    // ---- polling (Handler.postDelayed, tied to onResume / onPause) --------------------------------------------

    @Override
    public void onResume() {
        super.onResume();
        resumed = true;
        Outbox.GROUP_LISTENERS.add(outboxListener);
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
        Outbox.GROUP_LISTENERS.remove(outboxListener);
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
        final String after = BoardingRules.newestServerTime(server);
        Async.run(() -> after == null ? svc.page(bhId, null) : svc.fetchNewer(bhId, after), (List<GroupMessage> fresh) -> {
            if (my != token || b == null) return;
            boolean changed = false, incoming = false, mineNew = false;
            if (after == null) Collections.reverse(fresh);                  // first page arrives newest-first
            for (GroupMessage m : fresh) {
                Row r = BoardingRules.fromMessage(m, me);
                boolean known = false;
                for (Row s : server) if (s.id.equals(r.id)) { known = true; break; }
                if (known) continue;
                server.add(r); changed = true;
                if (r.mine) mineNew = true; else incoming = true;
            }
            syncQueued();
            if (changed) render(mineNew || nearBottom());
            if (incoming) markRead();
            if (changed || ++pinTick % 5 == 0) loadPinned();                     // pin / unpin by the owner shows up within about 15 s
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
        String text = ChatRules.outgoing(b.input.getText().toString(), BhGroupService.MAX_LEN);
        if (text.isEmpty() || !loaded) return;
        try {
            Outbox.addGroup(bhId, text);
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

    private void resend(Row r) {
        failed.remove(r);
        try { Outbox.addGroup(bhId, r.body); } catch (Exception bad) { failed.add(r); return; }
        syncQueued();
        render(true);
        Outbox.flushAsync();
    }

    // ---- long press: copy / delete my message ----------------------------------------------------------------

    private void options(Row r) {
        if (r.state == State.PENDING) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        labels.add(getString(R.string.chat_copy));
        acts.add(() -> {
            ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) { cm.setPrimaryClip(ClipData.newPlainText("message", r.body)); ListArea.toast(this, getString(R.string.chat_copied)); }
        });
        if (BoardingRules.canReport(r, me)) {
            labels.add(getString(R.string.boarding_report_message));
            acts.add(() -> ReportDialog.show(this, r.senderId, bhId, BoardingRules.reportQuote(r)));
        }
        if (r.state == State.FAILED) {
            labels.add(getString(R.string.chats_delete));
            acts.add(() -> { failed.remove(r); render(false); });
        } else if (r.mine) {
            labels.add(getString(R.string.boarding_delete_mine));
            acts.add(() -> delete(r));
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setItems(labels.toArray(new String[0]), (d, which) -> acts.get(which).run())
                .show();
    }

    private void delete(Row r) {
        Async.run(() -> svc.deleteMessage(r.id), (Boolean ok) -> {
            if (b == null) return;
            if (!ok) { ListArea.toast(this, getString(R.string.boarding_delete_failed)); return; }
            server.removeIf(s -> s.id.equals(r.id));
            render(false);
        }, e -> ListArea.toast(this, getString(R.string.boarding_delete_failed)));
    }

    // ---- announcements: pinned banner, "Got it" ---------------------------------------------------------------

    private void loadPinned() {
        final int my = token;
        Async.run(() -> svc.pinned(bhId), (List<GroupMessage> list) -> {
            if (my != token || b == null) return;
            List<Row> rows = new ArrayList<>();
            for (GroupMessage m : list) rows.add(BoardingRules.fromMessage(m, me));
            pinned = rows;
            renderBanner();
        }, e -> { /* offline: the banner keeps what it had */ });
    }

    private void renderBanner() {
        if (b == null) return;
        if (pinned.isEmpty()) { b.pinnedBar.setVisibility(View.GONE); return; }
        Row top = BoardingRules.bannerOrder(pinned).get(0);
        b.pinnedTitle.setText(BoardingRules.oneLine(top.title == null || top.title.isEmpty() ? getString(R.string.boarding_announcement) : top.title, 80));
        b.pinnedText.setText(BoardingRules.oneLine(top.body, 120));
        b.pinnedCount.setText(getResources().getQuantityString(R.plurals.boarding_pinned_count, pinned.size(), pinned.size()));
        b.pinnedGotIt.setVisibility(BoardingRules.canAck(top) ? View.VISIBLE : View.GONE);
        b.pinnedBar.setVisibility(View.VISIBLE);
    }

    /** "Got it": saved on the server, then every copy of the announcement on screen shows it as done. */
    private void acknowledge(Row r) {
        if (!BoardingRules.canAck(r)) return;
        b.pinnedGotIt.setEnabled(false);
        Async.run(() -> { svc.ack(r.id); return Boolean.TRUE; }, ok -> {
            if (b == null) return;
            b.pinnedGotIt.setEnabled(true);
            markAcked(r.id);
        }, e -> {
            if (b == null) return;
            b.pinnedGotIt.setEnabled(true);
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : getString(R.string.boarding_ack_failed));
        });
    }

    private void markAcked(String id) {
        List<Row> s = BoardingRules.markAcked(server, id);
        server.clear();
        server.addAll(s);
        pinned = BoardingRules.markAcked(pinned, id);
        renderBanner();
        render(false);
    }

    /** Banner tap: all pinned announcements in one dialog, each with its own "Got it". */
    private void showPinned() {
        if (pinned.isEmpty()) return;
        Context c = requireContext();
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * dp);
        box.setPadding(pad, Math.round(8 * dp), pad, Math.round(8 * dp));
        final androidx.appcompat.app.AlertDialog[] dlg = new androidx.appcompat.app.AlertDialog[1];
        for (Row r : BoardingRules.bannerOrder(pinned)) {
            TextView title = new TextView(c);
            title.setText(r.title == null || r.title.isEmpty() ? getString(R.string.boarding_announcement) : r.title);
            title.setTextColor(c.getColor(R.color.bofo_ink));
            title.setTextSize(16);
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            title.setPadding(0, Math.round(10 * dp), 0, 0);
            box.addView(title);
            TextView body = new TextView(c);
            body.setText(r.body);
            body.setTextColor(c.getColor(R.color.bofo_text));
            body.setTextSize(15);
            body.setPadding(0, Math.round(4 * dp), 0, 0);
            box.addView(body);
            if (BoardingRules.canAck(r)) {
                MaterialButton ok = new MaterialButton(c);
                ok.setText(R.string.boarding_got_it);
                ok.setAllCaps(false);
                ok.setBackgroundTintList(android.content.res.ColorStateList.valueOf(c.getColor(R.color.bofo_primary_tint)));
                ok.setTextColor(c.getColor(R.color.bofo_primary));
                ok.setOnClickListener(v -> { if (dlg[0] != null) dlg[0].dismiss(); acknowledge(r); });
                box.addView(ok);
            } else if (!r.mine && r.iAcked) {
                TextView done = new TextView(c);
                done.setText(R.string.boarding_got_it_done);
                done.setTextColor(c.getColor(R.color.bofo_success));
                done.setTextSize(12);
                done.setPadding(0, Math.round(6 * dp), 0, 0);
                box.addView(done);
            }
        }
        ScrollView sv = new ScrollView(c);
        sv.addView(box);
        dlg[0] = new MaterialAlertDialogBuilder(c).setTitle(R.string.boarding_pinned_title).setView(sv)
                .setPositiveButton(R.string.common_close, null).show();
    }

    // ---- leave (students) ------------------------------------------------------------------------------------

    private void confirmLeave() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.boarding_leave_title)
                .setMessage(R.string.boarding_leave_msg)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.boarding_leave, (d, w) -> Async.run(() -> { svc.leave(bhId); return Boolean.TRUE; }, ok -> {
                    if (b == null) return;
                    getParentFragmentManager().setFragmentResult(RESULT_LEFT, new Bundle());
                }, e -> ListArea.toast(this, getString(R.string.boarding_leave_failed))))
                .show();
    }

    // ---- rendering -------------------------------------------------------------------------------------------

    /** Queued (waiting) messages of this house from the Outbox file, as bubbles. */
    private void syncQueued() {
        List<Row> q = new ArrayList<>();
        try {
            for (JSONObject e : Outbox.pendingGroup(bhId))
                q.add(BoardingRules.queued(e.getString("id"), me, "", myRole, e.getString("body"), e.optLong("at"), State.PENDING));
        } catch (Exception bad) { /* unreadable queue file: nothing waiting */ }
        queued = q;
    }

    private List<Row> allQueued() {
        List<Row> l = new ArrayList<>(failed);
        l.addAll(queued);
        return l;
    }

    private boolean nearBottom() {
        int n = adapter.getItemCount();
        return n == 0 || lm.findLastVisibleItemPosition() >= n - 2;
    }

    private void render(boolean scrollDown) {
        if (b == null) return;
        List<Row> all = BoardingRules.merge(server, allQueued());
        adapter.submitList(all, () -> { if (b != null && scrollDown && !all.isEmpty()) b.list.scrollToPosition(all.size() - 1); });
        boolean empty = loaded && all.isEmpty();
        b.retry.setVisibility(View.GONE);
        b.helloMascot.setVisibility(View.VISIBLE);
        b.helloTitle.setVisibility(View.VISIBLE);
        b.messageText.setText(R.string.boarding_empty_sub);
        b.messageBox.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        token++;
        polling = false;
        handler.removeCallbacks(pollTask);
        Outbox.GROUP_LISTENERS.remove(outboxListener);
        b = null;
        super.onDestroyView();
    }

    // ---- adapter ---------------------------------------------------------------------------------------------

    static final class Adapter extends ListAdapter<Row, Adapter.Holder> {
        interface Listener { void tap(Row r); void hold(Row r); void ack(Row r); }
        private final Listener listener;
        private final String myRole;

        Adapter(String myRole, Listener l) {
            super(new DiffUtil.ItemCallback<Row>() {
                @Override public boolean areItemsTheSame(@NonNull Row a, @NonNull Row c) { return a.id.equals(c.id); }
                @Override public boolean areContentsTheSame(@NonNull Row a, @NonNull Row c) {
                    return a.state == c.state && a.body.equals(c.body) && a.createdAt.equals(c.createdAt) && a.name.equals(c.name) && java.util.Objects.equals(a.role, c.role)
                            && a.pinned == c.pinned && a.iAcked == c.iAcked && a.ackCount == c.ackCount;
                }
            });
            this.myRole = myRole;
            this.listener = l;
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup p, int type) {
            return new Holder(ItemGroupMsgBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Row r = getItem(position);
            Context c = h.v.getRoot().getContext();
            ((LinearLayout) h.v.getRoot()).setGravity(r.mine ? Gravity.END : Gravity.START);

            long now = System.currentTimeMillis();
            String dl = ChatRules.dayLabel(r.createdAt, now, ZoneId.systemDefault());
            String before = position > 0 ? ChatRules.dayLabel(getItem(position - 1).createdAt, now, ZoneId.systemDefault()) : "";
            h.v.day.setText(dl);
            h.v.day.setVisibility(!dl.isEmpty() && !dl.equals(before) ? View.VISIBLE : View.GONE);

            // sender name + role badge (every message)
            String role = r.mine && r.role == null ? myRole : r.role;
            boolean owner = GroupMessage.OWNER.equals(role);
            h.v.name.setText(BoardingRules.displayName(r, c.getString(R.string.boarding_you)));
            h.v.role.setText(owner ? R.string.boarding_role_owner : R.string.boarding_role_student);
            h.v.role.setBackgroundResource(owner ? R.drawable.bg_role_owner : R.drawable.bg_role_student);
            h.v.role.setTextColor(c.getColor(owner ? R.color.bofo_role_owner_text : R.color.bofo_primary));

            // announcement card (step 3) or plain bubble
            h.v.ann.setVisibility(r.announcement ? View.VISIBLE : View.GONE);
            h.v.body.setVisibility(r.announcement ? View.GONE : View.VISIBLE);
            if (r.announcement) {
                h.v.annTitle.setText(r.title == null || r.title.isEmpty() ? c.getString(R.string.boarding_announcement) : r.title);
                h.v.annBody.setText(r.body);
                h.v.annPinned.setVisibility(r.pinned ? View.VISIBLE : View.GONE);
                boolean ownerView = GroupMessage.OWNER.equals(myRole);
                h.v.annSeen.setVisibility(ownerView && r.mine && r.ackCount > 0 ? View.VISIBLE : View.GONE);
                if (r.ackCount > 0) h.v.annSeen.setText(c.getResources().getQuantityString(R.plurals.boarding_seen_by, r.ackCount, r.ackCount));
                h.v.gotIt.setVisibility(BoardingRules.canAck(r) ? View.VISIBLE : View.GONE);
                h.v.gotIt.setOnClickListener(v -> listener.ack(r));
                h.v.annDone.setVisibility(!r.mine && r.iAcked ? View.VISIBLE : View.GONE);
                h.v.ann.setOnLongClickListener(v -> { listener.hold(r); return true; });
            } else {
                h.v.body.setText(r.body);
            }
            h.v.body.setBackgroundResource(r.mine ? R.drawable.bg_bubble_mine : R.drawable.bg_bubble_theirs);
            h.v.body.setTextColor(c.getColor(r.mine ? R.color.bofo_white : R.color.bofo_text));

            String time = r.state == State.PENDING ? c.getString(R.string.chat_sending)
                    : r.state == State.FAILED ? c.getString(R.string.chat_failed_tap)
                    : ChatRules.timeLabel(r.createdAt, now, ZoneId.systemDefault());
            h.v.meta.setText(time);
            h.v.meta.setTextColor(c.getColor(r.state == State.FAILED ? R.color.bofo_error : R.color.bofo_muted));
            h.v.body.setOnClickListener(v -> listener.tap(r));
            h.v.body.setOnLongClickListener(v -> { listener.hold(r); return true; });
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ItemGroupMsgBinding v;
            Holder(ItemGroupMsgBinding v) { super(v.getRoot()); this.v = v; }
        }
    }
}
