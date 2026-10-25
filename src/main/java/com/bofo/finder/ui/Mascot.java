package com.bofo.finder.ui;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.animation.TranslateTransition;
import javafx.beans.value.WritableValue;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * BOFO mascot, drawn ONLY with basic shapes on a 200x200 grid:
 * a rounded-rectangle body, a white rounded-rectangle face screen, black pill-shaped eyes and mouth, an amber status light,
 * rounded-rectangle feet and a house-roof hat (triangle roof, chimney, rounded-rectangle brim). Standing = no arms; the idle poses add arms.
 * Colors follow the theme: purple, white, amber, ink.
 */
public final class Mascot extends Group {
    /** WAVING = cheerful ^ ^ eyes + open smile and a small greeting tilt. */
    public enum Expression { HAPPY, THINKING, WAVING }

    /** Palette (instance, so a white variant can exist). PURPLE=body, WHITE=face screen, INK=eyes, AMBER*=hat/light accents. */
    private final Color PURPLE, DARK, AMBER, AMBER_DARK, TINT, INK, WHITE;

    private final double size;
    private final Group art = new Group();
    private Group hat = new Group();
    private final Group fx = new Group();   // effects layer (sparks, z's, speed lines) in art-grid units, drawn above everything
    private RotateTransition tilt;

    /** Idle poses for the floating chat head. Each one is its OWN drawing, not the standing mascot squashed:
     *  THINKING (one arm up on the chin, foot tapping, bubble), SITTING (body low, feet out front, arms down, legs swing),
     *  RUNNING (leans forward, arms pump, feet stride, speed lines - runs IN PLACE), SLEEPING (slumped sit, eyes shut, head nods, z's). */
    public enum Idle { THINKING, SITTING, RUNNING, SLEEPING }

    // parts the idle animations move (rebuilt by every set())
    private Rectangle eyeL, eyeR, footL, footR, mouth;
    private Group thought, hand, torso = new Group();
    private Rotate torsoRot = new Rotate(0, 100, 162);        // leans / nods the whole upper body around the feet
    private Rotate rotL = new Rotate(), rotR = new Rotate();   // arm swing around the shoulders
    private final Scale pose = new Scale(1, 1, 0, 0);          // kept for compatibility, always 1 now
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

    public void set(Expression e) { build(e, null); }

