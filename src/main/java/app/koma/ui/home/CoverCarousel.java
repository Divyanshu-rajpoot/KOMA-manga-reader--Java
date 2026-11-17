package app.koma.ui.home;

import app.koma.AppContext;
import app.koma.core.Anim;
import app.koma.core.MotionPrefs;
import app.koma.library.Series;
import app.koma.ui.shared.CoverView;
import app.koma.ui.shared.Fmt;
import app.koma.ui.shared.Halftone;
import app.koma.ui.shared.Icons;
import app.koma.ui.shared.SpeedBurst;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.PerspectiveTransform;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Rotating 3D cover carousel on a halftone + speed-line stage.
 * Perspective is computed per card and applied with a {@link PerspectiveTransform} effect, so the
 * cards stay in the main scene (CSS and theme tokens apply) instead of a separate SubScene.
 * Geometry: side cards at translateX(d·175) translateZ(−|d|·170) rotateY(−d·30°); 700 ms per step,
 * auto-advance every 3.6 s, paused on hover and when motion is reduced.
 */
public final class CoverCarousel extends StackPane {
    private static final double FOCAL = 900;

    private final List<Series> series;
    private final Consumer<Series> onSelect;
    private final Consumer<Series> onOpen;
    private final List<CoverView> cards = new ArrayList<>();
    private final Pane stageLayer = new Pane();
    private final Region frontShadow = new Region();
    private final DoubleProperty pos = new SimpleDoubleProperty(0);
    private final Label counter = new Label();
    private final Label bubbleText = new Label();
    private final Timeline auto;
    private Timeline turn;
    private int index;
    // one effect pair per card, mutated every frame instead of allocating new ones (60 x 2 objects per second per card)
    private final List<PerspectiveTransform> perspectives = new ArrayList<>();
    private final List<ColorAdjust> grays = new ArrayList<>();
    private double lastW = -1, lastH = -1;
    private final javafx.beans.value.ChangeListener<Boolean> motionListener;

    public CoverCarousel(AppContext ctx, List<Series> series, Consumer<Series> onSelect, Consumer<Series> onOpen) {
        this.series = series;
        this.onSelect = onSelect;
        this.onOpen = onOpen;
        getStyleClass().add("carousel");
        setMinHeight(360);

        Halftone tone = new Halftone(ctx.theme);
        SpeedBurst burst = new SpeedBurst(0.09);

        frontShadow.getStyleClass().add("carousel-shadow");
        frontShadow.setManaged(false);
        stageLayer.getChildren().add(frontShadow);
        for (Series s : series) {
            CoverView c = new CoverView(ctx, s, s.current(), 180);
            c.setManaged(false);
            c.setMouseTransparent(true);
            ColorAdjust gray = new ColorAdjust();
            PerspectiveTransform pt = new PerspectiveTransform();
            pt.setInput(gray);
            c.setEffect(pt);
            grays.add(gray);
            perspectives.add(pt);
            cards.add(c);
            stageLayer.getChildren().add(c);
        }
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        Label tag = new Label("IN PROGRESS · " + series.size() + (series.size() == 1 ? " SERIES" : " SERIES"));
        tag.getStyleClass().add("label-box");
        StackPane.setAlignment(tag, Pos.TOP_LEFT);
        StackPane.setMargin(tag, new javafx.geometry.Insets(18, 0, 0, 18));

        Ellipse oval = new Ellipse(48, 38);
        oval.getStyleClass().add("bubble-shape");
        bubbleText.getStyleClass().add("bubble-text");
        bubbleText.setWrapText(true);
        bubbleText.setMaxWidth(80);
        bubbleText.setAlignment(Pos.CENTER);
        StackPane bubble = new StackPane(oval, bubbleText);
        bubble.setMaxSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);
        StackPane.setAlignment(bubble, Pos.TOP_RIGHT);
        StackPane.setMargin(bubble, new javafx.geometry.Insets(20, 26, 0, 0));

        Button prev = arrow("tri-left");
        prev.setOnAction(e -> step(-1));
        Button next = arrow("tri-right");
        next.setOnAction(e -> step(1));
        counter.getStyleClass().add("carousel-counter");
        HBox controls = new HBox(12, prev, counter, next);
        controls.setAlignment(Pos.CENTER);
        controls.setMaxHeight(HBox.USE_PREF_SIZE);
        StackPane.setAlignment(controls, Pos.BOTTOM_CENTER);
        StackPane.setMargin(controls, new javafx.geometry.Insets(0, 0, 14, 0));
        controls.setVisible(series.size() > 1);

        getChildren().addAll(tone, burst, stageLayer, tag, bubble, controls);

        stageLayer.setOnMouseClicked(e -> {
            double cx = getWidth() / 2, half = cardWidth() / 2;
            if (Math.abs(e.getX() - cx) <= half) onOpen.accept(current());
            else step(e.getX() < cx ? -1 : 1);
        });
        stageLayer.setStyle("-fx-cursor: hand;");

