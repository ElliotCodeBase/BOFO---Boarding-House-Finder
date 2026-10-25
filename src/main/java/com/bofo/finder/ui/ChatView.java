package com.bofo.finder.ui;

import com.bofo.finder.model.Message;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.Outbox;
import org.json.JSONObject;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** One conversation: paginated history (newest first, "Load older"), polling for new messages, report/block. */
public final class ChatView extends VBox {
    private final ChatService chat = new ChatService();
    private final String convId, me, otherUserId;
    private final VBox msgs = new VBox(6);
    private final ScrollPane scroll = Ui.scroll(msgs);
    private final TextField input = new TextField();
    private String oldest, newest = "1970-01-01T00:00:00Z";
    private ScheduledExecutorService poll;
    private volatile boolean active = true;
    private int idleTicks = 0;
    private final Set<String> seen = new HashSet<>();                 // message ids already on screen (no duplicates)
    private record Pending(HBox row, StackPane bubble, Label status) {}
    private final java.util.Map<String, Pending> queued = new java.util.LinkedHashMap<>();   // messages in the Outbox (device) that are not on the server yet
    private volatile boolean needsReload = false;                      // history could not be loaded (offline, nothing saved): reload when online
    private static final String WAITING = "Waiting for internet - sends automatically";
    private final Outbox.Listener outboxListener = new Outbox.Listener() {
        @Override public void sent(String id, Message m) { Platform.runLater(() -> onSent(id, m)); }
        @Override public void failed(String id, Throwable why) { Platform.runLater(() -> onFailed(id, why)); }
        @Override public void waiting(String id) { Platform.runLater(() -> { Pending q = queued.get(id); if (q != null) q.status().setText(WAITING); }); }
    };

    public ChatView(String convId, String bhName, String bhPhoto, String otherUserId, Runnable onBack) {
        this.convId = convId; this.otherUserId = otherUserId; this.me = AuthService.get().userId();
        Button back = Ui.btn("←", "heart", () -> { stop(); onBack.run(); });
        back.getStyleClass().setAll("button", "icon-btn");
        Button more = Ui.btn("⋯", "heart", this::reportDialog);
        more.getStyleClass().setAll("button", "icon-btn");
        HBox bar = Ui.appBar(bhName, more); bar.getChildren().add(0, back);

        msgs.setPadding(new Insets(10));
        VBox.setVgrow(scroll, Priority.ALWAYS);
        input.setPromptText("Message (max " + ChatService.MAX_LEN + " chars)"); HBox.setHgrow(input, Priority.ALWAYS);
        input.setOnAction(e -> send());
        HBox composer = Ui.hbox(8, input, Ui.btn("Send", "btn-primary", this::send)); composer.setPadding(new Insets(8, 12, 8, 12));
        getChildren().addAll(bar, scroll, composer);

        Outbox.LISTENERS.add(outboxListener);
        for (JSONObject e : Outbox.pending(convId)) addPending(e);     // messages written offline in an earlier visit are still here
        loadOlder(true);
        Outbox.flushAsync();
        poll = Async.scheduler();
        poll.schedule(this::tick, 3, TimeUnit.SECONDS);
    }

    private void stop() { active = false; Outbox.LISTENERS.remove(outboxListener); if (poll != null) poll.shutdownNow(); }

    /** Adaptive polling: 3 s while the chat is active, backing off to 12 s when quiet; resets on any new message. */
    private void tick() {
        if (!active) return;
        if (Outbox.hasPending()) Outbox.flushAsync();                    // queued messages go out as soon as the network is back
        boolean got = false;
        if (needsReload) { if (!Connectivity.OFFLINE.get()) { needsReload = false; Platform.runLater(() -> loadOlder(true)); } }
        else got = pollNew();
        idleTicks = got ? 0 : idleTicks + 1;
        long delay = idleTicks < 5 ? 3 : idleTicks < 20 ? 6 : 12;
        if (active) poll.schedule(this::tick, delay, TimeUnit.SECONDS);
    }

    private void loadOlder(boolean first) {
        if (first) msgs.getChildren().setAll(Skeleton.bubbles());          // skeleton while the history loads
        Async.run(() -> chat.page(convId, first ? null : oldest), page -> {
            if (first) msgs.getChildren().clear();
            if (page.isEmpty()) { if (first) restoreQueued(); return; }
            List<Message> asc = new ArrayList<>(page); Collections.reverse(asc);
            oldest = asc.get(0).createdAt();
            if (first) newest = asc.get(asc.size() - 1).createdAt();
            if (!first && !msgs.getChildren().isEmpty() && msgs.getChildren().get(0) instanceof Button) msgs.getChildren().remove(0);
            List<javafx.scene.Node> nodes = new ArrayList<>();
            if (page.size() == ChatService.PAGE) nodes.add(Ui.btn("Load older messages", "chip", () -> loadOlder(false)));
            for (Message m : asc) { seen.add(m.id()); nodes.add(bubble(m)); }
            msgs.getChildren().addAll(first ? msgs.getChildren().size() : 0, nodes);
            if (first) restoreQueued();
            if (first) Platform.runLater(() -> scroll.setVvalue(1.0));
            Async.bg(() -> { try { chat.markRead(convId); } catch (Exception ignored) {} });
        }, e -> {
            if (!first) { Nav.toast(Connectivity.isNetworkError(e) ? "No internet connection. Older messages need internet." : "Could not load messages"); return; }
            if (Connectivity.isNetworkError(e)) {           // offline and nothing saved for this chat yet: keep the chat usable, no dead screen
                needsReload = true;
                Label note = Ui.label("No saved messages for this chat yet. Open it once with internet and it will be here offline. You can still write: messages are sent when you are back online.", "secondary");
                note.setWrapText(true); note.setPadding(new Insets(16));
                msgs.getChildren().setAll(note);
            } else msgs.getChildren().setAll(Ui.errorState(e, () -> loadOlder(true)));
            restoreQueued();
        });
    }

