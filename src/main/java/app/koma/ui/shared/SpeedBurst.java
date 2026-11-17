package app.koma.ui.shared;

import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;

/**
 * Speed-line burst: 45 thin wedges (1° every 8°) around the center. Static: it used to rotate once per 80 s,
 * which re-rasterized a screen-sized vector fan on every frame for as long as Home was open.
 * Wedge color comes from CSS (.burst-ray).
 */
public final class SpeedBurst extends Pane {
    private final Group rays = new Group();

    public SpeedBurst(double opacity) {
        getStyleClass().add("speed-burst");
        setMouseTransparent(true);
        setMinSize(0, 0);
        setPrefSize(0, 0);
        for (int deg = 0; deg < 360; deg += 8) {
            Polygon p = new Polygon();
            p.getStyleClass().add("burst-ray");
            rays.getChildren().add(p);
        }
        rays.setOpacity(opacity);
        rays.setManaged(false);
        getChildren().add(rays);
        // static now, so rasterize the fan once (pane-sized bitmap) instead of 45 screen-wide polygons on every repaint
        setCache(true);
        setCacheHint(javafx.scene.CacheHint.SPEED);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
    }

    private double radius;

    /** Rays reach just past the corners. The pane (clipped to its size, not the full fan) is cached as a bitmap. */
    @Override
    protected void layoutChildren() {
        rays.setLayoutX(getWidth() / 2);
        rays.setLayoutY(getHeight() / 2);
        double r = Math.ceil(Math.hypot(getWidth(), getHeight()) / 2) + 4;
        if (Math.abs(r - radius) < 1) return;
        radius = r;
        int i = 0;
        for (int deg = 0; deg < 360; deg += 8) {
            double a1 = Math.toRadians(deg), a2 = Math.toRadians(deg + 1);
            ((Polygon) rays.getChildren().get(i++)).getPoints().setAll(0.0, 0.0,
                    r * Math.cos(a1), r * Math.sin(a1), r * Math.cos(a2), r * Math.sin(a2));
        }
    }
}
