package app.koma.ui.shared;

import app.koma.AppContext;
import app.koma.core.Anim;
import app.koma.core.Exec;
import app.koma.core.MotionPrefs;
import app.koma.library.Series;
import app.koma.library.Volume;
import javafx.animation.FadeTransition;
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
    private final VBox jpBox = new VBox(-4);
    private final Label title = new Label();
    private final Label kind = new Label();
    private final ImageView image = new ImageView();
    private final Label badge = new Label();
    private final Region frame = new Region();
    private long titleSize = -1;

    public CoverView(AppContext ctx, Series s, Volume v, double prefWidth) {
        getStyleClass().add("cover");
        setPrefWidth(prefWidth);
        setMinWidth(40);
        placeholder.getStyleClass().add("cover-placeholder");
        tone = new Halftone(ctx.theme);
        dot.getStyleClass().add("cover-dot");
        title.getStyleClass().add("cover-title");
        title.setText(Fmt.upper(s.title));
        kind.getStyleClass().add("mono-s");
        kind.setText(Fmt.upper(s.type));
        if (s.titleJp != null) {
            for (int i = 0; i < Math.min(8, s.titleJp.length()); i++) {
                Label c = new Label(String.valueOf(s.titleJp.charAt(i)));
                c.getStyleClass().add("cover-jp");
                jpBox.getChildren().add(c);
            }
        }
        image.setSmooth(true);
        badge.getStyleClass().add("cover-badge");
        badge.setVisible(v != null);
        if (v != null) badge.setText(v.label());
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

        Volume src = v != null ? v : (s.sortedVolumes().isEmpty() ? null : s.sortedVolumes().get(0));
        if (src != null) load(ctx, src);
    }

    public void setBadgeVisible(boolean visible) {
        badge.setVisible(visible);
    }

    private void load(AppContext ctx, Volume v) {
        String key = app.koma.imaging.ThumbCache.key(v.path, v.size, v.mtime);
        Image hit = ctx.thumbs.peek(key);
        if (hit != null) {
            setImage(hit, false);
            return;
        }
        ctx.thumbs.cover(v.path, v.size, v.mtime).whenComplete((img, err) -> {
            if (img != null) Exec.fx(() -> setImage(img, true));
        });
    }

    private void setImage(Image img, boolean fade) {
        image.setImage(img);
        requestLayout();
        if (fade && !MotionPrefs.reduced()) {
            image.setOpacity(0);
            FadeTransition f = new FadeTransition(Anim.UI, image);
            f.setToValue(1);
            f.play();
        }
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
        kind.setVisible(w > 120);
        jpBox.setVisible(w > 90);

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
