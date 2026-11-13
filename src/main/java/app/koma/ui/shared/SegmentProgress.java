package app.koma.ui.shared;

import app.koma.core.Anim;
import app.koma.core.MotionPrefs;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/** 20 blocks with a 3px gap; filled blocks are ink, the last filled block is the accent. */
public final class SegmentProgress extends HBox {
    public static final int BLOCKS = 20;
    private final Region[] blocks = new Region[BLOCKS];
    private Timeline fill;

    public SegmentProgress(double blockHeight) {
        super(3);
        getStyleClass().add("segment-progress");
        for (int i = 0; i < BLOCKS; i++) {
            Region r = new Region();
            r.getStyleClass().add("seg");
            r.setMinHeight(blockHeight);
            r.setPrefHeight(blockHeight);
            r.setMinWidth(4);
            r.setPrefWidth(19);
            HBox.setHgrow(r, Priority.ALWAYS);
            r.setMaxWidth(Double.MAX_VALUE);
            blocks[i] = r;
        }
        getChildren().addAll(blocks);
    }

    /** Sets progress 0..1; animated block by block unless motion is reduced. */
    public void set(double fraction, boolean animate) {
        int n = fraction <= 0 ? 0 : Math.max(1, (int) Math.round(Math.min(1, fraction) * BLOCKS));
        if (fill != null) fill.stop();
        if (!animate || MotionPrefs.reduced()) {
            paint(n);
            return;
        }
        paint(0);
        fill = new Timeline();
        double step = Anim.UI.toMillis() / Math.max(1, n);
        for (int i = 1; i <= n; i++) {
            int k = i;
            fill.getKeyFrames().add(new KeyFrame(Duration.millis(step * i), e -> paint(k)));
        }
        fill.play();
    }

    private void paint(int n) {
        for (int i = 0; i < BLOCKS; i++) {
            var sc = blocks[i].getStyleClass();
            sc.removeAll("filled", "last");
            if (i < n) sc.add(i == n - 1 ? "last" : "filled");
        }
    }
}