        pos.addListener((o, a, b) -> requestLayout());
        auto = new Timeline(new KeyFrame(Anim.CAROUSEL_AUTO, e -> {
            // no auto-advance while hovered or while the window is in the background
            boolean focused = getScene() != null && getScene().getWindow() != null && getScene().getWindow().isFocused();
            if (!isHover() && focused && series.size() > 1) step(1);
        }));
        auto.setCycleCount(Animation.INDEFINITE);
        // weak: the property is global and would otherwise keep every Home screen (and its covers) alive
        motionListener = (o, a, r) -> {
            if (r) auto.stop();
            else if (getScene() != null) auto.play();
        };
        MotionPrefs.reducedProperty().addListener(new javafx.beans.value.WeakChangeListener<>(motionListener));
        sceneProperty().addListener((o, a, s) -> {
            if (s == null) auto.stop();
            else Anim.play(auto);
        });
        updateLabels();
    }

    public Series current() {
        return series.get(index);
    }

    public void step(int dir) {
        if (series.size() < 2) return;
        index = Math.floorMod(index + dir, series.size());
        double target = Math.round(pos.get()) + dir;
        if (turn != null) turn.stop();
        if (MotionPrefs.reduced()) {
            pos.set(target);
        } else {
            turn = new Timeline(new KeyFrame(Anim.CAROUSEL, new KeyValue(pos, target, Anim.EASE)));
            turn.play();
        }
        auto.playFromStart();
        if (MotionPrefs.reduced()) auto.stop();
        updateLabels();
        onSelect.accept(current());
    }

    public void stop() {
        auto.stop();
        if (turn != null) turn.stop();
    }

    private void updateLabels() {
        counter.setText((index + 1) + " / " + series.size());
        bubbleText.setText(Fmt.ago(current().lastReadAt()) + "!");
    }

    private double cardHeight() {
        return Math.max(160, Math.min(getHeight() * 0.64, 320));
    }

    private double cardWidth() {
        return cardHeight() / CoverView.RATIO;
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        int n = cards.size();
        if (n == 0) return;
        double h = cardHeight(), w = cardWidth();
        double k = w / 180.0;
        double cx = getWidth() / 2, cy = getHeight() / 2 - 14;
        double p = pos.get();
        double nearest = Double.MAX_VALUE;

        List<Node> order = new ArrayList<>();
        List<double[]> depth = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            CoverView c = cards.get(i);
            double d = i - p;
            d = ((d % n) + n) % n;
            if (d > n / 2.0) d -= n;
            double ad = Math.abs(d);
            nearest = Math.min(nearest, ad);
            if (w != lastW || h != lastH) {
                c.resize(w, h);
                c.relocate(0, 0);
                c.layout();
            }
            if (ad > 2.6) {
                c.setVisible(false);
                continue;
            }
            c.setVisible(true);
            c.setOpacity(ad > 2 ? Math.max(0, (2.6 - ad) / 0.6) : 1);
            double tx = d * 175 * k, zc = ad * 170 * k;
            double a = Math.toRadians(30 * d);
            double[] pts = new double[8];
            double[][] corners = {{-w / 2, -h / 2}, {w / 2, -h / 2}, {w / 2, h / 2}, {-w / 2, h / 2}};
            for (int j = 0; j < 4; j++) {
                double xl = corners[j][0], yl = corners[j][1];
                double dep = zc + xl * Math.sin(a);
                double s = FOCAL / (FOCAL + dep);
                pts[j * 2] = cx + (tx + xl * Math.cos(a)) * s;
                pts[j * 2 + 1] = cy + yl * s;
            }
            ColorAdjust gray = grays.get(i);
            gray.setSaturation(-Math.min(1, ad) * 0.85);
            gray.setBrightness(-Math.min(1, ad) * 0.08);
            PerspectiveTransform pt = perspectives.get(i);
            pt.setUlx(pts[0]);
            pt.setUly(pts[1]);
            pt.setUrx(pts[2]);
            pt.setUry(pts[3]);
            pt.setLrx(pts[4]);
            pt.setLry(pts[5]);
            pt.setLlx(pts[6]);
            pt.setLly(pts[7]);
            order.add(c);
            depth.add(new double[]{ad});
        }
        // back-to-front
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) idx.add(i);
        idx.sort(Comparator.comparingDouble((Integer i) -> -depth.get(i)[0]));
        List<Node> sorted = new ArrayList<>();
        sorted.add(frontShadow);
        for (int i : idx) sorted.add(order.get(i));
        for (CoverView c : cards) if (!c.isVisible()) sorted.add(0, c);
        if (!sorted.equals(stageLayer.getChildren())) stageLayer.getChildren().setAll(sorted);

        lastW = w;
        lastH = h;
        frontShadow.resizeRelocate(cx - w / 2 + 10, cy - h / 2 + 10, w, h);
        frontShadow.setOpacity(Math.max(0, 1 - nearest * 2.5));
    }

    private static Button arrow(String icon) {
        Button b = new Button();
        b.setGraphic(Icons.of(icon, 14));
        b.getStyleClass().addAll("icon-button", "carousel-arrow");
        b.setFocusTraversable(false);
        return b;
    }
}
