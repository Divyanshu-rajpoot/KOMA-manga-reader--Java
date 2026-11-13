package app.koma.ui.shared;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.shape.Polygon;

/**
 * Panel with a slanted right edge, like clip-path: polygon(0 0,100% 0,97% 100%,0 100%).
 * Fill and 3px outline are polygons styled by CSS (.slant-fill / .slant-outline), so the border follows the slant.
 */
public final class SlantPanel extends Region {
    private final Polygon fill = new Polygon();
    private final Polygon outline = new Polygon();
    private final Node content;
    private final double slant;
    private final Insets pad;
    private final boolean leftSlant;

    /** slant: fraction of width cut at the bottom (0.03 = 97%). leftSlant mirrors the cut to the left edge. */
    public SlantPanel(Node content, double slant, boolean leftSlant, boolean outlined, String variant) {
        this.content = content;
        this.slant = slant;
        this.leftSlant = leftSlant;
        this.pad = new Insets(20, 24, 20, 20);
        getStyleClass().addAll("slant-panel", variant);
        fill.getStyleClass().add("slant-fill");
        outline.getStyleClass().add("slant-outline");
        outline.setVisible(outlined);
        outline.setMouseTransparent(true);
        getChildren().addAll(fill, content, outline);
        Polygon clipShape = new Polygon();
        setClip(clipShape);
        layoutBoundsProperty().addListener((o, a, b) -> {
            double[] p = points(b.getWidth(), b.getHeight(), 0);
            clipShape.getPoints().setAll(toObj(p));
        });
    }

    private double[] points(double w, double h, double inset) {
        double cut = w * slant;
        if (leftSlant) {
            return new double[]{inset, inset, w - inset, inset, w - inset, h - inset, cut + inset, h - inset};
        }
        return new double[]{inset, inset, w - inset, inset, w - cut - inset, h - inset, inset, h - inset};
    }

    private static Double[] toObj(double[] a) {
        Double[] o = new Double[a.length];
        for (int i = 0; i < a.length; i++) o[i] = a[i];
        return o;
    }

    @Override
    protected double computePrefHeight(double width) {
        return pad.getTop() + content.prefHeight(width - pad.getLeft() - pad.getRight()) + pad.getBottom();
    }

    @Override
    protected double computePrefWidth(double height) {
        return pad.getLeft() + content.prefWidth(-1) + pad.getRight();
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth(), h = getHeight();
        fill.getPoints().setAll(toObj(points(w, h, 0)));
        outline.getPoints().setAll(toObj(points(w, h, 1.5)));
        double cut = w * slant;
        double left = pad.getLeft() + (leftSlant ? cut : 0);
        double right = pad.getRight() + (leftSlant ? 0 : cut);
        content.resizeRelocate(left, pad.getTop(), w - left - right, h - pad.getTop() - pad.getBottom());
    }
}
