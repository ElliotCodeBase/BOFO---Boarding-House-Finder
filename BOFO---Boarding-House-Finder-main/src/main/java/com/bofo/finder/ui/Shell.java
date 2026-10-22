package com.bofo.finder.ui;

import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.function.Supplier;

/** Bottom-nav shell shared by Student / Owner / Admin. Tabs are built lazily. */
public class Shell extends BorderPane {
    protected final HBox nav = new HBox();
    protected final StackPane body = new StackPane();
    private final Button[] buttons;
    private final Supplier<Node>[] factories;
    private Button chatBtn; private String chatLabel;

    @SafeVarargs
    protected Shell(String[] labels, Supplier<Node>... tabs) {
        factories = tabs; buttons = new Button[labels.length];
        Label offline = Ui.label("You're offline — showing cached data", "secondary");
        offline.getStyleClass().add("offline-banner");
        offline.managedProperty().bind(State.offline); offline.visibleProperty().bind(State.offline);
        nav.getStyleClass().add("nav-bar");
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Button b = new Button(labels[i]); b.getStyleClass().add("nav-item"); b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS); buttons[i] = b; nav.getChildren().add(b);
            b.setOnAction(e -> select(idx));
            if (labels[i].startsWith("Chats")) { chatBtn = b; chatLabel = labels[i]; }
        }
        setTop(offline); setCenter(body); setBottom(nav);
        select(0);
    }

    public final void select(int i) {
        for (int j = 0; j < buttons.length; j++) { buttons[j].getStyleClass().setAll("button", j == i ? "nav-item-active" : "nav-item"); }
        body.getChildren().setAll(factories[i].get());
    }

    /** Unread badge on the Chats tab. */
    public final void refreshBadge(ChatService chat) {
        if (chatBtn == null) return;
        Async.run(chat::totalUnread, n -> chatBtn.setText(n > 0 ? chatLabel + " (" + n + ")" : chatLabel), e -> {});
    }
}
