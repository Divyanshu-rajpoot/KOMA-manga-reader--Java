package app.koma.ui.reader;

import app.koma.ui.shared.Icons;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.util.function.DoubleConsumer;

/** Floating zoom control: minus, slider (100 to 400 %), plus, and a percentage that resets to 100 % when clicked. */
final class ZoomBar extends HBox {
    static final double MIN = 0.25, MAX = 4.0, STEP = 0.25;
    /** The slider is logarithmic (-2..2 = 25%..400%) so 100% sits in the middle and zooming out is as fine as zooming in. */
    private static final double LOG_MIN = -2, LOG_MAX = 2;

    private final Slider slider = new Slider(LOG_MIN, LOG_MAX, 0);
    private final Label percent = new Label("100%");
    private boolean syncing;

    ZoomBar(DoubleConsumer onZoom) {
        super(8);
        getStyleClass().add("zoom-bar");
        setAlignment(Pos.CENTER);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);

        Button out = button("minus", "Zoom out (Ctrl -)");
        out.setOnAction(e -> onZoom.accept(zoom() - STEP));
        Button in = button("plus", "Zoom in (Ctrl +)");
        in.setOnAction(e -> onZoom.accept(zoom() + STEP));
        slider.getStyleClass().add("zoom-slider");
        slider.setPrefWidth(150);
        slider.setFocusTraversable(false);
        slider.valueProperty().addListener((o, a, b) -> {
            if (syncing) return;
            double v = Math.abs(b.doubleValue()) < 0.04 ? 0 : b.doubleValue(); // magnet at 100%
            onZoom.accept(Math.pow(2, v));
        });
        percent.getStyleClass().add("zoom-percent");
        percent.setMinWidth(50);
        percent.setAlignment(Pos.CENTER);
        percent.setTooltip(new Tooltip("Reset zoom (Ctrl 0)"));
        percent.setOnMouseClicked(e -> onZoom.accept(1));
        getChildren().addAll(out, slider, in, percent);
        // keep clicks on the bar from turning the page underneath
        addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
    }

    /** Reflects a zoom set elsewhere (wheel, keys, pinch) without notifying. */
    void set(double zoom) {
        syncing = true;
        slider.setValue(Math.log(zoom) / Math.log(2));
        syncing = false;
        percent.setText(Math.round(zoom * 100) + "%");
    }

    private double zoom() {
        return Math.pow(2, slider.getValue());
    }

    private static Button button(String icon, String tip) {
        Button b = new Button();
        b.setGraphic(Icons.of(icon, 12));
        b.getStyleClass().add("zoom-button");
        b.setTooltip(new Tooltip(tip));
        b.setFocusTraversable(false);
        return b;
    }
}
