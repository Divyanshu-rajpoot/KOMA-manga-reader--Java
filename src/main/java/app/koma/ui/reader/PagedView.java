package app.koma.ui.reader;

import app.koma.core.Anim;
import app.koma.core.Exec;
import app.koma.core.MotionPrefs;
import app.koma.imaging.PageDecoder;
import app.koma.imaging.PageLoader;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Single page or two-page spread. Spreads pair (1,2), (3,4)… with the cover alone; wide pages
 * (already double-page scans) are shown alone. In right-to-left books the first page of a spread is on the right.
 */
final class PagedView implements ReaderMode {
    private final PageLoader loader;
    private final ReaderState state;
    private final IntConsumer onPage;
    private final Runnable onEndForward;
    private final Runnable onEndBackward;
    private final ScrollPane scroll = new ScrollPane();
    private final StackPane stage = new StackPane();
    private final HBox frame = new HBox(6);
    private final ImageView left = new ImageView();
    private final ImageView right = new ImageView();
    private final Label loading = new Label("…");
    private int page;
    private int shown = 1;
    private int requestId;

    PagedView(PageLoader loader, ReaderState state, IntConsumer onPage, Runnable onEndForward, Runnable onEndBackward) {
        this.loader = loader;
        this.state = state;
        this.onPage = onPage;
        this.onEndForward = onEndForward;
        this.onEndBackward = onEndBackward;
        for (ImageView iv : List.of(left, right)) {
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
        }
        frame.getStyleClass().add("page-frame");
        frame.setAlignment(Pos.CENTER);
        frame.setMaxSize(HBox.USE_PREF_SIZE, HBox.USE_PREF_SIZE);
        loading.getStyleClass().add("reader-loading");
        stage.getChildren().addAll(frame, loading);
        stage.getStyleClass().add("reader-stage");
        scroll.setContent(stage);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPannable(true);
        scroll.viewportBoundsProperty().addListener((o, a, b) -> {
            stage.setMinHeight(b.getHeight());
            if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) relayout();
        });
        // click the outer thirds to turn pages (direction-aware); wheel turns pages when the page fits
        stage.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 1) return;
            double w = stage.getWidth();
            if (e.getX() < w / 3) leftAction();
            else if (e.getX() > w * 2 / 3) rightAction();
        });
        scroll.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isControlDown()) return;
            boolean fits = state.fit == ReaderState.Fit.PAGE;
            double v = scroll.getVvalue();
            if (fits || (e.getDeltaY() < 0 && v >= 0.999) || (e.getDeltaY() > 0 && v <= 0.001)) {
                if (e.getDeltaY() < 0) next();
                else if (e.getDeltaY() > 0) prev();
                e.consume();
            }
        });
    }

    @Override
    public Node node() {
        return scroll;
    }

    @Override
    public int current() {
        return page;
    }

    private boolean spread() {
        return state.mode == ReaderState.Mode.SPREAD;
    }

    private boolean wide(int p) {
        PageDecoder.Size s = loader.knownSize(p);
        return s != null && s.wide();
    }

    /** First page of the spread containing p. */
    private int spreadStart(int p) {
        if (!spread() || p <= 0) return Math.max(0, p);
        if (wide(p)) return p;
        int s = p - ((p - 1) % 2);
        if (s < p && wide(s)) return p;
        return s;
    }

    private int countAt(int s) {
        int n = loader.pageCount();
        if (!spread() || s == 0 || s + 1 >= n || wide(s) || wide(s + 1)) return 1;
        return 2;
    }

    @Override
    public void go(int p, boolean animate) {
        int n = loader.pageCount();
        p = Math.max(0, Math.min(n - 1, p));
        int dir = Integer.compare(p, page);
        page = spreadStart(p);
        shown = countAt(page);
        render(animate ? dir : 0);
        onPage.accept(page);
    }

    @Override
    public void next() {
        int n = loader.pageCount();
        if (page + shown >= n) {
            onEndForward.run();
            return;
        }
        go(page + shown, true);
    }

    @Override
    public void prev() {
        if (page <= 0) {
            onEndBackward.run();
            return;
        }
        int target = spreadStart(page - 1);
        page = target;
        shown = countAt(target);
        render(-1);
        onPage.accept(page);
    }

    private void leftAction() {
        if (state.rtl) next();
        else prev();
    }

    private void rightAction() {
        if (state.rtl) prev();
        else next();
    }

    @Override
    public void relayout() {
        render(0);
    }

    /** Target box for one page in device pixels. */
    private int[] box() {
        var vp = scroll.getViewportBounds();
        double scale = Math.max(1, stage.getScene() == null ? 1 : stage.getScene().getWindow().getOutputScaleX());
        double pad = 28;
        double w = Math.max(200, vp.getWidth() - 2 * pad) / shown;
        double h = Math.max(200, vp.getHeight() - 2 * pad);
        if (state.fit == ReaderState.Fit.WIDTH) {
            w = Math.min(w, (shown == 2 ? 1800 : 1100) / (double) shown);
            return new int[]{(int) (w * scale), 0, (int) w, 0};
        }
        return new int[]{(int) (w * scale), (int) (h * scale), (int) w, (int) h};
    }

    private void render(int dir) {
        int id = ++requestId;
        int[] b = box();
        int first = page;
        int count = shown;
        Image a = loader.cached(first, b[0], b[1]);
        Image c = count == 2 ? loader.cached(first + 1, b[0], b[1]) : null;
        if (a != null && (count == 1 || c != null)) {
            apply(a, c, b, dir);
        } else {
            loading.setVisible(true);
            var fa = loader.load(first, b[0], b[1]);
            var fc = count == 2 ? loader.load(first + 1, b[0], b[1]) : java.util.concurrent.CompletableFuture.<Image>completedFuture(null);
            fa.thenCombine(fc, (x, y) -> new Image[]{x, y}).whenComplete((imgs, err) -> Exec.fx(() -> {
                if (id != requestId) return;
                loading.setVisible(false);
                if (err != null || imgs[0] == null) {
                    loading.setText("Can't decode page " + (first + 1));
                    loading.setVisible(true);
                    return;
                }
                // a wide page discovered now can change the pairing
                if (spread() && count == 2 && (wide(first) || wide(first + 1))) {
                    shown = countAt(page);
                    render(0);
                    return;
                }
                apply(imgs[0], imgs[1], b, dir);
            }));
        }
        loader.preload(first + count - 1, state.preloadAhead, state.preloadBehind, b[0], b[1]);
    }

    private void apply(Image a, Image c, int[] b, int dir) {
        loading.setVisible(false);
        left.setImage(null);
        right.setImage(null);
        ImageView firstView = state.rtl && c != null ? right : left;
        ImageView secondView = firstView == left ? right : left;
        size(firstView, a, b);
        firstView.setImage(a);
        if (c != null) {
            size(secondView, c, b);
            secondView.setImage(c);
            frame.getChildren().setAll(left, right);
        } else {
            frame.getChildren().setAll(firstView);
        }
        if (dir != 0 && state.pageAnimation && !MotionPrefs.reduced()) {
            int sign = state.rtl ? -dir : dir;
            FadeTransition f = new FadeTransition(Anim.UI, frame);
            f.setFromValue(0.35);
            f.setToValue(1);
            TranslateTransition t = new TranslateTransition(Anim.UI, frame);
            t.setFromX(18 * sign);
            t.setToX(0);
            ParallelTransition p = new ParallelTransition(f, t);
            p.setInterpolator(Anim.EASE);
            f.setInterpolator(Anim.EASE);
            t.setInterpolator(Anim.EASE);
            p.play();
        }
        if (dir != 0) scroll.setVvalue(0);
    }

    private static void size(ImageView iv, Image img, int[] b) {
        double lw = b[2], lh = b[3];
        double iw = img.getWidth(), ih = img.getHeight();
        if (lh <= 0) {
            iv.setFitWidth(lw);
            iv.setFitHeight(0);
        } else {
            double s = Math.min(lw / iw, lh / ih);
            iv.setFitWidth(iw * s);
            iv.setFitHeight(ih * s);
        }
    }

    @Override
    public boolean onKey(KeyEvent e) {
        KeyCode k = e.getCode();
        switch (k) {
            case LEFT, A -> leftAction();
            case RIGHT, D -> rightAction();
            case SPACE, PAGE_DOWN -> {
                if (e.isShiftDown()) prev();
                else next();
            }
            case PAGE_UP, BACK_SPACE -> prev();
            case DOWN, S -> {
                if (state.fit == ReaderState.Fit.WIDTH && scroll.getVvalue() < 0.999) scroll.setVvalue(Math.min(1, scroll.getVvalue() + 0.15));
                else next();
            }
            case UP, W -> {
                if (state.fit == ReaderState.Fit.WIDTH && scroll.getVvalue() > 0.001) scroll.setVvalue(Math.max(0, scroll.getVvalue() - 0.15));
                else prev();
            }
            case HOME -> go(0, true);
            case END -> go(loader.pageCount() - 1, true);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void dispose() {
        requestId++;
    }
}
