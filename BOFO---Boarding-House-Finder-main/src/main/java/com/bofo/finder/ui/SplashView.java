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
 * (squash -> stretch -> airtime -> landing) with a soft ground shadow and three pulsing dots, while the saved session is checked.
 * Pure JavaFX shapes + Timeline, no image assets. Animates transform/opacity only.
 */
public final class SplashView extends VBox {
    private final Label status = Ui.label("Getting things ready", "splash-status");
    private final Timeline jump;

    public SplashView() {
        super(14); setAlignment(Pos.CENTER); getStyleClass().add("splash");

        Mascot mascot = new Mascot(Mascot.Expression.HAPPY, 150, true);
        Ellipse shadow = new Ellipse(46, 7); shadow.setFill(Color.rgb(0, 0, 0, 0.22));
        StackPane stage = new StackPane(shadow, mascot);
        StackPane.setAlignment(shadow, Pos.BOTTOM_CENTER);
        StackPane.setAlignment(mascot, Pos.CENTER);
        stage.setMaxSize(200, 210); stage.setPrefSize(200, 210);

        // One jump cycle (1.1 s), looped. Start and end share the same squashed pose so the loop is seamless.
        Interpolator out = Interpolator.SPLINE(0.2, 0.7, 0.3, 1), in = Interpolator.SPLINE(0.7, 0, 0.8, 0.3);
        jump = new Timeline(
            new KeyFrame(Duration.ZERO,        new KeyValue(mascot.translateYProperty(), 0),   new KeyValue(mascot.scaleXProperty(), 1.10), new KeyValue(mascot.scaleYProperty(), 0.90), new KeyValue(shadow.scaleXProperty(), 1.05), new KeyValue(shadow.opacityProperty(), 1.0)),
            new KeyFrame(Duration.millis(180), new KeyValue(mascot.translateYProperty(), 0),   new KeyValue(mascot.scaleXProperty(), 0.94), new KeyValue(mascot.scaleYProperty(), 1.07)),
            new KeyFrame(Duration.millis(560), new KeyValue(mascot.translateYProperty(), -44, out), new KeyValue(mascot.scaleXProperty(), 1, out), new KeyValue(mascot.scaleYProperty(), 1, out), new KeyValue(shadow.scaleXProperty(), 0.7, out), new KeyValue(shadow.opacityProperty(), 0.55, out)),
            new KeyFrame(Duration.millis(950), new KeyValue(mascot.translateYProperty(), 0, in),   new KeyValue(mascot.scaleXProperty(), 1.10, in), new KeyValue(mascot.scaleYProperty(), 0.90, in), new KeyValue(shadow.scaleXProperty(), 1.05, in), new KeyValue(shadow.opacityProperty(), 1.0, in)),
            new KeyFrame(Duration.millis(1100)));
        jump.setCycleCount(Animation.INDEFINITE);

        Label name = Ui.label("BOFO", "splash-title");
        Label sub = Ui.label("Boarding House Finder", "splash-sub");
        HBox dots = new HBox(8); dots.setAlignment(Pos.CENTER);
        for (int i = 0; i < 3; i++) {
            Circle c = new Circle(3.5, Color.WHITE); c.setOpacity(0.25);
            FadeTransition f = new FadeTransition(Duration.millis(520), c);
            f.setFromValue(0.25); f.setToValue(1); f.setAutoReverse(true); f.setCycleCount(Animation.INDEFINITE);
            f.setDelay(Duration.millis(i * 170)); f.play();
            dots.getChildren().add(c);
        }
        status.setAlignment(Pos.CENTER); status.setStyle("-fx-text-alignment:center;");
        VBox.setMargin(dots, new javafx.geometry.Insets(10, 0, 0, 0));
        getChildren().addAll(stage, name, sub, dots, status);

        // Stop animating when removed from the scene (saves battery).
        sceneProperty().addListener((o, a, scene) -> { if (scene == null) jump.stop(); else jump.play(); });
    }

    public void setStatus(String s) { status.setText(s); }
}
