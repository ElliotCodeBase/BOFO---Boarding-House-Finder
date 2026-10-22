package com.bofo.finder.ui;

import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.function.Supplier;

/** Shared shell for Student / Owner / Admin: content + a floating pill navigation bar with icons. Tabs are built lazily. */
public class Shell extends BorderPane {
    private static final Color IDLE = Color.web("#6A647C");
    protected final HBox nav = new HBox(6);
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

        nav.getStyleClass().add("nav-float"); nav.setAlignment(Pos.CENTER); nav.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        badge.getStyleClass().add("nav-badge"); badge.setVisible(false); badge.setMouseTransparent(true);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            icons[i] = Icons.icon(Icons.forLabel(labels[i]), 22, IDLE);
            Button b = new Button(); b.setGraphic(icons[i]); b.getStyleClass().add("nav-btn");
            b.setTooltip(new Tooltip(labels[i])); b.setOnAction(e -> select(idx));
            buttons[i] = b;
            if (labels[i].startsWith("Chats")) {
                hasChat = true;
                StackPane cell = new StackPane(b, badge);
                StackPane.setAlignment(badge, Pos.TOP_RIGHT); StackPane.setMargin(badge, new Insets(2, 0, 0, 0));
                nav.getChildren().add(cell);
            } else nav.getChildren().add(b);
        }
        StackPane center = new StackPane(body, nav);
        StackPane.setAlignment(nav, Pos.BOTTOM_CENTER); StackPane.setMargin(nav, new Insets(0, 0, 14, 0));
        setTop(offline); setCenter(center);
        select(0);
    }

    public final void select(int i) {
        for (int j = 0; j < buttons.length; j++) {
            buttons[j].getStyleClass().setAll("button", j == i ? "nav-btn-active" : "nav-btn");
            Icons.setColor(icons[j], j == i ? Color.WHITE : IDLE);
        }
        body.getChildren().setAll(factories[i].get());
    }

    /** Unread count badge on the Chats icon. */
    public final void refreshBadge(ChatService chat) {
        if (!hasChat) return;
        Async.run(chat::totalUnread, n -> { badge.setText(n > 9 ? "9+" : String.valueOf(n)); badge.setVisible(n > 0); }, e -> {});
    }
}
