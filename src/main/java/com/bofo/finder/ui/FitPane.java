package com.bofo.finder.ui;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;

/**
 * A StackPane that always gives its child exactly its own size and never asks for a minimum width.
 * A normal StackPane grows its child to the child's minimum width and centres it, so one wide control
 * anywhere inside makes the whole screen wider than the window and cuts both sides off (320 px phones).
 */
public final class FitPane extends StackPane {
    @Override protected double computeMinWidth(double height) { return 0; }
    @Override protected void layoutChildren() {
        for (Node n : getManagedChildren()) n.resizeRelocate(0, 0, getWidth(), getHeight());
    }
}
