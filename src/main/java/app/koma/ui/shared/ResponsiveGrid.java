package app.koma.ui.shared;

import javafx.scene.Node;
import javafx.scene.layout.Pane;

/** Equal-width columns: as many as fit at minCell width (between minCols and maxCols); rows size to content. */
public final class ResponsiveGrid extends Pane {
    private final double minCell;
    private final double hgap;
    private final double vgap;
    private final int minCols;
    private final int maxCols;

    public ResponsiveGrid(double minCell, double hgap, double vgap, int minCols, int maxCols) {
        this.minCell = minCell;
        this.hgap = hgap;
        this.vgap = vgap;
        this.minCols = minCols;
        this.maxCols = maxCols;
    }

    private int cols(double width) {
        int c = (int) Math.floor((width + hgap) / (minCell + hgap));
        return Math.max(minCols, Math.min(maxCols, c));
    }

    private double cellWidth(double width, int cols) {
        return (width - hgap * (cols - 1)) / cols;
    }

    @Override
    protected double computePrefWidth(double height) {
        return minCell * minCols + hgap * (minCols - 1);
    }

    @Override
    protected double computePrefHeight(double width) {
        double w = width > 0 ? width : getWidth() > 0 ? getWidth() : 1200;
        int cols = cols(w);
        double cw = cellWidth(w, cols);
        double total = 0, rowH = 0;
        int i = 0;
        for (Node n : getManagedChildren()) {
            rowH = Math.max(rowH, n.prefHeight(cw));
            if (++i % cols == 0) {
                total += rowH + vgap;
                rowH = 0;
            }
        }
        if (i % cols != 0) total += rowH;
        else if (i > 0) total -= vgap;
        return total;
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        int cols = cols(w);
        double cw = cellWidth(w, cols);
        double y = 0, rowH = 0;
        int i = 0;
        var kids = getManagedChildren();
        for (Node n : kids) {
            int col = i % cols;
            if (col == 0 && i > 0) {
                y += rowH + vgap;
                rowH = 0;
            }
            double h = n.prefHeight(cw);
            n.resizeRelocate(col * (cw + hgap), y, cw, h);
            rowH = Math.max(rowH, h);
            i++;
        }
    }
}
