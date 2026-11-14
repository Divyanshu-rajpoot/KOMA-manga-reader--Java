package app.koma.ui.shell;

import app.koma.ui.shared.Icons;
import app.koma.ui.shared.LogoMark;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/** Frameless ink title bar: drag to move, double-click to maximize, window buttons on the right. */
public final class TitleBar extends HBox {
    private final Label title = new Label("KOMA · Ink Archive");
    private final Button max;
    private double dragDX, dragDY;

    public TitleBar(Stage stage, MainWindow window) {
        getStyleClass().add("titlebar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);
        title.getStyleClass().add("titlebar-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button min = winButton("minimize");
        min.setOnAction(e -> stage.setIconified(true));
        max = winButton("maximize");
        max.setOnAction(e -> window.toggleMaximize());
        Button close = winButton("close");
        close.getStyleClass().add("close");
        close.setOnAction(e -> stage.close());

        LogoMark mark = new LogoMark(16);
        mark.getStyleClass().add("titlebar-mark");
        getChildren().addAll(mark, title, spacer, min, max, close);

        setOnMousePressed(e -> {
            dragDX = e.getScreenX() - stage.getX();
            dragDY = e.getScreenY() - stage.getY();
        });
        setOnMouseDragged(e -> {
            if (e.getButton() != MouseButton.PRIMARY || stage.isFullScreen()) return;
            if (window.isMaximized()) {
                // Restore under the cursor, keeping the same relative grab position.
                double rel = dragDX / stage.getWidth();
                window.toggleMaximize();
                dragDX = stage.getWidth() * rel;
                dragDY = Math.min(dragDY, getHeight() / 2);
            }
            stage.setX(e.getScreenX() - dragDX);
            stage.setY(e.getScreenY() - dragDY);
        });
        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) window.toggleMaximize();
        });
    }

    public void setTitle(String text) {
        title.setText(text);
    }

    public void setMaximized(boolean maximized) {
        max.setGraphic(Icons.of(maximized ? "restore" : "maximize", 12));
    }

    private static Button winButton(String icon) {
        Button b = new Button();
        b.setGraphic(Icons.of(icon, 12));
        b.getStyleClass().add("win-button");
        b.setFocusTraversable(false);
        return b;
    }
}
