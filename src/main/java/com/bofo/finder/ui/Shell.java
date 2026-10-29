package com.bofo.finder.ui;

import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import javafx.animation.*;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.function.Supplier;

/**
 * Shared shell for Student / Owner / Admin: content on top, floating white tab bar at the bottom.
 * Each tab is an outline icon with a label under it. A soft lavender highlight sits behind the active tab and,
 * when you press another tab, stretches over to it (the leading edge moves first, the trailing edge follows)
 * while the icon and label fade from pale to strong. Tabs are built lazily.
 * One tab can be an "action" tab (e.g. Create): it slides the highlight over, then runs the action instead of switching content.
 */
public class Shell extends BorderPane {
    /** Tab the next Shell opens on (set before Main.route() when coming back from a sub-screen). */
    public static int pendingTab = -1;

    private static final Color IDLE = Color.web("#AAA3DC");
    private static final Color ACTIVE = Color.web("#5B3BF0");
    private static final double BAR_H = 68, TAB_H = 54, PAD = 8, EDGE = 16, MIN_TAB = 54, MAX_TAB = 84, INSET = 3, TOP = 18;   // TOP = room above the bar for the raised Create button

    protected final StackPane body = new FitPane();
    private final Pane dock = new Pane();                     // absolute layout: bar, highlight, tab row
    private final Region bar = new Region(), highlight = new Region();
    private final HBox row = new HBox();
    private final DoubleProperty left = new SimpleDoubleProperty(), right = new SimpleDoubleProperty();
    private final DockTab[] tabs;
    private final StackPane[] cells;
    private final Supplier<Node>[] factories;
    private final Label badge = new Label();
    private double tabW = 72, lastAvail = 328;
    private boolean hasChat = false;
    private int current = -1, actionIndex = -1;
    private Runnable action;
    private Timeline hlAnim;

    /** One tab: outline icon + label. progress 0 = idle (pale), 1 = active (strong, bold). */
    private static final class DockTab extends Button {
        private final DockIcon icon;
        private final Text label = new Text();
        private final DoubleProperty progress = new SimpleDoubleProperty(0);
        private Timeline anim;
        private boolean bulge;

        DockTab(String name, String[] def) {
            icon = new DockIcon(def, IDLE, ACTIVE);
            label.setText(name); label.setMouseTransparent(true);
            VBox v = new VBox(3, icon, label); v.setAlignment(Pos.CENTER); v.setMouseTransparent(true);
            setGraphic(v); getStyleClass().add("dock-btn");
            progress.addListener((o, a, b) -> apply(b.doubleValue()));
            apply(0);
        }

        /** Turns this tab into the raised blue square action button (Create). */
        void makeBulge() {
            bulge = true;
            SVGPath plus = new SVGPath(); plus.setContent("M12 5v14M5 12h14"); plus.setFill(null); plus.setStroke(Color.WHITE);
            plus.setStrokeWidth(2.6); plus.setStrokeLineCap(StrokeLineCap.ROUND); plus.setMouseTransparent(true);
            Pane glyph = new Pane(plus); glyph.setMinSize(24, 24); glyph.setPrefSize(24, 24); glyph.setMaxSize(24, 24);
            StackPane sq = new StackPane(glyph); sq.getStyleClass().add("dock-create");
            sq.setMinSize(50, 50); sq.setPrefSize(50, 50); sq.setMaxSize(50, 50);
            VBox v = new VBox(4, sq, label); v.setAlignment(Pos.CENTER); v.setMouseTransparent(true); v.setTranslateY(-14);
            setGraphic(v); apply(1);
        }

        private void apply(double p) {
            if (bulge) { label.setStyle("-fx-fill:#2563EB;-fx-font-size:11px;-fx-font-weight:bold;"); return; }
            icon.setProgress(p);
            Color c = IDLE.interpolate(ACTIVE, p);
            label.setStyle("-fx-fill:" + Ui.hex(c) + ";-fx-font-size:11px;-fx-font-weight:" + (p > 0.5 ? "bold" : "normal") + ";");
        }

        void setActive(boolean on, boolean animate) {
            if (anim != null) anim.stop();
            if (!animate) { progress.set(on ? 1 : 0); return; }
            anim = new Timeline(new KeyFrame(Duration.millis(260), new KeyValue(progress, on ? 1 : 0, Interpolator.EASE_BOTH)));
            anim.play();
        }
    }

    @SafeVarargs
    protected Shell(String[] labels, Supplier<Node>... pages) {
        setMinWidth(0); body.setMinWidth(0);                  // a screen may never be wider than the window
        factories = pages; tabs = new DockTab[labels.length]; cells = new StackPane[labels.length];
        // offline notice: one slim plain line, like a system status bar (no icon, no card)
        Label offline = new Label("No connection. Showing saved places.");
        offline.getStyleClass().add("offline-bar"); offline.setMaxWidth(Double.MAX_VALUE); offline.setMinWidth(0);
        offline.managedProperty().bind(State.offline); offline.visibleProperty().bind(State.offline);

        badge.getStyleClass().add("nav-badge"); badge.setVisible(false); badge.setMouseTransparent(true);
        row.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            tabs[i] = new DockTab(labels[i], Icons.outlineFor(labels[i]));
            tabs[i].setOnAction(e -> select(idx));
            StackPane cell = new StackPane(tabs[i]); cells[i] = cell;
            if (labels[i].startsWith("Chats")) {
                hasChat = true;
                cell.getChildren().add(badge);
                StackPane.setAlignment(badge, Pos.TOP_RIGHT); StackPane.setMargin(badge, new Insets(2, 12, 0, 0));
            }
            row.getChildren().add(cell);
        }

