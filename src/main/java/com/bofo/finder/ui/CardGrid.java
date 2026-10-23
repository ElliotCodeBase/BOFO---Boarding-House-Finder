package com.bofo.finder.ui;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

/**
 * Responsive list of listing cards: 1 column on phones, 2 on tablets / landscape (as many as fit with each
 * column at least minCol wide). Anything that is not a BhCard (empty state, "Show more" button, error label)
 * gets its own full-width row, so views can keep adding children exactly as they did to a VBox.
 */
public final class CardGrid extends Pane {
    private final double gap, minCol;

    public CardGrid(double gap, double minCol) { this.gap = gap; this.minCol = minCol; }

    @Override public Orientation getContentBias() { return Orientation.HORIZONTAL; }   // height depends on width

    private int columns(double w) { return Math.max(1, (int) Math.floor((w + gap) / (minCol + gap))); }

    /** Lays out (or just measures) the children for the given outer width; returns the total height. */
    private double flow(double width, boolean apply) {
        Insets in = getInsets();
        double w = Math.max(0, width - in.getLeft() - in.getRight());
        int cols = columns(w);
        double colW = (w - gap * (cols - 1)) / cols;
        double y = in.getTop(), rowH = 0;
        int col = 0;
        boolean any = false;
        for (Node n : getManagedChildren()) {
            any = true;
            if (cols > 1 && (n instanceof BhCard || n instanceof Skeleton.Card)) {
                double h = n.prefHeight(colW);
                if (apply) n.resizeRelocate(in.getLeft() + col * (colW + gap), y, colW, h);
                rowH = Math.max(rowH, h);
                if (++col == cols) { y += rowH + gap; col = 0; rowH = 0; }
            } else {
                if (col > 0) { y += rowH + gap; col = 0; rowH = 0; }          // close a half-filled card row first
                double nw = Math.max(n.minWidth(-1), Math.min(w, n.maxWidth(-1)));
                double h = n.prefHeight(nw);
                if (apply) n.resizeRelocate(in.getLeft(), y, nw, h);
                y += h + gap;
            }
        }
        if (col > 0) y += rowH + gap;
        return (any ? y - gap : y) + in.getBottom();
    }

    @Override protected double computePrefHeight(double width) { return flow(width < 0 ? (getWidth() > 0 ? getWidth() : 360) : width, false); }
    @Override protected double computePrefWidth(double height) { return minCol + getInsets().getLeft() + getInsets().getRight(); }
    @Override protected double computeMinHeight(double width) { return computePrefHeight(width); }
    @Override protected void layoutChildren() { flow(getWidth(), true); }
}
