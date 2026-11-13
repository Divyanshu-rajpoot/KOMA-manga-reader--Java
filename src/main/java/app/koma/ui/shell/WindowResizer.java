package app.koma.ui.shell;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

/** Resize handles on the edges of the undecorated window. */
final class WindowResizer {
    private static final double EDGE = 6;

    private final Stage stage;
    private final MainWindow window;
    private Cursor zone = Cursor.DEFAULT;
    private boolean resizing;
    private double startX, startY, startW, startH, startSX, startSY;

    WindowResizer(Stage stage, MainWindow window) {
        this.stage = stage;
        this.window = window;
    }

    void install(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (window.isMaximized() || stage.isFullScreen()) {
                setZone(scene, Cursor.DEFAULT);
                return;
            }
            setZone(scene, zoneAt(e.getSceneX(), e.getSceneY(), scene.getWidth(), scene.getHeight()));
        });
        scene.addEventFilter(MouseEvent.MOUSE_EXITED_TARGET, e -> {
            if (!resizing && e.getTarget() == scene) setZone(scene, Cursor.DEFAULT);
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (zone == Cursor.DEFAULT) return;
            resizing = true;
            startX = stage.getX();
            startY = stage.getY();
            startW = stage.getWidth();
            startH = stage.getHeight();
            startSX = e.getScreenX();
            startSY = e.getScreenY();
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!resizing) return;
            double dx = e.getScreenX() - startSX, dy = e.getScreenY() - startSY;
            double minW = stage.getMinWidth(), minH = stage.getMinHeight();
            if (zone == Cursor.E_RESIZE || zone == Cursor.NE_RESIZE || zone == Cursor.SE_RESIZE) {
                stage.setWidth(Math.max(minW, startW + dx));
            }
            if (zone == Cursor.S_RESIZE || zone == Cursor.SE_RESIZE || zone == Cursor.SW_RESIZE) {
                stage.setHeight(Math.max(minH, startH + dy));
            }
            if (zone == Cursor.W_RESIZE || zone == Cursor.NW_RESIZE || zone == Cursor.SW_RESIZE) {
                double w = Math.max(minW, startW - dx);
                stage.setX(startX + startW - w);
                stage.setWidth(w);
            }
            if (zone == Cursor.N_RESIZE || zone == Cursor.NW_RESIZE || zone == Cursor.NE_RESIZE) {
                double h = Math.max(minH, startH - dy);
                stage.setY(startY + startH - h);
                stage.setHeight(h);
            }
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (resizing) {
                resizing = false;
                e.consume();
            }
        });
    }

    private void setZone(Scene scene, Cursor c) {
        if (resizing) return;
        zone = c;
        scene.setCursor(c == Cursor.DEFAULT ? null : c);
    }

    private static Cursor zoneAt(double x, double y, double w, double h) {
        boolean l = x < EDGE, r = x > w - EDGE, t = y < EDGE, b = y > h - EDGE;
        if (t && l) return Cursor.NW_RESIZE;
        if (t && r) return Cursor.NE_RESIZE;
        if (b && l) return Cursor.SW_RESIZE;
        if (b && r) return Cursor.SE_RESIZE;
        if (l) return Cursor.W_RESIZE;
        if (r) return Cursor.E_RESIZE;
        if (t) return Cursor.N_RESIZE;
        if (b) return Cursor.S_RESIZE;
        return Cursor.DEFAULT;
    }
}
