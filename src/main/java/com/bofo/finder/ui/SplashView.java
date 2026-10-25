package com.bofo.finder.ui;

import javafx.animation.*;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Loading screen shown when entering the app: a WHITE BOFO mascot jumps on a flat deep-purple background
 * (two jumps per cycle: squash -> stretch -> airtime -> landing; at the top the hat POPS off with a spark burst, stretches, floats, then falls back and POPS on again with a squash, a rebound and a second burst) with a soft ground shadow and three pulsing dots, while the saved session is checked.
 * Pure JavaFX shapes + Timeline, no image assets. Animates transform/opacity only.
 */
public final class SplashView extends VBox {
    /** One loop = two jumps + a short rest (ms). */
    private static final double CYCLE = 2400;
    /** Directions (degrees, -90 = straight up) of the spark dots in a pop burst. */
    private static final double[] ANG = {-170, -145, -118, -90, -62, -35, -10};

    private final Label status = Ui.label("Getting things ready", "splash-status");
    private final Timeline jump;

    private final Mascot mascot;
    private final Scale sq;               // body squash / stretch, pivot on the soles
    private final Ellipse shadow;
    private final Group hat;
    private final Rotate hr;              // hat tilt, pivot = brim centre
    private final Scale hs;               // hat squash / stretch, pivot = brim underside (presses onto the head)
    private final Circle ring;            // pop shock ring
    private final Circle[] sparks;        // pop spark dots
    private final Interpolator out = Interpolator.SPLINE(0.2, 0.7, 0.3, 1), in = Interpolator.SPLINE(0.7, 0, 0.8, 0.3), pop = Interpolator.SPLINE(0.15, 0.95, 0.35, 1);

    public SplashView() {
        super(10); setAlignment(Pos.CENTER); getStyleClass().add("splash");

        mascot = new Mascot(Mascot.Expression.HAPPY, 172, true);
        hat = mascot.hat();
        hr = new Rotate(0, 100, 52); hs = new Scale(1, 1, 100, 59);
        hat.getTransforms().addAll(hr, hs);

        // Pop burst nodes live in the mascot's effects layer (art-grid units, centred on the top of the head), so they follow the jump.
        Group fx = mascot.fx(); fx.setMouseTransparent(true);
        sparks = new Circle[ANG.length];
        for (int i = 0; i < ANG.length; i++) {
            sparks[i] = new Circle(100, 48, i % 2 == 0 ? 5 : 3.5, i % 2 == 0 ? Color.WHITE : Color.web("#C9BBFF"));
            sparks[i].setOpacity(0); fx.getChildren().add(sparks[i]);
        }
        ring = new Circle(100, 48, 16); ring.setFill(null); ring.setStroke(Color.WHITE); ring.setStrokeWidth(4); ring.setOpacity(0);
        fx.getChildren().add(ring);

        shadow = new Ellipse(54, 8); shadow.setFill(Color.web("#8D7BFF", 0.32));   // light purple: a black shadow is invisible on the dark ink background
        // Feet stand on a ground line: the mascot is bottom-aligned, the shadow is centred on that line (just under the soles).
        final double ground = 12;
        StackPane stage = new StackPane(shadow, mascot);
        StackPane.setAlignment(shadow, Pos.BOTTOM_CENTER);
        StackPane.setMargin(shadow, new Insets(0, 0, ground - 6, 0));
        StackPane.setAlignment(mascot, Pos.BOTTOM_CENTER);
        StackPane.setMargin(mascot, new Insets(0, 0, ground, 0));
        stage.setMaxSize(230, 250); stage.setPrefSize(230, 250);
        // Squash / stretch pivots on the soles (the default pivot is the middle of the body, which lifted the feet off the ground).
        Bounds mb = mascot.getBoundsInLocal();
        sq = new Scale(1, 1, mb.getCenterX(), mb.getMaxY());
        mascot.getTransforms().add(sq);

        // One cycle = TWO jumps (2.2 s) + a short rest, looped. Jump 2 is a bit higher with a higher hat toss and the hat tilts the other way.
        List<KeyFrame> kf = new ArrayList<>();
        addJump(kf, 0,    -44, -28, -9, 0.70);
        addJump(kf, 1100, -58, -34,  9, 0.60);
        // Recover from the landing squash, then crouch again (anticipation) so the loop restarts in the pose jump 1 begins with.
        kf.add(new KeyFrame(Duration.millis(2250), new KeyValue(sq.xProperty(), 1), new KeyValue(sq.yProperty(), 1),
                new KeyValue(shadow.scaleXProperty(), 1), new KeyValue(shadow.scaleYProperty(), 1)));
        kf.add(new KeyFrame(Duration.millis(CYCLE), new KeyValue(sq.xProperty(), 1.10), new KeyValue(sq.yProperty(), 0.90),
                new KeyValue(shadow.scaleXProperty(), 1.05), new KeyValue(shadow.scaleYProperty(), 1.05)));
        hatKey(kf, CYCLE, 0, 0, 1, 1, null);
        jump = new Timeline(kf.toArray(new KeyFrame[0]));
        jump.setCycleCount(Animation.INDEFINITE);

        Label name = Ui.label("BOFO", "splash-title");
        Label sub = Ui.label("Boarding House Finder", "splash-sub");
        HBox dots = new HBox(10); dots.setAlignment(Pos.CENTER); dots.setMinHeight(24);
        List<Animation> dotAnims = new ArrayList<>();
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
        VBox.setMargin(dots, new Insets(12, 0, 0, 0));
        getChildren().addAll(stage, name, sub, dots, status);

        // Stop animating when removed from the scene (saves battery).
        sceneProperty().addListener((o, a, scene) -> {
            if (scene == null) { jump.stop(); dotAnims.forEach(Animation::stop); } else { jump.play(); dotAnims.forEach(Animation::play); }
        });
    }

