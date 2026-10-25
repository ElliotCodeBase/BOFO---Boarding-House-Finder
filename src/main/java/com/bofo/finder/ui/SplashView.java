package com.bofo.finder.ui;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.util.Duration;

/**
 * Loading screen shown when entering the app: a WHITE BOFO mascot jumps on a flat deep-purple background
 * (two jumps per cycle: squash -> stretch -> airtime -> landing; the hat drops off at the top and lands with the feet) with a soft ground shadow and three pulsing dots, while the saved session is checked.
 * Pure JavaFX shapes + Timeline, no image assets. Animates transform/opacity only.
 */
public final class SplashView extends VBox {
    private final Label status = Ui.label("Getting things ready", "splash-status");
    private final Timeline jump;

    public SplashView() {
        super(10); setAlignment(Pos.CENTER); getStyleClass().add("splash");

        Mascot mascot = new Mascot(Mascot.Expression.HAPPY, 172, true);
        javafx.scene.Group hat = mascot.hat();
        Ellipse shadow = new Ellipse(54, 8); shadow.setFill(Color.web("#8D7BFF", 0.32));   // light purple: a black shadow is invisible on the dark ink background
        // Feet stand on a ground line: the mascot is bottom-aligned, the shadow is centred on that line (just under the soles).
        final double ground = 12;
        StackPane stage = new StackPane(shadow, mascot);
        StackPane.setAlignment(shadow, Pos.BOTTOM_CENTER);
        StackPane.setMargin(shadow, new javafx.geometry.Insets(0, 0, ground - 6, 0));
        StackPane.setAlignment(mascot, Pos.BOTTOM_CENTER);
        StackPane.setMargin(mascot, new javafx.geometry.Insets(0, 0, ground, 0));
        stage.setMaxSize(230, 250); stage.setPrefSize(230, 250);
        // Squash / stretch pivots on the soles (the default pivot is the middle of the body, which lifted the feet off the ground).
        javafx.geometry.Bounds mb = mascot.getBoundsInLocal();
        javafx.scene.transform.Scale sq = new javafx.scene.transform.Scale(1, 1, mb.getCenterX(), mb.getMaxY());
        mascot.getTransforms().add(sq);

        // One cycle = TWO jumps (2.2 s), looped. Each jump starts and ends in the same squashed pose, so the loop is seamless.
        // Jump 1 is normal, jump 2 is a bit higher with a higher hat toss. In both, the hat comes off at the top and lands on the head with the feet.
        Interpolator out = Interpolator.SPLINE(0.2, 0.7, 0.3, 1), in = Interpolator.SPLINE(0.7, 0, 0.8, 0.3), pop = Interpolator.SPLINE(0.2, 0.9, 0.4, 1);
        java.util.List<KeyFrame> kf = new java.util.ArrayList<>();
        addJump(kf, 0,    -44, -20, -5, 0.70, mascot, sq, shadow, hat, out, in, pop);
        addJump(kf, 1100, -58, -26,  5, 0.60, mascot, sq, shadow, hat, out, in, pop);
        kf.add(new KeyFrame(Duration.millis(2200), new KeyValue(hat.translateYProperty(), 0), new KeyValue(hat.rotateProperty(), 0)));
        jump = new Timeline(kf.toArray(new KeyFrame[0]));
        jump.setCycleCount(Animation.INDEFINITE);

        Label name = Ui.label("BOFO", "splash-title");
        Label sub = Ui.label("Boarding House Finder", "splash-sub");
        HBox dots = new HBox(10); dots.setAlignment(Pos.CENTER); dots.setMinHeight(24);
        java.util.List<Animation> dotAnims = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Circle c = new Circle(5, Color.WHITE); c.setOpacity(0.3);
            FadeTransition f = new FadeTransition(Duration.millis(450), c); f.setFromValue(0.3); f.setToValue(1);
            TranslateTransition up = new TranslateTransition(Duration.millis(450), c); up.setFromY(2); up.setToY(-5);
            ParallelTransition w = new ParallelTransition(f, up);
            w.setAutoReverse(true); w.setCycleCount(Animation.INDEFINITE); w.setDelay(Duration.millis(i * 160));
            w.setInterpolator(Interpolator.EASE_BOTH);
            dotAnims.add(w); dots.getChildren().add(c);
        }
        status.setAlignment(Pos.CENTER); status.setStyle("-fx-text-alignment:center;");
        VBox.setMargin(dots, new javafx.geometry.Insets(12, 0, 0, 0));
        getChildren().addAll(stage, name, sub, dots, status);

        // Stop animating when removed from the scene (saves battery).
        sceneProperty().addListener((o, a, scene) -> {
            if (scene == null) { jump.stop(); dotAnims.forEach(Animation::stop); } else { jump.play(); dotAnims.forEach(Animation::play); }
        });
    }

    /**
     * Key frames of ONE jump starting at t0 ms (1.1 s long): crouch -> stretch -> apex (hat pops off) -> landing (hat lands) -> small press.
     * @param height   apex height of the mascot (negative = up), @param hatUp how far the hat lifts off the head (art-grid units, negative = up)
     */
    private static void addJump(java.util.List<KeyFrame> k, double t0, double height, double hatUp, double hatTilt, double shadowMin, Mascot m,
                                javafx.scene.transform.Scale sq, Ellipse sh, javafx.scene.Group hat, Interpolator out, Interpolator in, Interpolator pop) {
        k.add(new KeyFrame(Duration.millis(t0),        new KeyValue(m.translateYProperty(), 0),   new KeyValue(sq.xProperty(), 1.10), new KeyValue(sq.yProperty(), 0.90), new KeyValue(sh.scaleXProperty(), 1.05), new KeyValue(sh.scaleYProperty(), 1.05), new KeyValue(sh.opacityProperty(), 1.0), new KeyValue(hat.translateYProperty(), 0), new KeyValue(hat.rotateProperty(), 0)));
        k.add(new KeyFrame(Duration.millis(t0 + 180), new KeyValue(m.translateYProperty(), 0),   new KeyValue(sq.xProperty(), 0.94), new KeyValue(sq.yProperty(), 1.07)));
        k.add(new KeyFrame(Duration.millis(t0 + 560), new KeyValue(m.translateYProperty(), height, out), new KeyValue(sq.xProperty(), 1, out), new KeyValue(sq.yProperty(), 1, out), new KeyValue(sh.scaleXProperty(), shadowMin, out), new KeyValue(sh.scaleYProperty(), shadowMin, out), new KeyValue(sh.opacityProperty(), 0.55, out), new KeyValue(hat.translateYProperty(), 0), new KeyValue(hat.rotateProperty(), 0)));
        k.add(new KeyFrame(Duration.millis(t0 + 680), new KeyValue(hat.translateYProperty(), hatUp, pop), new KeyValue(hat.rotateProperty(), hatTilt, pop)));     // hat pops off
        k.add(new KeyFrame(Duration.millis(t0 + 950), new KeyValue(m.translateYProperty(), 0, in), new KeyValue(sq.xProperty(), 1.10, in), new KeyValue(sq.yProperty(), 0.90, in), new KeyValue(sh.scaleXProperty(), 1.05, in), new KeyValue(sh.scaleYProperty(), 1.05, in), new KeyValue(sh.opacityProperty(), 1.0, in), new KeyValue(hat.translateYProperty(), 0, in), new KeyValue(hat.rotateProperty(), 0, in)));   // hat lands with the feet
        k.add(new KeyFrame(Duration.millis(t0 + 1010), new KeyValue(hat.translateYProperty(), 2)));                                                                 // small press
    }

    public void setStatus(String s) { status.setText(s); }
}
