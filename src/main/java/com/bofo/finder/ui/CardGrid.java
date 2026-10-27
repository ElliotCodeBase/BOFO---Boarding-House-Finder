package com.bofo.finder.ui;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsive grid of listing cards: as many columns as fit (each at least minCol wide), at most maxCols.
 * The app uses 2 columns on phones. Cards in the same row are stretched to the same height. Cells are
 * BhCard, Skeleton.Card and AdSlot (unless the ad is full width); anything else (empty state, "Show more"
 * button, error label, a full-width ad) gets its own full-width row, so views keep adding children exactly
 * as they did to a VBox.
 */
public final class CardGrid extends Pane {
    private final double gap, minCol;
    private final int maxCols;

    public CardGrid(double gap, double minCol) { this(gap, minCol, Integer.MAX_VALUE); }
    public CardGrid(double gap, double minCol, int maxCols) { this.gap = gap; this.minCol = minCol; this.maxCols = maxCols; }

    @Override public Orientation getContentBias() { return Orientation.HORIZONTAL; }   // height depends on width

    private int columns(double w) { return Math.min(maxCols, Math.max(1, (int) Math.floor((w + gap) / (minCol + gap)))); }

    private static boolean isCell(Node n) {
        return n instanceof BhCard || n instanceof Skeleton.Card || (n instanceof AdSlot && !((AdSlot) n).fullWidth());
    }

    /** Places one row of cells (all the height of the tallest); returns the y below it. */
    private double placeRow(List<Node> row, double y, double colW, Insets in, boolean apply) {
        double rowH = 0;
        for (Node n : row) rowH = Math.max(rowH, n.prefHeight(colW));
        if (apply) for (int i = 0; i < row.size(); i++) row.get(i).resizeRelocate(in.getLeft() + i * (colW + gap), y, colW, rowH);
        return y + rowH;
    }

    /** Lays out (or just measures) the children for the given outer width; returns the total height. */
    private double flow(double width, boolean apply) {
        Insets in = getInsets();
        double w = Math.max(0, width - in.getLeft() - in.getRight());
        int cols = columns(w);
        double colW = (w - gap * (cols - 1)) / cols;
        double y = in.getTop();
        boolean any = false;
        List<Node> row = new ArrayList<>();
        for (Node n : getManagedChildren()) {
            any = true;
            if (cols > 1 && isCell(n)) {
                row.add(n);
                if (row.size() == cols) { y = placeRow(row, y, colW, in, apply) + gap; row.clear(); }
            } else {
                if (!row.isEmpty()) { y = placeRow(row, y, colW, in, apply) + gap; row.clear(); }   // close a half-filled card row first
                // A card alone in a single column takes exactly the column width. Its own minimum width (a loaded photo makes it
                // large) must not make it wider than the screen; other rows (buttons, messages) still respect their minimum.
                double nw = isCell(n) ? Math.min(w, n.maxWidth(-1)) : Math.max(n.minWidth(-1), Math.min(w, n.maxWidth(-1)));
                double h = n.prefHeight(nw);
                if (apply) n.resizeRelocate(in.getLeft(), y, nw, h);
                y += h + gap;
            }
        }
        if (!row.isEmpty()) y = placeRow(row, y, colW, in, apply) + gap;
        return (any ? y - gap : y) + in.getBottom();
    }

    @Override protected double computePrefHeight(double width) { return flow(width < 0 ? (getWidth() > 0 ? getWidth() : 360) : width, false); }
    @Override protected double computePrefWidth(double height) { return minCol + getInsets().getLeft() + getInsets().getRight(); }
    @Override protected double computeMinHeight(double width) { return computePrefHeight(width); }
    @Override protected void layoutChildren() { flow(getWidth(), true); }
}
