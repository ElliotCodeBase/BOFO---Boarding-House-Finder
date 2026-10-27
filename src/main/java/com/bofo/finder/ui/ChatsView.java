package com.bofo.finder.ui;

import com.bofo.finder.app.Main;
import com.bofo.finder.model.Conversation;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.scene.layout.*;

/** Inbox: every conversation sorted by latest message, with BH name, preview, local time and unread count. */
public final class ChatsView extends VBox {
    public ChatsView() {
        ChatService chat = new ChatService();
        VBox rows = new VBox(10); rows.setPadding(new Insets(6, 16, 96, 16));
        getChildren().addAll(Ui.pageHeader("Chats", "Press and hold a chat (or right-click) to delete it"), Ui.scroll(rows));
        Ui.load(rows, () -> Skeleton.rows(5), chat::inbox, list -> { lastSig = signature(list); render(rows, list); });

        // live inbox: while this screen is shown, check every 4 seconds and redraw only when something changed (new message, unread count, time)
        Timeline live = new Timeline(new KeyFrame(Duration.seconds(4), e -> {
            if (busy) return;
            busy = true;
            Async.run(chat::inbox, list -> {
                busy = false;
                String sig = signature(list);
                if (!sig.equals(lastSig)) { lastSig = sig; render(rows, list); }
            }, err -> busy = false);
        }));
        live.setCycleCount(Timeline.INDEFINITE);
        sceneProperty().addListener((o, was, sc) -> { if (sc == null) live.stop(); else live.play(); });
    }

    private boolean busy;
    private String lastSig = "";

    private static String signature(List<Conversation> list) {
        StringBuilder sb = new StringBuilder();
        for (Conversation c : list) sb.append(c.id()).append('|').append(c.lastAt()).append('|').append(c.unread()).append('|').append(c.lastMessage()).append(';');
        return sb.toString();
    }

    private void render(VBox rows, List<Conversation> list) {
        {
            rows.getChildren().clear();
            ChatService chat = new ChatService();
            if (list.isEmpty()) rows.getChildren().add(Ui.emptyState("No conversations yet", "Open a boarding house and tap Chat with owner."));
            String me = AuthService.get().userId();
            for (Conversation c : list) {
                boolean unreadOn = c.unread() > 0;
                Label name = Ui.label(c.otherName().isBlank() ? c.bhName() : c.otherName(), "chat-row-name");
                Label bh = Ui.label(c.bhName(), "chat-row-bh");
                Label last = Ui.label(c.lastMessage(), unreadOn ? "chat-row-preview-on" : "chat-row-preview");
                for (Label l : new Label[]{name, bh, last}) { l.setWrapText(false); l.setMinWidth(0); l.setMaxWidth(Double.MAX_VALUE); l.setTextOverrun(OverrunStyle.ELLIPSIS); }
                VBox mid = Ui.vbox(2, name, bh, last); mid.setMinWidth(0); HBox.setHgrow(mid, Priority.ALWAYS);

                Label time = Ui.label(when(c.lastAt()), unreadOn ? "chat-row-time-on" : "chat-row-time");
                time.setWrapText(false); time.setMinWidth(Region.USE_PREF_SIZE);        // never breaks "1:20 PM" into two lines
                VBox right = Ui.vbox(6, time); right.setAlignment(Pos.TOP_RIGHT); right.setMinWidth(Region.USE_PREF_SIZE);
                if (unreadOn) { Label u = Ui.label(c.unread() > 99 ? "99+" : String.valueOf(c.unread()), "unread"); u.setMinWidth(Region.USE_PREF_SIZE); right.getChildren().add(u); }

                HBox row = Ui.hbox(12, Ui.avatar(c.otherName().isBlank() ? c.bhName() : c.otherName(), 48), mid, right);
                row.getStyleClass().addAll("card", "chat-row"); row.setAlignment(Pos.CENTER_LEFT); row.setCursor(Cursor.HAND);
                if (unreadOn) row.getStyleClass().add("chat-row-unread");
                String other = c.studentId().equals(me) ? c.ownerId() : c.studentId();
                // owners see the student's name in the chat header; students see the boarding house name
                String title = c.ownerId().equals(me) && !c.otherName().isBlank() ? c.otherName() : c.bhName();
                row.setOnMouseClicked(e -> Nav.show(new ChatView(c.id(), title, null, other, () -> Nav.show(Main.home(), true)), false));
                row.setOnContextMenuRequested(e -> {                         // press and hold on a phone, right-click on a PC
                    ContextMenu cm = new ContextMenu(); MenuItem del = new MenuItem("Delete chat");
                    del.setOnAction(x -> confirmDelete(c, rows));
                    cm.getItems().add(del); cm.show(row, e.getScreenX(), e.getScreenY()); e.consume();
                });
                rows.getChildren().add(row);
            }
        }
    }

    private void confirmDelete(Conversation c, VBox rows) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "The chat and its messages are removed for you. The other person keeps their copy. If they write again, the chat comes back with only the new messages.", ButtonType.CANCEL, ButtonType.OK);
        Ui.style(a); a.setTitle("Delete chat"); a.setHeaderText("Delete this chat?");
        a.showAndWait().filter(x -> x == ButtonType.OK).ifPresent(x ->
                Async.run(() -> { new ChatService().deleteConversation(c.id()); return true; },
                        ok -> { Nav.toast("Chat deleted"); Async.run(() -> new ChatService().inbox(), list -> { lastSig = signature(list); render(rows, list); }, err -> {}); },
                        e -> Ui.failToast(e, "Could not delete the chat")));
    }

    /** The server sends UTC: show local time today, "Yesterday", or the date. */
    private static String when(String iso) {
        try {
            ZonedDateTime t = OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault());
            LocalDate d = t.toLocalDate(), today = LocalDate.now();
            if (d.equals(today)) return t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
            if (d.equals(today.minusDays(1))) return "Yesterday";
            return t.format(DateTimeFormatter.ofPattern(d.getYear() == today.getYear() ? "MMM d" : "MMM d, yyyy", Locale.ENGLISH));
        } catch (Exception e) { return iso != null && iso.length() >= 16 ? iso.substring(11, 16) : ""; }
    }
}
