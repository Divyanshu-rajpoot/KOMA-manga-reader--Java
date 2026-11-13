package app.koma.ui.shared;

import javafx.scene.layout.Region;

/** A thin bar: line-colored track with an accent fill. */
public final class ThinProgress extends Region {
    private final Region fill = new Region();
    private double fraction;

    public ThinProgress(double height) {
        getStyleClass().add("thin-progress");
        fill.getStyleClass().add("fill");
        getChildren().add(fill);
        setMinHeight(height);
        setPrefHeight(height);
        setMaxHeight(height);
    }

    public void set(double f) {
        fraction = Math.max(0, Math.min(1, f));
        requestLayout();
    }

    @Override
    protected void layoutChildren() {
        fill.resizeRelocate(0, 0, Math.round(getWidth() * fraction), getHeight());
    }
}
