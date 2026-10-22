package com.bofo.finder.ui;

import com.bofo.finder.service.BofoAiService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Validate;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.json.JSONObject;

import java.util.function.Consumer;

/** Floating BOFO mascot: draggable, snaps to edge, idle bounce, opens the AI chat panel. */
public final class BofoPanel extends Pane {
    private static boolean enabled = true;
    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean e) { enabled = e; Nav.refreshMascot(); }

    /** ExploreView registers here so "cheap BH with free wifi" applies real filters. */
    public static Consumer<JSONObject> filtersListener = f -> {};

    private final Button fab = new Button();
    private final Mascot face = new Mascot(Mascot.Expression.HAPPY, 46);
    private final VBox chat = new VBox(8);
    private final VBox messages = new VBox(8);
    private final TextField input = new TextField();
    private final BofoAiService ai = new BofoAiService();
    private double dx, dy; private boolean dragged;
    private long lastAsk = 0; private int asksThisMinute = 0;
    private boolean greeted = false;

    public BofoPanel() {
        setPickOnBounds(false);
        fab.getStyleClass().add("bofo-fab");
        fab.setGraphic(face); fab.setStyle("-fx-padding:4;");
        fab.setLayoutX(290); fab.setLayoutY(520);
        TranslateTransition idle = new TranslateTransition(Duration.seconds(1.2), fab);
        idle.setByY(-6); idle.setAutoReverse(true); idle.setCycleCount(Animation.INDEFINITE); idle.play();

        fab.setOnMousePressed(e -> { dx = e.getSceneX() - fab.getLayoutX(); dy = e.getSceneY() - fab.getLayoutY(); dragged = false; });
        fab.setOnMouseDragged(e -> {
            dragged = true;
            fab.setLayoutX(clamp(e.getSceneX() - dx, 0, getWidth() - 56));
            fab.setLayoutY(clamp(e.getSceneY() - dy, 40, getHeight() - 140));
        });
        fab.setOnMouseReleased(e -> {
            if (!dragged) { toggle(); return; }
            double target = fab.getLayoutX() + 28 < getWidth() / 2 ? 6 : getWidth() - 62;   // snap to nearest edge
            Timeline t = new Timeline(new KeyFrame(Duration.millis(180), new KeyValue(fab.layoutXProperty(), target)));
            t.play();
        });

        chat.getStyleClass().add("card");
        chat.setPadding(new Insets(12)); chat.setVisible(false);
        chat.setPrefSize(320, 420); chat.setLayoutX(20); chat.setLayoutY(120);
        Label head = Ui.label("BOFO", "h2");
        Button close = Ui.btn("✕", "heart", this::toggle);
        ScrollPane sp = Ui.scroll(messages); VBox.setVgrow(sp, Priority.ALWAYS);
        input.setPromptText("Ask BOFO…"); HBox.setHgrow(input, Priority.ALWAYS);
        Button send = Ui.btn("Send", "btn-primary", this::send);
        input.setOnAction(e -> send());
        Label note = Ui.label("BOFO can make mistakes. Check details with the owner.", "secondary");
        chat.getChildren().addAll(Ui.hbox(8, head, Ui.grow(), close), sp, note, Ui.hbox(6, input, send));
        getChildren().addAll(chat, fab);
        messages.setPadding(new Insets(4));
    }

    private void toggle() {
        chat.setVisible(!chat.isVisible());
        if (chat.isVisible() && !greeted) { greeted = true; bubble("Hi! I'm BOFO 👋 Ask me to find a boarding house, compare listings, or explain verification.", false); }
    }

    private void send() {
        String q = Validate.clean(input.getText(), 300);
        if (q.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastAsk > 60_000) { asksThisMinute = 0; lastAsk = now; }
        if (++asksThisMinute > 10) { bubble("Slow down a little 🙂 Try again in a minute.", false); return; }  // client-side courtesy; real limit is server-side
        input.clear(); bubble(q, true);
        Label thinking = bubble("…", false);
        face.set(Mascot.Expression.THINKING);
        String collegeId = State.college.get() == null ? null : State.college.get().id();
        Async.run(() -> ai.ask(q, collegeId), a -> {
            face.set(Mascot.Expression.HAPPY); thinking.setText(a.reply());
            if (a.filters() != null && !a.filters().isEmpty()) filtersListener.accept(a.filters());
        }, err -> { face.set(Mascot.Expression.HAPPY); thinking.setText(BofoAiService.faq(q)); });
    }

    private Label bubble(String text, boolean me) {
        Label l = new Label(text); l.setWrapText(true); l.setMaxWidth(240);
        HBox wrap = new HBox(new StackPane(l)); wrap.getChildren().get(0).getStyleClass().add(me ? "bubble-me" : "bubble-them");
        wrap.setAlignment(me ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messages.getChildren().add(wrap); return l;
    }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
