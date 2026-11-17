package app.koma.ui.shared;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * A responsive grid that only builds the cells near the viewport of its {@link ScrollPane}.
 * All cells must have the same height for a given width (that is how the scroll height is known
 * without building them). A library with thousands of series keeps a few dozen nodes alive.
 */
public final class VirtualGrid extends Pane {
    private static final int BUFFER_ROWS = 1;

    private final ScrollPane scroll;
    private final int count;
    private final java.util.function.Supplier<Node> creator;
    private final java.util.function.ObjIntConsumer<Node> binder;
    /** Cells that scrolled out of view, kept for reuse: re-binding a cell is far cheaper than building and styling a new one. */
    private final java.util.ArrayDeque<Node> pool = new java.util.ArrayDeque<>();
    private final double minCell, hgap, vgap;
    private final int minCols, maxCols;
    private final Map<Integer, Node> live = new HashMap<>();
    private final ChangeListener<Object> onScroll = (o, a, b) -> update();
    private final ChangeListener<Object> weakScroll = new WeakChangeListener<>(onScroll);
    private double rowH = -1;
    private double rowHWidth = -1;

    /** One new cell per index (use {@link #pooled} for heavy cells). */
    public VirtualGrid(ScrollPane scroll, int count, IntFunction<Node> factory,
                       double minCell, double hgap, double vgap, int minCols, int maxCols) {
        this(scroll, count, null, null, minCell, hgap, vgap, minCols, maxCols, factory);
    }

    /** Recycling grid: cells come from {@code creator} once and are re-bound to indexes with {@code binder}. */
    public static VirtualGrid pooled(ScrollPane scroll, int count, java.util.function.Supplier<Node> creator,
                                     java.util.function.ObjIntConsumer<Node> binder,
                                     double minCell, double hgap, double vgap, int minCols, int maxCols) {
        return new VirtualGrid(scroll, count, creator, binder, minCell, hgap, vgap, minCols, maxCols, null);
    }

    private final IntFunction<Node> factory;

    private VirtualGrid(ScrollPane scroll, int count, java.util.function.Supplier<Node> creator,
                        java.util.function.ObjIntConsumer<Node> binder, double minCell, double hgap, double vgap,
                        int minCols, int maxCols, IntFunction<Node> factory) {
        this.scroll = scroll;
        this.count = count;
        this.creator = creator;
        this.binder = binder;
        this.factory = factory;
        this.minCell = minCell;
        this.hgap = hgap;
        this.vgap = vgap;
        this.minCols = minCols;
        this.maxCols = maxCols;
        scroll.vvalueProperty().addListener(weakScroll);
        scroll.viewportBoundsProperty().addListener(weakScroll);
    }

    private int cols(double width) {
        int c = (int) Math.floor((width + hgap) / (minCell + hgap));
        return Math.max(minCols, Math.min(maxCols, c));
    }

    private double cellWidth(double width, int cols) {
        return (width - hgap * (cols - 1)) / cols;
    }

    private double rowHeight(double cw) {
        if (rowH < 0 || Math.abs(cw - rowHWidth) > 0.5) {
            Node probe = count > 0 ? obtain(0) : null;
            rowH = probe == null ? 0 : probe.prefHeight(cw);
            if (probe != null) release(probe);
            rowHWidth = cw;
        }
        return rowH;
    }

    private Node obtain(int i) {
        if (factory != null) return factory.apply(i);
        Node n = pool.poll();
        if (n == null) n = creator.get();
        binder.accept(n, i);
        return n;
    }

    private void release(Node n) {
        if (factory == null && pool.size() < 120) pool.add(n);
    }

    private double width() {
        double w = getWidth();
        if (w > 0) return w;
        Bounds vp = scroll.getViewportBounds();
        return vp != null && vp.getWidth() > 0 ? vp.getWidth() : 1200;
    }

    @Override
    protected double computePrefWidth(double height) {
        return minCell * minCols + hgap * (minCols - 1);
    }

    @Override
    protected double computePrefHeight(double width) {
        double w = width > 0 ? width : width();
        int cols = cols(w);
        int rows = (count + cols - 1) / cols;
        if (rows == 0) return 0;
        return rows * rowHeight(cellWidth(w, cols)) + (rows - 1) * vgap;
    }

    @Override
    protected void layoutChildren() {
        update();
    }

    private void update() {
        double w = getWidth();
        if (w <= 0 || count == 0) return;
        int cols = cols(w);
        double cw = cellWidth(w, cols);
        double rh = rowHeight(cw);
        double pitch = rh + vgap;
        if (pitch <= 0) return;
        int rows = (count + cols - 1) / cols;

        // visible band in this node's coordinates
        double viewTop = 0, viewH = 900;
        Bounds vp = scroll.getViewportBounds();
        if (vp != null && vp.getHeight() > 0 && getScene() != null) {
            viewH = vp.getHeight();
            var p = sceneToLocal(scroll.localToScene(0, 0));
            if (p != null) viewTop = p.getY();
        }
        int firstRow = Math.max(0, (int) Math.floor(viewTop / pitch) - BUFFER_ROWS);
        int lastRow = Math.min(rows - 1, (int) Math.floor((viewTop + viewH) / pitch) + BUFFER_ROWS);
        int first = firstRow * cols;
        int last = Math.min(count - 1, (lastRow + 1) * cols - 1);

        Iterator<Map.Entry<Integer, Node>> it = live.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getKey() < first || e.getKey() > last) {
                getChildren().remove(e.getValue());
                release(e.getValue());
                it.remove();
            }
        }
        for (int i = first; i <= last; i++) {
            Node n = live.get(i);
            if (n == null) {
                n = obtain(i);
                live.put(i, n);
                getChildren().add(n);
            }
            // whole pixels: cached cells drawn at fractional offsets look soft
            n.resizeRelocate(Math.round((i % cols) * (cw + hgap)), Math.round((i / cols) * pitch),
                    Math.floor(cw), Math.round(rh));
        }
    }
}
