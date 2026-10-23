package com.bofo.finder.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Loading placeholders that copy the shape of the real content (listing cards, chat rows, stat tiles, detail page,
 * chat bubbles). Each one pulses softly while data loads and stops itself when it leaves the screen.
 * Every factory returns a ready-to-add list, so views do: container.getChildren().setAll(Skeleton.cards(4)).
 */
public final class Skeleton {
    private Skeleton() {}

    /** Card-shaped placeholder. CardGrid puts these in the same columns as real BhCards. */
    public static final class Card extends VBox { Card() { super(10); } }

    /** A grey rounded block. w <= 0 means "fill the available width". */
    public static Region block(double w, double h, double radius) {
        Region r = new Region(); r.getStyleClass().add("skeleton");
        r.setStyle("-fx-background-radius:" + radius + ";");
        if (w > 0) { r.setMinWidth(w); r.setPrefWidth(w); r.setMaxWidth(w); }
        r.setMinHeight(h); r.setPrefHeight(h); r.setMaxHeight(h);
        return r;
    }

    private static <T extends Node> T pulse(T n) {
        FadeTransition ft = new FadeTransition(Duration.millis(900), n);
        ft.setFromValue(1); ft.setToValue(0.45); ft.setCycleCount(Animation.INDEFINITE); ft.setAutoReverse(true);
        ft.setInterpolator(Interpolator.EASE_BOTH);
        n.sceneProperty().addListener((o, was, scene) -> { if (scene != null) ft.play(); else ft.stop(); });   // no work when off screen
        return n;
    }

    private static Region spacer() { Region s = new Region(); HBox.setHgrow(s, Priority.ALWAYS); return s; }

    /** Looks like a BhCard: photo, name + price, distance, rating, tags. */
    public static List<Node> cards(int n) {
        List<Node> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Card c = new Card(); c.getStyleClass().add("card");
            HBox top = new HBox(8, block(150, 16, 8), spacer(), block(64, 16, 8)); top.setAlignment(Pos.CENTER_LEFT);
            HBox tags = new HBox(6, block(54, 22, 8), block(54, 22, 8), block(54, 22, 8));
            c.getChildren().addAll(block(0, 190, 16), top, block(120, 12, 6), block(90, 12, 6), tags);
            out.add(pulse(c));
        }
        return out;
    }

    /** Looks like an inbox / list row: avatar + two lines + small value on the right. */
    public static List<Node> rows(int n) {
        List<Node> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            VBox mid = new VBox(8, block(150, 14, 7), block(110, 12, 6));
            HBox row = new HBox(12, block(44, 44, 22), mid, spacer(), block(36, 12, 6));
            row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("card");
            out.add(pulse(row));
        }
        return out;
    }

    /** n headline-number tiles in a row (owner dashboard). */
    public static List<Node> stats(int n) {
        HBox row = new HBox(10);
        for (int i = 0; i < n; i++) {
            VBox t = new VBox(8, block(40, 26, 8), block(70, 12, 6)); t.getStyleClass().add("card");
            t.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(t, Priority.ALWAYS); row.getChildren().add(t);
        }
        return List.of(pulse(row));
    }

    /** Boarding-house detail page: photo strip, title lines, price, tags, map. */
    public static List<Node> detail() {
        HBox photos = new HBox(8, block(260, 180, 16), block(260, 180, 16));
        VBox info = new VBox(10, block(210, 22, 8), block(160, 12, 6), block(120, 18, 8),
                new HBox(6, block(70, 22, 8), block(90, 22, 8), block(70, 22, 8)), block(130, 16, 8));
        VBox all = new VBox(14, photos, info, block(0, 200, 16));
        all.setPadding(new Insets(0, 16, 0, 16));
        return List.of(pulse(all));
    }

    /** Chat history: bubbles alternating left / right. */
    public static List<Node> bubbles() {
        VBox v = new VBox(10);
        double[][] spec = {{0, 210, 40}, {1, 150, 40}, {0, 250, 58}, {1, 190, 40}, {0, 170, 40}};
        for (double[] s : spec) {
            HBox row = new HBox(block(s[1], s[2], 18)); row.setAlignment(s[0] == 0 ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
            v.getChildren().add(row);
        }
        return List.of(pulse(v));
    }
}