        bar.getStyleClass().add("dock-bar");
        bar.setEffect(new DropShadow(24, 0, 8, Color.rgb(91, 59, 240, 0.18)));       // soft lavender glow, like the reference
        highlight.getStyleClass().add("dock-highlight"); highlight.setMouseTransparent(true);
        left.addListener((o, a, b) -> placeHighlight()); right.addListener((o, a, b) -> placeHighlight());
        dock.getChildren().addAll(bar, highlight, row);

        StackPane wrap = new StackPane(dock); wrap.setPadding(new Insets(0, EDGE, 14, EDGE)); wrap.setPickOnBounds(false);
        setTop(offline); setCenter(body); setBottom(wrap);
        relayout(lastAvail);
        // responsive: the bar re-fits whenever the available width changes (rotation, tablet, window resize)
        wrap.widthProperty().addListener((o, a, w) -> {
            double avail = w.doubleValue() - 2 * EDGE;
            if (avail > 0 && Math.abs(avail - lastAvail) > 0.5) relayout(avail);
        });
        int start = pendingTab >= 0 && pendingTab < labels.length ? pendingTab : 0;
        pendingTab = -1;
        select(start);

        // one short entrance: the bar rises into place
        dock.setTranslateY(36); dock.setOpacity(0);
        TranslateTransition rise = new TranslateTransition(Duration.millis(320), dock); rise.setToY(0);
        rise.setInterpolator(Interpolator.SPLINE(0.2, 0.8, 0.2, 1));
        FadeTransition fade = new FadeTransition(Duration.millis(240), dock); fade.setToValue(1);
        new ParallelTransition(rise, fade).play();
    }

    /** Makes the tab at index an action tab (e.g. "Create"): pressing it runs r instead of switching content. */
    protected final void setActionTab(int index, Runnable r) { actionIndex = index; action = r; tabs[index].makeBulge(); }

    /** Fits the bar into the available width: tabs between MIN_TAB and MAX_TAB wide, whole bar stays centred. */
    private void relayout(double avail) {
        lastAvail = avail;
        int n = tabs.length;
        tabW = Math.max(MIN_TAB, Math.min(MAX_TAB, (avail - 2 * PAD) / n));
        double barW = 2 * PAD + n * tabW;
        size(dock, barW, BAR_H + TOP); size(bar, barW, BAR_H); bar.setLayoutY(TOP);
        dock.setMinWidth(0);                                  // the bar may shrink with the window; a fixed minimum made the whole screen wider than a 320 px phone
        row.setLayoutX(PAD); row.setLayoutY(TOP + (BAR_H - TAB_H) / 2); size(row, n * tabW, TAB_H);
        for (int i = 0; i < n; i++) { size(cells[i], tabW, TAB_H); size(tabs[i], tabW - 2 * INSET, TAB_H); }
        if (hlAnim != null) hlAnim.stop();
        int at = Math.max(current, 0);
        left.set(edgeL(at)); right.set(edgeR(at));
        placeHighlight();
    }

    private double edgeL(int i) { return PAD + i * tabW + INSET; }
    private double edgeR(int i) { return PAD + (i + 1) * tabW - INSET; }
    private void placeHighlight() { highlight.resizeRelocate(left.get(), TOP + (BAR_H - TAB_H) / 2, Math.max(0, right.get() - left.get()), TAB_H); }

    public final void select(int i) {
        if (i == actionIndex) {                               // action tab: highlight slides over, then the action runs
            if (action != null) action.run();
            return;
        }
        boolean animate = current >= 0;
        for (int j = 0; j < tabs.length; j++) tabs[j].setActive(j == i, animate && (j == i || j == current));
        moveHighlight(i, animate);
        current = i;
        body.getChildren().setAll(factories[i].get());
    }

    /** Liquid move: the edge in the direction of travel goes first and fast, the other edge catches up. */
    private void moveHighlight(int i, boolean animate) {
        double nl = edgeL(i), nr = edgeR(i);
        if (hlAnim != null) hlAnim.stop();
        if (!animate) { left.set(nl); right.set(nr); return; }
        boolean toRight = nl > left.get();
        Duration lead = Duration.millis(190), trail = Duration.millis(340);
        Interpolator out = Interpolator.SPLINE(0.2, 0.8, 0.2, 1), both = Interpolator.SPLINE(0.4, 0, 0.2, 1);
        hlAnim = new Timeline(
                new KeyFrame(toRight ? trail : lead, new KeyValue(left, nl, toRight ? both : out)),
                new KeyFrame(toRight ? lead : trail, new KeyValue(right, nr, toRight ? out : both)));
        hlAnim.play();
    }

    private static void size(Region n, double w, double h) {
        n.setMinSize(w, h); n.setPrefSize(w, h); n.setMaxSize(w, h);
    }

    /** Unread count badge on the Chats icon. */
    public final void refreshBadge(ChatService chat) {
        if (!hasChat) return;
        Async.run(chat::totalUnread, n -> { badge.setText(n > 9 ? "9+" : String.valueOf(n)); badge.setVisible(n > 0); }, e -> {});
    }
}
