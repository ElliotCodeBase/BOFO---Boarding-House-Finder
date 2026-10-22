package com.bofo.finder.ui;

import javafx.animation.Animation;
import javafx.animation.RotateTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

/**
 * BOFO mascot, drawn ONLY with basic shapes on a 200x200 grid:
 * a rounded-rectangle body, a white rounded-rectangle face screen, black pill-shaped eyes and mouth, an amber status light,
 * rounded-rectangle feet and a house-roof hat (triangle roof, chimney, rounded-rectangle brim). No arms.
 * Colors follow the theme: purple, white, amber, ink.
 */
public final class Mascot extends Group {
    /** WAVING = cheerful ^ ^ eyes + open smile and a small greeting tilt (there are no arms). */
    public enum Expression { HAPPY, THINKING, WAVING }

    private static final Color PURPLE = Color.web("#6C3FC5"), DARK = Color.web("#4A2A8F"), AMBER = Color.web("#FFB400"),
            AMBER_DARK = Color.web("#E39B00"), TINT = Color.web("#EDE4FA"), INK = Color.web("#1F1A2E"), WHITE = Color.WHITE;

    private final double size;
    private final Group art = new Group();
    private RotateTransition tilt;

    public Mascot(Expression e, double size) {
        this.size = size;
        double k = size / 200.0;
        art.getTransforms().add(new Scale(k, k, 0, 0));
        getChildren().add(art);
        set(e);
    }

    public void set(Expression e) {
        if (tilt != null) { tilt.stop(); setRotate(0); tilt = null; }
        art.getChildren().clear();

        // feet
        art.getChildren().addAll(rr(60, 156, 30, 16, 8, DARK), rr(110, 156, 30, 16, 8, DARK));
        // hat (behind the head): chimney + roof
        Polygon roof = new Polygon(57, 48, 100, 16, 143, 48);
        roof.setFill(AMBER); roof.setStroke(AMBER); roof.setStrokeWidth(8); roof.setStrokeLineJoin(StrokeLineJoin.ROUND);
        art.getChildren().addAll(rr(119, 18, 13, 24, 4, AMBER_DARK), roof);
        // body, face screen, glass highlight, status light, brim
        Rectangle glass = rr(66, 77, 36, 5, 2.5, TINT);
        art.getChildren().addAll(rr(40, 50, 120, 112, 32, PURPLE), rr(56, 70, 88, 68, 22, WHITE), glass,
                rr(86, 146, 28, 6, 3, AMBER), rr(38, 45, 124, 14, 7, AMBER_DARK));

        switch (e) {
            case THINKING -> {
                art.getChildren().addAll(rr(80, 90, 14, 18, 7, INK), rr(110, 90, 14, 18, 7, INK), rr(88, 119, 24, 5, 2.5, INK));
                art.getChildren().addAll(circle(168, 40, 5), circle(180, 26, 7), rr(166, 2, 30, 20, 10, AMBER));   // thought bubble
            }
            case WAVING -> {
                art.getChildren().addAll(arc(72, 104, 81, 88, 90, 104, INK, 5), arc(110, 104, 119, 88, 128, 104, INK, 5));
                QuadCurve m = new QuadCurve(84, 113, 100, 136, 116, 113);          // open smile
                m.setFill(INK); m.setStroke(INK); m.setStrokeWidth(4); m.setStrokeLineJoin(StrokeLineJoin.ROUND);
                art.getChildren().add(m);
                tilt = new RotateTransition(Duration.millis(450), this);           // greeting tilt
                tilt.setFromAngle(-5); tilt.setToAngle(5); tilt.setAutoReverse(true); tilt.setCycleCount(Animation.INDEFINITE); tilt.play();
            }
            default -> art.getChildren().addAll(rr(76, 92, 14, 22, 7, INK), rr(110, 92, 14, 22, 7, INK),
                    arc(86, 122, 100, 134, 114, 122, INK, 5));
        }
    }

    /** Gentle idle bounce (splash screen, login, floating button). */
    public Mascot bob() {
        TranslateTransition t = new TranslateTransition(Duration.seconds(1.1), this);
        t.setByY(-size * 0.04); t.setAutoReverse(true); t.setCycleCount(Animation.INDEFINITE); t.play();
        return this;
    }

    private static Rectangle rr(double x, double y, double w, double h, double r, Color c) {
        Rectangle rect = new Rectangle(x, y, w, h); rect.setArcWidth(r * 2); rect.setArcHeight(r * 2); rect.setFill(c); return rect;
    }
    private static Circle circle(double cx, double cy, double r) { return new Circle(cx, cy, r, AMBER); }
    private static QuadCurve arc(double x1, double y1, double cx, double cy, double x2, double y2, Color c, double w) {
        QuadCurve q = new QuadCurve(x1, y1, cx, cy, x2, y2);
        q.setFill(null); q.setStroke(c); q.setStrokeWidth(w); q.setStrokeLineCap(StrokeLineCap.ROUND); return q;
    }
}
