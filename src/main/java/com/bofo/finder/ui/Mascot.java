package com.bofo.finder.ui;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.animation.TranslateTransition;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * BOFO mascot, drawn ONLY with basic shapes on a 200x200 grid:
 * a rounded-rectangle body, a white rounded-rectangle face screen, black pill-shaped eyes and mouth, an amber status light,
 * rounded-rectangle feet and a house-roof hat (triangle roof, chimney, rounded-rectangle brim). No arms.
 * Colors follow the theme: purple, white, amber, ink.
 */
public final class Mascot extends Group {
    /** WAVING = cheerful ^ ^ eyes + open smile and a small greeting tilt (there are no arms). */
    public enum Expression { HAPPY, THINKING, WAVING }

    /** Palette (instance, so a white variant can exist). PURPLE=body, WHITE=face screen, INK=eyes, AMBER*=hat/light accents. */
    private final Color PURPLE, DARK, AMBER, AMBER_DARK, TINT, INK, WHITE;

    private final double size;
    private final Group art = new Group();
    private Group hat = new Group();
    private final Group fx = new Group();   // effects layer (sparks etc.) in art-grid units, drawn above everything
    private RotateTransition tilt;

    /** Idle moods for the floating chat head: THINKING (eyes look around, bubble pulses), SITTING (sinks down, breathes, blinks),
     *  RUNNING (runs a circle), SLEEPING (eyes closed, nodding, floating z's). */
    public enum Idle { THINKING, SITTING, RUNNING, SLEEPING }

    // parts the idle animations move (rebuilt by every set())
    private Rectangle eyeL, eyeR, footL, footR;
    private Group thought;
    private final Scale pose = new Scale(1, 1, 0, 0);          // squash / stretch pivoting on the feet
    private final List<Animation> idleAnims = new ArrayList<>();

    public Mascot(Expression e, double size) { this(e, size, false); }

    /** @param white true = white mascot (white body, deep-purple face) for use on purple backgrounds such as the splash. */
    public Mascot(Expression e, double size, boolean white) {
        if (white) {
            PURPLE = Color.WHITE; DARK = Color.web("#E4D9F7"); AMBER = Color.web("#D9C8F5"); AMBER_DARK = Color.web("#C4ABEE");
            TINT = Color.web("#5B3BF0"); INK = Color.WHITE; WHITE = Color.web("#3A22B8");
        } else {
            PURPLE = Color.web("#5B3BF0"); DARK = Color.web("#3A22B8"); AMBER = Color.web("#8D7BFF"); AMBER_DARK = Color.web("#6B55F5");
            TINT = Color.web("#ECE9FF"); INK = Color.web("#17142A"); WHITE = Color.WHITE;
        }
        this.size = size;
        double k = size / 200.0;
        art.getTransforms().add(new Scale(k, k, 0, 0));
        pose.setPivotX(size / 2); pose.setPivotY(size * 0.86);   // feet line (y = 172 of 200)
        getTransforms().add(pose);
        getChildren().add(art);
        set(e);
    }

    public void set(Expression e) {
        if (tilt != null) { tilt.stop(); setRotate(0); tilt = null; }
        art.getChildren().clear();
        eyeL = eyeR = null; thought = null;

        // feet
        footL = rr(60, 156, 30, 16, 8, DARK); footR = rr(110, 156, 30, 16, 8, DARK);
        art.getChildren().addAll(footL, footR);
        // hat: chimney + roof + brim in ONE group so it can come off the head (see hat()); drawn last, the brim hides the roof's base
        Polygon roof = new Polygon(57, 48, 100, 16, 143, 48);
        roof.setFill(AMBER); roof.setStroke(AMBER); roof.setStrokeWidth(8); roof.setStrokeLineJoin(StrokeLineJoin.ROUND);
        hat = new Group(rr(119, 18, 13, 24, 4, AMBER_DARK), roof, rr(38, 45, 124, 14, 7, AMBER_DARK));
        // body, face screen, glass highlight, status light, hat
        Rectangle glass = rr(66, 77, 36, 5, 2.5, TINT);
        art.getChildren().addAll(rr(40, 50, 120, 112, 32, PURPLE), rr(56, 70, 88, 68, 22, WHITE), glass,
                rr(86, 146, 28, 6, 3, AMBER), hat);
        art.getChildren().add(fx);

        switch (e) {
            case THINKING -> {
                eyeL = rr(80, 90, 14, 18, 7, INK); eyeR = rr(110, 90, 14, 18, 7, INK);
                art.getChildren().addAll(eyeL, eyeR, rr(88, 119, 24, 5, 2.5, INK));
                thought = new Group(circle(168, 40, 5, AMBER), circle(180, 26, 7, AMBER), rr(166, 2, 30, 20, 10, AMBER));   // thought bubble
                art.getChildren().add(thought);
            }
            case WAVING -> {
                art.getChildren().addAll(arc(72, 104, 81, 88, 90, 104, INK, 5), arc(110, 104, 119, 88, 128, 104, INK, 5));
                QuadCurve m = new QuadCurve(84, 113, 100, 136, 116, 113);          // open smile
                m.setFill(INK); m.setStroke(INK); m.setStrokeWidth(4); m.setStrokeLineJoin(StrokeLineJoin.ROUND);
                art.getChildren().add(m);
                tilt = new RotateTransition(Duration.millis(450), this);           // greeting tilt
                tilt.setFromAngle(-5); tilt.setToAngle(5); tilt.setAutoReverse(true); tilt.setCycleCount(Animation.INDEFINITE); tilt.play();
            }
            default -> {
                eyeL = rr(76, 92, 14, 22, 7, INK); eyeR = rr(110, 92, 14, 22, 7, INK);
                art.getChildren().addAll(eyeL, eyeR, arc(86, 122, 100, 134, 114, 122, INK, 5));
            }
        }
    }

