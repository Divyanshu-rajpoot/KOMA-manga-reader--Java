package app.koma.ui.reader;

import app.koma.AppContext;
import app.koma.archive.CbzIndex;
import app.koma.core.Exec;
import app.koma.imaging.PageLoader;
import app.koma.library.Series;
import app.koma.library.Volume;
import app.koma.ui.shared.Css;
import app.koma.ui.shared.Fmt;
import app.koma.ui.shared.Icons;
import app.koma.ui.shell.Screen;
import app.koma.ui.shell.View;
import javafx.animation.PauseTransition;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The reader: top bar (back, Spread / Single / Long strip, title + page counter, Fit / Width, direction,
 * more), the page area, and the thumbnail strip + scrubber. Remembers progress and logs a session on close.
 */
public final class ReaderView implements View {
    private final AppContext ctx;
    private final Volume volume;
    private final Series series;
    private final StackPane root = new StackPane();
    private final BorderPane layout = new BorderPane();
    private final Label title = new Label();
    private final Label sub = new Label();
    private final ReaderState state = new ReaderState();
    private final PauseTransition saveLater = new PauseTransition(Duration.millis(1200));
    private final PauseTransition resizeLater = new PauseTransition(Duration.millis(140));
    private final ToggleButton spreadB = modeButton("Spread");
    private final ToggleButton singleB = modeButton("Single");
    private final ToggleButton stripB = modeButton("Long strip");
    private final ToggleButton fitB = modeButton("Fit");
    private final ToggleButton widthB = modeButton("Width");
    private final Button dirB = new Button();
    private HBox top;
    private PageLoader loader;
    private ReaderMode mode;
    private ThumbStrip strip;
    private int startPage;
    private int page;
    private boolean disposed;
    private boolean chromeHidden;
    /** Full-screen reading: no title bar, no frame, controls appear as overlays near the screen edges. */
    private boolean immersive;
    private final VBox overlayTop = new VBox();
    private final VBox overlayBottom = new VBox();
    private final PauseTransition hideOverlays = new PauseTransition(Duration.millis(1600));
    private final PauseTransition hideCursor = new PauseTransition(Duration.millis(2200));
    private javafx.beans.value.ChangeListener<Boolean> fullScreenListener;
    private EventHandler<KeyEvent> keys;
    private final ZoomBar zoomBar = new ZoomBar(this::setZoom);
    private final PauseTransition hideZoom = new PauseTransition(Duration.millis(1800));

