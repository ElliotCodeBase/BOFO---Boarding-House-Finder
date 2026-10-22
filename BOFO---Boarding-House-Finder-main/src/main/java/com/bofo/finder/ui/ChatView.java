package com.bofo.finder.ui;

import com.bofo.finder.model.Message;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    public ChatView(String convId, String bhName, String bhPhoto, String otherUserId, Runnable onBack) {
        this.convId = convId; this.otherUserId = otherUserId; this.me = AuthService.get().userId();
        Button back = Ui.btn("←", "heart", () -> { stop(); onBack.run(); });
        back.setStyle("-fx-text-fill:white;-fx-font-size:20px;-fx-background-color:transparent;");
        Button more = Ui.btn("⋯", "heart", this::reportDialog);
        more.setStyle("-fx-text-fill:white;-fx-font-size:20px;-fx-background-color:transparent;");
        HBox bar = Ui.appBar("🏠 " + bhName, more); bar.getChildren().add(0, back);

        msgs.setPadding(new Insets(10));
        VBox.setVgrow(scroll, Priority.ALWAYS);
        input.setPromptText("Message (max " + ChatService.MAX_LEN + " chars)"); HBox.setHgrow(input, Priority.ALWAYS);
        input.setOnAction(e -> send());
        HBox composer = Ui.hbox(8, input, Ui.btn("Send", "btn-primary", this::send)); composer.setPadding(new Insets(8, 12, 8, 12));
        getChildren().addAll(bar, scroll, composer);

        loadOlder(true);
        poll = Async.scheduler();
        poll.schedule(this::tick, 3, TimeUnit.SECONDS);
    }

    private void stop() { active = false; if (poll != null) poll.shutdownNow(); }

    /** Adaptive polling: 3 s while the chat is active, backing off to 12 s when quiet; resets on any new message. */
    private void tick() {
        if (!active) return;
        boolean got = pollNew();
        idleTicks = got ? 0 : idleTicks + 1;
        long delay = idleTicks < 5 ? 3 : idleTicks < 20 ? 6 : 12;
        if (active) poll.schedule(this::tick, delay, TimeUnit.SECONDS);
    }

    private void loadOlder(boolean first) {
        Async.run(() -> chat.page(convId, first ? null : oldest), page -> {
            if (page.isEmpty()) return;
            List<Message> asc = new ArrayList<>(page); Collections.reverse(asc);
            oldest = asc.get(0).createdAt();
            if (first) newest = asc.get(asc.size() - 1).createdAt();
            if (!first && !msgs.getChildren().isEmpty() && msgs.getChildren().get(0) instanceof Button) msgs.getChildren().remove(0);
            List<javafx.scene.Node> nodes = new ArrayList<>();
            if (page.size() == ChatService.PAGE) nodes.add(Ui.btn("Load older messages", "chip", () -> loadOlder(false)));
            for (Message m : asc) nodes.add(bubble(m));
            msgs.getChildren().addAll(first ? msgs.getChildren().size() : 0, nodes);
            if (first) Platform.runLater(() -> scroll.setVvalue(1.0));
            Async.bg(() -> { try { chat.markRead(convId); } catch (Exception ignored) {} });
        }, e -> Nav.toast("Could not load messages"));
    }

    private boolean pollNew() {
        try {
            List<Message> fresh = chat.fetchNewer(convId, newest);
            if (fresh.isEmpty()) return false;
            Platform.runLater(() -> {
                for (Message m : fresh) { msgs.getChildren().add(bubble(m)); newest = m.createdAt(); }
                scroll.setVvalue(1.0);
            });
            chat.markRead(convId);
            return true;
        } catch (Exception ignored) { return false; /* offline: next tick retries */ }
    }

    private javafx.scene.Node bubble(Message m) {
        boolean mine = m.senderId().equals(me);
        Label l = new Label(m.body()); l.setWrapText(true); l.setMaxWidth(250);
        StackPane b = new StackPane(l); b.getStyleClass().add(mine ? "bubble-me" : "bubble-them");
        String status = mine ? (m.readAt() != null ? "✓✓ Read" : "✓ Sent") : "";
        VBox box = new VBox(2, b, Ui.label(status, "secondary")); box.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        HBox row = new HBox(box); row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT); return row;
    }

    private void send() {
        String t = input.getText(); if (t == null || t.isBlank()) return;
        if (t.length() > ChatService.MAX_LEN) { Nav.toast("Message too long"); return; }
        input.clear(); idleTicks = 0;
        Async.run(() -> chat.send(convId, me, t), m -> { msgs.getChildren().add(bubble(m)); newest = m.createdAt(); scroll.setVvalue(1.0); },
                e -> { Nav.toast("Not sent. Are you blocked or offline?"); input.setText(t); });
    }

    private void reportDialog() {
        TextInputDialog d = new TextInputDialog(); d.setTitle("Report / block"); d.setHeaderText("Tell us what happened. This also blocks the user.");
        d.showAndWait().ifPresent(reason -> Async.run(() -> { chat.block(me, otherUserId, reason); return true; },
                ok -> Nav.toast("User reported and blocked"), e -> Nav.toast("Could not submit")));
    }
}
