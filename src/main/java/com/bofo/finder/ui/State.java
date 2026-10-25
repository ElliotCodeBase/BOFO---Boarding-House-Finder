package com.bofo.finder.ui;

import com.bofo.finder.model.College;
import javafx.beans.property.*;

/** Session-wide UI state shared by the student screens. */
public final class State {
    public static final ObjectProperty<College> college = new SimpleObjectProperty<>();
    public static final DoubleProperty radiusKm = new SimpleDoubleProperty(1.0);
    public static final ObjectProperty<double[]> gps = new SimpleObjectProperty<>(); // {lat,lng}
    /** True when the last fix came from a PC (Windows Wi-Fi / browser / a dev override), which can be off by several km. Phone GPS = false. */
    public static final BooleanProperty gpsApprox = new SimpleBooleanProperty(false);
    public static final BooleanProperty offline = com.bofo.finder.util.Connectivity.OFFLINE;   // set automatically by the HTTP layer
    private State() {}
    /** Reference point: chosen college, else GPS, else null. */
    public static double[] ref() {
        if (college.get() != null) return new double[]{college.get().lat(), college.get().lng()};
        return gps.get();
    }
}