    // ------------------------------------------------------------------ idle animations (floating chat head)

    /** Starts one idle mood. Replaces any mood already playing; {@link #endIdle()} puts the mascot back to normal. */
    public void idle(Idle a) {
        endIdle();
        switch (a) {
            case THINKING -> idleThinking();
            case SITTING -> idleSitting();
            case RUNNING -> idleRunning();
            case SLEEPING -> idleSleeping();
        }
    }

    /** Stops every idle animation and returns to the normal happy face. */
    public void endIdle() {
        for (Animation x : idleAnims) x.stop();
        boolean was = !idleAnims.isEmpty();
        idleAnims.clear();
        pose.setX(1); pose.setY(1);
        setTranslateX(0); setTranslateY(0); setRotate(0);
        if (was) set(Expression.HAPPY);
    }

    private <T extends Animation> T keep(T a) { idleAnims.add(a); a.play(); return a; }

    private static KeyFrame eyes(Duration t, Rectangle l, Rectangle r, double x, double y) {
        return new KeyFrame(t, new KeyValue(l.translateXProperty(), x, Interpolator.EASE_BOTH), new KeyValue(l.translateYProperty(), y, Interpolator.EASE_BOTH),
                new KeyValue(r.translateXProperty(), x, Interpolator.EASE_BOTH), new KeyValue(r.translateYProperty(), y, Interpolator.EASE_BOTH));
    }

