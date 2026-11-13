package app.koma.ui.shared;

import app.koma.core.ThemeManager;
import javafx.beans.value.ChangeListener;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;

/**
 * Screentone: ink dots on a 7px grid at 9% opacity. The 7x7 tile is drawn once per theme and
 * repeated with an ImagePattern, so nothing is redrawn per frame.
 */
public final class Halftone extends Region {
    private final ThemeManager theme;
    private final String token;
    private final ChangeListener<Number> onTheme = (o, a, b) -> paint();

    public Halftone(ThemeManager theme) {
        this(theme, "ink", 0.09);
    }

    public Halftone(ThemeManager theme, String token, double opacity) {
        this.theme = theme;
        this.token = token;
        setOpacity(opacity);
        setMouseTransparent(true);
        setMinSize(0, 0);
        setPrefSize(0, 0);
        paint();
        theme.revisionProperty().addListener(new javafx.beans.value.WeakChangeListener<>(onTheme));
    }

    private void paint() {
        Color ink = theme.color(token);
        int size = 7;
        WritableImage tile = new WritableImage(size, size);
        PixelWriter pw = tile.getPixelWriter();
        double c = 3.5;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double d = Math.hypot(x + 0.5 - c, y + 0.5 - c);
                // radial-gradient(ink 1.1px, transparent 1.4px)
                double a = d <= 1.1 ? 1 : d >= 1.4 ? 0 : (1.4 - d) / 0.3;
                pw.setColor(x, y, new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), a));
            }
        }
        setBackground(new Background(new BackgroundFill(new ImagePattern(tile, 0, 0, size, size, false), null, null)));
    }
}