    /**
     * Key frames of ONE jump starting at t0 ms (1.1 s long).
     * Body: crouch -> stretch -> apex -> landing squash.
     * Hat: sits on the head while the body launches (tiny lag), POPS off just after the apex (stretches upward, tilts, spark burst),
     * hovers, falls back with a stretch, then POPS on at the landing (squash onto the head, rebound, second burst) and settles.
     * @param height apex height of the mascot (negative = up), @param hatUp how far the hat lifts off the head (art-grid units, negative = up),
     * @param tilt   hat tilt at the top in degrees
     */
    private void addJump(List<KeyFrame> k, double t0, double height, double hatUp, double tilt, double shadowMin) {
        k.add(new KeyFrame(Duration.millis(t0),       new KeyValue(mascot.translateYProperty(), 0), new KeyValue(sq.xProperty(), 1.10), new KeyValue(sq.yProperty(), 0.90), new KeyValue(shadow.scaleXProperty(), 1.05), new KeyValue(shadow.scaleYProperty(), 1.05), new KeyValue(shadow.opacityProperty(), 1.0)));
        k.add(new KeyFrame(Duration.millis(t0 + 180), new KeyValue(mascot.translateYProperty(), 0), new KeyValue(sq.xProperty(), 0.94), new KeyValue(sq.yProperty(), 1.07)));
        k.add(new KeyFrame(Duration.millis(t0 + 560), new KeyValue(mascot.translateYProperty(), height, out), new KeyValue(sq.xProperty(), 1, out), new KeyValue(sq.yProperty(), 1, out), new KeyValue(shadow.scaleXProperty(), shadowMin, out), new KeyValue(shadow.scaleYProperty(), shadowMin, out), new KeyValue(shadow.opacityProperty(), 0.55, out)));
        k.add(new KeyFrame(Duration.millis(t0 + 950), new KeyValue(mascot.translateYProperty(), 0, in), new KeyValue(sq.xProperty(), 1.10, in), new KeyValue(sq.yProperty(), 0.90, in), new KeyValue(shadow.scaleXProperty(), 1.05, in), new KeyValue(shadow.scaleYProperty(), 1.05, in), new KeyValue(shadow.opacityProperty(), 1.0, in)));

        //             time        y      angle    sx    sy   ease
        hatKey(k, t0,            0,      0,     1.00, 1.00, null);
        hatKey(k, t0 + 420,      2,      0,     1.04, 0.94, null);   // lag: the hat is pressed down while the body shoots up
        hatKey(k, t0 + 560,      0,      0,     1.00, 1.00, null);   // apex
        hatKey(k, t0 + 650,      hatUp - 8,  tilt * 1.5,  0.80, 1.30, pop);     // POP: shoots off, stretched
        hatKey(k, t0 + 770,      hatUp + 3, tilt * 0.4, 1.00, 1.00, out);   // hover at the top
        hatKey(k, t0 + 900,     -3,      0,     0.96, 1.08, in);     // falls back, stretched
        hatKey(k, t0 + 950,      4,      0,     1.22, 0.74, in);     // lands on the head: squash (same moment as the feet)
        hatKey(k, t0 + 1015,    -6,      0,     0.92, 1.14, out);    // rebound
        hatKey(k, t0 + 1070,     0,      0,     1.00, 1.00, out);    // settle
        addBurst(k, t0 + 640, 380);    // pop off
        addBurst(k, t0 + 950, 340);    // pop on
    }

