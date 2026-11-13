package app.koma.ui.home;

import app.koma.AppContext;
import app.koma.core.Anim;
import app.koma.library.Series;
import app.koma.library.Volume;
import app.koma.ui.shared.CoverView;
import app.koma.ui.shell.Screen;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/** The next three volumes of the series, or "All caught up". */
final class UpNext extends VBox {
    private final AppContext ctx;
    private final HBox row = new HBox(14);

    UpNext(AppContext ctx) {
        this.ctx = ctx;
        setSpacing(14);
        Label heading = new Label("UP NEXT IN THIS SERIES");
        heading.getStyleClass().add("display-s");
        VBox.setVgrow(row, Priority.ALWAYS);
        getChildren().addAll(heading, row);
    }

    void show(Series s) {
        row.getChildren().clear();
        List<Volume> sorted = s.sortedVolumes();
        Volume cur = s.current();
        int i = cur == null ? -1 : sorted.indexOf(cur);
        List<Volume> next = new ArrayList<>();
        for (int k = i + 1; k < sorted.size() && next.size() < 3; k++) next.add(sorted.get(k));
        if (next.isEmpty()) {
            Label done = new Label("ALL CAUGHT UP");
            done.getStyleClass().add("display-m");
            Label sub = new Label("No more volumes of " + s.title + " in your library.");
            sub.getStyleClass().add("muted");
            sub.setWrapText(true);
            double nextNum = sorted.isEmpty() ? 1 : Math.floor(sorted.get(sorted.size() - 1).number) + 1;
            Button imp = new Button("IMPORT №" + Volume.num(nextNum));
            imp.getStyleClass().add("ink-button");
            imp.setOnAction(e -> ctx.window.nav().chooseFiles());
            VBox box = new VBox(10, done, sub, imp);
            box.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().add(box);
            return;
        }
        List<Node> items = new ArrayList<>();
        for (Volume v : next) {
            CoverView c = new CoverView(ctx, s, v, 118);
            c.getStyleClass().add("hover-lift");
            c.setOnMouseClicked(e -> ctx.router.navigate(Screen.READER, v.id));
            Label l = new Label("№" + v.label() + " · " + (v.isFinished() ? "READ" : v.isStarted() ? "OPEN" : "UNREAD"));
            l.getStyleClass().addAll("mono", "strong");
            VBox cell = new VBox(6, c, l);
            cell.setMaxWidth(118);
            HBox.setHgrow(cell, Priority.ALWAYS);
            items.add(cell);
        }
        row.getChildren().setAll(items);
        Anim.stagger(items);
    }
}
