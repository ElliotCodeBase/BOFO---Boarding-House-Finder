package com.bofo.finder.ui;

import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.function.Supplier;

/** Shared shell for Student / Owner / Admin: content on top, full-width bottom tab bar (icon + label). Tabs are built lazily. */
public class Shell extends BorderPane {
    private static final Color IDLE = Color.web("#5E5A70");
    private static final Color ACTIVE = Color.web("#5B34B8");
    protected final HBox nav = new HBox(4);
    protected final StackPane body = new StackPane();
    private final Button[] buttons;
    private final Node[] icons;
    private final Supplier<Node>[] factories;
    private final Label badge = new Label();
    private boolean hasChat = false;

    @SafeVarargs
    protected Shell(String[] labels, Supplier<Node>... tabs) {
        factories = tabs; buttons = new Button[labels.length]; icons = new Node[labels.length];
        Label offline = Ui.label("You're offline - showing cached data", "secondary");
        offline.getStyleClass().add("offline-banner"); offline.setMaxWidth(Double.MAX_VALUE);
        offline.managedProperty().bind(State.offline); offline.visibleProperty().bind(State.offline);

        nav.getStyleClass().add("tab-bar"); nav.setAlignment(Pos.CENTER);
        badge.getStyleClass().add("nav-badge"); badge.setVisible(false); badge.setMouseTransparent(true);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            icons[i] = Icons.icon(Icons.forLabel(labels[i]), 22, IDLE);
            Button b = new Button(labels[i]); b.setGraphic(icons[i]); b.setContentDisplay(ContentDisplay.TOP);
            b.getStyleClass().add("tab-btn"); b.setOnAction(e -> select(idx));
            b.setMinWidth(0); b.setPrefWidth(10); b.setMaxWidth(Double.MAX_VALUE);
            buttons[i] = b;
            if (labels[i].startsWith("Chats")) {
                hasChat = true;
                StackPane cell = new StackPane(b, badge);
                cell.setMinWidth(0); cell.setPrefWidth(10); cell.setMaxWidth(Double.MAX_VALUE);
                StackPane.setAlignment(badge, Pos.TOP_CENTER); badge.setTranslateX(14); StackPane.setMargin(badge, new Insets(2, 0, 0, 0));
                HBox.setHgrow(cell, Priority.ALWAYS);
                nav.getChildren().add(cell);
            } else {
                HBox.setHgrow(b, Priority.ALWAYS);
                nav.getChildren().add(b);
            }
        }
        setTop(offline); setCenter(body); setBottom(nav);
        select(0);
    }

    public final void select(int i) {
        for (int j = 0; j < buttons.length; j++) {
            buttons[j].getStyleClass().setAll("button", j == i ? "tab-btn-active" : "tab-btn");
            Icons.setColor(icons[j], j == i ? ACTIVE : IDLE);
        }
        body.getChildren().setAll(factories[i].get());
    }

    /** Unread count badge on the Chats icon. */
    public final void refreshBadge(ChatService chat) {
        if (!hasChat) return;
        Async.run(chat::totalUnread, n -> { badge.setText(n > 9 ? "9+" : String.valueOf(n)); badge.setVisible(n > 0); }, e -> {});
    }
}