    /** Draws the mascot. p == null = the normal standing mascot (no arms); otherwise the drawing for that idle pose. */
    private void build(Expression e, Idle p) {
        if (tilt != null) { tilt.stop(); setRotate(0); tilt = null; }
        art.getChildren().clear();
        eyeL = eyeR = mouth = null; thought = null; hand = null;
        final boolean low = p == Idle.SITTING || p == Idle.SLEEPING;   // body is 40 units shorter, hat sits 40 lower

        torso = new Group();
        torsoRot = new Rotate(0, 100, 162); torso.getTransforms().add(torsoRot);
        rotL = new Rotate(0, 36, 114); rotR = new Rotate(0, 164, 114);

        // feet: standing = under the body; sitting = out in front of it, splayed
        footL = low ? rr(22, 150, 40, 20, 10, DARK) : rr(60, 156, 30, 16, 8, DARK);
        footR = low ? rr(138, 150, 40, 20, 10, DARK) : rr(110, 156, 30, 16, 8, DARK);
        if (!low) art.getChildren().addAll(footL, footR);

        // hat: chimney + roof + brim in ONE group so it can come off the head (see hat()); the brim hides the roof's base
        Polygon roof = new Polygon(57, 48, 100, 16, 143, 48);
        roof.setFill(AMBER); roof.setStroke(AMBER); roof.setStrokeWidth(8); roof.setStrokeLineJoin(StrokeLineJoin.ROUND);
        hat = new Group(rr(119, 18, 13, 24, 4, AMBER_DARK), roof, rr(38, 45, 124, 14, 7, AMBER_DARK));
        hat.setTranslateY(low ? 40 : 0);
        // body, face screen, glass highlight, status light, hat
        if (low) torso.getChildren().addAll(rr(34, 90, 132, 72, 30, PURPLE), rr(52, 102, 96, 48, 18, WHITE), rr(62, 108, 34, 4, 2, TINT), rr(86, 152, 28, 6, 3, AMBER), hat);
        else torso.getChildren().addAll(rr(40, 50, 120, 112, 32, PURPLE), rr(56, 70, 88, 68, 22, WHITE), rr(66, 77, 36, 5, 2.5, TINT), rr(86, 146, 28, 6, 3, AMBER), hat);

        // face
        if (p == Idle.SLEEPING) {
            torso.getChildren().addAll(arc(72, 124, 83, 134, 94, 124, INK, 5), arc(106, 124, 117, 134, 128, 124, INK, 5));   // shut eyes
            mouth = rr(94, 138, 12, 7, 3.5, INK);                                                                         // small open mouth
            torso.getChildren().add(mouth);
        } else switch (e) {
            case THINKING -> {
                eyeL = rr(80, 90, 14, 18, 7, INK); eyeR = rr(110, 90, 14, 18, 7, INK);
                torso.getChildren().addAll(eyeL, eyeR, rr(88, 119, 24, 5, 2.5, INK));
                thought = new Group(circle(168, 40, 5, AMBER), circle(180, 26, 7, AMBER), rr(166, 2, 30, 20, 10, AMBER));   // thought bubble
            }
            case WAVING -> {
                torso.getChildren().addAll(arc(72, 104, 81, 88, 90, 104, INK, 5), arc(110, 104, 119, 88, 128, 104, INK, 5));
                QuadCurve m = new QuadCurve(84, 113, 100, 136, 116, 113);          // open smile
                m.setFill(INK); m.setStroke(INK); m.setStrokeWidth(4); m.setStrokeLineJoin(StrokeLineJoin.ROUND);
                torso.getChildren().add(m);
                if (p == null) {                                                    // greeting tilt (not while running)
                    tilt = new RotateTransition(Duration.millis(450), this);
                    tilt.setFromAngle(-5); tilt.setToAngle(5); tilt.setAutoReverse(true); tilt.setCycleCount(Animation.INDEFINITE); tilt.play();
                }
            }
            default -> {
                double ey = low ? 112 : 92, eh = low ? 18 : 22, my = low ? 136 : 122;
                eyeL = rr(76, ey, 14, eh, 7, INK); eyeR = rr(110, ey, 14, eh, 7, INK);
                torso.getChildren().addAll(eyeL, eyeR, arc(86 + (low ? 2 : 0), my, 100, my + (low ? 10 : 12), 114 - (low ? 2 : 0), my, INK, 5));
            }
        }

        // arms (only the idle poses have them; they ride on the torso so they lean / nod with it)
        if (p == Idle.RUNNING) {
            rotL.setPivotX(40); rotL.setPivotY(112); rotR.setPivotX(160); rotR.setPivotY(112);
            torso.getChildren().addAll(arm(rotL, 40, 112, 38), arm(rotR, 160, 112, 38));
        } else if (low) {
            rotL.setPivotX(34); rotL.setPivotY(112); rotR.setPivotX(166); rotR.setPivotY(112);
            rotL.setAngle(p == Idle.SLEEPING ? 7 : 15); rotR.setAngle(p == Idle.SLEEPING ? -7 : -15);   // hands rest out to the sides
            torso.getChildren().addAll(arm(rotL, 34, 112, 42), arm(rotR, 166, 112, 42));
        }

        art.getChildren().add(torso);
        if (low) art.getChildren().addAll(footL, footR);                            // sitting feet are in FRONT of the body
        if (p == Idle.THINKING) {
            rotL.setPivotX(36); rotL.setPivotY(114); rotL.setAngle(8);
            art.getChildren().add(arm(rotL, 36, 114, 38));                          // spare arm hangs down
            Polyline up = new Polyline(160, 114, 178, 134, 138, 142);               // other arm: up to the chin
            up.setFill(null); up.setStroke(DARK); up.setStrokeWidth(14); up.setStrokeLineCap(StrokeLineCap.ROUND); up.setStrokeLineJoin(StrokeLineJoin.ROUND);
            hand = new Group(up, circle(136, 142, 9, AMBER));
            art.getChildren().add(hand);
        }
        if (thought != null) art.getChildren().add(thought);
        art.getChildren().add(fx);
    }

    /** One arm: a rounded bar hanging from the shoulder (sx, sy), swung by rotating {@code rot} around that point. */
    private Group arm(Rotate rot, double sx, double sy, double len) {
        Group g = new Group(rr(sx - 7, sy - 7, 14, len, 7, DARK));
        g.getTransforms().add(rot);
        return g;
    }

    // ------------------------------------------------------------------ idle animations (floating chat head)

    /** Starts one idle pose. Replaces any pose already playing; {@link #endIdle()} puts the mascot back to normal. */
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

