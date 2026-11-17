package app.koma.ui.shared;

import app.koma.AppContext;
import app.koma.core.Exec;
import app.koma.library.Series;
import app.koma.library.Volume;
import javafx.geometry.Orientation;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/**
 * A 3:4.3 cover with a 3px ink frame: the first image of the CBZ (from the thumbnail cache),
 * over a generated ink placeholder while it loads or if the archive can't be read.
 */
public class CoverView extends Region {
    public static final double RATIO = 4.3 / 3.0;

    private final Region placeholder = new Region();
    private final Halftone tone;
    private final Circle dot = new Circle();
    /** The vertical kanji title: ONE label with a character per line (eight separate labels were ~24 nodes per cover). */
    private final Label jpBox = new Label();
    private final Label title = new Label();
    private final Label kind = new Label();
    private final ImageView image = new ImageView();
    private final Label badge = new Label();
    private final Region frame = new Region();
    private final AppContext ctx;
    private long titleSize = -1;
    private int bindId;

    public CoverView(AppContext ctx, Series s, Volume v, double prefWidth) {
        this(ctx, prefWidth);
        bind(s, v);
    }

    /** An empty cover for pooling: call {@link #bind} to show a series (or one of its volumes). */
    public CoverView(AppContext ctx, double prefWidth) {
        this.ctx = ctx;
        getStyleClass().add("cover");
        setPrefWidth(prefWidth);
        setMinWidth(40);
        placeholder.getStyleClass().add("cover-placeholder");
        tone = new Halftone(ctx.theme);
        dot.getStyleClass().add("cover-dot");
        title.getStyleClass().add("cover-title");
        kind.getStyleClass().add("mono-s");
        jpBox.getStyleClass().add("cover-jp");
        jpBox.setLineSpacing(-4);
        image.setSmooth(true);
        badge.getStyleClass().add("cover-badge");
        badge.setVisible(false);
        frame.getStyleClass().add("cover-frame");
        frame.setMouseTransparent(true);
        // Children sit in a clipped group so a CSS offset shadow on this node is not clipped away.
        javafx.scene.Group content = new javafx.scene.Group(placeholder, tone, dot, jpBox, title, kind, image, badge, frame);
        content.setAutoSizeChildren(false);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        content.setClip(clip);
        getChildren().add(content);
    }

    /** Shows a series (v == null: its first volume's cover, no volume badge) or one specific volume. */
    public void bind(Series s, Volume v) {
        int id = ++bindId;
        title.setText(Fmt.upper(s.title));
        kind.setText(Fmt.upper(s.type));
        hasJp = s.titleJp != null && !s.titleJp.isEmpty();
        if (hasJp) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(8, s.titleJp.length()); i++) {
                if (i > 0) sb.append('\n');
                sb.append(s.titleJp.charAt(i));
            }
            jpBox.setText(sb.toString());
        }
        badge.setVisible(v != null && badgeWanted);
        if (v != null) badge.setText(v.label());
        image.setImage(null);
        showPlaceholder(true);
        Volume src = v != null ? v : (s.sortedVolumes().isEmpty() ? null : s.sortedVolumes().get(0));
        if (src != null) load(src, id);
        requestLayout();
    }

    private boolean badgeWanted = true;

    public void setBadgeVisible(boolean visible) {
        badgeWanted = visible;
        badge.setVisible(visible && !badge.getText().isEmpty());
    }

    private void load(Volume v, int id) {
        String key = app.koma.imaging.ThumbCache.key(v.path, v.size, v.mtime);
        Image hit = ctx.thumbs.peek(key);
        if (hit != null) {
            setImage(hit);
            return;
        }
        // a pooled cover may be re-bound to another series while this loads: skip the work for covers scrolled past
        ctx.thumbs.cover(v.path, v.size, v.mtime, () -> id == bindId).whenComplete((img, err) -> {
            if (img != null) {
                Exec.fx(() -> {
                    if (id == bindId) setImage(img);
                });
            } else if (err == null) {
                // skipped by someone else's request sharing this load: ask again if we are still showing it
                Exec.fx(() -> {
                    if (id != bindId) return;
                    ctx.thumbs.cover(v.path, v.size, v.mtime).thenAccept(i2 -> Exec.fx(() -> {
                        if (i2 != null && id == bindId) setImage(i2);
                    }));
                });
            }
        });
    }

    private void setImage(Image img) {
        image.setImage(img);
        showPlaceholder(false);
        requestLayout();
    }

    private boolean placeholderOn = true;
    private boolean hasJp;

    /** The ink placeholder (tone, dot, title, kanji) is hidden once the real cover is there: it is fully covered, and painting it is wasted work. */
    private void showPlaceholder(boolean on) {
        placeholderOn = on;
        placeholder.setVisible(on);
        tone.setVisible(on);
        dot.setVisible(on);
        title.setVisible(on);
        kind.setVisible(on && getWidth() > 120);
        jpBox.setVisible(on && hasJp && getWidth() > 90);
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computePrefHeight(double width) {
        double w = width > 0 ? width : getPrefWidth();
        return Math.round(w * RATIO);
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected double computeMaxHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth(), h = getHeight();
        placeholder.resizeRelocate(0, 0, w, h);
        tone.resizeRelocate(0, 0, w, h);
        double r = w * 0.24;
        dot.setRadius(r);
        dot.setCenterX(w * 0.42);
        dot.setCenterY(h * 0.36);
        double pad = Math.max(6, w * 0.07);
        double jw = jpBox.prefWidth(-1), jh = Math.min(h * 0.6, jpBox.prefHeight(-1));
        jpBox.resizeRelocate(w - pad - jw, pad, jw, jh);
        long fs = Math.round(Math.max(10, w * 0.12));
        if (fs != titleSize) { // setStyle re-applies CSS, so only when the size actually changes
            titleSize = fs;
            title.setStyle("-fx-font-size: " + fs + "px;");
        }
        double th = title.prefHeight(w - 2 * pad);
        title.resizeRelocate(pad, h - pad - th - 14, w - 2 * pad, th);
        kind.resizeRelocate(pad, h - pad - 12, w - 2 * pad, 12);
        kind.setVisible(placeholderOn && w > 120);
        jpBox.setVisible(placeholderOn && hasJp && w > 90);

        Image img = image.getImage();
        if (img != null && img.getWidth() > 0) {
            // crop to fill (object-fit: cover), anchored at the top
            double iw = img.getWidth(), ih = img.getHeight();
            double scale = Math.max(w / iw, h / ih);
            double vw = w / scale, vh = h / scale;
            image.setViewport(new Rectangle2D((iw - vw) / 2, 0, vw, vh));
            image.setFitWidth(w);
            image.setFitHeight(h);
            image.relocate(0, 0);
        }
        double bw = badge.prefWidth(-1), bh = badge.prefHeight(-1);
        badge.resizeRelocate(w - bw - 6, h - bh - 6, bw, bh);
        frame.resizeRelocate(0, 0, w, h);
    }
}
