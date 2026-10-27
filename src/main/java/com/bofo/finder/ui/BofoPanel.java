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
    /** Fixed 46x46 box around the mascot: idle poses move and overflow (z's, bubble) WITHOUT resizing the button or shifting the UI. */
    private final Pane faceBox = new Pane(face);
    private final VBox chat = new VBox(8);
    private final VBox messages = new VBox(8);
    private final TextField input = new TextField();
    private final BofoAiService ai = new BofoAiService();
    private double dx, dy; private boolean dragged;
    private long lastAsk = 0; private int asksThisMinute = 0;
    private boolean greeted = false;
    // idle moods: after a quiet spell the chat head thinks, sits, runs a circle or sleeps for a few seconds, then rests again
    private final TranslateTransition bob = new TranslateTransition(Duration.seconds(1.2), fab);
    private final PauseTransition idleTimer = new PauseTransition();
    private final java.util.Random rnd = new java.util.Random();
    private Mascot.Idle lastIdle;
    private boolean idling, busy, pressing;
    private final EventHandler<MouseEvent> outsideClose = e -> {
        if (!chat.isVisible()) return;
        if (e.getTarget() instanceof Node n && (isInside(n, chat) || isInside(n, fab))) return;
        chat.setVisible(false); scheduleIdle();
    };
    private static boolean isInside(Node n, Node root) {
        for (Node x = n; x != null; x = x.getParent()) if (x == root) return true;
        return false;
    }

    public BofoPanel() {
        setPickOnBounds(false);
        fab.getStyleClass().add("bofo-fab");
        faceBox.setMinSize(46, 46); faceBox.setPrefSize(46, 46); faceBox.setMaxSize(46, 46);
        fab.setGraphic(faceBox); fab.setStyle("-fx-padding:5;");
        fab.setMinSize(56, 56); fab.setPrefSize(56, 56); fab.setMaxSize(56, 56);   // never grows, whatever the mascot does
        widthProperty().addListener((o, a, w) -> fit());      // re-fit on rotate / resize / first layout
        heightProperty().addListener((o, a, h) -> fit());
        bob.setByY(-6); bob.setAutoReverse(true); bob.setCycleCount(Animation.INDEFINITE); bob.play();
        visibleProperty().addListener((o, a, v) -> { if (v) scheduleIdle(); else stopIdle(false); });

        fab.setOnMousePressed(e -> { pressing = true; stopIdle(false); dx = e.getSceneX() - fab.getLayoutX(); dy = e.getSceneY() - fab.getLayoutY(); dragged = false; });
        fab.setOnMouseDragged(e -> {
            dragged = true; userMoved = true;
            fab.setLayoutX(clamp(e.getSceneX() - dx, 0, getWidth() - 56));
            fab.setLayoutY(clamp(e.getSceneY() - dy, 40, getHeight() - 140));
        });
        fab.setOnMouseReleased(e -> {
            pressing = false;
            if (!dragged) { toggle(); return; }
            scheduleIdle();
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
            if (newS == null) stopIdle(false); else scheduleIdle();
        });
    }

    private boolean placed, userMoved;
    /** Keeps the mascot on screen and sizes the chat panel to the screen (phone, tablet, landscape). */
    private void fit() {
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        double s = w < 400 ? 0.82 : 1;                         // smaller on small phones so it covers less
        fab.setScaleX(s); fab.setScaleY(s);
        if (!placed || !userMoved) { fab.setLayoutX(w - 62); fab.setLayoutY(Math.max(40, h - 166)); placed = true; }   // right above the tab bar
        else { fab.setLayoutX(clamp(fab.getLayoutX(), 0, w - 56)); fab.setLayoutY(clamp(fab.getLayoutY(), 40, Math.max(40, h - 140))); }
        double cw = Math.min(380, w - 24), ch = Math.max(220, Math.min(500, h - 230));
        chat.setMinSize(cw, ch); chat.setPrefSize(cw, ch); chat.setMaxSize(cw, ch);
        chat.setLayoutX((w - cw) / 2); chat.setLayoutY(Math.max(12, h - ch - 150));
    }

    // ---- idle moods ----
    private boolean canIdle() { return isVisible() && getScene() != null && !chat.isVisible() && !busy && !pressing; }

    /** Waits 4-10 s of quiet, then starts a mood. Safe to call any time; does nothing while the chat is open or BOFO is answering. */
    private void scheduleIdle() {
        if (idling) return;
        idleTimer.stop();
        if (!canIdle()) return;
        idleTimer.setDuration(Duration.seconds(4 + rnd.nextDouble() * 6));
        idleTimer.setOnFinished(e -> startIdle());
        idleTimer.playFromStart();
    }

    private void startIdle() {
        if (!canIdle()) return;
        Mascot.Idle[] all = Mascot.Idle.values();
        Mascot.Idle pick;
        do { pick = all[rnd.nextInt(all.length)]; } while (pick == lastIdle);     // never the same mood twice in a row
        lastIdle = pick;
        bob.stop(); fab.setTranslateY(0);
        face.idle(pick); idling = true;
        double secs = switch (pick) { case SLEEPING -> 12 + rnd.nextDouble() * 4; case RUNNING -> 5.4; case THINKING -> 7; case SITTING -> 8; };
        idleTimer.setDuration(Duration.seconds(secs));
        idleTimer.setOnFinished(e -> stopIdle(true));
        idleTimer.playFromStart();
    }

    /** Ends the current mood (a tap, drag, open chat or question ends it at once) and optionally plans the next one. */
    private void stopIdle(boolean reschedule) {
        idleTimer.stop();
        if (idling) { face.endIdle(); idling = false; bob.play(); }
        if (reschedule) scheduleIdle();
    }

    private void toggle() {
        chat.setVisible(!chat.isVisible());
        if (chat.isVisible()) stopIdle(false); else scheduleIdle();
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
        busy = true; stopIdle(false);
        face.set(Mascot.Expression.THINKING);
        String collegeId = State.college.get() == null ? null : State.college.get().id();
        Async.run(() -> ai.ask(q, collegeId), a -> {
            busy = false; face.set(Mascot.Expression.HAPPY); scheduleIdle(); thinking.setText(a.reply());
            if (a.filters() != null && !a.filters().isEmpty()) filtersListener.accept(a.filters());
        }, err -> { busy = false; face.set(Mascot.Expression.HAPPY); scheduleIdle(); thinking.setText(BofoAiService.faq(q)); });
    }

    private Label bubble(String text, boolean me) {
        Label l = new Label(text); l.setWrapText(true); l.maxWidthProperty().bind(Bindings.max(160, chat.widthProperty().multiply(0.72)));
        HBox wrap = new HBox(new StackPane(l)); wrap.getChildren().get(0).getStyleClass().add(me ? "bubble-me" : "bubble-them");
        wrap.setAlignment(me ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messages.getChildren().add(wrap); return l;
    }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
