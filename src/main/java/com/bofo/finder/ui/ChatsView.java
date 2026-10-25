package com.bofo.finder.ui;

import com.bofo.finder.app.Main;
import com.bofo.finder.model.Conversation;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

/** Inbox: every conversation sorted by latest message, with BH name, preview, time and unread count. */
public final class ChatsView extends VBox {
    public ChatsView() {
        ChatService chat = new ChatService();
        VBox rows = new VBox(8); rows.setPadding(new Insets(12, 16, 72, 16));
        getChildren().addAll(Ui.appBar("Chats"), Ui.scroll(rows));
        Ui.load(rows, () -> Skeleton.rows(5), chat::inbox, list -> {
            if (list.isEmpty()) rows.getChildren().add(Ui.emptyState("No conversations yet", "Open a boarding house and tap Chat with owner."));
            String me = AuthService.get().userId();
            for (Conversation c : list) {
                String time = c.lastAt().length() >= 16 ? c.lastAt().substring(11, 16) : "";
                Label unread = c.unread() > 0 ? Ui.label(String.valueOf(c.unread()), "unread") : new Label();
                VBox mid = Ui.vbox(2, Ui.label(c.otherName(), "chat-row-name"), Ui.label(c.bhName(), "secondary"), Ui.label(c.lastMessage(), "preview"));
                HBox.setHgrow(mid, Priority.ALWAYS);
                VBox right = Ui.vbox(4, Ui.label(time, "secondary"), unread); right.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
                HBox row = Ui.hbox(12, Ui.avatar(c.otherName(), 44), mid, right);
                row.getStyleClass().addAll("card", "chat-row"); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT); row.setCursor(javafx.scene.Cursor.HAND);
                if (c.unread() > 0) row.getStyleClass().add("chat-row-unread");
                String other = c.studentId().equals(me) ? c.ownerId() : c.studentId();
                // owners see the student's name in the chat header; students see the boarding house name
                String title = c.ownerId().equals(me) && !c.otherName().isBlank() ? c.otherName() : c.bhName();
                row.setOnMouseClicked(e -> Nav.show(new ChatView(c.id(), title, null, other, () -> Nav.show(Main.home(), true)), true));
                rows.getChildren().add(row);
            }
        });
    }
}
