package com.bofo.finder.ui;

import com.bofo.finder.app.Main;
import com.bofo.finder.model.Conversation;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import javafx.geometry.Insets;
import javafx.scene.layout.*;

/** Inbox: every conversation sorted by latest message, with BH name, preview, time and unread count. */
public final class ChatsView extends VBox {
    public ChatsView() {
        ChatService chat = new ChatService();
        VBox rows = new VBox(8); rows.setPadding(new Insets(12, 16, 90, 16));
        getChildren().addAll(Ui.appBar("Chats"), Ui.scroll(rows));
        Async.run(chat::inbox, list -> {
            if (list.isEmpty()) rows.getChildren().add(Ui.label("No conversations yet. Open a boarding house and tap “Chat with owner”.", "secondary"));
            String me = AuthService.get().userId();
            for (Conversation c : list) {
                String time = c.lastAt().length() >= 16 ? c.lastAt().substring(11, 16) : "";
                HBox row = Ui.hbox(10,
                        Ui.vbox(2, Ui.label(c.otherName(), "h2"), Ui.label("🏠 " + c.bhName(), "secondary"), Ui.label(c.lastMessage(), "label")),
                        Ui.grow(), Ui.vbox(4, Ui.label(time, "secondary"), c.unread() > 0 ? Ui.label(String.valueOf(c.unread()), "badge-hot") : new javafx.scene.control.Label()));
                row.getStyleClass().add("card");
                String other = c.studentId().equals(me) ? c.ownerId() : c.studentId();
                row.setOnMouseClicked(e -> Nav.show(new ChatView(c.id(), c.bhName(), null, other, () -> Nav.show(Main.home(), true)), true));
                rows.getChildren().add(row);
            }
        }, e -> rows.getChildren().add(Ui.label("Could not load chats (offline?).", "error-text")));
    }
}