    private static KeyValue kv(WritableValue<Number> p, double v) { return new KeyValue(p, v); }
    private static KeyValue kv(WritableValue<Number> p, double v, Interpolator i) { return new KeyValue(p, v, i); }

    private static KeyFrame eyes(Duration t, Rectangle l, Rectangle r, double x, double y) {
        return new KeyFrame(t, kv(l.translateXProperty(), x, Interpolator.EASE_BOTH), kv(l.translateYProperty(), y, Interpolator.EASE_BOTH),
                kv(r.translateXProperty(), x, Interpolator.EASE_BOTH), kv(r.translateYProperty(), y, Interpolator.EASE_BOTH));
    }

    private Timeline loop(boolean reverse, KeyFrame... frames) {
        Timeline t = new Timeline(frames);
        t.setAutoReverse(reverse); t.setCycleCount(Animation.INDEFINITE);
        return keep(t);
    }

    /** Thinking: one hand on the chin (tapping), the other arm hangs, a foot taps, head tilts, eyes look up, bubble floats. */
    private void idleThinking() {
        build(Expression.THINKING, Idle.THINKING);
        RotateTransition t = new RotateTransition(Duration.seconds(1.8), this);
        t.setFromAngle(-5); t.setToAngle(6); t.setInterpolator(Interpolator.EASE_BOTH); t.setAutoReverse(true); t.setCycleCount(Animation.INDEFINITE);
        keep(t);
        loop(false, eyes(Duration.ZERO, eyeL, eyeR, 0, 0), eyes(Duration.millis(700), eyeL, eyeR, -7, -8),
                eyes(Duration.millis(1700), eyeL, eyeR, 8, -8), eyes(Duration.millis(2500), eyeL, eyeR, 0, 0), eyes(Duration.millis(3000), eyeL, eyeR, 0, 0));
        loop(true, new KeyFrame(Duration.ZERO, kv(thought.opacityProperty(), 1.0), kv(thought.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(900), kv(thought.opacityProperty(), 0.35, Interpolator.EASE_BOTH), kv(thought.translateYProperty(), -6, Interpolator.EASE_BOTH)));
        loop(true, new KeyFrame(Duration.ZERO, kv(hand.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(380), kv(hand.translateYProperty(), -4, Interpolator.EASE_BOTH)));            // fingers drum on the chin
        loop(true, new KeyFrame(Duration.ZERO, kv(footR.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(520), kv(footR.translateYProperty(), -5, Interpolator.EASE_BOTH)));            // foot tap
    }

    /** Sitting: plops down on the floor, feet out front swinging, arms resting, breathes, blinks and glances around. */
    private void idleSitting() {
        build(Expression.HAPPY, Idle.SITTING);
        torso.setTranslateY(-34);
        Timeline breathe = new Timeline(new KeyFrame(Duration.ZERO, kv(torso.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(1500), kv(torso.translateYProperty(), -2, Interpolator.EASE_BOTH)));
        breathe.setAutoReverse(true); breathe.setCycleCount(Animation.INDEFINITE);
        Timeline plop = new Timeline(new KeyFrame(Duration.ZERO, kv(torso.translateYProperty(), -34)),
                new KeyFrame(Duration.millis(260), kv(torso.translateYProperty(), 0, Interpolator.EASE_IN)),
                new KeyFrame(Duration.millis(340), kv(torso.translateYProperty(), -5, Interpolator.EASE_OUT)),
                new KeyFrame(Duration.millis(420), kv(torso.translateYProperty(), 0, Interpolator.EASE_IN)));
        plop.setOnFinished(e -> breathe.play());
        keep(plop); idleAnims.add(breathe);         // registered now so endIdle() also stops it if the plop never finished
        loop(true, new KeyFrame(Duration.ZERO, kv(footL.translateYProperty(), 0), kv(footR.translateYProperty(), -4)),
                new KeyFrame(Duration.millis(650), kv(footL.translateYProperty(), -4, Interpolator.EASE_BOTH), kv(footR.translateYProperty(), 0, Interpolator.EASE_BOTH)));
        loop(false, new KeyFrame(Duration.ZERO, kv(eyeL.scaleYProperty(), 1), kv(eyeR.scaleYProperty(), 1)),
                new KeyFrame(Duration.millis(2600), kv(eyeL.scaleYProperty(), 1), kv(eyeR.scaleYProperty(), 1)),
                new KeyFrame(Duration.millis(2700), kv(eyeL.scaleYProperty(), 0.1), kv(eyeR.scaleYProperty(), 0.1)),
                new KeyFrame(Duration.millis(2850), kv(eyeL.scaleYProperty(), 1), kv(eyeR.scaleYProperty(), 1)),
                new KeyFrame(Duration.millis(3400), kv(eyeL.scaleYProperty(), 1)));
        loop(false, eyes(Duration.ZERO, eyeL, eyeR, 0, 0), eyes(Duration.millis(1200), eyeL, eyeR, 0, 0), eyes(Duration.millis(1700), eyeL, eyeR, -6, 0),
                eyes(Duration.millis(2700), eyeL, eyeR, 6, 0), eyes(Duration.millis(3300), eyeL, eyeR, 0, 0), eyes(Duration.millis(4000), eyeL, eyeR, 0, 0));
    }

    /** Running IN PLACE (the head never leaves its button): leans forward, arms pump, feet stride, hops, speed lines stream behind. */
    private void idleRunning() {
        build(Expression.WAVING, Idle.RUNNING);
        torsoRot.setAngle(11);
        for (int i = 0; i < 3; i++) {
            Rectangle l = rr(2, 92 + i * 20, 30 - i * 4, 5, 2.5, AMBER); l.setOpacity(0);
            fx.getChildren().add(l);
            Timeline s = new Timeline(new KeyFrame(Duration.ZERO, kv(l.opacityProperty(), 0.9), kv(l.translateXProperty(), 0)),
                    new KeyFrame(Duration.millis(420), kv(l.opacityProperty(), 0), kv(l.translateXProperty(), -14)));
            s.setDelay(Duration.millis(130L * i)); s.setCycleCount(Animation.INDEFINITE); keep(s);
        }
        Transition run = new Transition() {
            { setCycleDuration(Duration.millis(520)); setInterpolator(Interpolator.LINEAR); }
            @Override protected void interpolate(double f) {
                double a = f * Math.PI * 2, s = Math.sin(a), c = Math.cos(a);
                footL.setTranslateX(12 * s);  footL.setTranslateY(-9 * Math.max(0, c));
                footR.setTranslateX(-12 * s); footR.setTranslateY(-9 * Math.max(0, -c));
                rotL.setAngle(55 * s); rotR.setAngle(-55 * s);
                torso.setTranslateY(-4 * Math.abs(s));          // a small hop on every step
            }
        };
        run.setCycleCount(Animation.INDEFINITE); keep(run);
    }

    /** Sleeping: slumped sit, eyes shut, mouth breathing, head nodding forward with the hat drooping, z's floating up. */
    private void idleSleeping() {
        build(Expression.HAPPY, Idle.SLEEPING);
        loop(true, new KeyFrame(Duration.ZERO, kv(torsoRot.angleProperty(), 1), kv(hat.rotateProperty(), 0)),
                new KeyFrame(Duration.millis(2200), kv(torsoRot.angleProperty(), 9, Interpolator.EASE_BOTH), kv(hat.rotateProperty(), 7, Interpolator.EASE_BOTH)));
        loop(true, new KeyFrame(Duration.ZERO, kv(torso.translateYProperty(), 0), kv(mouth.scaleXProperty(), 0.8), kv(mouth.scaleYProperty(), 0.8)),
                new KeyFrame(Duration.millis(1800), kv(torso.translateYProperty(), -2, Interpolator.EASE_BOTH),
                        kv(mouth.scaleXProperty(), 1.3, Interpolator.EASE_BOTH), kv(mouth.scaleYProperty(), 1.5, Interpolator.EASE_BOTH)));
        double[] sz = {44, 54, 64};
        for (int i = 0; i < 3; i++) {
            Text z = new Text(148, 84, "z"); z.setFont(Font.font("System", FontWeight.BOLD, sz[i])); z.setFill(DARK); z.setOpacity(0);
            fx.getChildren().add(z);
            Timeline fl = new Timeline(
                    new KeyFrame(Duration.ZERO, kv(z.opacityProperty(), 0), kv(z.translateYProperty(), 0), kv(z.translateXProperty(), 0)),
                    new KeyFrame(Duration.millis(500), kv(z.opacityProperty(), 1)),
                    new KeyFrame(Duration.millis(2400), kv(z.opacityProperty(), 0), kv(z.translateYProperty(), -56), kv(z.translateXProperty(), 18)));
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
