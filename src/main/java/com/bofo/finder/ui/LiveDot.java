package com.bofo.finder.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

/**
 * "You are here" marker for the map (v1.18.0 redesign). Drawn around (0,0) so the map layer only has to translate/scale it.
 * <ul>
 *   <li><b>Core</b>: a glossy blue-indigo dot in a white ring with a soft contact shadow.</li>
 *   <li><b>Ripples</b>: two soft rings that expand and fade, staggered, so the dot reads as live without flashing.</li>
 *   <li><b>View cone</b>: a fading fan that points the way you are moving (replaces the old tiny arrow).</li>
 *   <li><b>Motion rules</b> (ui-animation skill): only transform + opacity are animated, never from scale 0, enter curve
 *       {@code cubic-bezier(0.22,1,0.36,1)}, everything interruptible (a new call restarts from the current value), and the
 *       looping ripples are paused whenever the map is off screen ({@link #setRunning}).</li>
 * </ul>
 */
final class LiveDot extends Group {
    /** cubic-bezier(0.22, 1, 0.36, 1): entrances and pops. */
    static final Interpolator ENTER = Interpolator.SPLINE(0.22, 1, 0.36, 1);
    /** cubic-bezier(0.25, 1, 0.5, 1): moves and turns. */
    static final Interpolator MOVE = Interpolator.SPLINE(0.25, 1, 0.5, 1);

    private static final Color BLUE = Color.web("#2F6FED");
    private static final double RIPPLE_R = 16, CONE_R = 40, CONE_SPREAD = 64;

    private final Circle rippleA = ripple(), rippleB = ripple(), ping = ripple();
    private final Arc cone = new Arc(0, 0, CONE_R, CONE_R, 90 - CONE_SPREAD / 2, CONE_SPREAD);
    private final Rotate coneRot = new Rotate(0, 0, 0);
    private final Group body = new Group();                       // shadow + ring + core: pops in and on ping
    private final Circle core = new Circle(6.8);
    private final Timeline loopA, loopB;
    private Timeline coneFade, coneTurn, popAnim, pingAnim;
    private boolean coneOn;

    LiveDot() {
        setMouseTransparent(true);
        setAutoSizeChildren(false);

        cone.setType(ArcType.ROUND);
        cone.setFill(new RadialGradient(0, 0, 0, 0, CONE_R, false, CycleMethod.NO_CYCLE,
                new Stop(0.15, Color.web("#2F6FED", 0.46)), new Stop(1.0, Color.web("#2F6FED", 0.0))));
        cone.getTransforms().add(coneRot);
        cone.setOpacity(0); cone.setVisible(false);

        Circle shadow = new Circle(0, 1.8, 12);
        shadow.setFill(new RadialGradient(0, 0, 0, 1.8, 12, false, CycleMethod.NO_CYCLE,
                new Stop(0.55, Color.web("#17142A", 0.26)), new Stop(1.0, Color.web("#17142A", 0.0))));
        Circle ring = new Circle(9.6, Color.WHITE);
        core.setFill(new LinearGradient(-0.7, -1, 0.7, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#7DB2FF")), new Stop(1, Color.web("#3D4FEA"))));
        Circle glint = new Circle(-2.1, -2.5, 2.1, Color.web("#FFFFFF", 0.38));
        body.getChildren().addAll(shadow, ring, core, glint);

        ping.setOpacity(0);
        getChildren().addAll(rippleA, rippleB, ping, cone, body);

        loopA = loop(rippleA, 0);
        loopB = loop(rippleB, 1300);
    }

    // ---------------------------------------------------------------- pieces

    private static Circle ripple() {
        Circle c = new Circle(RIPPLE_R);
        c.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#2F6FED", 0.0)), new Stop(0.55, Color.web("#2F6FED", 0.05)),
                new Stop(0.86, Color.web("#2F6FED", 0.22)), new Stop(1.0, Color.web("#2F6FED", 0.0))));
        c.setScaleX(0.5); c.setScaleY(0.5); c.setOpacity(0);
        return c;
    }

