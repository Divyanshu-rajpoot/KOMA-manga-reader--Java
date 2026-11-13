package app.koma.ui.reader;

import app.koma.core.Exec;
import app.koma.imaging.PageLoader;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.IntConsumer;

/** Bottom of the reader: 15 thumbnails around the current page, and a scrubber that runs right-to-left for RTL books. */
final class ThumbStrip extends VBox {
    private static final int COUNT = 15;
    private static final int THUMB_H = 52;

    private final PageLoader loader;
    private final IntConsumer onJump;
    private final boolean rtl;
    private final HBox thumbs = new HBox(6);
    private final Slider slider = new Slider();
    private final Label total = new Label();
    private final Label status = new Label();
    private boolean syncing;
    private int page;

    ThumbStrip(PageLoader loader, boolean rtl, IntConsumer onJump) {
        this.loader = loader;
        this.onJump = onJump;
        getStyleClass().add("thumb-strip");
        setSpacing(8);
        thumbs.setAlignment(Pos.CENTER);
        thumbs.setMinHeight(THUMB_H + 6);

        int n = loader.pageCount();
        slider.setMin(1);
        slider.setMax(Math.max(1, n));
        slider.setBlockIncrement(1);
        slider.setNodeOrientation(rtl ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        slider.getStyleClass().add("scrubber");
        HBox.setHgrow(slider, Priority.ALWAYS);
        slider.valueProperty().addListener((o, a, b) -> {
            if (syncing) return;
            int p = (int) Math.round(b.doubleValue()) - 1;
            if (p != page) onJump.accept(p);
        });
        total.setText(String.valueOf(n));
        total.getStyleClass().add("mono-s");
        status.getStyleClass().add("mono-s");
        status.setMinWidth(240);
        status.setAlignment(Pos.CENTER_RIGHT);
        Label one = new Label("1");
        one.getStyleClass().add("mono-s");
        HBox scrub = rtl ? new HBox(14, total, slider, one, status) : new HBox(14, one, slider, total, status);
        scrub.setAlignment(Pos.CENTER);
        getChildren().addAll(thumbs, scrub);
        this.rtl = rtl;
    }

    void setPage(int p, int[] cachedRange) {
        page = p;
        syncing = true;
        slider.setValue(p + 1);
        syncing = false;
        int n = loader.pageCount();
        status.setText((p + 1) + " / " + n + (cachedRange == null ? "" : " · pages " + (cachedRange[0] + 1) + "–" + (cachedRange[1] + 1) + " cached"));

        int start = Math.max(0, Math.min(n - COUNT, p - COUNT / 2));
        int end = Math.min(n, start + COUNT);
        java.util.List<javafx.scene.Node> list = new java.util.ArrayList<>();
        for (int i = start; i < end; i++) {
            list.add(thumb(i, i == p, cachedRange != null && i >= cachedRange[0] && i <= cachedRange[1]));
        }
        if (rtl) java.util.Collections.reverse(list);
        thumbs.getChildren().setAll(list);
    }

    private StackPane thumb(int i, boolean current, boolean cached) {
        ImageView iv = new ImageView();
        iv.setFitHeight(THUMB_H);
        iv.setFitWidth(THUMB_H * 0.7);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        StackPane box = new StackPane(iv);
        box.getStyleClass().add("page-thumb");
        if (current) box.getStyleClass().add("current");
        if (cached) box.getStyleClass().add("cached");
        box.setMinSize(THUMB_H * 0.7 + 4, THUMB_H + 4);
        box.setMaxSize(THUMB_H * 0.7 + 4, THUMB_H + 4);
        box.setOnMouseClicked(e -> onJump.accept(i));
        Image hit = loader.cachedThumb(i, THUMB_H * 2);
        if (hit != null) {
            iv.setImage(hit);
        } else {
            loader.thumb(i, THUMB_H * 2).whenComplete((img, err) -> Exec.fx(() -> {
                if (img != null) iv.setImage(img);
            }));
        }
        javafx.scene.control.Tooltip.install(box, new javafx.scene.control.Tooltip("Page " + (i + 1)));
        return box;
    }
}