    private void hatKey(List<KeyFrame> k, double t, double y, double angle, double sx, double sy, Interpolator ip) {
        Interpolator i = ip == null ? Interpolator.EASE_BOTH : ip;
        k.add(new KeyFrame(Duration.millis(t), new KeyValue(hat.translateYProperty(), y, i), new KeyValue(hr.angleProperty(), angle, i),
                new KeyValue(hs.xProperty(), sx, i), new KeyValue(hs.yProperty(), sy, i)));
    }

    /** Shock ring + spark dots that fly out from the top of the head and fade; reset 1 ms before each start so bursts never smear into each other. */
    private void addBurst(List<KeyFrame> k, double t, double dur) {
        List<KeyValue> reset = new ArrayList<>(), start = new ArrayList<>(), end = new ArrayList<>();
        reset.add(new KeyValue(ring.opacityProperty(), 0)); reset.add(new KeyValue(ring.scaleXProperty(), 0.4)); reset.add(new KeyValue(ring.scaleYProperty(), 0.4));
        start.add(new KeyValue(ring.opacityProperty(), 0.9));
        end.add(new KeyValue(ring.opacityProperty(), 0, out)); end.add(new KeyValue(ring.scaleXProperty(), 2.8, out)); end.add(new KeyValue(ring.scaleYProperty(), 2.8, out));
        for (int i = 0; i < sparks.length; i++) {
            Circle c = sparks[i]; double a = Math.toRadians(ANG[i]), d = i % 2 == 0 ? 56 : 42;
            reset.add(new KeyValue(c.opacityProperty(), 0)); reset.add(new KeyValue(c.translateXProperty(), 0)); reset.add(new KeyValue(c.translateYProperty(), 0));
            reset.add(new KeyValue(c.scaleXProperty(), 1)); reset.add(new KeyValue(c.scaleYProperty(), 1));
            start.add(new KeyValue(c.opacityProperty(), 1));
            end.add(new KeyValue(c.opacityProperty(), 0, out)); end.add(new KeyValue(c.translateXProperty(), Math.cos(a) * d, out)); end.add(new KeyValue(c.translateYProperty(), Math.sin(a) * d, out));
            end.add(new KeyValue(c.scaleXProperty(), 0.3, out)); end.add(new KeyValue(c.scaleYProperty(), 0.3, out));
        }
        k.add(new KeyFrame(Duration.millis(t - 1), reset.toArray(new KeyValue[0])));
        k.add(new KeyFrame(Duration.millis(t), start.toArray(new KeyValue[0])));
        k.add(new KeyFrame(Duration.millis(t + dur), end.toArray(new KeyValue[0])));
    }

    public void setStatus(String s) { status.setText(s); }
}