    /** Thinking: head tilts slowly, eyes look up-left then up-right, the thought bubble floats and fades. */
    private void idleThinking() {
        set(Expression.THINKING);
        if (tilt != null) { tilt.stop(); tilt = null; }
        RotateTransition t = new RotateTransition(Duration.seconds(1.8), this);
        t.setFromAngle(-4); t.setToAngle(5); t.setInterpolator(Interpolator.EASE_BOTH); t.setAutoReverse(true); t.setCycleCount(Animation.INDEFINITE);
        keep(t);
        Timeline look = new Timeline(eyes(Duration.ZERO, eyeL, eyeR, 0, 0), eyes(Duration.millis(700), eyeL, eyeR, -4, -5),
                eyes(Duration.millis(1700), eyeL, eyeR, 5, -5), eyes(Duration.millis(2500), eyeL, eyeR, 0, 0), eyes(Duration.millis(3000), eyeL, eyeR, 0, 0));
        look.setCycleCount(Animation.INDEFINITE); keep(look);
        Timeline bubble = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(thought.opacityProperty(), 1.0), new KeyValue(thought.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(900), new KeyValue(thought.opacityProperty(), 0.35, Interpolator.EASE_BOTH), new KeyValue(thought.translateYProperty(), -6, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1800), new KeyValue(thought.opacityProperty(), 1.0, Interpolator.EASE_BOTH), new KeyValue(thought.translateYProperty(), 0, Interpolator.EASE_BOTH)));
        bubble.setCycleCount(Animation.INDEFINITE); keep(bubble);
    }

    /** Sitting: sinks down and widens, feet spread out, then breathes and blinks. */
    private void idleSitting() {
        set(Expression.HAPPY);
        Timeline sit = new Timeline(new KeyFrame(Duration.millis(500),
                new KeyValue(pose.yProperty(), 0.86, Interpolator.EASE_OUT), new KeyValue(pose.xProperty(), 1.06, Interpolator.EASE_OUT),
                new KeyValue(footL.translateXProperty(), -9, Interpolator.EASE_OUT), new KeyValue(footR.translateXProperty(), 9, Interpolator.EASE_OUT)));
        keep(sit);
        Timeline breathe = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(pose.yProperty(), 0.86)),
                new KeyFrame(Duration.millis(1500), new KeyValue(pose.yProperty(), 0.885, Interpolator.EASE_BOTH)));
        breathe.setAutoReverse(true); breathe.setCycleCount(Animation.INDEFINITE);
        sit.setOnFinished(e -> keep(breathe));
        idleAnims.add(breathe);                      // registered now so endIdle() also stops it if the sit never finished
        Timeline blink = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(eyeL.scaleYProperty(), 1), new KeyValue(eyeR.scaleYProperty(), 1)),
                new KeyFrame(Duration.millis(2600), new KeyValue(eyeL.scaleYProperty(), 1), new KeyValue(eyeR.scaleYProperty(), 1)),
                new KeyFrame(Duration.millis(2700), new KeyValue(eyeL.scaleYProperty(), 0.1), new KeyValue(eyeR.scaleYProperty(), 0.1)),
                new KeyFrame(Duration.millis(2850), new KeyValue(eyeL.scaleYProperty(), 1), new KeyValue(eyeR.scaleYProperty(), 1)),
                new KeyFrame(Duration.millis(3400), new KeyValue(eyeL.scaleYProperty(), 1)));
        blink.setCycleCount(Animation.INDEFINITE); keep(blink);
    }

    /** Running: happy ^ ^ face, runs a circle (about 1.8 s a lap), leans into the turn, feet take turns, small hops. */
    private void idleRunning() {
        set(Expression.WAVING);
        if (tilt != null) { tilt.stop(); tilt = null; setRotate(0); }   // the greeting tilt would fight the lean
        final double r = size * 0.34, TAU = Math.PI * 2;
        Transition run = new Transition() {
            { setCycleDuration(Duration.millis(1800)); setInterpolator(Interpolator.LINEAR); }
            @Override protected void interpolate(double f) {
                double a = f * TAU, step = Math.sin(a * 8);
                setTranslateX(r * Math.sin(a));
                setTranslateY(r * (Math.cos(a) - 1) - Math.abs(step) * size * 0.03);
                setRotate(12 * Math.cos(a));
                footL.setTranslateY(-7 * Math.max(0, step));
                footR.setTranslateY(-7 * Math.max(0, -step));
            }
        };
        run.setCycleCount(Animation.INDEFINITE); keep(run);
    }

    /** Sleeping: sunk down, eyes closed, head nodding, slow breathing and little z's floating up from the head. */
    private void idleSleeping() {
        set(Expression.HAPPY);
        eyeL.setVisible(false); eyeR.setVisible(false);
        art.getChildren().addAll(arc(72, 104, 83, 114, 94, 104, INK, 5), arc(106, 104, 117, 114, 128, 104, INK, 5));   // closed eyes
        pose.setY(0.9); pose.setX(1.04);
        RotateTransition nod = new RotateTransition(Duration.millis(2200), this);
        nod.setFromAngle(2); nod.setToAngle(9); nod.setInterpolator(Interpolator.EASE_BOTH); nod.setAutoReverse(true); nod.setCycleCount(Animation.INDEFINITE);
        keep(nod);
        Timeline breathe = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(pose.yProperty(), 0.9)),
                new KeyFrame(Duration.millis(1800), new KeyValue(pose.yProperty(), 0.93, Interpolator.EASE_BOTH)));
        breathe.setAutoReverse(true); breathe.setCycleCount(Animation.INDEFINITE); keep(breathe);
        double[] sz = {34, 42, 50};
        for (int i = 0; i < 3; i++) {
            Text z = new Text(150, 64, "z"); z.setFont(Font.font("System", FontWeight.BOLD, sz[i])); z.setFill(DARK); z.setOpacity(0);
            fx.getChildren().add(z);
            Timeline fl = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(z.opacityProperty(), 0), new KeyValue(z.translateYProperty(), 0), new KeyValue(z.translateXProperty(), 0)),
                    new KeyFrame(Duration.millis(500), new KeyValue(z.opacityProperty(), 1)),
                    new KeyFrame(Duration.millis(2400), new KeyValue(z.opacityProperty(), 0), new KeyValue(z.translateYProperty(), -44), new KeyValue(z.translateXProperty(), 14)));
            fl.setDelay(Duration.millis(800L * i)); fl.setCycleCount(Animation.INDEFINITE); keep(fl);
        }
    }

    /** Empty overlay group in art-grid units (200x200), drawn on top of the mascot; survives set(). Used for pop sparks. */
    public Group fx() { return fx; }

    /** The hat as its own node (units of the 200x200 art grid, before scaling): animate translateY / rotate to lift it off the head. */
    public Group hat() { return hat; }

    /** Gentle idle bounce (splash screen, login, floating button). */
    public Mascot bob() {
        TranslateTransition t = new TranslateTransition(Duration.seconds(1.1), this);
        t.setByY(-size * 0.04); t.setAutoReverse(true); t.setCycleCount(Animation.INDEFINITE); t.play();
        return this;
    }

    private static Rectangle rr(double x, double y, double w, double h, double r, Color c) {
        Rectangle rect = new Rectangle(x, y, w, h); rect.setArcWidth(r * 2); rect.setArcHeight(r * 2); rect.setFill(c); return rect;
    }
    private static Circle circle(double cx, double cy, double r, Color c) { return new Circle(cx, cy, r, c); }
    private static QuadCurve arc(double x1, double y1, double cx, double cy, double x2, double y2, Color c, double w) {
        QuadCurve q = new QuadCurve(x1, y1, cx, cy, x2, y2);
        q.setFill(null); q.setStroke(c); q.setStrokeWidth(w); q.setStrokeLineCap(StrokeLineCap.ROUND); return q;
    }
}
