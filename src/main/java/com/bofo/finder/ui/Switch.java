package com.bofo.finder.ui;

import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/** Small on/off switch (JavaFX has none built in). Read or bind {@link #onProperty()}; clicking toggles it. */
public final class Switch extends StackPane {
    private static final Color ON = Color.web("#5B3BF0"), OFF = Color.web("#CFCAE0");
    private final BooleanProperty on = new SimpleBooleanProperty();
    private final Rectangle track = new Rectangle(46, 28);
    private final Circle knob = new Circle(11, Color.WHITE);

    public Switch(boolean initial) {
        track.setArcWidth(28); track.setArcHeight(28);
        setAlignment(Pos.CENTER_LEFT);
        knob.setTranslateX(3);
        setMinSize(46, 28); setPrefSize(46, 28); setMaxSize(46, 28);
        getChildren().addAll(track, knob);
        setCursor(Cursor.HAND);
        on.addListener((o, a, b) -> paint(true));
        setOnMouseClicked(e -> on.set(!on.get()));
        on.set(initial);
        paint(false);
    }

    private void paint(boolean animate) {
        track.setFill(on.get() ? ON : OFF);
        double x = on.get() ? 46 - 22 - 3 : 3;
        if (animate) { TranslateTransition t = new TranslateTransition(Duration.millis(140), knob); t.setToX(x); t.play(); }
        else knob.setTranslateX(x);
    }

    public BooleanProperty onProperty() { return on; }
    public boolean isOn() { return on.get(); }
    public void setOn(boolean v) { on.set(v); }
}
