package app.koma.core;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.List;

/**
 * Motion tokens from DESIGN.md and helpers that respect "reduce motion":
 * loops are skipped and transitions finish instantly.
 */
public final class Anim {
    private Anim() {}

    public static final Duration MICRO = Duration.millis(140);
    public static final Duration UI = Duration.millis(260);
    public static final Duration PAGE = Duration.millis(420);
    public static final Duration SLAM = Duration.millis(500);
    public static final Duration CAROUSEL = Duration.millis(700);
    public static final Duration CAROUSEL_AUTO = Duration.millis(3600);
    public static final Duration STAGGER = Duration.millis(50);

    /** cubic-bezier(.2,.8,.2,1) */
    public static final Interpolator EASE = new CubicBezier(.2, .8, .2, 1);
    /** cubic-bezier(.3,1.3,.5,1): overshoots, so it can't be expressed with Interpolator.SPLINE. */
    public static final Interpolator SLAM_EASE = new CubicBezier(.3, 1.3, .5, 1);

    /** Plays an animation, or completes it at once when motion is reduced. Loops are not started at all. */
    public static void play(Animation a) {
        if (MotionPrefs.reduced()) {
            if (a.getCycleCount() == Animation.INDEFINITE) return;
            a.setRate(10_000);
        }
        a.play();
    }

    /** Fade + rise 12px. */
    public static void riseIn(Node n, Duration delay) {
        n.setOpacity(0);
        FadeTransition f = new FadeTransition(UI, n);
        f.setToValue(1);
        TranslateTransition t = new TranslateTransition(UI, n);
        t.setFromY(12);
        t.setToY(0);
        ParallelTransition p = new ParallelTransition(n, f, t);
        p.setInterpolator(EASE);
        f.setInterpolator(EASE);
        t.setInterpolator(EASE);
        p.setDelay(MotionPrefs.reduced() ? Duration.ZERO : delay);
        play(p);
    }

    /** Lists stagger in at 50 ms per item (capped so long lists don't drag). */
    public static void stagger(List<? extends Node> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            riseIn(nodes.get(i), STAGGER.multiply(Math.min(i, 16)));
        }
    }

    /** Titles, panels and stamps slam in: scale from 1.25 with an overshooting curve. */
    public static void slam(Node n, Duration delay) {
        n.setOpacity(0);
        ScaleTransition s = new ScaleTransition(SLAM, n);
        s.setFromX(1.25);
        s.setFromY(1.25);
        s.setToX(1);
        s.setToY(1);
        s.setInterpolator(SLAM_EASE);
        FadeTransition f = new FadeTransition(Duration.millis(180), n);
        f.setToValue(1);
        ParallelTransition p = new ParallelTransition(n, s, f);
        p.setDelay(MotionPrefs.reduced() ? Duration.ZERO : delay);
        play(p);
    }

    /** CSS-style cubic-bezier easing; allows control points outside [0,1] on the y axis. */
    static final class CubicBezier extends Interpolator {
        private final double x1, y1, x2, y2;

        CubicBezier(double x1, double y1, double x2, double y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        private static double bez(double t, double p1, double p2) {
            double u = 1 - t;
            return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t;
        }

        private static double dBez(double t, double p1, double p2) {
            double u = 1 - t;
            return 3 * u * u * p1 + 6 * u * t * (p2 - p1) + 3 * t * t * (1 - p2);
        }

        @Override
        protected double curve(double x) {
            if (x <= 0) return 0;
            if (x >= 1) return 1;
            double t = x;
            for (int i = 0; i < 8; i++) {
                double err = bez(t, x1, x2) - x;
                if (Math.abs(err) < 1e-5) break;
                double d = dBez(t, x1, x2);
                if (Math.abs(d) < 1e-6) break;
                t -= err / d;
            }
            t = Math.max(0, Math.min(1, t));
            return bez(t, y1, y2);
        }
    }
}
