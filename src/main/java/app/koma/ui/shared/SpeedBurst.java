package app.koma.ui.shared;

import app.koma.core.Anim;
import app.koma.core.MotionPrefs;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Speed-line burst: 45 thin wedges (1° every 8°) around the center, rotating once per 80 s.
 * Wedge color comes from CSS (.burst-ray); the group is cached as a bitmap while it rotates.
 */
public final class SpeedBurst extends Pane {
    private final Group rays = new Group();
    private final RotateTransition spin;

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
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        spin = new RotateTransition(Duration.seconds(80), rays);
        spin.setByAngle(360);
        spin.setInterpolator(Interpolator.LINEAR);
        spin.setCycleCount(Animation.INDEFINITE);
        MotionPrefs.reducedProperty().addListener((o, a, reduced) -> {
            if (reduced) spin.pause();
            else if (getScene() != null) spin.play();
        });
        sceneProperty().addListener((o, a, s) -> {
            if (s == null) spin.stop();
            else Anim.play(spin);
        });
    }

    private double radius;

    /** Rays reach just past the corners; vector polygons, no bitmap cache (a cached 4k texture per burst eats VRAM). */
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