    /** One looping ripple: scale 0.5 -> 2.7 (enter curve, front-loaded), opacity fades out linearly. Transform + opacity only. */
    private static Timeline loop(Circle c, int delayMs) {
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(c.scaleXProperty(), 0.5), new KeyValue(c.scaleYProperty(), 0.5), new KeyValue(c.opacityProperty(), 0.9)),
                new KeyFrame(Duration.millis(2600), new KeyValue(c.scaleXProperty(), 2.7, ENTER), new KeyValue(c.scaleYProperty(), 2.7, ENTER),
                        new KeyValue(c.opacityProperty(), 0.0, Interpolator.LINEAR)));
        t.setCycleCount(Timeline.INDEFINITE);
        t.setDelay(Duration.millis(delayMs));
        return t;
    }

    // ---------------------------------------------------------------- state

    /** Ripples run only while the map is on screen and we have a fix (saves battery). Pausing keeps the current frame. */
    void setRunning(boolean on) {
        if (on) { loopA.play(); loopB.play(); }
        else { loopA.pause(); loopB.pause(); }
    }

    /** First fix: the dot fades and settles in from 82% size (never from 0). */
    void appear() {
        if (popAnim != null) popAnim.stop();
        body.setScaleX(0.82); body.setScaleY(0.82); body.setOpacity(0);
        popAnim = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(body.scaleXProperty(), 0.82), new KeyValue(body.scaleYProperty(), 0.82), new KeyValue(body.opacityProperty(), 0.0)),
                new KeyFrame(Duration.millis(260), new KeyValue(body.scaleXProperty(), 1.0, ENTER), new KeyValue(body.scaleYProperty(), 1.0, ENTER), new KeyValue(body.opacityProperty(), 1.0, ENTER)));
        popAnim.play();
    }

    /** "Found you": one bigger ring and a small pop of the core. Used when the locate button lands on the dot. */
    void ping() {
        if (pingAnim != null) pingAnim.stop();
        pingAnim = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(ping.scaleXProperty(), 0.6), new KeyValue(ping.scaleYProperty(), 0.6), new KeyValue(ping.opacityProperty(), 1.0),
                        new KeyValue(body.scaleXProperty(), body.getScaleX()), new KeyValue(body.scaleYProperty(), body.getScaleY())),
                new KeyFrame(Duration.millis(110), new KeyValue(body.scaleXProperty(), 1.16, ENTER), new KeyValue(body.scaleYProperty(), 1.16, ENTER)),
                new KeyFrame(Duration.millis(320), new KeyValue(body.scaleXProperty(), 1.0, ENTER), new KeyValue(body.scaleYProperty(), 1.0, ENTER)),
                new KeyFrame(Duration.millis(900), new KeyValue(ping.scaleXProperty(), 3.5, ENTER), new KeyValue(ping.scaleYProperty(), 3.5, ENTER),
                        new KeyValue(ping.opacityProperty(), 0.0, Interpolator.LINEAR)));
        pingAnim.play();
    }

    /** Points the view cone at {@code deg} (0 = north, clockwise). Turns the short way round; fades in the first time. */
    void setHeading(double deg) {
        double cur = coneRot.getAngle();
        double target = cur + (((deg - cur) % 360 + 540) % 360 - 180);
        if (!coneOn) { coneRot.setAngle(deg); target = deg; }              // first heading: no spin from 0
        if (coneTurn != null) coneTurn.stop();
        coneTurn = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(coneRot.angleProperty(), coneRot.getAngle())),
                new KeyFrame(Duration.millis(300), new KeyValue(coneRot.angleProperty(), target, MOVE)));
        coneTurn.play();
        fadeCone(true);
    }

    /** Location lost / switched off: hide the cone, stop the ripples. */
    void reset() {
        setRunning(false);
        if (coneFade != null) coneFade.stop();
        if (coneTurn != null) coneTurn.stop();
        coneOn = false; cone.setOpacity(0); cone.setVisible(false);
    }

    private void fadeCone(boolean on) {
        if (on == coneOn) return;
        coneOn = on;
        if (coneFade != null) coneFade.stop();
        cone.setVisible(true);
        coneFade = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(cone.opacityProperty(), cone.getOpacity())),
                new KeyFrame(Duration.millis(on ? 240 : 160), new KeyValue(cone.opacityProperty(), on ? 1.0 : 0.0, ENTER)));
        if (!on) coneFade.setOnFinished(e -> { if (!coneOn) cone.setVisible(false); });
        coneFade.play();
    }
}