    private boolean pollNew() {
        try {
            List<Message> fresh = chat.fetchNewer(convId, newest);
            if (fresh.isEmpty()) return false;
            Platform.runLater(() -> {
                for (Message m : fresh) { newest = m.createdAt(); if (seen.add(m.id())) msgs.getChildren().add(bubble(m)); }
                scroll.setVvalue(1.0);
            });
            chat.markRead(convId);
            refreshSavedCopy();
            return true;
        } catch (Exception ignored) { return false; /* offline: next tick retries */ }
    }

    private javafx.scene.Node bubble(Message m) {
        boolean mine = m.senderId().equals(me);
        Label l = new Label(m.body()); l.setWrapText(true); l.maxWidthProperty().bind(javafx.beans.binding.Bindings.max(180, scroll.widthProperty().multiply(0.74)));
        StackPane b = new StackPane(l); b.getStyleClass().add(mine ? "bubble-me" : "bubble-them");
        String status = mine ? (m.readAt() != null ? "✓✓ Read" : "✓ Sent") : "";
        VBox box = new VBox(2, b, Ui.label(status, "secondary")); box.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        HBox row = new HBox(box); row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT); return row;
    }

    private void send() {
        String t = input.getText(); if (t == null || t.isBlank()) return;
        if (t.length() > ChatService.MAX_LEN) { Nav.toast("Message too long"); return; }
        String clean = com.bofo.finder.util.Validate.clean(t, ChatService.MAX_LEN);
        if (clean.isEmpty()) return;
        input.clear(); idleTicks = 0;
        addPending(Outbox.add(convId, clean));                         // saved on the device first, so it is never lost
        Outbox.flushAsync();                                           // online: goes out now; offline: waits and is sent by itself later
    }

    /** A message that is not on the server yet: faded bubble with "Sending..." (online) or "Waiting for internet" (offline). */
    private void addPending(JSONObject e) {
        String id = e.getString("id");
        Label l = new Label(e.getString("body")); l.setWrapText(true);
        l.maxWidthProperty().bind(javafx.beans.binding.Bindings.max(180, scroll.widthProperty().multiply(0.74)));
        StackPane b = new StackPane(l); b.getStyleClass().add("bubble-me"); b.setOpacity(0.6);
        Label status = Ui.label(Connectivity.OFFLINE.get() ? WAITING : "Sending...", "secondary");
        VBox box = new VBox(2, b, status); box.setAlignment(Pos.CENTER_RIGHT);
        HBox row = new HBox(box); row.setAlignment(Pos.CENTER_RIGHT);
        queued.put(id, new Pending(row, b, status));
        msgs.getChildren().add(row); Platform.runLater(() -> scroll.setVvalue(1.0));
    }

    /** After the history is (re)drawn, the queued bubbles go back at the bottom. */
    private void restoreQueued() {
        for (Pending q : queued.values()) if (!msgs.getChildren().contains(q.row())) msgs.getChildren().add(q.row());
    }

    private void onSent(String id, Message m) {
        Pending q = queued.remove(id); if (q == null) return;
        int i = msgs.getChildren().indexOf(q.row());
        if (seen.add(m.id())) { if (i >= 0) msgs.getChildren().set(i, bubble(m)); else msgs.getChildren().add(bubble(m)); }
        else msgs.getChildren().remove(q.row());                       // the poll already showed it
        if (m.createdAt().compareTo(newest) > 0) newest = m.createdAt();
        scroll.setVvalue(1.0);
        Async.bg(this::refreshSavedCopy);
    }

    private void onFailed(String id, Throwable why) {
        Pending q = queued.remove(id); if (q == null) return;
        boolean blocked = why instanceof com.bofo.finder.service.SupabaseClient.ApiException api && (api.status == 401 || api.status == 403);
        q.status().setText((blocked ? "Not sent - you may be blocked" : "Not sent") + " (tap to remove)");
        q.status().getStyleClass().add("error-text"); q.status().setCursor(Cursor.HAND);
        q.status().setOnMouseClicked(x -> msgs.getChildren().remove(q.row()));
    }

    /** Re-saves the newest page on the device so the chat opens offline with the latest messages. */
    private void refreshSavedCopy() { try { chat.page(convId, null); } catch (Exception ignored) { } }

    private void reportDialog() {
        TextInputDialog d = new TextInputDialog(); d.setTitle("Report / block"); d.setHeaderText("Tell us what happened. This also blocks the user.");
        d.showAndWait().ifPresent(reason -> Async.run(() -> { chat.block(me, otherUserId, reason); return true; },
                ok -> Nav.toast("User reported and blocked"), e -> Ui.failToast(e, "Could not submit")));
    }
}
