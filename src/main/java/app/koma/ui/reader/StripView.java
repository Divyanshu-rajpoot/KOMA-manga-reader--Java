package app.koma.ui.reader;

import app.koma.core.Exec;
import app.koma.imaging.PageDecoder;
import app.koma.imaging.PageLoader;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Long strip (manhwa / webtoon). Virtualized: page heights come from image headers (read in the
 * background), and only pages near the viewport have an ImageView with a decoded image.
 */
final class StripView implements ReaderMode {
    private static final double DEFAULT_RATIO = 1.45;

    private final PageLoader loader;
    private final ReaderState state;
    private final IntConsumer onPage;
    private final Runnable onEndForward;
    private final BorderPane root = new BorderPane();
    private final Pane viewport = new Pane();
    private final ScrollBar bar = new ScrollBar();
    private final double[] ratio;
    private final double[] tops;
    private final Map<Integer, ImageView> live = new HashMap<>();
    private double top;
    private double totalH;
    private double colW = 800;
    private int current;
    private boolean disposed;
    private boolean syncing;

    StripView(PageLoader loader, ReaderState state, IntConsumer onPage, Runnable onEndForward) {
        this.loader = loader;
        this.state = state;
        this.onPage = onPage;
        this.onEndForward = onEndForward;
        int n = loader.pageCount();
        ratio = new double[n];
        tops = new double[n + 1];
        java.util.Arrays.fill(ratio, DEFAULT_RATIO);

        viewport.getStyleClass().add("reader-stage");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(viewport.widthProperty());
        clip.heightProperty().bind(viewport.heightProperty());
        viewport.setClip(clip);
        bar.setOrientation(Orientation.VERTICAL);
        bar.getStyleClass().add("strip-bar");
        root.setCenter(viewport);
        root.setRight(bar);

        viewport.widthProperty().addListener((o, a, b) -> relayout());
        viewport.heightProperty().addListener((o, a, b) -> update());
        bar.valueProperty().addListener((o, a, b) -> {
            if (!syncing) setTop(b.doubleValue());
        });
        viewport.addEventFilter(ScrollEvent.SCROLL, e -> {
            setTop(top - e.getDeltaY() * 2.2);
            e.consume();
        });
        loadSizes();
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public int current() {
        return current;
    }

    /** Header-only size reads for every page, applied in batches. */
    private void loadSizes() {
        Exec.io(() -> {
            int n = loader.pageCount();
            double[] batch = new double[n];
            int from = 0;
            for (int p = 0; p < n && !disposed; p++) {
                try {
                    PageDecoder.Size s = loader.size(p);
                    batch[p] = s.height() / (double) Math.max(1, s.width());
                } catch (Exception e) {
                    batch[p] = DEFAULT_RATIO;
                }
                if ((p + 1) % 24 == 0 || p == n - 1) {
                    int f = from, t = p;
                    double[] copy = java.util.Arrays.copyOfRange(batch, f, t + 1);
                    Exec.fx(() -> applyRatios(f, copy));
                    from = p + 1;
                }
            }
        });
    }

    private void applyRatios(int from, double[] r) {
        if (disposed) return;
        int anchor = current;
        double within = (top - tops[anchor]) / Math.max(1, tops[anchor + 1] - tops[anchor]);
        System.arraycopy(r, 0, ratio, from, r.length);
        computeTops();
        setTop(tops[anchor] + within * (tops[anchor + 1] - tops[anchor]));
    }

    private void computeTops() {
        double y = 0;
        for (int i = 0; i < ratio.length; i++) {
            tops[i] = y;
            y += Math.round(colW * ratio[i]);
        }
        tops[ratio.length] = y;
        totalH = y;
    }

    @Override
    public void relayout() {
        double w = viewport.getWidth();
        if (w <= 0) return;
        int anchor = current;
        double within = tops[anchor + 1] > tops[anchor] ? (top - tops[anchor]) / (tops[anchor + 1] - tops[anchor]) : 0;
        double maxCol = state.fit == ReaderState.Fit.WIDTH ? 1400 : 820;
        colW = Math.max(240, Math.min(w - 32, maxCol));
        computeTops();
        for (ImageView iv : live.values()) iv.setImage(null);
        live.clear();
        viewport.getChildren().clear();
        setTop(tops[anchor] + within * (tops[anchor + 1] - tops[anchor]));
    }

    private void setTop(double t) {
        double max = Math.max(0, totalH - viewport.getHeight());
        top = Math.max(0, Math.min(max, t));
        syncing = true;
        bar.setMin(0);
        bar.setMax(max);
        bar.setVisibleAmount(max <= 0 ? 0 : viewport.getHeight() / totalH * max);
        bar.setUnitIncrement(80);
        bar.setBlockIncrement(viewport.getHeight() * 0.9);
        bar.setValue(top);
        syncing = false;
        update();
    }

    private int pageAt(double y) {
        int lo = 0, hi = ratio.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (tops[mid] <= y) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    private void update() {
        if (disposed || ratio.length == 0) return;
        double vh = viewport.getHeight(), vw = viewport.getWidth();
        if (vh <= 0 || vw <= 0) return;
        double x = Math.round((vw - colW) / 2);
        int first = pageAt(Math.max(0, top - vh * 0.5));
        int last = pageAt(top + vh * 1.8);
        double scale = viewport.getScene() == null ? 1 : Math.max(1, viewport.getScene().getWindow().getOutputScaleX());
        int targetW = (int) Math.round(colW * scale);

        Iterator<Map.Entry<Integer, ImageView>> it = live.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getKey() < first - 1 || e.getKey() > last + 1) {
                e.getValue().setImage(null);
                viewport.getChildren().remove(e.getValue());
                it.remove();
            }
        }
        for (int p = first; p <= last; p++) {
            ImageView iv = live.get(p);
            if (iv == null) {
                iv = new ImageView();
                iv.setSmooth(true);
                live.put(p, iv);
                viewport.getChildren().add(iv);
                int page = p;
                ImageView view = iv;
                Image cached = loader.cached(p, targetW, 0);
                if (cached != null) {
                    view.setImage(cached);
                } else {
                    loader.load(p, targetW, 0).whenComplete((img, err) -> Exec.fx(() -> {
                        if (img == null || live.get(page) != view) return;
                        view.setImage(img);
                        double r = img.getHeight() / Math.max(1, img.getWidth());
                        if (Math.abs(r - ratio[page]) > 0.01) applyRatios(page, new double[]{r});
                    }));
                }
            }
            iv.setFitWidth(colW);
            iv.setFitHeight(Math.round(colW * ratio[p]));
            iv.relocate(x, Math.round(tops[p] - top));
        }
        int cur = pageAt(top + vh * 0.25);
        if (top >= totalH - vh - 1) cur = ratio.length - 1;
        if (cur != current) {
            current = cur;
            onPage.accept(cur);
        }
        loader.preload(last, state.preloadAhead, 0, targetW, 0);
    }

    @Override
    public void go(int page, boolean animate) {
        page = Math.max(0, Math.min(ratio.length - 1, page));
        current = page;
        setTop(tops[page]);
        onPage.accept(page);
    }

    @Override
    public void next() {
        double max = Math.max(0, totalH - viewport.getHeight());
        if (top >= max - 1) {
            onEndForward.run();
            return;
        }
        setTop(top + viewport.getHeight() * 0.85);
    }

    @Override
    public void prev() {
        setTop(top - viewport.getHeight() * 0.85);
    }

    @Override
    public boolean onKey(KeyEvent e) {
        switch (e.getCode()) {
            case DOWN, S -> setTop(top + viewport.getHeight() * 0.15);
            case UP, W -> setTop(top - viewport.getHeight() * 0.15);
            case SPACE, PAGE_DOWN -> {
                if (e.isShiftDown()) prev();
                else next();
            }
            case PAGE_UP -> prev();
            case RIGHT, D -> go(current + 1, false);
            case LEFT, A -> go(current - 1, false);
            case HOME -> go(0, false);
            case END -> setTop(totalH);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void dispose() {
        disposed = true;
        for (ImageView iv : live.values()) iv.setImage(null);
        live.clear();
    }
}
