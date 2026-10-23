package com.bofo.finder.ui;

import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * 24x24 outline dock icon. setProgress(0..1) blends from the pale idle colour to the strong active colour
 * and makes the line a little thicker, so the tab animation can drive it frame by frame.
 * Uses a plain Pane (not a StackPane) so both paths keep their shared 24x24 coordinates.
 */
final class DockIcon extends Pane {
    private final SVGPath shape = new SVGPath();
    private final SVGPath detail = new SVGPath();
    private final Color idle, active;

    DockIcon(String[] def, Color idle, Color active) {
        this.idle = idle; this.active = active;
        shape.setContent(def[0]); detail.setContent(def[1]);
        for (SVGPath p : new SVGPath[]{shape, detail}) {
            p.setFill(null); p.setStrokeLineCap(StrokeLineCap.ROUND); p.setStrokeLineJoin(StrokeLineJoin.ROUND);
            p.setMouseTransparent(true);
        }
        getChildren().addAll(shape, detail);
        setMinSize(24, 24); setPrefSize(24, 24); setMaxSize(24, 24);
        setMouseTransparent(true);
        setProgress(0);
    }

    void setProgress(double t) {
        Color c = idle.interpolate(active, t);
        double w = 1.8 + 0.35 * t;
        shape.setStroke(c); detail.setStroke(c);
        shape.setStrokeWidth(w); detail.setStrokeWidth(w);
    }
}
