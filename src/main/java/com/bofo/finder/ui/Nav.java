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
    private static final StackPane SCREEN = new StackPane();
    private static final BofoPanel BOFO = new BofoPanel();
    private static boolean mascotAllowed = false;

    static { ROOT.getChildren().addAll(SCREEN, BOFO); }
    private Nav() {}

    public static StackPane root() { return ROOT; }

    /** @param withMascot the floating BOFO stays on top of every logged-in screen */
    public static void show(Node screen, boolean withMascot) {
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

    public static void toast(String msg) {
        Label l = new Label(msg);
        l.getStyleClass().add("toast");
        StackPane.setAlignment(l, Pos.BOTTOM_CENTER);
        StackPane.setMargin(l, new javafx.geometry.Insets(0, 0, 96, 0));
        ROOT.getChildren().add(l);
        PauseTransition t = new PauseTransition(Duration.seconds(2.5));
        t.setOnFinished(e -> ROOT.getChildren().remove(l)); t.play();
    }
}
