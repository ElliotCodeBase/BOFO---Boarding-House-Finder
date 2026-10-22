package com.bofo.finder.ui;

import com.bofo.finder.model.College;
import javafx.beans.property.*;

/** Session-wide UI state shared by the student screens. */
public final class State {
    public static final ObjectProperty<College> college = new SimpleObjectProperty<>();
    public static final DoubleProperty radiusKm = new SimpleDoubleProperty(1.0);
    public static final ObjectProperty<double[]> gps = new SimpleObjectProperty<>(); // {lat,lng}
    public static final BooleanProperty offline = new SimpleBooleanProperty(false);
    private State() {}
    /** Reference point: chosen college, else GPS, else null. */
    public static double[] ref() {
        if (college.get() != null) return new double[]{college.get().lat(), college.get().lng()};
        return gps.get();
    }
}
