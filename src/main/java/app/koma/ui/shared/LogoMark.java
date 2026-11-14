package app.koma.ui.shared;

import javafx.scene.layout.StackPane;

/**
 * The KOMA brand mark: an accent square with a vector コ.
 * Drawn as a shape (not a font glyph) so it stays bold, centered and crisp at any size,
 * and matches the window/taskbar icon exactly.
 */
public final class LogoMark extends StackPane {
    public LogoMark(double size) {
        getStyleClass().add("logo-box");
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        getChildren().add(Icons.of("koma", size));
    }
}