    public ReaderView(AppContext ctx, String volumeId) {
        this.ctx = ctx;
        this.volume = ctx.store.findVolume(volumeId);
        this.series = volume == null ? null : ctx.store.seriesOf(volume);
        root.getStyleClass().add("reader");
        Css.add(root, ReaderView.class, "reader");
        // overlays hold the top bar and thumbnail strip while reading full screen
        for (VBox o : List.of(overlayTop, overlayBottom)) {
            o.getStyleClass().add("reader-overlay");
            o.setMaxHeight(Region.USE_PREF_SIZE);
            o.setVisible(false);
        }
        StackPane.setAlignment(overlayTop, Pos.TOP_CENTER);
        StackPane.setAlignment(overlayBottom, Pos.BOTTOM_CENTER);
        StackPane.setAlignment(zoomBar, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(zoomBar, new Insets(0, 26, 132, 0));
        root.getChildren().addAll(layout, overlayTop, overlayBottom, zoomBar);
        hideZoom.setOnFinished(e -> syncZoomBar());
        hideOverlays.setOnFinished(e -> showOverlays(false));
        hideCursor.setOnFinished(e -> {
            if (immersive) root.setCursor(javafx.scene.Cursor.NONE);
        });

        if (volume == null || series == null) {
            fail("This volume is no longer in your library.");
            return;
        }
        state.rtl = !"ltr".equals(series.direction);
        state.mode = "ttb".equals(series.direction) ? ReaderState.Mode.STRIP
                : "spread".equals(ctx.settings.readerMode) ? ReaderState.Mode.SPREAD : ReaderState.Mode.SINGLE;
        state.pageAnimation = ctx.settings.pageTurnAnimation;
        state.preloadAhead = ctx.settings.preloadAhead;
        state.preloadBehind = ctx.settings.preloadBehind;
        startPage = volume.progress == null || volume.progress.finished ? 0 : volume.progress.page;
        page = startPage;

        layout.setTop(topBar());
        Label opening = new Label("OPENING " + Fmt.upper(volume.fileName()) + "…");
        opening.getStyleClass().add("reader-loading");
        layout.setCenter(opening);
        updateTitle();

        saveLater.setOnFinished(e -> ctx.store.updateProgress(volume, page));
        resizeLater.setOnFinished(e -> {
            if (mode != null) mode.relayout();
        });
        layout.widthProperty().addListener((o, a, b) -> resizeLater.playFromStart());

        Path path = Path.of(volume.path);
        Exec.io(() -> {
            if (!Files.isRegularFile(path)) throw new IllegalStateException("File not found: " + path);
            try {
                return CbzIndex.open(path);
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }).whenComplete((idx, err) -> Exec.fx(() -> {
            if (disposed) {
                if (idx != null) Exec.io(idx::close);
                return;
            }
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                fail("Can't open this volume.\n" + c.getMessage());
                return;
            }
            if (idx.pageCount() == 0) {
                Exec.io(idx::close);
                fail("This archive has no images.");
                return;
            }
            opened(idx);
        }));
    }

    private void opened(CbzIndex idx) {
        loader = new PageLoader(idx, ctx.pages, ctx.pageThumbs, volume.id);
        if (volume.pages != idx.pageCount()) volume.pages = idx.pageCount();
        page = Math.min(page, idx.pageCount() - 1);
        strip = new ThumbStrip(loader, state.rtl && state.mode != ReaderState.Mode.STRIP, p -> mode.go(p, true));
        BorderPane.setMargin(strip, new Insets(6, 22, 12, 22));
        placeChrome();
        buildMode();
        syncButtons();
    }

    private void buildMode() {
        int p = mode == null ? page : mode.current();
        if (mode != null) mode.dispose();
        mode = state.mode == ReaderState.Mode.STRIP
                ? new StripView(loader, state, this::onPage, this::endForward)
                : new PagedView(loader, state, this::onPage, this::endForward, this::endBackward);
        layout.setCenter(mode.node());
        BorderPane.setMargin(mode.node(), new Insets(0));
        // let layout settle so the viewport has a size before the first page request
        javafx.application.Platform.runLater(() -> {
            if (!disposed) mode.go(p, false);
        });
    }

    private void onPage(int p) {
        page = p;
        updateTitle();
        if (strip != null && loader != null) {
            strip.setPage(p, cachedRange());
        }
        saveLater.playFromStart();
    }

    private int[] cachedRange() {
        // report pages that are decoded at any size near the current one
        // bounded: each probe scans the cache, so only look as far as the thumbnail strip shows
        int lo = page, hi = page;
        int n = loader.pageCount();
        while (lo - 1 >= 0 && page - lo < 8 && anyCached(lo - 1)) lo--;
        while (hi + 1 < n && hi - page < 8 && anyCached(hi + 1)) hi++;
        return anyCached(page) ? new int[]{lo, hi} : null;
    }

    private boolean anyCached(int p) {
        String prefix = volume.id + ":" + p + ":";
        return ctx.pages.containsPrefix(prefix);
    }

    private void updateTitle() {
        if (volume == null) return;
        String kind = "chapter".equals(volume.kind) ? " · CH. " : " · VOL. ";
        title.setText(Fmt.upper(series.title) + kind + volume.label());
        String dir = state.mode == ReaderState.Mode.STRIP ? "long strip" : state.rtl ? "right to left" : "left to right";
        int total = loader == null ? volume.pages : loader.pageCount();
        sub.setText("Page " + (page + 1) + " / " + total + " · " + dir);
    }

    private void endForward() {
        Volume next = neighbour(1);
        if (next != null) {
            ctx.store.updateProgress(volume, loader.pageCount() - 1);
            ctx.window.toast("Next: " + series.title + " vol. " + next.label());
            ctx.router.replace(Screen.READER, next.id);
        } else {
            ctx.store.updateProgress(volume, loader.pageCount() - 1);
            ctx.window.toast("End of " + series.title + ". You're all caught up.");
        }
    }

    private void endBackward() {
        Volume prev = neighbour(-1);
        if (prev != null) ctx.window.toast("Start of the volume. Previous: vol. " + prev.label() + " (Ctrl ←)");
    }

    private Volume neighbour(int dir) {
        List<Volume> sorted = series.sortedVolumes();
        int i = sorted.indexOf(volume) + dir;
        return i >= 0 && i < sorted.size() ? sorted.get(i) : null;
    }

    private HBox topBar() {
        Button back = new Button();
        back.setGraphic(Icons.of("left", 16));
        back.getStyleClass().add("reader-button");
        back.setTooltip(new Tooltip("Back (Esc)"));
        back.setOnAction(e -> ctx.router.back());

        ToggleGroup modes = new ToggleGroup();
        for (ToggleButton b : List.of(spreadB, singleB, stripB)) b.setToggleGroup(modes);
        spreadB.setOnAction(e -> setMode(ReaderState.Mode.SPREAD));
        singleB.setOnAction(e -> setMode(ReaderState.Mode.SINGLE));
        stripB.setOnAction(e -> setMode(ReaderState.Mode.STRIP));
        HBox leftBox = new HBox(8, back, spreadB, singleB, stripB);
        leftBox.setAlignment(Pos.CENTER_LEFT);

        title.getStyleClass().add("reader-title");
        sub.getStyleClass().add("reader-sub");
        VBox mid = new VBox(0, title, sub);
        mid.setAlignment(Pos.CENTER);
        HBox.setHgrow(mid, Priority.ALWAYS);

        ToggleGroup fits = new ToggleGroup();
        fitB.setToggleGroup(fits);
        widthB.setToggleGroup(fits);
        fitB.setTooltip(new Tooltip("Fit page to window (Z)"));
        widthB.setTooltip(new Tooltip("Fit width, scroll down the page (Z)"));
        fitB.setOnAction(e -> setFit(ReaderState.Fit.PAGE));
        widthB.setOnAction(e -> setFit(ReaderState.Fit.WIDTH));
        dirB.getStyleClass().add("reader-button");
        dirB.setTooltip(new Tooltip("Reading direction"));
        dirB.setOnAction(e -> {
            state.rtl = !state.rtl;
            ctx.store.setSeriesMeta(series, null, null, state.rtl ? "rtl" : "ltr");
            rebuildStrip();
            if (mode != null) mode.relayout();
            syncButtons();
            updateTitle();
        });
        Button full = new Button();
        full.setGraphic(Icons.of("fullscreen", 14));
        full.getStyleClass().add("reader-button");
        full.setTooltip(new Tooltip("Full screen (F or F11, or double-click the page)"));
        full.setOnAction(e -> toggleImmersive());
        Button more = new Button();
        more.setGraphic(Icons.of("more", 16));
        more.getStyleClass().add("reader-button");
        more.setOnAction(e -> moreMenu().show(more, javafx.geometry.Side.BOTTOM, 0, 4));
        HBox rightBox = new HBox(8, fitB, widthB, dirB, full, more);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        leftBox.setMinWidth(Region.USE_PREF_SIZE);
        rightBox.setMinWidth(Region.USE_PREF_SIZE);

        top = new HBox(16, leftBox, mid, rightBox);
        top.getStyleClass().add("reader-top");
        top.setAlignment(Pos.CENTER);
        syncButtons();
        return top;
    }

    private ContextMenu moreMenu() {
        MenuItem read = new MenuItem(volume.isFinished() ? "Mark volume as unread" : "Mark volume as read");
        read.setOnAction(e -> ctx.store.setFinished(volume, !volume.isFinished()));
        MenuItem open = new MenuItem("Open series");
        open.setOnAction(e -> ctx.router.navigate(Screen.SERIES, series.id));
        MenuItem prev = new MenuItem("Previous volume");
        Volume pv = neighbour(-1);
        prev.setDisable(pv == null);
        prev.setOnAction(e -> ctx.router.replace(Screen.READER, pv.id));
        MenuItem next = new MenuItem("Next volume");
        Volume nv = neighbour(1);
        next.setDisable(nv == null);
        next.setOnAction(e -> ctx.router.replace(Screen.READER, nv.id));
        MenuItem theme = new MenuItem("Toggle light / dark");
        theme.setOnAction(e -> {
            ctx.theme.toggle();
            ctx.window.nav().updateThemeIcon();
        });
        MenuItem help = new MenuItem("Shortcuts: ← → Space PgUp/PgDn Home End · 1 2 3 modes · F full screen · Z fit · + − 0 zoom (or Ctrl+wheel) · Tab bars · Esc");
        help.setDisable(true);
        return new ContextMenu(read, open, prev, next, theme, help);
    }

    private void setMode(ReaderState.Mode m) {
        if (state.mode != m) {
            state.mode = m;
            if (m != ReaderState.Mode.STRIP) {
                ctx.settings.readerMode = m == ReaderState.Mode.SPREAD ? "spread" : "single";
                ctx.settings.save();
            }
            if (loader != null) {
                rebuildStrip();
                buildMode();
            }
        }
        syncButtons();
        updateTitle();
    }

    private void rebuildStrip() {
        if (loader == null) return;
        strip = new ThumbStrip(loader, state.rtl && state.mode != ReaderState.Mode.STRIP, p -> mode.go(p, true));
        BorderPane.setMargin(strip, new Insets(6, 22, 12, 22));
        placeChrome();
        strip.setPage(page, null);
    }

    /** Zoom 25 to 400 %, from the slider, Ctrl +/-/0, Ctrl+wheel or a pinch. */
    private void setZoom(double z) {
        z = Math.max(ZoomBar.MIN, Math.min(ZoomBar.MAX, Math.round(z * 100) / 100.0));
        if (Math.abs(z - state.zoom) < 0.001) {
            zoomBar.set(state.zoom);
            return;
        }
        state.zoom = z;
        zoomBar.set(z);
        if (mode != null) mode.zoomChanged();
        syncZoomBar();
        if (immersive) hideZoom.playFromStart(); // full screen: flash the bar, then tuck it away again
    }

    private void zoomBy(double delta) {
        setZoom(state.zoom + delta);
    }

    /** In full screen the zoom bar shows with the other controls (or briefly after a zoom). */
    private void syncZoomBar() {
        zoomBar.setVisible(!immersive || overlayBottom.isVisible() || hideZoom.getStatus() == javafx.animation.Animation.Status.RUNNING);
    }

    private void setFit(ReaderState.Fit f) {
        state.fit = f;
        syncButtons();
        if (mode != null) mode.relayout();
    }

    private void syncButtons() {
        spreadB.setSelected(state.mode == ReaderState.Mode.SPREAD);
        singleB.setSelected(state.mode == ReaderState.Mode.SINGLE);
        stripB.setSelected(state.mode == ReaderState.Mode.STRIP);
        fitB.setSelected(state.fit == ReaderState.Fit.PAGE);
        widthB.setSelected(state.fit == ReaderState.Fit.WIDTH);
        dirB.setText(state.rtl ? "RTL" : "LTR");
        dirB.setDisable(state.mode == ReaderState.Mode.STRIP);
    }

    private void toggleChrome() {
        if (immersive) {
            showOverlays(!overlayTop.isVisible());
            return;
        }
        chromeHidden = !chromeHidden;
        placeChrome();
    }

    /** Puts the top bar and thumbnail strip inline (normal) or into the overlays (full screen). */
    private void placeChrome() {
        // detach first: a node can only have one parent
        layout.setTop(null);
        layout.setBottom(null);
        overlayTop.getChildren().clear();
        overlayBottom.getChildren().clear();
        if (immersive) {
            overlayTop.getChildren().add(top);
            if (strip != null) overlayBottom.getChildren().add(strip);
        } else if (!chromeHidden) {
            layout.setTop(top);
            if (strip != null) layout.setBottom(strip);
        }
        showOverlays(false);
        if (mode != null) resizeLater.playFromStart();
    }

    private void showOverlays(boolean show) {
        boolean visible = show && immersive;
        overlayTop.setVisible(visible);
        overlayBottom.setVisible(visible);
        syncZoomBar();
    }

    private void toggleImmersive() {
        setImmersive(!immersive);
    }

    private void setImmersive(boolean on) {
        if (immersive == on) return;
        immersive = on;
        state.immersive = on;
        if (on) {
            root.getStyleClass().add("immersive");
        } else {
            root.getStyleClass().remove("immersive");
            root.setCursor(null);
            hideCursor.stop();
        }
        if (ctx.stage.isFullScreen() != on) ctx.window.setFullScreen(on);
        placeChrome();
        if (on) {
            ctx.window.toast("Full screen · move the mouse to the top or bottom for controls · Esc or F to exit");
            hideCursor.playFromStart();
        }
    }

    private void fail(String message) {
        Label l = new Label(message);
        l.getStyleClass().add("reader-error");
        l.setWrapText(true);
        Button back = new Button("BACK");
        back.getStyleClass().add("ink-button");
        back.setOnAction(e -> ctx.router.back());
        VBox box = new VBox(16, l, back);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(560);
        layout.setCenter(box);
        layout.setBottom(null);
    }

    @Override
    public String title() {
        return volume == null ? "Reader" : series.title + " · Vol. " + volume.label();
    }

    @Override
    public void onShown() {
        Scene scene = root.getScene();
        if (scene == null) return;
        keys = e -> {
            if (e.getTarget() instanceof javafx.scene.control.TextInputControl) return;
            if (handleKey(e)) e.consume();
        };
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keys);
        // Ctrl + wheel and trackpad pinch zoom (filters run before the page views see the event)
        root.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
            if (e.isShortcutDown() && mode != null) {
                if (e.getDeltaY() != 0) setZoom(state.zoom * (e.getDeltaY() > 0 ? 1.1 : 1 / 1.1)); // proportional, so 25% to 400% is the same effort each way
                e.consume();
            }
        });
        root.addEventFilter(javafx.scene.input.ZoomEvent.ZOOM, e -> {
            if (mode != null) setZoom(state.zoom * e.getZoomFactor());
            e.consume();
        });
        root.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.MIDDLE) {
                toggleChrome();
            } else if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                // double-click the middle of the page (the outer thirds turn pages)
                double x = e.getX() / root.getWidth();
                if (state.mode == ReaderState.Mode.STRIP || (x > 1 / 3.0 && x < 2 / 3.0)) toggleImmersive();
            }
        });
        // full screen: controls appear near the top/bottom edge; the cursor hides when idle
        root.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, e -> {
            if (!immersive) return;
            root.setCursor(null);
            hideCursor.playFromStart();
            boolean nearEdge = e.getY() < 110 || e.getY() > root.getHeight() - 150;
            if (nearEdge) {
                showOverlays(true);
                hideOverlays.stop();
            } else if (overlayTop.isVisible()) {
                hideOverlays.playFromStart();
            }
        });
        // leaving full screen any other way also leaves full-screen reading
        fullScreenListener = (o, was, now) -> {
            if (!now && immersive) setImmersive(false);
        };
        ctx.stage.fullScreenProperty().addListener(fullScreenListener);
        root.requestFocus();
    }

    private boolean handleKey(KeyEvent e) {
        KeyCode k = e.getCode();
        if (k == KeyCode.ESCAPE) {
            if (immersive) setImmersive(false);
            else if (ctx.stage.isFullScreen()) ctx.window.setFullScreen(false);
            else ctx.router.back();
            return true;
        }
        if (k == KeyCode.F11 || (k == KeyCode.F && !e.isShortcutDown() && !e.isAltDown())) {
            toggleImmersive();
            return true;
        }
        if (mode == null) return false;
        boolean plus = k == KeyCode.EQUALS || k == KeyCode.PLUS || k == KeyCode.ADD;
        boolean minus = k == KeyCode.MINUS || k == KeyCode.SUBTRACT;
        if (plus || minus) {
            zoomBy(plus ? ZoomBar.STEP : -ZoomBar.STEP);
            return true;
        }
        if (k == KeyCode.DIGIT0 || k == KeyCode.NUMPAD0) {
            setZoom(1);
            return true;
        }
        if (e.isShortcutDown() && (k == KeyCode.LEFT || k == KeyCode.RIGHT)) {
            Volume v = neighbour(k == KeyCode.RIGHT ? 1 : -1);
            if (v != null) ctx.router.replace(Screen.READER, v.id);
            return true;
        }
        if (e.isShortcutDown() || e.isAltDown()) return false;
        switch (k) {
            case DIGIT1, NUMPAD1 -> setMode(ReaderState.Mode.SPREAD);
            case DIGIT2, NUMPAD2 -> setMode(ReaderState.Mode.SINGLE);
            case DIGIT3, NUMPAD3 -> setMode(ReaderState.Mode.STRIP);
            case Z -> setFit(state.fit == ReaderState.Fit.PAGE ? ReaderState.Fit.WIDTH : ReaderState.Fit.PAGE);
            case TAB -> toggleChrome();
            default -> {
                return mode.onKey(e);
            }
        }
        return true;
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        saveLater.stop();
        resizeLater.stop();
        hideOverlays.stop();
        hideCursor.stop();
        hideZoom.stop();
        if (fullScreenListener != null) ctx.stage.fullScreenProperty().removeListener(fullScreenListener);
        if (keys != null && root.getScene() != null) root.getScene().removeEventFilter(KeyEvent.KEY_PRESSED, keys);
        if (volume != null && loader != null) {
            ctx.store.addSession(volume, startPage, page);
            ctx.store.updateProgress(volume, page);
        }
        if (mode != null) mode.dispose();
        if (loader != null) loader.close();
        if (ctx.stage.isFullScreen()) ctx.window.setFullScreen(false);
    }

    @Override
    public Parent root() {
        return root;
    }

    private static ToggleButton modeButton(String text) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().add("reader-toggle");
        b.setFocusTraversable(false);
        return b;
    }
}
