package app.koma.ui.home;

import app.koma.core.Anim;
import app.koma.core.MotionPrefs;
import app.koma.library.Series;
import app.koma.library.Volume;
import app.koma.ui.shared.Fmt;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

/** Three stacked cards (accent, grey, paper) with the session log and a rotated stamp; fans out on hover. */
final class ReadingCard extends VBox {
    private final Label heading = new Label();
    private final Region back1 = new Region();
    private final Region back2 = new Region();
    private final VBox front = new VBox(4);
    private final Label stamp = new Label();
    private final StackPane stack;

    ReadingCard() {
        getStyleClass().add("reading-card");
        setSpacing(14);
        heading.getStyleClass().add("display-s");
        back1.getStyleClass().add("card-back-acc");
        back2.getStyleClass().add("card-back-grey");
        front.getStyleClass().add("card-front");
        stamp.getStyleClass().add("stamp");
        stamp.setRotate(-9);
        stamp.setMouseTransparent(true);

        stack = new StackPane(back1, back2, front, stamp);
        StackPane.setAlignment(stamp, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(stamp, new javafx.geometry.Insets(0, 4, 4, 0));
        stack.setPadding(new javafx.geometry.Insets(6, 18, 6, 18));
        back1.setRotate(-4);
        back2.setRotate(3);
        VBox.setVgrow(stack, Priority.ALWAYS);
        getChildren().addAll(heading, stack);

        stack.setOnMouseEntered(e -> fan(true));
        stack.setOnMouseExited(e -> fan(false));
    }

    void show(Series s) {
        Volume v = s.current();
        heading.setText("READING CARD · VOL. " + (v == null ? "—" : v.label()));
        front.getChildren().clear();
        Label head = new Label("SESSIONS");
        head.getStyleClass().addAll("mono", "strong");
        Label vol = new Label(v == null ? "" : "VOL " + v.label());
        vol.getStyleClass().addAll("mono", "strong");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox top = new HBox(head, sp, vol);
        Region rule = new Region();
        rule.getStyleClass().add("card-rule");
        front.getChildren().addAll(top, rule);

        GridPane rows = new GridPane();
        rows.setVgap(6);
        for (double pct : new double[]{34, 38, 28}) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(pct);
            rows.getColumnConstraints().add(c);
        }
        List<Volume.Session> sessions = v == null ? List.of() : v.sessions;
        int r = 0;
        for (Volume.Session ss : sessions.subList(0, Math.min(4, sessions.size()))) {
            rows.add(mono(Fmt.shortDate(ss.date)), 0, r);
            rows.add(mono("p." + Math.min(ss.from, ss.to) + "–" + Math.max(ss.from, ss.to)), 1, r);
            rows.add(mono(Math.abs(ss.to - ss.from) + " pp"), 2, r);
            r++;
        }
        if (r == 0) rows.add(mono("No sessions yet. Open a volume to start."), 0, 0, 3, 1);
        front.getChildren().add(rows);

        if (v == null || !v.isStarted()) stamp.setText("NOT STARTED");
        else if (v.isFinished()) stamp.setText("FINISHED");
        else stamp.setText("IN PROGRESS");
        Anim.slam(stamp, Duration.millis(120));
    }

    private void fan(boolean out) {
        if (MotionPrefs.reduced()) return;
        RotateTransition r1 = new RotateTransition(Anim.UI, back1);
        r1.setToAngle(out ? -8 : -4);
        RotateTransition r2 = new RotateTransition(Anim.UI, back2);
        r2.setToAngle(out ? 6 : 3);
        TranslateTransition t1 = new TranslateTransition(Anim.UI, back1);
        t1.setToX(out ? -10 : 0);
        TranslateTransition t2 = new TranslateTransition(Anim.UI, back2);
        t2.setToX(out ? 10 : 0);
        ParallelTransition p = new ParallelTransition(r1, r2, t1, t2);
        p.setInterpolator(Anim.EASE);
        p.play();
    }

    private static Label mono(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("mono");
        return l;
    }
}
