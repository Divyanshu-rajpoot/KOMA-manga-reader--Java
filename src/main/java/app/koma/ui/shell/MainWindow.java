package app.koma.ui.shell;

import app.koma.AppContext;
import app.koma.core.Anim;
import app.koma.core.ThemeManager;
import app.koma.ui.shared.Css;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.File;

/** Undecorated stage: title bar, nav bar, content host, toast layer, drag-and-drop, global shortcuts. */
public final class MainWindow {
    private final AppContext ctx;
    private final Stage stage;
    private final TitleBar titleBar;
    private final NavBar nav;
    private final StackPane toastLayer = new StackPane();
    private boolean maximized;
    private Rectangle2D restoreBounds;

    public MainWindow(AppContext ctx, boolean systemDark) {
        this.ctx = ctx;
        this.stage = ctx.stage;
        ctx.window = this;
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("KOMA");
        stage.setMinWidth(980);
        stage.setMinHeight(640);

        titleBar = new TitleBar(stage, this);
        nav = new NavBar(ctx);
        StackPane host = new StackPane();
        host.getStyleClass().add("content-host");

        VBox top = new VBox(titleBar, nav);
        BorderPane frame = new BorderPane(host);
        frame.setTop(top);
        frame.getStyleClass().add("window-root");

        toastLayer.setPickOnBounds(false);
        toastLayer.setAlignment(Pos.BOTTOM_CENTER);
        StackPane root = new StackPane(frame, toastLayer);
        root.setFocusTraversable(false);
        Css.add(root, MainWindow.class, "shell");

        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        double w = Math.min(1440, vb.getWidth() * 0.9), h = Math.min(920, vb.getHeight() * 0.9);
        Scene scene = new Scene(root, w, h);
        ctx.theme = new ThemeManager(scene, ctx.settings, systemDark);
        nav.updateThemeIcon();
        titleBar.setMaximized(false);

        ctx.router = new Router(ctx, host, (screen, title) -> {
            nav.setVisible(screen.showNav);
            nav.setManaged(screen.showNav);
            nav.setActive(screen);
            titleBar.setTitle("KOMA · Ink Archive · " + (title != null ? title : screen.title));
            stage.setTitle("KOMA · " + (title != null ? title : screen.title));
        });

        installDragAndDrop(scene);
        installShortcuts(scene);
        new WindowResizer(stage, this).install(scene);
        stage.setScene(scene);
        stage.centerOnScreen();
    }

    public void show() {
        stage.show();
        stage.getScene().getRoot().requestFocus(); // don't start with the cursor in the search box
    }

    public NavBar nav() {
        return nav;
    }

    public boolean isMaximized() {
        return maximized;
    }

    /** Maximize to the visual bounds (keeps the taskbar visible, which an undecorated stage otherwise covers). */
    public void toggleMaximize() {
        if (maximized) {
            stage.setX(restoreBounds.getMinX());
            stage.setY(restoreBounds.getMinY());
            stage.setWidth(restoreBounds.getWidth());
            stage.setHeight(restoreBounds.getHeight());
            maximized = false;
        } else {
            restoreBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
            var screens = Screen.getScreensForRectangle(restoreBounds);
            Rectangle2D vb = (screens.isEmpty() ? Screen.getPrimary() : screens.get(0)).getVisualBounds();
            stage.setX(vb.getMinX());
            stage.setY(vb.getMinY());
            stage.setWidth(vb.getWidth());
            stage.setHeight(vb.getHeight());
            maximized = true;
        }
        titleBar.setMaximized(maximized);
    }

    public void toggleFullScreen() {
        stage.setFullScreenExitHint("");
        stage.setFullScreen(!stage.isFullScreen());
        titleBar.setVisible(!stage.isFullScreen());
        titleBar.setManaged(!stage.isFullScreen());
    }

    public void toast(String message) {
        Label l = new Label(message);
        l.setWrapText(true);
        l.setMaxWidth(560);
        StackPane box = new StackPane(l);
        box.getStyleClass().add("toast");
        box.setMaxSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);
        StackPane.setMargin(box, new javafx.geometry.Insets(0, 0, 32, 0));
        toastLayer.getChildren().add(box);
        FadeTransition in = new FadeTransition(Anim.UI, box);
        in.setFromValue(0);
        in.setToValue(1);
        FadeTransition out = new FadeTransition(Anim.UI, box);
        out.setToValue(0);
        SequentialTransition s = new SequentialTransition(in, new PauseTransition(Duration.seconds(3.2)), out);
        s.setOnFinished(e -> toastLayer.getChildren().remove(box));
        s.play();
    }

    private void installDragAndDrop(Scene scene) {
        scene.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            e.consume();
        });
        scene.setOnDragDropped(e -> {
            var db = e.getDragboard();
            if (db.hasFiles()) {
                var paths = db.getFiles().stream().map(File::toPath).toList();
                if (ctx.router.currentScreen() == app.koma.ui.shell.Screen.IMPORT
                        && ctx.router.currentView() instanceof app.koma.ui.importer.ImportView iv) {
                    iv.addPaths(paths);
                } else {
                    ctx.router.navigate(app.koma.ui.shell.Screen.IMPORT, paths);
                }
            }
            e.setDropCompleted(db.hasFiles());
            e.consume();
        });
    }

    private void installShortcuts(Scene scene) {
        var acc = scene.getAccelerators();
        acc.put(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN), () -> {
            if (nav.isVisible()) nav.focusSearch();
        });
        acc.put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), nav::chooseFiles);
        acc.put(new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), () -> {
            ctx.theme.toggle();
            nav.updateThemeIcon();
        });
        acc.put(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN),
                () -> ctx.router.navigate(app.koma.ui.shell.Screen.SETTINGS, null));
        acc.put(new KeyCodeCombination(KeyCode.F11), this::toggleFullScreen);
        acc.put(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN), ctx.router::back);
    }
}
