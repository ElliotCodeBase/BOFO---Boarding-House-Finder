package com.bofo.finder.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/** Screen host: one active screen + floating BOFO mascot + toast overlay. */
public final class Nav {
    private static final StackPane ROOT = new StackPane();
    private static final StackPane SCREEN = new FitPane();
    private static final BofoPanel BOFO = new BofoPanel();
    private static boolean mascotAllowed = false;
    /** Widest the app column gets; on tablets / landscape / desktop the column is centred and the sides stay page-colour. */
    private static final double MAX_COLUMN = 840;

    static {
        SCREEN.setMaxWidth(MAX_COLUMN); ROOT.getChildren().addAll(SCREEN, BOFO);
        keyboardAware();
    }

    /**
     * Phones: when the on-screen keyboard opens, lift the whole app above it (so the field you type in and the Send / Sign in
     * button stay visible) and scroll the focused text field into view. Does nothing on a PC (no keyboard service).
     */
    private static void keyboardAware() {
        try {
            com.gluonhq.attach.keyboard.KeyboardService.create().ifPresent(k ->
                k.visibleHeightProperty().addListener((o, was, now) -> javafx.application.Platform.runLater(() -> {
                    double h = now.doubleValue();
                    double max = ROOT.getHeight() * 0.6;                     // ignore nonsense values
                    ROOT.setPadding(new javafx.geometry.Insets(0, 0, h > 0 ? Math.min(h, max) : 0, 0));
                    revealFocused();
                })));
        } catch (Throwable noKeyboardService) { /* desktop */ }
        ROOT.sceneProperty().addListener((o, was, sc) -> {
            if (sc != null) sc.focusOwnerProperty().addListener((oo, a, focus) -> { if (focus instanceof javafx.scene.control.TextInputControl) revealFocused(); });
        });
    }

    /** Scrolls the ScrollPane that holds the focused text field so the field sits in the upper part of the visible area. */
    private static void revealFocused() {
        PauseTransition t = new PauseTransition(Duration.millis(220));        // wait until the keyboard animation has resized the window
        t.setOnFinished(e -> {
            if (ROOT.getScene() == null || !(ROOT.getScene().getFocusOwner() instanceof javafx.scene.control.TextInputControl f)) return;
            Node n = f.getParent();
            while (n != null && !(n instanceof javafx.scene.control.ScrollPane)) n = n.getParent();
            if (!(n instanceof javafx.scene.control.ScrollPane sp) || sp.getContent() == null) return;
            Node content = sp.getContent();
            double contentH = content.getLayoutBounds().getHeight(), viewH = sp.getViewportBounds().getHeight();
            if (contentH <= viewH + 1) return;
            javafx.geometry.Bounds b = content.sceneToLocal(f.localToScene(f.getBoundsInLocal()));
            if (b == null) return;
            double want = b.getMinY() - viewH * 0.25;                          // field about a quarter down from the top
            sp.setVvalue(Math.max(0, Math.min(1, want / (contentH - viewH))));
        });
        t.play();
    }
    private Nav() {}

    public static StackPane root() { return ROOT; }

    /** @param withMascot the floating BOFO stays on top of every logged-in screen */
    public static void show(Node screen, boolean withMascot) {
        SCREEN.setMaxWidth(screen instanceof SplashView ? Double.MAX_VALUE : MAX_COLUMN);
        if (screen instanceof javafx.scene.layout.Region r) r.setMinWidth(0);   // never wider than the window (320 px phones)
        SCREEN.getChildren().setAll(screen);
        if (!(screen instanceof SplashView)) {                       // soft fade-in for every real screen
            screen.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(260), screen); ft.setToValue(1); ft.play();
        }
        mascotAllowed = withMascot;
        BOFO.setVisible(withMascot && BofoPanel.enabled());
    }
    public static void refreshMascot() { BOFO.setVisible(mascotAllowed && BofoPanel.enabled()); }
    public static BofoPanel bofo() { return BOFO; }

    /** Puts a full-screen overlay (bottom sheet, reader) on top of the current screen and the mascot. */
    public static void pushOverlay(Node overlay) { ROOT.getChildren().add(overlay); }
    public static void popOverlay(Node overlay) { ROOT.getChildren().remove(overlay); }

    public static void toast(String msg) {
        Label l = new Label(msg);
        l.getStyleClass().add("toast");
        StackPane.setAlignment(l, Pos.BOTTOM_CENTER);
        StackPane.setMargin(l, new javafx.geometry.Insets(0, 0, 80, 0));
        ROOT.getChildren().add(l);
        PauseTransition t = new PauseTransition(Duration.seconds(2.5));
        t.setOnFinished(e -> ROOT.getChildren().remove(l)); t.play();
    }
}
