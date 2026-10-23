package com.bofo.finder.ui;

import com.bofo.finder.service.BofoAiService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Validate;
import javafx.animation.*;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.json.JSONObject;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;

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
    private final EventHandler<MouseEvent> outsideClose = e -> {
        if (!chat.isVisible()) return;
        if (e.getTarget() instanceof Node n && (isInside(n, chat) || isInside(n, fab))) return;
        chat.setVisible(false);
    };
    private static boolean isInside(Node n, Node root) {
        for (Node x = n; x != null; x = x.getParent()) if (x == root) return true;
        return false;
    }

    public BofoPanel() {
        setPickOnBounds(false);
        fab.getStyleClass().add("bofo-fab");
        fab.setGraphic(face); fab.setStyle("-fx-padding:4;");
        widthProperty().addListener((o, a, w) -> fit());      // re-fit on rotate / resize / first layout
        heightProperty().addListener((o, a, h) -> fit());
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
        Label head = Ui.label("BOFO", "h2");
        Button close = Ui.btn("X", "heart", this::toggle);
        ScrollPane sp = Ui.scroll(messages); VBox.setVgrow(sp, Priority.ALWAYS);
        input.setPromptText("Ask BOFO..."); HBox.setHgrow(input, Priority.ALWAYS);
        Button send = Ui.btn("Send", "btn-primary", this::send);
        input.setOnAction(e -> send());
        Label note = Ui.label("BOFO can make mistakes. Check details with the owner.", "secondary");
        chat.getChildren().addAll(Ui.hbox(8, head, Ui.grow(), close), sp, note, Ui.hbox(6, input, send));
        getChildren().addAll(chat, fab);
        messages.setPadding(new Insets(4));
        sceneProperty().addListener((obs, oldS, newS) -> {
            if (oldS != null) oldS.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClose);
            if (newS != null) newS.addEventFilter(MouseEvent.MOUSE_PRESSED, outsideClose);
        });
    }

    private boolean placed;
    /** Keeps the mascot on screen and sizes the chat panel to the screen (phone, tablet, landscape). */
    private void fit() {
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (!placed) { fab.setLayoutX(w - 62); fab.setLayoutY(Math.max(40, h - 200)); placed = true; }
        else { fab.setLayoutX(clamp(fab.getLayoutX(), 0, w - 56)); fab.setLayoutY(clamp(fab.getLayoutY(), 40, Math.max(40, h - 140))); }
        double cw = Math.min(380, w - 24), ch = Math.max(220, Math.min(500, h - 230));
        chat.setMinSize(cw, ch); chat.setPrefSize(cw, ch); chat.setMaxSize(cw, ch);
        chat.setLayoutX((w - cw) / 2); chat.setLayoutY(Math.max(12, h - ch - 150));
    }

    private void toggle() {
        chat.setVisible(!chat.isVisible());
        if (chat.isVisible() && !greeted) { greeted = true; bubble("Hi! I'm BOFO. Ask me to find a boarding house, compare listings, or explain verification.", false); }
    }

    private void send() {
        String q = Validate.clean(input.getText(), 300);
        if (q.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastAsk > 60_000) { asksThisMinute = 0; lastAsk = now; }
        if (++asksThisMinute > 10) { bubble("Slow down a little. Try again in a minute.", false); return; }  // client-side courtesy; real limit is server-side
        input.clear(); bubble(q, true);
        Label thinking = bubble("...", false);
        face.set(Mascot.Expression.THINKING);
        String collegeId = State.college.get() == null ? null : State.college.get().id();
        Async.run(() -> ai.ask(q, collegeId), a -> {
            face.set(Mascot.Expression.HAPPY); thinking.setText(a.reply());
            if (a.filters() != null && !a.filters().isEmpty()) filtersListener.accept(a.filters());
        }, err -> { face.set(Mascot.Expression.HAPPY); thinking.setText(BofoAiService.faq(q)); });
    }

    private Label bubble(String text, boolean me) {
        Label l = new Label(text); l.setWrapText(true); l.maxWidthProperty().bind(Bindings.max(160, chat.widthProperty().multiply(0.72)));
        HBox wrap = new HBox(new StackPane(l)); wrap.getChildren().get(0).getStyleClass().add(me ? "bubble-me" : "bubble-them");
        wrap.setAlignment(me ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messages.getChildren().add(wrap); return l;
    }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
